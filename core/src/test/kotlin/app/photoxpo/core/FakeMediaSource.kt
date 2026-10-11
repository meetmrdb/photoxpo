package app.photoxpo.core

import java.io.ByteArrayInputStream
import java.io.InputStream

class FakeMediaSource(
    private val files: List<Pair<MediaFile, ByteArray>>,
) : MediaSource {
    override fun listCameraFolder(): List<MediaFile> = files.map { it.first }

    override fun openOriginal(id: String): InputStream {
        val bytes = files.first { it.first.id == id }.second
        return ByteArrayInputStream(bytes.copyOf())
    }
}
