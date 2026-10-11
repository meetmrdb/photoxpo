package app.photoxpo.core

import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

sealed interface LoadResult {
    data class Loaded(val uri: String) : LoadResult

    data object NotOnDrive : LoadResult

    data object DriveGone : LoadResult

    data object Cancelled : LoadResult

    data object Failed : LoadResult
}

/**
 * Copies an Original from the Archive Drive onto the phone.
 * The drive is checked again before the copy. The file is published only when
 * the copied bytes and a second read of the drive both match the Manifest hash
 * and [expectedSize].
 */
class OriginalLoader(
    private val archiveDrive: ArchiveDrive,
    private val gallery: PhoneGallery,
) {
    fun load(
        hash: String,
        drivePath: String,
        expectedSize: Long,
        name: String,
        mimeHint: String?,
        isCancelled: () -> Boolean,
        onProgress: (Long) -> Unit = {},
    ): LoadResult {
        var handle: LoadedFileHandle? = null
        var published = false
        val outcome = try {
            if (isCancelled()) {
                LoadResult.Cancelled
            } else if (!onDrive(drivePath, expectedSize)) {
                LoadResult.NotOnDrive
            } else if (isCancelled()) {
                LoadResult.Cancelled
            } else {
                val created = gallery.createLoadedFile(name, mimeHint)
                handle = created
                val copied = archiveDrive.read(drivePath).use { input ->
                    streamHash(input, created.output, isCancelled, onProgress)
                }
                when {
                    copied is StreamHash.Cancelled -> LoadResult.Cancelled
                    copied is StreamHash.Done && !copied.matches(hash, expectedSize) -> LoadResult.Failed
                    else -> {
                        val readBack = archiveDrive.read(drivePath).use { input ->
                            streamHash(
                                input,
                                OutputStream.nullOutputStream(),
                                isCancelled,
                                onProgress = {},
                            )
                        }
                        when {
                            readBack is StreamHash.Cancelled -> LoadResult.Cancelled
                            readBack is StreamHash.Done && !readBack.matches(hash, expectedSize) ->
                                LoadResult.Failed
                            isCancelled() -> LoadResult.Cancelled
                            else -> {
                                val uri = created.commit()
                                published = true
                                handle = null
                                LoadResult.Loaded(uri)
                            }
                        }
                    }
                }
            }
        } catch (_: DriveUnavailableException) {
            LoadResult.DriveGone
        } catch (_: Exception) {
            LoadResult.Failed
        } finally {
            if (!published) abort(handle)
        }
        return outcome
    }

    private fun onDrive(path: String, expectedSize: Long): Boolean {
        return try {
            archiveDrive.exists(path) && archiveDrive.byteCount(path) == expectedSize
        } catch (_: FileNotFoundException) {
            false
        }
    }

    private fun abort(handle: LoadedFileHandle?) {
        if (handle == null) return
        try {
            handle.abort()
        } catch (_: Exception) {
            // The Load result is already decided. A failed abort must not replace it.
        }
    }
}

private sealed interface StreamHash {
    data class Done(val sha256: String, val byteCount: Long) : StreamHash

    data object Cancelled : StreamHash
}

private fun StreamHash.Done.matches(hash: String, expectedSize: Long): Boolean {
    return sha256 == hash && byteCount == expectedSize
}

private fun streamHash(
    input: InputStream,
    output: OutputStream,
    isCancelled: () -> Boolean,
    onProgress: (Long) -> Unit,
): StreamHash {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    var byteCount = 0L
    while (true) {
        if (isCancelled()) return StreamHash.Cancelled
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        digest.update(buffer, 0, read)
        output.write(buffer, 0, read)
        byteCount += read
        onProgress(byteCount)
    }
    output.flush()
    return StreamHash.Done(digest.digest().toHex(), byteCount)
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

private const val COPY_BUFFER_BYTES = 64 * 1024
