package app.photoxpo.photos

import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.round

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

internal fun formatByteCount(bytes: Long): String {
    if (bytes < 0) return "Unknown"
    if (bytes < 1024) {
        return if (bytes == 1L) "1 byte" else "$bytes bytes"
    }
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (unitIndex < units.lastIndex && value >= 1024.0) {
        value /= 1024.0
        unitIndex++
    }
    return "${trimOneDecimal(value)} ${units[unitIndex]}"
}

private fun formatChosenDate(chosenDate: String?, zone: ZoneId, locale: Locale): String {
    val date = chosenLocalDate(chosenDate, zone) ?: return "No date"
    return date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
}

private fun trimOneDecimal(value: Double): String {
    val rounded = round(value * 10.0) / 10.0
    val whole = rounded.toLong()
    return if (rounded == whole.toDouble()) {
        whole.toString()
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
}
