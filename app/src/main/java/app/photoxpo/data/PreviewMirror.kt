package app.photoxpo.data

import app.photoxpo.core.ArchiveDrive
import app.photoxpo.core.previewFileName

/**
 * Best-effort copy of a Preview onto the Archive Drive.
 * Returns false when the drive is absent or the copy fails. Never throws.
 */
class PreviewMirror(
    private val archiveDrive: ArchiveDrive,
) {
    fun write(hash: String, bytes: ByteArray): Boolean {
        val path = "Photoxpo/.photoxpo/previews/${previewFileName(hash)}"
        return try {
            archiveDrive.create(path)
            archiveDrive.write(path).use { stream ->
                stream.write(bytes)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
