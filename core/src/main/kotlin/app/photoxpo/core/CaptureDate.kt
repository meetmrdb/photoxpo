package app.photoxpo.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

private val EXIF_DATE_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu':'MM':'dd HH':'mm':'ss")
        .withResolverStyle(ResolverStyle.STRICT)

private const val VIDEO_METADATA_MIN_YEAR = 1904

fun chooseCaptureDate(file: MediaFile): ChosenDate {
    return when (file.kind) {
        MediaKind.PHOTO -> choosePhotoCaptureDate(file)
        MediaKind.VIDEO -> chooseVideoCaptureDate(file)
    }
}

private fun choosePhotoCaptureDate(file: MediaFile): ChosenDate {
    parseExifWallClockDate(file.exifDateTimeOriginal)?.let { localDate ->
        return ChosenDate(localDate = localDate, sourceTag = "exif")
    }
    file.dateTakenEpochMillis?.let { millis ->
        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        return ChosenDate(localDate = date.toString(), sourceTag = "taken")
    }
    return chooseModifiedTimeUtc(file)
}

private fun chooseVideoCaptureDate(file: MediaFile): ChosenDate {
    file.videoMetadataUtcEpochMillis?.let { millis ->
        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        if (date.year > VIDEO_METADATA_MIN_YEAR) {
            return ChosenDate(localDate = date.toString(), sourceTag = "exif")
        }
    }
    return chooseModifiedTimeUtc(file)
}

private fun parseExifWallClockDate(exif: String?): String? {
    if (exif.isNullOrBlank()) return null
    return try {
        val dateTime = LocalDateTime.parse(exif.trim(), EXIF_DATE_TIME)
        if (dateTime.year < 1) return null
        dateTime.toLocalDate().toString()
    } catch (_: DateTimeParseException) {
        null
    }
}
