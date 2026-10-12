package app.photoxpo.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class PhoneFilePreviewStoreTest {
    @Rule
    @JvmField
    val folders = TemporaryFolder()

    @Test
    fun move_replacesThePreview_andRemovesTheTempFile() {
        val dir = folders.newFolder("previews")
        val target = File(dir, "abc.jpg")
        val temp = File(dir, "preview-new.tmp")
        target.writeBytes(byteArrayOf(1, 2, 3))
        temp.writeBytes(byteArrayOf(4, 5))

        movePreviewFile(temp, target)

        assertArrayEquals(byteArrayOf(4, 5), target.readBytes())
        assertFalse(temp.exists())
    }

    @Test
    fun failedMove_leavesTheExistingPreview() {
        val dir = folders.newFolder("previews")
        val target = File(dir, "abc.jpg")
        target.writeBytes(byteArrayOf(1, 2, 3))
        val missing = File(dir, "missing.tmp")

        var thrown = false
        try {
            movePreviewFile(missing, target)
        } catch (_: IOException) {
            thrown = true
        }

        assertTrue(thrown)
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
    }
}
