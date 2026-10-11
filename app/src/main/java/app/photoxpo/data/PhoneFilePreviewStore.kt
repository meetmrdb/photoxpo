package app.photoxpo.data

import android.content.Context
import app.photoxpo.core.PreviewStore
import app.photoxpo.core.previewFileName
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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
            if (!temp.renameTo(target)) {
                if (target.exists() && !target.delete()) {
                    throw IOException("Could not replace Preview ${previewFileName(hash)}")
                }
                if (!temp.renameTo(target)) {
                    throw IOException("Could not store Preview ${previewFileName(hash)}")
                }
            }
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
