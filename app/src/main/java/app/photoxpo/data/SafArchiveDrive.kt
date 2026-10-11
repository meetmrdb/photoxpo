package app.photoxpo.data

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import app.photoxpo.core.ArchiveDrive
import app.photoxpo.core.CreateResult
import app.photoxpo.core.RenameResult
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Archive Drive rooted at a persisted SAF tree.
 * Paths are relative to that tree, for example "Photoxpo/Originals/2020/2020-01/IMG.jpg".
 */
class SafArchiveDrive(
    private val context: Context,
    private val treeUri: Uri,
) : ArchiveDrive {
    private val resolver get() = context.contentResolver
    private val gate = Any()
    private val docs = HashMap<String, Doc>()

    override fun create(path: String): CreateResult {
        return synchronized(gate) {
            val segments = requireSegments(path)
            val parent = ensureDirectory(segments.dropLast(1))
            val name = segments.last()
            if (findChild(parent.documentId, name) != null) {
                return@synchronized CreateResult.Collision(path)
            }
            val created = DocumentsContract.createDocument(
                resolver,
                documentUri(parent.documentId),
                OCTET_STREAM,
                name,
            ) ?: throw IOException("Could not create $path on the Archive Drive")
            val newId = DocumentsContract.getDocumentId(created)
            val actual = displayName(newId)
                ?: throw IOException("Archive Drive did not return a name for $path")
            if (actual != name) {
                if (!DocumentsContract.deleteDocument(resolver, documentUri(newId))) {
                    throw IOException("Archive Drive created $actual instead of $name")
                }
                return@synchronized CreateResult.Collision(path)
            }
            remember(segments, newId, directory = false)
            CreateResult.Created
        }
    }

    override fun write(path: String): OutputStream {
        val uri = synchronized(gate) { documentUri(requireDoc(path).documentId) }
        val pfd = resolver.openFileDescriptor(uri, "wt") ?: throw FileNotFoundException(path)
        return SyncingOutputStream(pfd)
    }

    override fun read(path: String): InputStream {
        val uri = synchronized(gate) { documentUri(requireDoc(path).documentId) }
        val pfd = resolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException(path)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd)
    }

    override fun byteCount(path: String): Long {
        val uri = synchronized(gate) { documentUri(requireDoc(path).documentId) }
        val pfd = resolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException(path)
        pfd.use { descriptor ->
            val size = descriptor.statSize
            if (size < 0) throw IOException("Archive Drive did not report a size for $path")
            return size
        }
    }

    override fun rename(from: String, to: String): RenameResult {
        return synchronized(gate) {
            val fromSegments = requireSegments(from)
            val toSegments = requireSegments(to)
            if (fromSegments == toSegments) return@synchronized RenameResult.Renamed
            val source = requireDoc(from)
            val targetParent = ensureDirectory(toSegments.dropLast(1))
            val targetName = toSegments.last()
            if (findChild(targetParent.documentId, targetName) != null) {
                return@synchronized RenameResult.Collision(to)
            }
            val newId = if (fromSegments.dropLast(1) == toSegments.dropLast(1)) {
                renameOrNull(source.documentId, fromSegments.last(), targetName)
            } else {
                moveThenRename(source.documentId, fromSegments, toSegments, targetParent.documentId)
            }
            if (newId == null) return@synchronized RenameResult.Collision(to)
            invalidate(archivePathKey(fromSegments))
            invalidate(archivePathKey(toSegments))
            RenameResult.Renamed
        }
    }

    override fun exists(path: String): Boolean {
        return synchronized(gate) {
            documentAt(parseArchivePath(path).segments) != null
        }
    }

    override fun list(directory: String): List<String> {
        return synchronized(gate) {
            val segments = parseArchivePath(directory).segments
            val doc = documentAt(segments) ?: return@synchronized emptyList()
            if (segments.isNotEmpty() && !doc.directory) return@synchronized emptyList()
            children(doc.documentId).map { it.displayName }.sorted()
        }
    }

    override fun readText(path: String): String {
        return read(path).use { input -> input.readBytes() }.toString(Charsets.UTF_8)
    }

    override fun appendText(path: String, text: String) {
        // "wa" is not used. Read the current bytes, then write the combined text with "wt".
        if (!exists(path)) {
            create(path)
        }
        val combined = readText(path) + text
        write(path).use { output ->
            output.write(combined.toByteArray(Charsets.UTF_8))
        }
    }

    override fun delete(path: String) {
        synchronized(gate) {
            val segments = requireSegments(path)
            val doc = documentAt(segments) ?: return
            val key = archivePathKey(segments)
            invalidate(key)
            if (!DocumentsContract.deleteDocument(resolver, documentUri(doc.documentId))) {
                throw IOException("Could not delete $path on the Archive Drive")
            }
        }
    }

    private fun requireSegments(path: String): List<String> {
        val segments = parseArchivePath(path).segments
        require(segments.isNotEmpty()) { "path is empty" }
        return segments
    }

    private fun requireDoc(path: String): Doc {
        val segments = requireSegments(path)
        return documentAt(segments) ?: throw FileNotFoundException(path)
    }

    private fun documentAt(segments: List<String>): Doc? {
        if (segments.isEmpty()) return rootDoc()
        docs[archivePathKey(segments)]?.let { return it }
        var parent = rootDoc()
        val built = ArrayList<String>(segments.size)
        for (segment in segments) {
            built += segment
            val key = archivePathKey(built)
            val cached = docs[key]
            if (cached != null) {
                parent = cached
                continue
            }
            val child = findChild(parent.documentId, segment) ?: return null
            val directory = child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR
            if (built.size != segments.size && !directory) return null
            val doc = Doc(child.documentId, directory)
            docs[key] = doc
            parent = doc
        }
        return parent
    }

    private fun ensureDirectory(segments: List<String>): Doc {
        var parent = rootDoc()
        val built = ArrayList<String>(segments.size)
        for (segment in segments) {
            built += segment
            val key = archivePathKey(built)
            val cached = docs[key]
            if (cached != null) {
                if (!cached.directory) {
                    throw IOException("$key is not a directory on the Archive Drive")
                }
                parent = cached
                continue
            }
            val child = findChild(parent.documentId, segment)
            if (child == null) {
                val created = DocumentsContract.createDocument(
                    resolver,
                    documentUri(parent.documentId),
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    segment,
                ) ?: throw IOException("Could not create directory $key on the Archive Drive")
                val newId = DocumentsContract.getDocumentId(created)
                val actual = displayName(newId)
                    ?: throw IOException("Archive Drive did not return a name for $key")
                if (actual != segment) {
                    DocumentsContract.deleteDocument(resolver, documentUri(newId))
                    throw IOException("Archive Drive created directory $actual instead of $segment")
                }
                val doc = Doc(newId, directory = true)
                docs[key] = doc
                parent = doc
            } else {
                if (child.mimeType != DocumentsContract.Document.MIME_TYPE_DIR) {
                    throw IOException("$key is not a directory on the Archive Drive")
                }
                val doc = Doc(child.documentId, directory = true)
                docs[key] = doc
                parent = doc
            }
        }
        return parent
    }

    private fun renameOrNull(documentId: String, fromName: String, toName: String): String? {
        val renamed = DocumentsContract.renameDocument(resolver, documentUri(documentId), toName)
            ?: throw IOException("Rename failed on the Archive Drive")
        val newId = DocumentsContract.getDocumentId(renamed)
        val actual = displayName(newId)
            ?: throw IOException("Archive Drive did not return a name after rename")
        if (actual == toName) return newId
        val restored = DocumentsContract.renameDocument(resolver, documentUri(newId), fromName)
        if (restored == null || displayName(DocumentsContract.getDocumentId(restored)) != fromName) {
            throw IOException("Archive Drive renamed $fromName to $actual")
        }
        return null
    }

    private fun moveThenRename(
        sourceId: String,
        fromSegments: List<String>,
        toSegments: List<String>,
        targetParentId: String,
    ): String? {
        val sourceParentSegments = fromSegments.dropLast(1)
        val sourceParentId = if (sourceParentSegments.isEmpty()) {
            rootDoc().documentId
        } else {
            documentAt(sourceParentSegments)?.documentId
                ?: throw FileNotFoundException(archivePathKey(fromSegments))
        }
        val moved = DocumentsContract.moveDocument(
            resolver,
            documentUri(sourceId),
            documentUri(sourceParentId),
            documentUri(targetParentId),
        ) ?: throw IOException("Move failed on the Archive Drive")
        val movedId = DocumentsContract.getDocumentId(moved)
        if (fromSegments.last() == toSegments.last()) return movedId
        return renameOrNull(movedId, fromSegments.last(), toSegments.last())
    }

    private fun findChild(parentId: String, name: String): Child? {
        return children(parentId).firstOrNull { it.displayName == name }
    }

    private fun children(parentId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val cursor = resolver.query(uri, CHILD_COLUMNS, null, null, null)
            ?: throw IOException("Archive Drive is absent")
        val rows = ArrayList<Child>()
        cursor.use { result ->
            val idCol = result.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = result.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = result.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (result.moveToNext()) {
                val name = result.getString(nameCol) ?: continue
                val mime = result.getString(mimeCol)
                rows += Child(
                    documentId = result.getString(idCol),
                    displayName = name,
                    mimeType = mime,
                )
            }
        }
        return rows
    }

    private fun displayName(documentId: String): String? {
        val cursor = resolver.query(
            documentUri(documentId),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        ) ?: return null
        cursor.use { result ->
            if (!result.moveToFirst() || result.isNull(0)) return null
            return result.getString(0)
        }
    }

    private fun rootDoc(): Doc {
        return docs.getOrPut("") {
            Doc(
                documentId = DocumentsContract.getTreeDocumentId(treeUri),
                directory = true,
            )
        }
    }

    private fun remember(segments: List<String>, documentId: String, directory: Boolean) {
        docs[archivePathKey(segments)] = Doc(documentId, directory)
    }

    private fun invalidate(pathKey: String) {
        val prefix = "$pathKey/"
        docs.keys.removeAll { key -> key == pathKey || key.startsWith(prefix) }
    }

    private fun documentUri(documentId: String): Uri {
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    }

    private data class Doc(
        val documentId: String,
        val directory: Boolean,
    )

    private data class Child(
        val documentId: String,
        val displayName: String,
        val mimeType: String?,
    )

    private class SyncingOutputStream(
        private val descriptor: ParcelFileDescriptor,
    ) : OutputStream() {
        private val stream = ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
        private var closed = false

        override fun write(byte: Int) {
            stream.write(byte)
        }

        override fun write(bytes: ByteArray) {
            stream.write(bytes)
        }

        override fun write(bytes: ByteArray, off: Int, len: Int) {
            stream.write(bytes, off, len)
        }

        override fun flush() {
            stream.flush()
        }

        override fun close() {
            if (closed) return
            closed = true
            var failure: IOException? = null
            try {
                stream.flush()
                descriptor.fileDescriptor.sync()
            } catch (e: IOException) {
                failure = e
            }
            try {
                stream.close()
            } catch (e: IOException) {
                if (failure == null) failure = e
            }
            if (failure != null) throw failure
        }
    }

    private companion object {
        const val OCTET_STREAM = "application/octet-stream"
        val CHILD_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
    }
}
