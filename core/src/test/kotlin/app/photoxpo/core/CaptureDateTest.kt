package app.photoxpo.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class CaptureDateTest {
    @Test
    fun chooseCaptureDate_cases() {
        val modified2020 = Instant.parse("2020-06-15T12:00:00Z").toEpochMilli()
        val taken2022Jan1 = Instant.parse("2022-01-01T00:00:01Z").toEpochMilli()
        val takenNearMidnightUtc = Instant.parse("2022-03-31T23:30:00Z").toEpochMilli()
        val videoMeta2023 = Instant.parse("2023-07-04T10:00:00Z").toEpochMilli()
        val video1904 = Instant.parse("1904-01-01T00:00:00Z").toEpochMilli()
        val video1904Earlier = Instant.parse("1900-01-01T00:00:00Z").toEpochMilli()

        val cases = listOf(
            Case(
                name = "photo EXIF wins over taken and modified",
                file = photo(
                    exif = "2021:05:10 08:00:00",
                    taken = taken2022Jan1,
                    modified = modified2020,
                ),
                expected = ChosenDate("2021-05-10", "exif"),
            ),
            Case(
                name = "photo EXIF wall-clock date not shifted by zones",
                file = photo(
                    exif = "2021:12:31 23:59:59",
                    taken = Instant.parse("2022-01-01T00:00:00Z").toEpochMilli(),
                    modified = modified2020,
                ),
                expected = ChosenDate("2021-12-31", "exif"),
            ),
            Case(
                name = "photo taken fallback uses UTC date",
                file = photo(
                    exif = null,
                    taken = taken2022Jan1,
                    modified = modified2020,
                ),
                expected = ChosenDate("2022-01-01", "taken"),
            ),
            Case(
                name = "photo taken fallback near midnight UTC",
                file = photo(
                    exif = null,
                    taken = takenNearMidnightUtc,
                    modified = modified2020,
                ),
                expected = ChosenDate("2022-03-31", "taken"),
            ),
            Case(
                name = "photo only modified time",
                file = photo(
                    exif = null,
                    taken = null,
                    modified = modified2020,
                ),
                expected = ChosenDate("2020-06-15", "modified"),
            ),
            Case(
                name = "photo malformed EXIF falls to taken",
                file = photo(
                    exif = "not-a-date",
                    taken = taken2022Jan1,
                    modified = modified2020,
                ),
                expected = ChosenDate("2022-01-01", "taken"),
            ),
            Case(
                name = "photo blank EXIF falls to taken",
                file = photo(
                    exif = "   ",
                    taken = taken2022Jan1,
                    modified = modified2020,
                ),
                expected = ChosenDate("2022-01-01", "taken"),
            ),
            Case(
                name = "photo zero EXIF falls to taken",
                file = photo(
                    exif = "0000:00:00 00:00:00",
                    taken = taken2022Jan1,
                    modified = modified2020,
                ),
                expected = ChosenDate("2022-01-01", "taken"),
            ),
            Case(
                name = "photo invalid month EXIF falls to taken",
                file = photo(
                    exif = "2024:13:01 12:00:00",
                    taken = taken2022Jan1,
                    modified = modified2020,
                ),
                expected = ChosenDate("2022-01-01", "taken"),
            ),
            Case(
                name = "photo invalid day EXIF falls to taken",
                file = photo(
                    exif = "2024:02:30 12:00:00",
                    taken = taken2022Jan1,
                    modified = modified2020,
                ),
                expected = ChosenDate("2022-01-01", "taken"),
            ),
            Case(
                name = "photo bad EXIF and no taken falls to modified",
                file = photo(
                    exif = "0000:00:00 00:00:00",
                    taken = null,
                    modified = modified2020,
                ),
                expected = ChosenDate("2020-06-15", "modified"),
            ),
            Case(
                name = "video metadata uses exif tag",
                file = video(
                    videoMeta = videoMeta2023,
                    exif = "2021:01:01 00:00:00",
                    taken = taken2022Jan1,
                    modified = modified2020,
                ),
                expected = ChosenDate("2023-07-04", "exif"),
            ),
            Case(
                name = "video metadata year 1904 falls to modified",
                file = video(
                    videoMeta = video1904,
                    modified = modified2020,
                ),
                expected = ChosenDate("2020-06-15", "modified"),
            ),
            Case(
                name = "video metadata before 1904 falls to modified",
                file = video(
                    videoMeta = video1904Earlier,
                    modified = modified2020,
                ),
                expected = ChosenDate("2020-06-15", "modified"),
            ),
            Case(
                name = "video no metadata uses modified",
                file = video(
                    videoMeta = null,
                    modified = modified2020,
                ),
                expected = ChosenDate("2020-06-15", "modified"),
            ),
            Case(
                name = "video ignores photo EXIF and taken",
                file = video(
                    videoMeta = null,
                    exif = "2019:11:11 11:11:11",
                    taken = taken2022Jan1,
                    modified = modified2020,
                ),
                expected = ChosenDate("2020-06-15", "modified"),
            ),
        )

        for (case in cases) {
            assertEquals(
                case.name,
                case.expected,
                chooseCaptureDate(case.file),
            )
        }
    }

    private data class Case(
        val name: String,
        val file: MediaFile,
        val expected: ChosenDate,
    )

    private fun photo(
        exif: String?,
        taken: Long?,
        modified: Long,
    ): MediaFile = MediaFile(
        id = "p",
        name = "photo.jpg",
        sizeBytes = 1L,
        modifiedAtEpochMillis = modified,
        kind = MediaKind.PHOTO,
        exifDateTimeOriginal = exif,
        dateTakenEpochMillis = taken,
    )

    private fun video(
        videoMeta: Long?,
        modified: Long,
        exif: String? = null,
        taken: Long? = null,
    ): MediaFile = MediaFile(
        id = "v",
        name = "video.mp4",
        sizeBytes = 1L,
        modifiedAtEpochMillis = modified,
        kind = MediaKind.VIDEO,
        exifDateTimeOriginal = exif,
        dateTakenEpochMillis = taken,
        videoMetadataUtcEpochMillis = videoMeta,
    )
}
