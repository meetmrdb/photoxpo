package app.photoxpo.core

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

internal data class FileBackupResult(
    val outcome: BackupItemOutcome,
    val wroteNewFileLine: Boolean,
    val copiedFile: Boolean,
    val deferredByCap: Boolean,
    val hash: String?,
)

internal interface ManifestCommit {
    fun appendStored(entry: ManifestFileEntry)

    fun updateNames(entry: ManifestFileEntry)
}

internal class OriginalCopier(
    private val mediaSource: MediaSource,
    private val archiveDrive: ArchiveDrive,
    private val chooseDate: DateChooser,
    private val committed: MutableList<ManifestFileEntry>,
    private val manifest: ManifestCommit,
) {
    fun copy(file: MediaFile, allowNewCopy: Boolean): FileBackupResult {
        val chosen = chooseDate.choose(file)
        val intended = originalPath(chosen.localDate, file.name)
        deletePart(intended)

        val source = hashSource(file)
        if (source.byteCount != file.sizeBytes) {
            return attention(file)
        }

        if (listedAndVerified(intended, source)) {
            return backedUp(file, source.sha256)
        }

        val stored = committed.firstOrNull { entry ->
            entry.hash == source.sha256 && verifiedOnDrive(entry.drivePath, source)
        }
        if (stored != null) {
            if (file.name !in stored.originalFilenames) {
                linkName(stored, file.name)
            }
            return backedUp(file, source.sha256)
        }

        val shortPath = originalPath(chosen.localDate, fileNameWithShortHash(file.name, source.sha256))
        val intendedMatches = verifiedOnDrive(intended, source)
        val destination = if (!archiveDrive.exists(intended) || intendedMatches) {
            intended
        } else {
            shortPath
        }
        val destinationMatches = if (destination == intended) {
            intendedMatches
        } else {
            verifiedOnDrive(destination, source)
        }
        if (destinationMatches) {
            if (listedAndVerified(destination, source)) {
                return backedUp(file, source.sha256)
            }
            storeNew(file, chosen, destination, source.sha256)
            return FileBackupResult(
                outcome = BackupItemOutcome(file.id, file.name, ItemState.COMMITTED),
                wroteNewFileLine = true,
                copiedFile = false,
                deferredByCap = false,
                hash = source.sha256,
            )
        }

        if (!allowNewCopy) {
            return FileBackupResult(
                outcome = BackupItemOutcome(file.id, file.name, ItemState.FOUND),
                wroteNewFileLine = false,
                copiedFile = false,
                deferredByCap = true,
                hash = null,
            )
        }

        deletePart(destination)
        if (!archiveDrive.exists(destination)) {
            return copyToFinal(file, chosen, destination, source)
        }
        if (committed.any { it.drivePath == destination }) {
            return attention(file)
        }
        return displaceUnproven(file, chosen, destination, source)
    }

    private fun listedAndVerified(path: String, source: HashedBytes): Boolean {
        val listed = committed.any { it.drivePath == path && it.hash == source.sha256 }
        return listed && verifiedOnDrive(path, source)
    }

    private fun verifiedOnDrive(path: String, source: HashedBytes): Boolean {
        if (!archiveDrive.exists(path)) return false
        if (archiveDrive.byteCount(path) != source.byteCount) return false
        val onDrive = archiveDrive.read(path).use { input ->
            copyHashed(input, OutputStream.nullOutputStream())
        }
        return onDrive.sha256 == source.sha256 && onDrive.byteCount == source.byteCount
    }

    private fun linkName(existing: ManifestFileEntry, filename: String) {
        val names = (existing.originalFilenames + filename).distinct()
        val updated = existing.copy(originalFilenames = names)
        manifest.updateNames(updated)
        val index = committed.indexOfFirst { it.hash == existing.hash }
        if (index >= 0) {
            committed[index] = updated
        }
    }

    private fun storeNew(file: MediaFile, chosen: ChosenDate, drivePath: String, hash: String) {
        val entry = ManifestFileEntry(
            hash = hash,
            drivePath = drivePath,
            size = file.sizeBytes,
            originalFilename = file.name,
            originalFilenames = listOf(file.name),
            kind = file.kind.wireName(),
            chosenDate = chosen.localDate,
            sourceTag = chosen.sourceTag,
        )
        manifest.appendStored(entry)
        committed += entry
    }

    private fun copyToFinal(
        file: MediaFile,
        chosen: ChosenDate,
        finalPath: String,
        source: HashedBytes,
    ): FileBackupResult {
        val partPath = "$finalPath$PART_SUFFIX"
        val copied = writeVerifiedPart(file, partPath, source) ?: return attention(file, copiedFile = true)
        when (archiveDrive.rename(partPath, finalPath)) {
            RenameResult.Renamed -> Unit
            is RenameResult.Collision -> {
                archiveDrive.delete(partPath)
                return attention(file, copiedFile = true)
            }
        }
        storeNew(file, chosen, finalPath, copied.sha256)
        return FileBackupResult(
            outcome = BackupItemOutcome(file.id, file.name, ItemState.COMMITTED),
            wroteNewFileLine = true,
            copiedFile = true,
            deferredByCap = false,
            hash = copied.sha256,
        )
    }

    private fun displaceUnproven(
        file: MediaFile,
        chosen: ChosenDate,
        finalPath: String,
        source: HashedBytes,
    ): FileBackupResult {
        val partPath = "$finalPath$PART_SUFFIX"
        val copied = writeVerifiedPart(file, partPath, source) ?: return attention(file, copiedFile = true)
        val aside = unusedAside(finalPath)
        if (aside == null) {
            archiveDrive.delete(partPath)
            return attention(file, copiedFile = true)
        }
        when (archiveDrive.rename(finalPath, aside)) {
            RenameResult.Renamed -> Unit
            is RenameResult.Collision -> {
                archiveDrive.delete(partPath)
                return attention(file, copiedFile = true)
            }
        }
        when (archiveDrive.rename(partPath, finalPath)) {
            RenameResult.Renamed -> Unit
            is RenameResult.Collision -> {
                archiveDrive.delete(partPath)
                return attention(file, copiedFile = true)
            }
        }
        storeNew(file, chosen, finalPath, copied.sha256)
        return FileBackupResult(
            outcome = BackupItemOutcome(file.id, file.name, ItemState.COMMITTED),
            wroteNewFileLine = true,
            copiedFile = true,
            deferredByCap = false,
            hash = copied.sha256,
        )
    }

    private fun writeVerifiedPart(file: MediaFile, partPath: String, expected: HashedBytes): HashedBytes? {
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
        val matches = copied.sha256 == expected.sha256 &&
            readBack.sha256 == expected.sha256 &&
            copied.byteCount == file.sizeBytes &&
            readBack.byteCount == file.sizeBytes &&
            archiveDrive.byteCount(partPath) == file.sizeBytes
        if (!matches) {
            archiveDrive.delete(partPath)
            return null
        }
        return copied
    }

    private fun hashSource(file: MediaFile): HashedBytes {
        return mediaSource.openOriginal(file.id).use { input ->
            copyHashed(input, OutputStream.nullOutputStream())
        }
    }

    private fun deletePart(finalPath: String) {
        val partPath = "$finalPath$PART_SUFFIX"
        if (archiveDrive.exists(partPath)) {
            archiveDrive.delete(partPath)
        }
    }

    private fun unusedAside(finalPath: String): String? {
        for (n in 1..999) {
            val candidate = "$finalPath.photoxpo-unproven-$n"
            if (!archiveDrive.exists(candidate)) return candidate
        }
        return null
    }

    private fun backedUp(file: MediaFile, hash: String): FileBackupResult {
        return FileBackupResult(
            outcome = BackupItemOutcome(file.id, file.name, ItemState.COMMITTED),
            wroteNewFileLine = false,
            copiedFile = false,
            deferredByCap = false,
            hash = hash,
        )
    }

    private fun attention(file: MediaFile, copiedFile: Boolean = false): FileBackupResult {
        return FileBackupResult(
            outcome = BackupItemOutcome(file.id, file.name, ItemState.NEEDS_ATTENTION),
            wroteNewFileLine = false,
            copiedFile = copiedFile,
            deferredByCap = false,
            hash = null,
        )
    }
}

internal fun fileNameWithShortHash(filename: String, sha256: String): String {
    val prefix = sha256.substring(0, 8)
    val dot = filename.lastIndexOf('.')
    if (dot <= 0) return "$filename-$prefix"
    return filename.substring(0, dot) + "-" + prefix + filename.substring(dot)
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
