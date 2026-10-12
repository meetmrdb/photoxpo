package app.photoxpo.photos

import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PhotoMonthGroup(
    val label: String,
    val photos: List<PhotoEntity>,
)

/**
 * Groups [photos] under month labels such as "October 2026".
 * The newest month comes first. Inside a month, the newest photo comes first.
 * Photos with no readable [PhotoEntity.chosenDate] go in a final "No date" group.
 */
fun groupByMonth(
    photos: List<PhotoEntity>,
    zone: ZoneId,
    locale: Locale = Locale.ENGLISH,
): List<PhotoMonthGroup> {
    if (photos.isEmpty()) return emptyList()
    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    val dated = photos.map { photo ->
        val date = chosenLocalDate(photo.chosenDate, zone)
        DatedPhoto(
            photo = photo,
            month = date?.let(YearMonth::from),
            sortMillis = chosenSortMillis(photo.chosenDate, zone),
        )
    }
    val byMonth = dated.groupBy { it.month }
    val groups = ArrayList<PhotoMonthGroup>(byMonth.size)
    for (month in byMonth.keys.filterNotNull().sortedDescending()) {
        val rows = byMonth.getValue(month).sortedWith(newestFirst)
        groups += PhotoMonthGroup(
            label = month.atDay(1).format(formatter),
            photos = rows.map { it.photo },
        )
    }
    val undated = byMonth[null]
    if (!undated.isNullOrEmpty()) {
        groups += PhotoMonthGroup(
            label = "No date",
            photos = undated.sortedBy { it.photo.hash }.map { it.photo },
        )
    }
    return groups
}

/**
 * Groups [photos] under day labels such as "11 October 2026".
 * The newest day comes first. Photos with no readable date go in a final "No date" group.
 */
fun groupByDay(
    photos: List<PhotoEntity>,
    zone: ZoneId,
    locale: Locale = Locale.ENGLISH,
): List<PhotoMonthGroup> {
    if (photos.isEmpty()) return emptyList()
    val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    val dated = photos.map { photo ->
        DatedDay(
            photo = photo,
            day = chosenLocalDate(photo.chosenDate, zone),
            sortMillis = chosenSortMillis(photo.chosenDate, zone),
        )
    }
    val byDay = dated.groupBy { it.day }
    val groups = ArrayList<PhotoMonthGroup>(byDay.size)
    for (day in byDay.keys.filterNotNull().sortedDescending()) {
        val rows = byDay.getValue(day).sortedWith(
            compareByDescending<DatedDay> { it.sortMillis ?: Long.MIN_VALUE }.thenBy { it.photo.hash },
        )
        groups += PhotoMonthGroup(label = day.format(formatter), photos = rows.map { it.photo })
    }
    val undated = byDay[null]
    if (!undated.isNullOrEmpty()) {
        groups += PhotoMonthGroup(
            label = "No date",
            photos = undated.sortedBy { it.photo.hash }.map { it.photo },
        )
    }
    return groups
}

private data class DatedDay(
    val photo: PhotoEntity,
    val day: java.time.LocalDate?,
    val sortMillis: Long?,
)

private data class DatedPhoto(
    val photo: PhotoEntity,
    val month: YearMonth?,
    val sortMillis: Long?,
)

private val newestFirst = compareByDescending<DatedPhoto> { it.sortMillis ?: Long.MIN_VALUE }
    .thenBy { it.photo.hash }
