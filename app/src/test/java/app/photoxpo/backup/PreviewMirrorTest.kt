package app.photoxpo.backup

import app.photoxpo.core.ArchiveDrive
import app.photoxpo.core.CreateResult
import app.photoxpo.core.PreviewStore
import app.photoxpo.core.RenameResult
import app.photoxpo.core.previewFileName
import app.photoxpo.data.PreviewMirror
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class PreviewMirrorTest {
    @Test
    fun writesThePreviewUnderPhotoxpoPreviews() {
        val drive = MemoryDrive()
        val previews = MemoryPreviews()
        previews.write("abc", byteArrayOf(1, 2, 3))

        mirrorPreviews(drive, previews, listOf("abc", "missing"))

        val path = "Photoxpo/.photoxpo/previews/${previewFileName("abc")}"
        assertArrayEquals(byteArrayOf(1, 2, 3), drive.files.getValue(path))
        assertEquals(1, drive.writeCalls)
        assertFalse(drive.files.keys.any { it.contains("missing") })
    }

    @Test
    fun skipsAPreviewAlreadyOnTheArchiveDrive() {
        val drive = MemoryDrive()
        val path = "Photoxpo/.photoxpo/previews/${previewFileName("abc")}"
        drive.files[path] = byteArrayOf(9, 9)
        val previews = MemoryPreviews()
        previews.write("abc", byteArrayOf(1, 2, 3))

        mirrorPreviews(drive, previews, listOf("abc"))

        assertArrayEquals(byteArrayOf(9, 9), drive.files.getValue(path))
        assertEquals(0, drive.writeCalls)
        assertTrue(PreviewMirror(drive).write("abc", byteArrayOf(4)))
        assertEquals(0, drive.writeCalls)
    }

    @Test
    fun aDriveFailureDoesNotEscape() {
        val drive = MemoryDrive(failExists = true)
        val previews = MemoryPreviews()
        previews.write("abc", byteArrayOf(1))

        mirrorPreviews(drive, previews, listOf("abc"))

        assertEquals(0, drive.writeCalls)
        assertFalse(PreviewMirror(drive).write("abc", byteArrayOf(1)))
    }
}

private class MemoryPreviews : PreviewStore {
    private val files = linkedMapOf<String, ByteArray>()

    override fun write(hash: String, bytes: ByteArray) {
        files[hash] = bytes
    }

    override fun read(hash: String): ByteArray? = files[hash]

    override fun exists(hash: String): Boolean = hash in files
}

private class MemoryDrive(
    private val failExists: Boolean = false,
) : ArchiveDrive {
    val files = linkedMapOf<String, ByteArray>()
    var writeCalls: Int = 0

    override fun create(path: String): CreateResult {
        if (path in files) return CreateResult.Collision(path)
        files[path] = ByteArray(0)
        return CreateResult.Created
    }

    override fun write(path: String): OutputStream {
        writeCalls += 1
        return object : OutputStream() {
            private val buffer = ByteArrayOutputStream()

            override fun write(byte: Int) {
                buffer.write(byte)
            }

            override fun write(bytes: ByteArray, off: Int, len: Int) {
                buffer.write(bytes, off, len)
            }

            override fun close() {
                files[path] = buffer.toByteArray()
            }
        }
    }

    override fun read(path: String): InputStream = error("unused")

    override fun byteCount(path: String): Long = files.getValue(path).size.toLong()

    override fun rename(from: String, to: String): RenameResult = error("unused")

    override fun exists(path: String): Boolean {
        if (failExists) throw IOException("Archive Drive is absent")
        return path in files
    }

    override fun list(directory: String): List<String> = emptyList()

    override fun readText(path: String): String = error("unused")

    override fun appendText(path: String, text: String) = error("unused")

    override fun delete(path: String) = error("unused")
}
