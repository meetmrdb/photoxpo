package app.photoxpo.photos

import app.photoxpo.formatByteCount
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PhotoInfo(
    val date: String,
    val fileName: String,
    val originalSize: String,
    val previewSize: String,
    val whereTheOriginalIs: String,
    val backedUp: String,
    val driveFolder: String,
) {
    companion object {
        fun from(
            photo: PhotoEntity,
            previewSizeBytes: Long?,
            zone: ZoneId = ZoneOffset.UTC,
            locale: Locale = Locale.ENGLISH,
        ): PhotoInfo {
            return PhotoInfo(
                date = formatChosenDate(photo.chosenDate, zone, locale),
                fileName = photo.name,
                originalSize = formatByteCount(photo.sizeBytes),
                previewSize = if (previewSizeBytes == null) "No Preview" else formatByteCount(previewSizeBytes),
                whereTheOriginalIs = whereTheOriginalIs(
                    onDrive = photo.originalOnThisDrive,
                    onPhone = photo.originalOnPhone,
                ),
                backedUp = if (photo.backedUp) "Yes" else "No",
                driveFolder = driveFolder(photo.drivePath),
            )
        }
    }
}

internal fun whereTheOriginalIs(onDrive: Boolean, onPhone: Boolean): String {
    return when {
        onDrive && onPhone -> "On this phone and your drive"
        onDrive -> "On your Archive Drive"
        else -> "Original not on this Archive Drive"
    }
}

internal fun driveFolder(drivePath: String?): String {
    if (drivePath.isNullOrBlank()) return "Not on your Archive Drive"
    val trimmed = drivePath.trim().trimEnd('/')
    val slash = trimmed.lastIndexOf('/')
    if (slash <= 0) return "Archive Drive"
    return trimmed.substring(0, slash)
}


private fun formatChosenDate(chosenDate: String?, zone: ZoneId, locale: Locale): String {
    val date = chosenLocalDate(chosenDate, zone) ?: return "No date"
    return date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
}

