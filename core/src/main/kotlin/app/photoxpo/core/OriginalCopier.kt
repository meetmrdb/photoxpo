package app.photoxpo.core

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

internal data class FileBackupResult(
    val outcome: BackupItemOutcome,
    val wroteManifestLine: Boolean,
)

internal class OriginalCopier(
    private val mediaSource: MediaSource,
    private val archiveDrive: ArchiveDrive,
    private val chooseDate: DateChooser,
    private val committed: MutableList<ManifestFileEntry>,
    private val onCommit: (MediaFile, ChosenDate, String, String) -> Unit,
) {
    fun copy(file: MediaFile): FileBackupResult {
        val chosen = chooseDate.choose(file)
        val finalPath = originalPath(chosen.localDate, file.name)
        val partPath = "$finalPath$PART_SUFFIX"
        // A part file is never resumed. Delete it and copy from byte 0 when a copy is still required.
        if (archiveDrive.exists(partPath)) {
            archiveDrive.delete(partPath)
        }

        if (alreadyCommitted(file, finalPath)) {
            return committedResult(file, wroteManifestLine = false)
        }

        if (archiveDrive.exists(finalPath)) {
            if (committed.any { it.drivePath == finalPath }) {
                return attention(file)
            }
            val hash = hashIfIdentical(file, finalPath)
            if (hash != null) {
                commit(file, chosen, finalPath, hash)
                return committedResult(file, wroteManifestLine = true)
            }
            return displaceUnproven(file, chosen, finalPath, partPath)
        }

        return copyToFinal(file, chosen, finalPath, partPath)
    }

    private fun alreadyCommitted(file: MediaFile, finalPath: String): Boolean {
        val listed = committed.any { entry ->
            entry.drivePath == finalPath && entry.size == file.sizeBytes
        }
        if (!listed) return false
        if (!archiveDrive.exists(finalPath)) return false
        return archiveDrive.byteCount(finalPath) == file.sizeBytes
    }

    private fun hashIfIdentical(file: MediaFile, finalPath: String): String? {
        val source = mediaSource.openOriginal(file.id).use { input ->
            copyHashed(input, OutputStream.nullOutputStream())
        }
        val onDrive = archiveDrive.read(finalPath).use { input ->
            copyHashed(input, OutputStream.nullOutputStream())
        }
        val listedSize = archiveDrive.byteCount(finalPath)
        val matches = source.sha256 == onDrive.sha256 &&
            source.byteCount == file.sizeBytes &&
            onDrive.byteCount == file.sizeBytes &&
            listedSize == file.sizeBytes
        if (!matches) return null
        return source.sha256
    }

    private fun copyToFinal(
        file: MediaFile,
        chosen: ChosenDate,
        finalPath: String,
        partPath: String,
    ): FileBackupResult {
        val copied = writeVerifiedPart(file, partPath) ?: return attention(file)
        when (archiveDrive.rename(partPath, finalPath)) {
            RenameResult.Renamed -> Unit
            is RenameResult.Collision -> {
                archiveDrive.delete(partPath)
                return attention(file)
            }
        }
        commit(file, chosen, finalPath, copied.sha256)
        return committedResult(file, wroteManifestLine = true)
    }

    private fun displaceUnproven(
        file: MediaFile,
        chosen: ChosenDate,
        finalPath: String,
        partPath: String,
    ): FileBackupResult {
        val copied = writeVerifiedPart(file, partPath) ?: return attention(file)
        val aside = unusedAside(finalPath)
        if (aside == null) {
            archiveDrive.delete(partPath)
            return attention(file)
        }
        // Keep the mismatching file. Move it aside, then put the verified part in its place.
        when (archiveDrive.rename(finalPath, aside)) {
            RenameResult.Renamed -> Unit
            is RenameResult.Collision -> {
                archiveDrive.delete(partPath)
                return attention(file)
            }
        }
        when (archiveDrive.rename(partPath, finalPath)) {
            RenameResult.Renamed -> Unit
            is RenameResult.Collision -> {
                archiveDrive.delete(partPath)
                return attention(file)
            }
        }
        commit(file, chosen, finalPath, copied.sha256)
        return committedResult(file, wroteManifestLine = true)
    }

    private fun writeVerifiedPart(file: MediaFile, partPath: String): HashedBytes? {
        if (archiveDrive.exists(partPath)) {
            archiveDrive.delete(partPath)
        }
        when (archiveDrive.create(partPath)) {
            CreateResult.Created -> Unit
            is CreateResult.Collision -> return null
        }
        val copied = mediaSource.openOriginal(file.id).use { input ->
            archiveDrive.write(partPath).use { output ->
                copyHashed(input, output)
            }
        }
        val readBack = archiveDrive.read(partPath).use { input ->
            copyHashed(input, OutputStream.nullOutputStream())
        }
        val matches = copied.sha256 == readBack.sha256 &&
            copied.byteCount == file.sizeBytes &&
            readBack.byteCount == file.sizeBytes &&
            archiveDrive.byteCount(partPath) == file.sizeBytes
        if (!matches) {
            archiveDrive.delete(partPath)
            return null
        }
        return copied
    }

    private fun unusedAside(finalPath: String): String? {
        for (n in 1..999) {
            val candidate = "$finalPath.photoxpo-unproven-$n"
            if (!archiveDrive.exists(candidate)) return candidate
        }
        return null
    }

    private fun commit(file: MediaFile, chosen: ChosenDate, drivePath: String, hash: String) {
        onCommit(file, chosen, drivePath, hash)
        committed += ManifestFileEntry(
            hash = hash,
            drivePath = drivePath,
            size = file.sizeBytes,
            originalFilename = file.name,
            kind = file.kind.wireName(),
            chosenDate = chosen.localDate,
            sourceTag = chosen.sourceTag,
        )
    }

    private fun committedResult(file: MediaFile, wroteManifestLine: Boolean): FileBackupResult {
        return FileBackupResult(
            BackupItemOutcome(file.id, file.name, ItemState.COMMITTED),
            wroteManifestLine = wroteManifestLine,
        )
    }

    private fun attention(file: MediaFile): FileBackupResult {
        return FileBackupResult(
            BackupItemOutcome(file.id, file.name, ItemState.NEEDS_ATTENTION),
            wroteManifestLine = false,
        )
    }
}

private data class HashedBytes(val sha256: String, val byteCount: Long)

private fun copyHashed(input: InputStream, output: OutputStream): HashedBytes {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    var byteCount = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        digest.update(buffer, 0, read)
        output.write(buffer, 0, read)
        byteCount += read
    }
    output.flush()
    return HashedBytes(digest.digest().toHex(), byteCount)
}

private fun ByteArray.toHex(): String {
    val digits = "0123456789abcdef"
    val chars = CharArray(size * 2)
    for (index in indices) {
        val value = this[index].toInt() and 0xff
        chars[index * 2] = digits[value ushr 4]
        chars[index * 2 + 1] = digits[value and 0x0f]
    }
    return String(chars)
}

private const val PART_SUFFIX = ".photoxpo-part"
private const val COPY_BUFFER_BYTES = 64 * 1024
