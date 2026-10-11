package app.photoxpo.photos

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class PhotoMonthGroupsTest {
    @Test
    fun newestMonthFirstAndNewestPhotoFirst() {
        val photos = listOf(
            sample("old-dec", "2025-12-02"),
            sample("oct-early", "2026-10-02"),
            sample("oct-late", "2026-10-20"),
            sample("sep", "2026-09-15"),
            sample("undated", null),
        )

        val groups = groupByMonth(photos, ZoneOffset.UTC)

        assertEquals(
            listOf("October 2026", "September 2026", "December 2025", "No date"),
            groups.map { it.label },
        )
        assertEquals(listOf("oct-late", "oct-early"), groups[0].photos.map { it.hash })
        assertEquals(listOf("sep"), groups[1].photos.map { it.hash })
        assertEquals(listOf("old-dec"), groups[2].photos.map { it.hash })
        assertEquals(listOf("undated"), groups[3].photos.map { it.hash })
    }

    @Test
    fun orderDoesNotDependOnInputOrder() {
        val photos = listOf(
            sample("b", "2026-08-02"),
            sample("a", "2026-08-02"),
            sample("c", "2026-11-01"),
        )
        val forward = groupByMonth(photos, ZoneOffset.UTC)
        val backward = groupByMonth(photos.reversed(), ZoneOffset.UTC)

        assertEquals(forward.map { it.label }, backward.map { it.label })
        assertEquals(
            forward.map { group -> group.photos.map { it.hash } },
            backward.map { group -> group.photos.map { it.hash } },
        )
        assertEquals(listOf("a", "b"), forward[1].photos.map { it.hash })
    }

    @Test
    fun epochMillisUsesTheZone() {
        val millis = Instant.parse("2026-01-01T06:30:00Z").toEpochMilli().toString()
        val photo = sample("z", millis)

        assertEquals("January 2026", groupByMonth(listOf(photo), ZoneOffset.UTC).single().label)
        assertEquals(
            "December 2025",
            groupByMonth(listOf(photo), ZoneId.of("America/Los_Angeles")).single().label,
        )
    }

    @Test
    fun isoInstantUsesTheZone() {
        val photo = sample("i", "2026-03-01T00:30:00Z")

        assertEquals("March 2026", groupByMonth(listOf(photo), ZoneOffset.UTC).single().label)
        assertEquals(
            "February 2026",
            groupByMonth(listOf(photo), ZoneId.of("America/Los_Angeles")).single().label,
        )
    }

    @Test
    fun calendarDateStaysOnThatDay() {
        val photo = sample("d", "2026-03-01")
        val zone = ZoneId.of("Pacific/Kiritimati")

        assertEquals("March 2026", groupByMonth(listOf(photo), zone).single().label)
        assertEquals("March 2026", groupByMonth(listOf(photo), ZoneOffset.UTC).single().label)
    }

    @Test
    fun emptyList() {
        val groups = groupByMonth(emptyList<PhotoEntity>(), ZoneOffset.UTC)
        assertEquals(0, groups.size)
    }
}

private fun sample(hash: String, chosenDate: String?): PhotoEntity {
    return PhotoEntity(
        hash = hash,
        mediaId = null,
        name = "$hash.jpg",
        chosenDate = chosenDate,
        sourceTag = null,
        kind = "photo",
        sizeBytes = 100,
        drivePath = null,
        backedUp = false,
        previewFile = null,
        originalOnThisDrive = false,
        originalOnPhone = false,
    )
}
