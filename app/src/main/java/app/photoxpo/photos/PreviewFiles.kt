package app.photoxpo.photos

import app.photoxpo.core.previewFileName
import java.io.File

/** Preview JPEG kept at filesDir/previews/<hash>.jpg. */
fun previewJpeg(filesDir: File, hash: String): File {
    return File(filesDir, "previews/${previewFileName(hash)}")
}
