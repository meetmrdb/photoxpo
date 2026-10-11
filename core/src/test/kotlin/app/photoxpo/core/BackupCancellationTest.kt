package app.photoxpo.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BackupCancellationTest {
    @Rule
    @JvmField
    val folders = TemporaryFolder()

    @Test
    fun cancelledRun_stopsBetweenFiles_andLeavesNothingHalfDone() {
        val root = folders.newFolder("pause").toPath()
        val drive = FakeArchiveDrive(root)
        val first = file("media-1", "IMG_0001.jpg", "2020-01-15T10:00:00Z", byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val second = file("media-2", "IMG_0002.jpg", "2020-06-02T12:00:00Z", byteArrayOf(9, 8, 7, 6))
        val firstPath = originalPath("2020-01-15", first.name)
        val secondPath = originalPath("2020-06-02", second.name)
        var checks = 0
        val runner = BackupRunner(
            mediaSource = FakeMediaSource(listOf(first.file to first.bytes, second.file to second.bytes)),
            archiveDrive = drive,
            clock = Clock.fixed(START, ZoneOffset.UTC),
            isCancelled = {
                checks += 1
                drive.exists(firstPath)
            },
        )

        val paused = runner.start()

        assertEquals(2, checks)
        assertEquals(BackupRunStatus.Paused, paused.status)
        assertEquals(listOf(first.id, second.id), paused.items.map { it.id })
        assertEquals(ItemState.COMMITTED, paused.items[0].state)
        assertEquals(ItemState.PAUSED_DRIVE_OUT, paused.items[1].state)
        assertEquals(1, drive.writeCalls)
        assertArrayEquals(first.bytes, Files.readAllBytes(root.resolve(firstPath)))
        assertFalse(drive.exists(secondPath))
        assertFalse(drive.exists(secondPath + ".photoxpo-part"))
        assertNoPartFiles(root)

        val contents = ManifestReader().read(drive.readText(paused.manifestPath))
        assertTrue(contents.interrupted)
        assertEquals(1, contents.entries.size)
        val entry = contents.entries.single()
        assertEquals(firstPath, entry.drivePath)
        assertEquals(first.bytes.size.toLong(), entry.size)
        assertEquals(sha256(first.bytes), entry.hash)
        assertEquals(first.name, entry.originalFilename)
        assertFalse(drive.readText(paused.manifestPath).contains(second.name))

        val resumedDrive = FakeArchiveDrive(root)
        val resumed = BackupRunner(
            mediaSource = FakeMediaSource(listOf(first.file to first.bytes, second.file to second.bytes)),
            archiveDrive = resumedDrive,
            clock = Clock.fixed(START.plusSeconds(5), ZoneOffset.UTC),
            isCancelled = { false },
        ).start()
        assertEquals(BackupRunStatus.Completed, resumed.status)
        assertTrue(resumed.items.all { it.state == ItemState.COMMITTED })
        assertEquals(1, resumedDrive.writeCalls)
        assertArrayEquals(second.bytes, Files.readAllBytes(root.resolve(secondPath)))
        val stored = ManifestReader().readAll(resumedDrive).flatMap { it.entries }.map { it.drivePath }.distinct()
        assertEquals(listOf(firstPath, secondPath).sorted(), stored.sorted())
        assertNoPartFiles(root)
    }

    @Test
    fun cancelledBeforeAnyOriginal_leavesNoPartialAndNoManifest() {
        val root = folders.newFolder("immediate").toPath()
        val drive = FakeArchiveDrive(root)
        val first = file("media-1", "IMG_0001.jpg", "2020-01-15T10:00:00Z", byteArrayOf(1, 2, 3, 4))
        val second = file("media-2", "IMG_0002.jpg", "2020-06-02T12:00:00Z", byteArrayOf(5, 6, 7, 8))
        var checks = 0
        val paused = BackupRunner(
            mediaSource = FakeMediaSource(listOf(first.file to first.bytes, second.file to second.bytes)),
            archiveDrive = drive,
            clock = Clock.fixed(START, ZoneOffset.UTC),
            isCancelled = {
                checks += 1
                true
            },
        ).start()

        assertEquals(1, checks)
        assertEquals(BackupRunStatus.Paused, paused.status)
        assertEquals("", paused.manifestPath)
        assertTrue(paused.items.all { it.state == ItemState.PAUSED_DRIVE_OUT })
        assertEquals(0, drive.writeCalls)
        assertTrue(drive.list("Photoxpo/.photoxpo/runs").isEmpty())
        assertFalse(drive.exists(originalPath("2020-01-15", first.name)))
        assertFalse(drive.exists(originalPath("2020-06-02", second.name)))
        assertNoPartFiles(root)
    }

    private fun assertNoPartFiles(root: java.nio.file.Path) {
        val parts = Files.walk(root).use { stream ->
            stream.filter { it.fileName?.toString()?.endsWith(".photoxpo-part") == true }.toList()
        }
        assertTrue("leftover parts: $parts", parts.isEmpty())
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

    private fun file(id: String, name: String, modified: String, bytes: ByteArray): Sample {
        return Sample(
            id = id,
            name = name,
            bytes = bytes,
            modifiedAtEpochMillis = Instant.parse(modified).toEpochMilli(),
        )
    }

    private class Sample(
        val id: String,
        val name: String,
        val bytes: ByteArray,
        modifiedAtEpochMillis: Long,
    ) {
        val file = MediaFile(
            id = id,
            name = name,
            sizeBytes = bytes.size.toLong(),
            modifiedAtEpochMillis = modifiedAtEpochMillis,
            kind = MediaKind.PHOTO,
        )
    }

    private companion object {
        val START: Instant = Instant.parse("2026-10-11T18:30:00Z")
    }
}
