package app.photoxpo.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoDurationTest {
    @Test
    fun shortAndLongVideos_readAsMinutesAndSeconds_orWithHours() {
        assertEquals("0:07", videoDurationLabel(7_000))
        assertEquals("2:35", videoDurationLabel(155_000))
        assertEquals("1:02:10", videoDurationLabel(3_730_000))
    }

    @Test
    fun roundsToTheNearestSecond() {
        assertEquals("0:02", videoDurationLabel(1_600))
        assertEquals("0:01", videoDurationLabel(1_400))
    }

    @Test
    fun unknownOrEmptyLength_hasNoLabel() {
        assertNull(videoDurationLabel(null))
        assertNull(videoDurationLabel(0))
    }
}
