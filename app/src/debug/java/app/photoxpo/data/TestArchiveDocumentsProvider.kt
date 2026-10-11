package app.photoxpo.data

import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/**
 * Archive Drive DocumentsProvider for instrumented tests.
 * Document ids are "root" and "root/" plus a relative path under the test cache folder archive-drive.
 * createDocument writes the requested name, or throws if that name already exists.
 * See [SafArchiveDriveInstrumentedTest] for how the test process reaches this provider.
 */
class TestArchiveDocumentsProvider : DocumentsProvider() {
    private val lock = Any()

    override fun onCreate(): Boolean {
        val dir = File(requireNotNull(context).cacheDir, CACHE_DIR)
        return dir.isDirectory || dir.mkdirs()
    }

    override fun queryRoots(projection: Array<out String>?): MatrixCursor {
        val columns = columnsOr(projection, DEFAULT_ROOT_COLUMNS)
        val cursor = MatrixCursor(columns)
        val names = columns.toSet()
        val row = cursor.newRow()
        fun put(name: String, value: Any) {
            if (name in names) row.add(name, value)
        }
        put(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
        put(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_ID)
        put(DocumentsContract.Root.COLUMN_TITLE, "Archive Drive")
        put(
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.FLAG_SUPPORTS_CREATE or DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD,
        )
        put(DocumentsContract.Root.COLUMN_ICON, android.R.drawable.ic_menu_save)
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): MatrixCursor {
        synchronized(lock) {
            val file = existing(documentId)
            val cursor = documentCursor(projection)
            include(cursor, file)
            return cursor
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): MatrixCursor {
        synchronized(lock) {
            val parent = existing(parentDocumentId)
            if (!parent.isDirectory) throw FileNotFoundException(parentDocumentId)
            val cursor = documentCursor(projection)
            val children = parent.listFiles() ?: throw FileNotFoundException(parentDocumentId)
            children.forEach { child -> include(cursor, child) }
            return cursor
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        synchronized(lock) {
            val file = existing(documentId)
            if (!file.isFile) throw FileNotFoundException(documentId)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
        }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        synchronized(lock) {
            rejectName(displayName)
            val parent = existing(parentDocumentId)
            if (!parent.isDirectory) throw FileNotFoundException(parentDocumentId)
            val child = File(parent, displayName)
            if (child.exists()) throw FileNotFoundException(displayName)
            val created = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                child.mkdir()
            } else {
                child.createNewFile()
            }
            if (!created) throw FileNotFoundException(displayName)
            return idFor(child)
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        synchronized(lock) {
            rejectName(displayName)
            val file = existing(documentId)
            val parent = file.parentFile ?: throw FileNotFoundException(documentId)
            val target = File(parent, displayName)
            if (target.exists()) throw FileNotFoundException(displayName)
            if (!file.renameTo(target)) throw FileNotFoundException(documentId)
            return idFor(target)
        }
    }

    override fun deleteDocument(documentId: String) {
        synchronized(lock) {
            if (documentId == ROOT_ID) throw FileNotFoundException(documentId)
            val file = existing(documentId)
            if (!file.deleteRecursively()) throw FileNotFoundException(documentId)
        }
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        synchronized(lock) {
            val file = existing(sourceDocumentId)
            val targetParent = existing(targetParentDocumentId)
            if (!targetParent.isDirectory) throw FileNotFoundException(targetParentDocumentId)
            val target = File(targetParent, file.name)
            if (target.exists()) throw FileNotFoundException(file.name)
            if (!file.renameTo(target)) throw FileNotFoundException(sourceDocumentId)
            return idFor(target)
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        if (parentDocumentId == documentId) return true
        return documentId.startsWith("$parentDocumentId/")
    }

    private fun existing(documentId: String): File {
        val file = fileFor(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        return file
    }

    private fun fileFor(documentId: String): File {
        if (documentId == ROOT_ID) return baseDir()
        val prefix = "$ROOT_ID/"
        if (!documentId.startsWith(prefix)) throw FileNotFoundException(documentId)
        val segments = documentId.removePrefix(prefix).split('/')
        if (segments.any { segment -> segment.isEmpty() || segment == "." || segment == ".." }) {
            throw FileNotFoundException(documentId)
        }
        return File(baseDir(), segments.joinToString("/"))
    }

    private fun idFor(file: File): String {
        val base = baseDir().absolutePath.removeSuffix("/")
        val path = file.absolutePath.removeSuffix("/")
        if (path == base) return ROOT_ID
        val prefix = "$base/"
        if (!path.startsWith(prefix)) throw FileNotFoundException(path)
        return "$ROOT_ID/${path.removePrefix(prefix)}"
    }

    private fun baseDir(): File {
        val dir = File(requireNotNull(context).cacheDir, CACHE_DIR)
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw FileNotFoundException(CACHE_DIR)
        }
        return dir
    }

    private fun documentCursor(projection: Array<out String>?): MatrixCursor {
        return MatrixCursor(columnsOr(projection, DEFAULT_DOCUMENT_COLUMNS))
    }

    private fun include(cursor: MatrixCursor, file: File) {
        val names = cursor.columnNames.toSet()
        val row = cursor.newRow()
        fun put(name: String, value: Any) {
            if (name in names) row.add(name, value)
        }
        put(DocumentsContract.Document.COLUMN_DOCUMENT_ID, idFor(file))
        put(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
        put(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeFor(file))
        put(DocumentsContract.Document.COLUMN_FLAGS, flagsFor(file))
        put(DocumentsContract.Document.COLUMN_SIZE, file.length())
        put(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
    }

    private fun mimeFor(file: File): String {
        return if (file.isDirectory) {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            OCTET_STREAM
        }
    }

    private fun flagsFor(file: File): Int {
        var flags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
            DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
            DocumentsContract.Document.FLAG_SUPPORTS_RENAME
        if (file.isDirectory) {
            flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        }
        return flags
    }

    private fun rejectName(displayName: String) {
        if (displayName.isEmpty() || displayName == "." || displayName == ".." || '/' in displayName) {
            throw IllegalArgumentException(displayName)
        }
    }

    private fun columnsOr(projection: Array<out String>?, defaults: Array<String>): Array<String> {
        if (projection == null || projection.isEmpty()) return defaults
        return Array(projection.size) { index -> projection[index] }
    }

    companion object {
        /** Must match android:authorities in androidTest/AndroidManifest.xml. */
        const val AUTHORITY = "app.photoxpo.test.archive"
        const val ROOT_ID = "root"
        private const val CACHE_DIR = "archive-drive"
        private const val OCTET_STREAM = "application/octet-stream"

        fun treeUri() = DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID)

        fun absentTreeUri() = DocumentsContract.buildTreeDocumentUri(
            AUTHORITY,
            "missing-volume:no-such-folder",
        )

        private val DEFAULT_ROOT_COLUMNS = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
        )

        private val DEFAULT_DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
