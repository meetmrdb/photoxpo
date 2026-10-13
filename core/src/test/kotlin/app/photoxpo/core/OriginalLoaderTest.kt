package app.photoxpo.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Random

class OriginalLoaderTest {
    @Rule
    @JvmField
    val folders = TemporaryFolder()

    @Test
    fun fiveRandomOriginals_roundTrip_hashEqualsTheDrive() {
        val random = Random(32)
        val root = folders.newFolder("drive").toPath()
        val drive = FakeArchiveDrive(root)
        val gallery = FakePhoneGallery()
        val loader = OriginalLoader(drive, gallery)

        repeat(5) { index ->
            val bytes = ByteArray(random.nextInt(200_000) + 1)
            random.nextBytes(bytes)
            val name = "IMG_$index.jpg"
            val path = "Photoxpo/Originals/2024/2024-06/$name"
            drive.create(path)
            drive.write(path).use { output -> output.write(bytes) }
            val hash = sha256(bytes)
            val progress = ArrayList<Long>()
            val result = loader.load(
                hash = hash,
                drivePath = path,
                expectedSize = bytes.size.toLong(),
                name = name,
                mimeHint = "image/jpeg",
                isCancelled = { false },
                onProgress = { copied -> progress += copied },
            )
            assertTrue(result is LoadResult.Loaded)
            val uri = (result as LoadResult.Loaded).uri
            val published = gallery.bytes(uri)
            assertArrayEquals(bytes, published)
            assertEquals(hash, sha256(published))
            assertEquals(hash, sha256(Files.readAllBytes(root.resolve(path))))
            assertEquals(bytes.size.toLong(), progress.last())
        }
        assertEquals(5, gallery.publishedCount())
        assertEquals(0, gallery.partialCount())
    }

    @Test
    fun cancel_leavesNothingPublishedAndNothingPartial() {
        val bytes = ByteArray(96 * 1024) { index -> (index % 251).toByte() }
        val drive = driveWith("Photoxpo/Originals/2024/2024-06/IMG.jpg", bytes)
        val gallery = FakePhoneGallery()
        var checks = 0
        val result = OriginalLoader(drive.drive, gallery).load(
            hash = sha256(bytes),
            drivePath = drive.path,
            expectedSize = bytes.size.toLong(),
            name = "IMG.jpg",
            mimeHint = "image/jpeg",
            isCancelled = {
                checks += 1
                checks >= 4
            },
        )
        assertEquals(LoadResult.Cancelled, result)
        assertEquals(0, gallery.publishedCount())
        assertEquals(0, gallery.partialCount())
        assertArrayEquals(bytes, Files.readAllBytes(drive.root.resolve(drive.path)))
    }

    @Test
    fun driveUnavailableMidLoad_leavesNothingAndReturnsDriveGone() {
        val bytes = ByteArray(8 * 1024) { it.toByte() }
        val stored = driveWith("Photoxpo/Originals/2024/2024-06/IMG.jpg", bytes)
        stored.drive.unavailableAfterBytes = 1
        val gallery = FakePhoneGallery()
        val result = OriginalLoader(stored.drive, gallery).load(
            hash = sha256(bytes),
            drivePath = stored.path,
            expectedSize = bytes.size.toLong(),
            name = "IMG.jpg",
            mimeHint = "image/jpeg",
            isCancelled = { false },
        )
        assertEquals(LoadResult.DriveGone, result)
        assertEquals(0, gallery.publishedCount())
        assertEquals(0, gallery.partialCount())
    }

