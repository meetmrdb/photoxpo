package app.photoxpo.photos

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * [chosenDate] is either `yyyy-MM-dd` or a decimal epoch-millisecond instant.
 * A local date stays on that calendar day. Milliseconds and ISO instants are
 * placed in [zone].
 */
internal fun chosenLocalDate(chosenDate: String?, zone: ZoneId): LocalDate? {
    val raw = chosenDate?.trim().orEmpty()
    if (raw.isEmpty()) return null
    try {
        return LocalDate.parse(raw)
    } catch (_: DateTimeParseException) {
        // Not a calendar date.
    }
    raw.toLongOrNull()?.let { millis ->
        return instantDate(Instant.ofEpochMilli(millis), zone)
    }
    return try {
        instantDate(Instant.parse(raw), zone)
    } catch (_: DateTimeParseException) {
        null
    }
}

internal fun chosenSortMillis(chosenDate: String?, zone: ZoneId): Long? {
    val raw = chosenDate?.trim().orEmpty()
    if (raw.isEmpty()) return null
    raw.toLongOrNull()?.let { return it }
    try {
        return LocalDate.parse(raw).atStartOfDay(zone).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        // Not a calendar date.
    }
    return try {
        Instant.parse(raw).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun instantDate(instant: Instant, zone: ZoneId): LocalDate? {
    return try {
        instant.atZone(zone).toLocalDate()
    } catch (_: DateTimeException) {
        null
    }
}
