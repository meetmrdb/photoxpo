package app.photoxpo.data

import android.content.Context
import app.photoxpo.core.PreviewStore
import app.photoxpo.core.previewFileName
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Previews on the phone, under filesDir/previews, one JPEG per hash. */
class PhoneFilePreviewStore(
    private val context: Context,
) : PreviewStore {
    override fun write(hash: String, bytes: ByteArray) {
        val directory = previewsDirectory()
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Could not create the Preview directory")
        }
        val target = fileFor(hash)
        val temp = File.createTempFile("preview-", ".tmp", directory)
        try {
            FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            movePreviewFile(temp, target)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    override fun read(hash: String): ByteArray? {
        val file = fileFor(hash)
        if (!file.isFile) return null
        return file.readBytes()
    }

    override fun exists(hash: String): Boolean = fileFor(hash).isFile

    private fun previewsDirectory(): File = File(context.filesDir, "previews")

    private fun fileFor(hash: String): File = File(previewsDirectory(), previewFileName(hash))
}

/**
 * Replaces [target] with [temp]. An existing Preview stays in place when the move fails.
 */
internal fun movePreviewFile(temp: File, target: File) {
    val source = temp.toPath()
    val destination = target.toPath()
    try {
        Files.move(
            source,
            destination,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
    }
}
