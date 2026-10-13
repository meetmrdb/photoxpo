package app.photoxpo.history

import app.photoxpo.core.DifferingOriginal
import app.photoxpo.core.MediaFile
import app.photoxpo.core.MediaKind
import app.photoxpo.core.VerifyEverythingReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifyEverythingTextTest {
    private fun d(name: String) = DifferingOriginal(MediaFile(name, name, 1, 1, MediaKind.PHOTO), "x")

    @Test
    fun allIdentical() {
        assertEquals(
            "All 3 checked files are identical to their copies",
            verifyEverythingMessage(VerifyEverythingReport(3, emptyList(), emptyList())),
        )
    }

    @Test
    fun differingFilesAreNamed() {
        val text = verifyEverythingMessage(VerifyEverythingReport(3, listOf(d("a.jpg")), emptyList()))
        assertTrue(text.startsWith("1 file is different from its copy: a.jpg"))
    }

    @Test
    fun unreadableFilesAreNotCalledIdentical() {
        val text = verifyEverythingMessage(VerifyEverythingReport(0, emptyList(), listOf(d("a.jpg"))))
        assertEquals("1 file could not be checked. Reconnect your drive and try again", text)
    }

    @Test
    fun nothingBackedUp() {
        assertEquals("Nothing to check yet", verifyEverythingMessage(VerifyEverythingReport(0, emptyList(), emptyList())))
    }
}
