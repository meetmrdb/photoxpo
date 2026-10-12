package app.photoxpo.core

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

internal fun sha256Hex(bytes: ByteArray): String {
    return MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
}

internal data class HashedCopy(
    val sha256: String,
    val byteCount: Long,
)

internal fun copyHashed(input: InputStream, output: OutputStream): HashedCopy {
    val copied = copyHashed(input, output, isCancelled = { false }, onProgress = {})
    return checkNotNull(copied)
}

/**
 * Copies [input] to [output]. Returns null when [isCancelled] is true before the copy finishes.
 */
internal fun copyHashed(
    input: InputStream,
    output: OutputStream,
    isCancelled: () -> Boolean,
    onProgress: (Long) -> Unit,
): HashedCopy? {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    var byteCount = 0L
    while (true) {
        if (isCancelled()) return null
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        digest.update(buffer, 0, read)
        output.write(buffer, 0, read)
        byteCount += read
        onProgress(byteCount)
    }
    output.flush()
    return HashedCopy(digest.digest().toHex(), byteCount)
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
