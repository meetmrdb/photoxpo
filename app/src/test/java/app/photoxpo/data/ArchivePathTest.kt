package app.photoxpo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchivePathTest {
    @Test
    fun splitsARelativePath() {
        assertEquals(listOf("a", "b", "c"), parseArchivePath("a/b/c").segments)
    }

    @Test
    fun dropsEmptyAndDotSegments() {
        assertEquals(listOf("a", "b"), parseArchivePath("/a//./b/").segments)
        assertEquals(emptyList<String>(), parseArchivePath("").segments)
        assertEquals(emptyList<String>(), parseArchivePath("/").segments)
    }

    @Test
    fun rejectsASegmentThatEscapesTheArchiveDrive() {
        val error = org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            parseArchivePath("a/../b")
        }
        assertTrue(error.message!!.contains("Archive Drive"))
    }
}
