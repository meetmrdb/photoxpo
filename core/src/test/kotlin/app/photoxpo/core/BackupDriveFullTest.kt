package app.photoxpo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BackupDriveFullTest {
    @Rule
    @JvmField
    val folders = TemporaryFolder()

    @Test
    fun runOverFreeSpace_copiesWholeFilesUntilNoneMoreFits_thenStopsDriveFull_withNoPartialFile() {
        val root = folders.newFolder("full").toPath()
        val files = (1..4).map { sample(it, size = 1_000) }
        // Room for the drive id, the manifest and two whole Originals, but not a third.
        val drive = FakeArchiveDrive(root, capacityBytes = 2_000 + 1_500)
        val result = BackupRunner(
            mediaSource = FakeMediaSource(files.map { it.file to it.bytes }),
            archiveDrive = drive,
            clock = Clock.fixed(START, ZoneOffset.UTC),
        ).start()

        assertEquals(BackupRunStatus.Completed, result.status)
        assertEquals(StopReason.DriveFull, result.stopReason)
        val committed = result.items.count { it.state == ItemState.COMMITTED }
        assertTrue(committed in 1..3)
        assertEquals(4 - committed, result.filesLeft)
        files.take(committed).forEach { assertEquals(1_000L, drive.byteCount(it.path)) }
        files.drop(committed).forEach { assertFalse(drive.exists(it.path)) }
        val parts = Files.walk(root).use { s ->
            s.filter { it.fileName?.toString()?.endsWith(".photoxpo-part") == true }.toList()
        }
        assertTrue("leftover parts: $parts", parts.isEmpty())
    }

    @Test
    fun partDeleteThatFailsOnce_isRetried_andTheRunStillEndsDriveFullWithNoPart() {
        val root = folders.newFolder("retry").toPath()
        val files = (1..4).map { sample(it, size = 1_000) }
        val real = FakeArchiveDrive(root, capacityBytes = 2_000 + 1_500)
        val flaky = FlakyPartDelete(real, failures = 1)
        val result = BackupRunner(
            mediaSource = FakeMediaSource(files.map { it.file to it.bytes }),
            archiveDrive = flaky,
            clock = Clock.fixed(START, ZoneOffset.UTC),
        ).start()

        assertEquals(StopReason.DriveFull, result.stopReason)
        val parts = Files.walk(root).use { s ->
            s.filter { it.fileName?.toString()?.endsWith(".photoxpo-part") == true }.toList()
        }
        assertTrue("leftover parts: $parts", parts.isEmpty())
    }

    @Test
    fun partDeleteThatKeepsFailing_isSurfaced_notSwallowed() {
        val root = folders.newFolder("stuck").toPath()
        val files = (1..4).map { sample(it, size = 1_000) }
        val flaky = FlakyPartDelete(FakeArchiveDrive(root, capacityBytes = 2_000 + 1_500), failures = 100)
        val error = runCatching {
            BackupRunner(
                mediaSource = FakeMediaSource(files.map { it.file to it.bytes }),
                archiveDrive = flaky,
                clock = Clock.fixed(START, ZoneOffset.UTC),
            ).start()
        }.exceptionOrNull()

        assertTrue("expected the failure to surface, got $error", error != null && error !is DriveFullException)
        assertTrue(error!!.suppressed.any { it is DriveFullException })
    }

    private class FlakyPartDelete(private val inner: ArchiveDrive, private var failures: Int) : ArchiveDrive by inner {
        override fun delete(path: String) {
            if (path.endsWith(".photoxpo-part") && failures > 0) {
                failures -= 1
                throw java.io.IOException("cannot delete")
            }
            inner.delete(path)
        }
    }

    private fun sample(index: Int, size: Int): Sample {
        val bytes = ByteArray(size) { (index * 31 + it).toByte() }
        val name = "IMG_000$index.jpg"
        val date = "2020-01-0$index"
        return Sample(
            file = MediaFile(
                id = "media-$index",
                name = name,
                sizeBytes = size.toLong(),
                modifiedAtEpochMillis = Instant.parse("${date}T10:00:00Z").toEpochMilli(),
                kind = MediaKind.PHOTO,
            ),
            bytes = bytes,
            path = originalPath(date, name),
        )
    }

    private class Sample(val file: MediaFile, val bytes: ByteArray, val path: String)

    private companion object {
        val START: Instant = Instant.parse("2026-10-11T18:30:00Z")
    }
}
