package app.photoxpo.photos

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotosStripTest {
    @Test
    fun saysHowManyAreOnTheArchiveDrive() {
        assertEquals("502 on your Archive Drive", photosStripLine(onDrive = 502, newWaiting = null))
        assertEquals("502 on your Archive Drive", photosStripLine(onDrive = 502, newWaiting = 0))
    }

    @Test
    fun addsHowManyNewOnesAreWaiting() {
        assertEquals(
            "502 on your Archive Drive · 12 new waiting",
            photosStripLine(onDrive = 502, newWaiting = 12),
        )
    }

    @Test
    fun groupsThousands() {
        assertEquals(
            "1,204 on your Archive Drive · 3,660 new waiting",
            photosStripLine(onDrive = 1204, newWaiting = 3660, locale = java.util.Locale.US),
        )
    }

    @Test
    fun withNothingOnTheDriveYet_saysSoPlainly() {
        assertEquals("Nothing on your Archive Drive yet", photosStripLine(onDrive = 0, newWaiting = null))
        assertEquals(
            "Nothing on your Archive Drive yet · 12 new waiting",
            photosStripLine(onDrive = 0, newWaiting = 12),
        )
    }
}