    @Test
    fun driveUnavailableBeforeCopy_returnsDriveGone() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val stored = driveWith("Photoxpo/Originals/2024/2024-06/IMG.jpg", bytes)
        stored.drive.unavailable = true
        val gallery = FakePhoneGallery()
        val result = OriginalLoader(stored.drive, gallery).load(
            hash = sha256(bytes),
            drivePath = stored.path,
            expectedSize = bytes.size.toLong(),
            name = "IMG.jpg",
            mimeHint = null,
            isCancelled = { false },
        )
        assertEquals(LoadResult.DriveGone, result)
        assertEquals(0, gallery.publishedCount())
        assertEquals(0, gallery.partialCount())
    }

    @Test
    fun missingDriveFile_returnsNotOnDrive() {
        val root = folders.newFolder("missing").toPath()
        val gallery = FakePhoneGallery()
        val result = OriginalLoader(FakeArchiveDrive(root), gallery).load(
            hash = sha256(byteArrayOf(1)),
            drivePath = "Photoxpo/Originals/2024/2024-06/missing.jpg",
            expectedSize = 1,
            name = "missing.jpg",
            mimeHint = "image/jpeg",
            isCancelled = { false },
        )
        assertEquals(LoadResult.NotOnDrive, result)
        assertEquals(0, gallery.publishedCount())
        assertEquals(0, gallery.partialCount())
    }

    @Test
    fun sizeMismatch_returnsNotOnDrive() {
        val bytes = byteArrayOf(1, 2, 3)
        val stored = driveWith("Photoxpo/Originals/2024/2024-06/IMG.jpg", bytes)
        val gallery = FakePhoneGallery()
        val result = OriginalLoader(stored.drive, gallery).load(
            hash = sha256(bytes),
            drivePath = stored.path,
            expectedSize = bytes.size.toLong() + 1,
            name = "IMG.jpg",
            mimeHint = "image/jpeg",
            isCancelled = { false },
        )
        assertEquals(LoadResult.NotOnDrive, result)
        assertEquals(0, gallery.publishedCount())
        assertEquals(0, gallery.partialCount())
    }

    @Test
    fun manifestHashMismatch_isNeverPublished() {
        val bytes = byteArrayOf(9, 8, 7, 6, 5)
        val stored = driveWith("Photoxpo/Originals/2024/2024-06/IMG.jpg", bytes)
        val gallery = FakePhoneGallery()
        val result = OriginalLoader(stored.drive, gallery).load(
            hash = "0".repeat(64),
            drivePath = stored.path,
            expectedSize = bytes.size.toLong(),
            name = "IMG.jpg",
            mimeHint = "image/jpeg",
            isCancelled = { false },
        )
        assertEquals(LoadResult.Failed, result)
        assertEquals(0, gallery.publishedCount())
        assertEquals(0, gallery.partialCount())
    }

    @Test
    fun corruptedDriveRead_isNeverPublished() {
        val bytes = byteArrayOf(4, 5, 6, 7, 8, 9)
        val root = folders.newFolder("corrupt").toPath()
        val drive = FakeArchiveDrive(root, corruptReadBack = true)
        val path = "Photoxpo/Originals/2024/2024-06/IMG.jpg"
        drive.create(path)
        drive.write(path).use { output -> output.write(bytes) }
        val gallery = FakePhoneGallery()
        val result = OriginalLoader(drive, gallery).load(
            hash = sha256(bytes),
            drivePath = path,
            expectedSize = bytes.size.toLong(),
            name = "IMG.jpg",
            mimeHint = "image/jpeg",
            isCancelled = { false },
        )
        assertEquals(LoadResult.Failed, result)
        assertEquals(0, gallery.publishedCount())
        assertEquals(0, gallery.partialCount())
    }

    @Test
    fun writeFailure_abortsAndDoesNotCommit() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val stored = driveWith("Photoxpo/Originals/2024/2024-06/IMG.jpg", bytes)
        val gallery = ThrowingGallery()
        val result = OriginalLoader(stored.drive, gallery).load(
            hash = sha256(bytes),
            drivePath = stored.path,
            expectedSize = bytes.size.toLong(),
            name = "IMG.jpg",
            mimeHint = "image/jpeg",
            isCancelled = { false },
        )
        assertEquals(LoadResult.Failed, result)
        assertTrue(gallery.aborted)
        assertEquals(false, gallery.committed)
    }

    private fun driveWith(path: String, bytes: ByteArray): Stored {
        val root = folders.newFolder().toPath()
        val drive = FakeArchiveDrive(root)
        drive.create(path)
        drive.write(path).use { output -> output.write(bytes) }
        return Stored(drive, root, path)
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val digits = "0123456789abcdef"
        val chars = CharArray(digest.size * 2)
        digest.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            chars[index * 2] = digits[value ushr 4]
            chars[index * 2 + 1] = digits[value and 0x0f]
        }
        return String(chars)
    }
}

private class Stored(
    val drive: FakeArchiveDrive,
    val root: java.nio.file.Path,
    val path: String,
)

private class ThrowingGallery : PhoneGallery {
    var aborted: Boolean = false
    var committed: Boolean = false

    override fun createLoadedFile(name: String, mimeHint: String?): LoadedFileHandle {
        return object : LoadedFileHandle {
            override val output: OutputStream = object : OutputStream() {
                override fun write(b: Int) {
                    throw IOException("phone copy stopped")
                }
            }

            override fun commit(): String {
                committed = true
                return "loaded://nope"
            }

            override fun abort() {
                aborted = true
            }
        }
    }
}
