package app.photoxpo.core

import java.io.InputStream

enum class MediaKind {
    PHOTO,
    VIDEO,
}

data class MediaFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedAtEpochMillis: Long,
    val kind: MediaKind,
    val exifDateTimeOriginal: String? = null,
    val dateTakenEpochMillis: Long? = null,
    val videoMetadataUtcEpochMillis: Long? = null,
)

interface MediaSource {
    fun listCameraFolder(): List<MediaFile>

    fun openOriginal(id: String): InputStream
}
