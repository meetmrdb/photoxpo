package app.photoxpo.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class BackupResumeTest {
    @Rule
    @JvmField
    val folders = TemporaryFolder()

    @Test
    fun threeFiles_areBackedUpWithOneManifestLineEach() {
        val root = folders.newFolder("clean").toPath()
        val drive = FakeArchiveDrive(root)
        val samples = samples()
        val result = startBackupRun(media(samples), drive, clock = fixedClock())
        assertEquals(3, drive.writeCalls)
        assertBackedUp(root, drive, result, samples)
        val contents = ManifestReader().readAll(drive)
        assertEquals(1, contents.size)
        assertFalse(contents.single().interrupted)
    }

    @Test
    fun killAfterCreate_resumesWithoutDuplicates() {
        assertKillThenResume(DriveFaultStep.AFTER_CREATE)
    }

    @Test
    fun killMidWrite_resumesWithoutDuplicates() {
        assertKillThenResume(DriveFaultStep.MID_WRITE)
    }

    @Test
    fun killAfterWrite_resumesWithoutDuplicates() {
        assertKillThenResume(DriveFaultStep.AFTER_WRITE)
    }

    @Test
    fun killMidReadBack_resumesWithoutDuplicates() {
        assertKillThenResume(DriveFaultStep.MID_READ_BACK)
    }

    @Test
    fun killBeforeRename_resumesWithoutDuplicates() {
        assertKillThenResume(DriveFaultStep.BEFORE_RENAME)
    }

    @Test
    fun killAfterRenameBeforeManifest_resumesWithoutDuplicates() {
        assertKillThenResume(DriveFaultStep.AFTER_RENAME_BEFORE_MANIFEST)
    }

    @Test
    fun killAfterManifestLine_resumesWithoutDuplicates() {
        assertKillThenResume(DriveFaultStep.AFTER_MANIFEST_LINE)
    }

    @Test
    fun driveRemovedMidWrite_pausesAndResumesOnTheSameRunner() {
        val root = folders.newFolder("unplug").toPath()
        val samples = samples()
        val drive = FakeArchiveDrive(root)
        drive.faults = DriveFault(
            step = DriveFaultStep.MID_WRITE,
            error = DriveUnavailableException("Archive Drive removed"),
            afterBytes = 2,
        )
        val clock = SettableClock(START)
        val runner = BackupRunner(media(samples), drive, clock = clock)
        val paused = runner.start()

        assertEquals(samples.map { it.file.id }, paused.items.map { it.id })
        assertTrue(paused.items.all { it.state == ItemState.PAUSED_DRIVE_OUT })
        assertEquals(2L, drive.byteCount(samples[0].drivePath + ".photoxpo-part"))
        val text = drive.readText(paused.manifestPath)
        assertFalse(text.contains("\"type\":\"file\""))
        assertFalse(text.contains("\"type\":\"footer\""))
        val killed = ManifestReader().read(text)
        assertTrue(killed.entries.isEmpty())
        assertTrue(killed.interrupted)

        drive.faults = null
        clock.now = clock.now.plusSeconds(2)
        val writesAtPause = drive.writeCalls
        val resumed = runner.start()
        assertEquals(samples.size, drive.writeCalls - writesAtPause)
        assertBackedUp(root, drive, resumed, samples)
    }

    @Test
    fun driveRemovedAfterOneCommit_pausesOnlyTheRest() {
        val root = folders.newFolder("unplug-later").toPath()
        val samples = samples()
        val drive = FakeArchiveDrive(root)
        drive.faults = DriveFault(
            step = DriveFaultStep.MID_WRITE,
            error = DriveUnavailableException("Archive Drive removed"),
            afterBytes = 2,
            occurrence = 2,
        )
        val clock = SettableClock(START)
        val runner = BackupRunner(media(samples), drive, clock = clock)
        val paused = runner.start()

        assertEquals(ItemState.COMMITTED, paused.items[0].state)
        assertEquals(ItemState.PAUSED_DRIVE_OUT, paused.items[1].state)
        assertEquals(ItemState.PAUSED_DRIVE_OUT, paused.items[2].state)
        assertArrayEquals(samples[0].bytes, Files.readAllBytes(root.resolve(samples[0].drivePath)))
        assertEquals(2L, drive.byteCount(samples[1].drivePath + ".photoxpo-part"))
        val text = drive.readText(paused.manifestPath)
        assertTrue(text.contains("\"type\":\"file\""))
        assertFalse(text.contains("\"type\":\"footer\""))
        val killed = ManifestReader().read(text)
        assertEquals(1, killed.entries.size)
        assertEquals(samples[0].drivePath, killed.entries.single().drivePath)
        assertTrue(killed.interrupted)

        drive.faults = null
        clock.now = clock.now.plusSeconds(2)
        val writesAtPause = drive.writeCalls
        val resumed = runner.start()
        assertEquals(2, drive.writeCalls - writesAtPause)
        assertBackedUp(root, drive, resumed, samples)
        val contents = ManifestReader().readAll(drive)
        assertEquals(2, contents.size)
        assertEquals(1, contents[0].entries.size)
        assertEquals(2, contents[1].entries.size)
        assertEquals(samples.size, contents.flatMap { it.entries }.map { it.drivePath }.distinct().size)
    }

    @Test
    fun unprovenFinalWithMatchingBytes_isAdoptedWithoutRecopy() {
        val root = folders.newFolder("adopt").toPath()
        val samples = samples().take(2)
        seedFinal(root, samples[0])
        val drive = FakeArchiveDrive(root)
        val result = startBackupRun(media(samples), drive, clock = fixedClock())

        assertEquals(1, drive.writeCalls)
        assertEquals(ItemState.COMMITTED, result.items.single { it.id == samples[0].file.id }.state)
        assertEquals(ItemState.COMMITTED, result.items.single { it.id == samples[1].file.id }.state)
        assertBackedUp(root, drive, result, samples)
        assertFalse(drive.exists(samples[0].drivePath + ".photoxpo-unproven-1"))
    }

    @Test
    fun unprovenFinalWithDifferentBytes_isMovedAsideAndOriginalIsCopied() {
        val root = folders.newFolder("mismatch").toPath()
        val samples = samples().take(2)
        val wrong = byteArrayOf(42, 42, 42, 42)
        seedBytes(root, samples[0].drivePath, wrong)
        val drive = FakeArchiveDrive(root)
        val result = startBackupRun(media(samples), drive, clock = fixedClock())

        assertEquals(2, drive.writeCalls)
        assertTrue(result.items.all { it.state == ItemState.COMMITTED })
        assertArrayEquals(samples[0].bytes, Files.readAllBytes(root.resolve(samples[0].drivePath)))
        assertArrayEquals(samples[1].bytes, Files.readAllBytes(root.resolve(samples[1].drivePath)))
        val aside = samples[0].drivePath + ".photoxpo-unproven-1"
        assertTrue(drive.exists(aside))
        assertArrayEquals(wrong, Files.readAllBytes(root.resolve(aside)))
        assertNoPartFiles(root)

        val entries = ManifestReader().readAll(drive).flatMap { it.entries }
        assertEquals(samples.map { it.drivePath }.sorted(), entries.map { it.drivePath }.sorted())
        val adopted = entries.single { it.drivePath == samples[0].drivePath }
        assertEquals(sha256(samples[0].bytes), adopted.hash)
        assertEquals(samples[0].file.sizeBytes, adopted.size)
    }

    @Test
    fun driveUnavailable_isAnIoException() {
        val error: Throwable = DriveUnavailableException("Archive Drive removed")
        assertTrue(error is java.io.IOException)
        assertEquals("Archive Drive removed", error.message)
    }

    private fun assertKillThenResume(step: DriveFaultStep) {
        val root = folders.newFolder(step.name).toPath()
        val samples = samples()
        val drive = FakeArchiveDrive(root)
        drive.faults = DriveFault(
            step = step,
            error = SimulatedProcessKill(step),
            afterBytes = 2,
        )
        val clock = SettableClock(START)
        val media = media(samples)
        val thrown = try {
            BackupRunner(media, drive, clock = clock).start()
            null
        } catch (error: Throwable) {
            error
        }
        assertTrue(
            "expected process kill at $step but was $thrown",
            thrown is SimulatedProcessKill,
        )

        val killed = ManifestReader().readAll(drive)
        assertEquals(1, killed.size)
        assertTrue(killed.single().interrupted)
        assertEquals(linesBeforeResume(step), killed.single().entries.size)
        val present = samples.count { Files.exists(root.resolve(it.drivePath)) }
        assertTrue("later files still pending after $step ($present present)", present < samples.size)

        clock.now = clock.now.plusSeconds(2)
        val healthy = FakeArchiveDrive(root)
        val resumed = BackupRunner(media, healthy, clock = clock).start()
        assertEquals(resumedWrites(step), healthy.writeCalls)
        assertBackedUp(root, healthy, resumed, samples)
        val contents = ManifestReader().readAll(healthy)
        assertEquals(2, contents.size)
        assertTrue(contents[0].interrupted)
        assertFalse(contents[1].interrupted)
        assertEquals(linesBeforeResume(step), contents[0].entries.size)
        assertEquals(samples.size - linesBeforeResume(step), contents[1].entries.size)
        assertTrue(resumed.items.all { it.state == ItemState.COMMITTED })
    }

    private fun assertBackedUp(
        root: Path,
        drive: ArchiveDrive,
        result: BackupRunResult,
        samples: List<Sample>,
    ) {
        assertEquals(samples.map { it.file.id }, result.items.map { it.id })
        for (sample in samples) {
            val item = result.items.single { it.id == sample.file.id }
            assertEquals(ItemState.COMMITTED, item.state)
            assertEquals(sample.file.name, item.name)
            assertArrayEquals(sample.bytes, Files.readAllBytes(root.resolve(sample.drivePath)))
        }
        assertNoPartFiles(root)
        val entries = ManifestReader().readAll(drive).flatMap { it.entries }
        assertEquals(samples.size, entries.size)
        assertEquals(samples.size, entries.map { it.drivePath }.distinct().size)
        val expectedPaths = samples.map { it.drivePath.removePrefix("Photoxpo/Originals/") }.sorted()
        assertEquals(expectedPaths, filesUnder(root.resolve("Photoxpo/Originals")))
        for (sample in samples) {
            val entry = entries.single { it.drivePath == sample.drivePath }
            assertEquals(sample.file.sizeBytes, entry.size)
            assertEquals(sample.file.name, entry.originalFilename)
            assertEquals(sha256(sample.bytes), entry.hash)
            assertEquals(sample.kindWire, entry.kind)
            assertEquals(sample.chosenDate, entry.chosenDate)
            assertEquals("modified", entry.sourceTag)
            assertTrue(result.items.any { it.state == ItemState.COMMITTED && it.name == entry.originalFilename })
        }
    }

    private fun assertNoPartFiles(root: Path) {
        val parts = Files.walk(root).use { stream ->
            stream.filter { it.fileName?.toString()?.endsWith(".photoxpo-part") == true }.toList()
        }
        assertTrue("leftover parts: $parts", parts.isEmpty())
    }

    private fun filesUnder(root: Path): List<String> {
        if (!Files.exists(root)) return emptyList()
        return Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .map { root.relativize(it).toString().replace('\\', '/') }
                .sorted()
                .toList()
        }
    }

    private fun seedFinal(root: Path, sample: Sample) {
        seedBytes(root, sample.drivePath, sample.bytes)
    }

    private fun seedBytes(root: Path, drivePath: String, bytes: ByteArray) {
        val target = root.resolve(drivePath)
        Files.createDirectories(target.parent)
        Files.write(target, bytes)
    }

    private fun media(samples: List<Sample>): FakeMediaSource {
        return FakeMediaSource(samples.map { it.file to it.bytes })
    }

    private fun samples(): List<Sample> {
        return listOf(
            sample(
                id = "media-1",
                name = "IMG_0001.jpg",
                kind = MediaKind.PHOTO,
                modified = "2020-01-15T10:00:00Z",
                bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            ),
            sample(
                id = "media-2",
                name = "IMG_0002.jpg",
                kind = MediaKind.PHOTO,
                modified = "2020-06-02T12:00:00Z",
                bytes = byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1),
            ),
            sample(
                id = "media-3",
                name = "VID_0003.mp4",
                kind = MediaKind.VIDEO,
                modified = "2021-03-03T08:00:00Z",
                bytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
            ),
        )
    }

    private fun sample(
        id: String,
        name: String,
        kind: MediaKind,
        modified: String,
        bytes: ByteArray,
    ): Sample {
        val instant = Instant.parse(modified)
        val localDate = instant.atZone(ZoneOffset.UTC).toLocalDate().toString()
        val year = localDate.substring(0, 4)
        val yearMonth = localDate.substring(0, 7)
        val file = MediaFile(
            id = id,
            name = name,
            sizeBytes = bytes.size.toLong(),
            modifiedAtEpochMillis = instant.toEpochMilli(),
            kind = kind,
        )
        return Sample(
            file = file,
            bytes = bytes,
            drivePath = "Photoxpo/Originals/$year/$yearMonth/$name",
            chosenDate = localDate,
            kindWire = kind.wireName(),
        )
    }

    private fun resumedWrites(step: DriveFaultStep): Int = when (step) {
        DriveFaultStep.AFTER_RENAME_BEFORE_MANIFEST,
        DriveFaultStep.AFTER_MANIFEST_LINE,
        -> 2
        DriveFaultStep.AFTER_CREATE,
        DriveFaultStep.MID_WRITE,
        DriveFaultStep.AFTER_WRITE,
        DriveFaultStep.MID_READ_BACK,
        DriveFaultStep.BEFORE_RENAME,
        -> 3
    }

    private fun linesBeforeResume(step: DriveFaultStep): Int = when (step) {
        DriveFaultStep.AFTER_MANIFEST_LINE -> 1
        else -> 0
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

    private fun fixedClock(): Clock = Clock.fixed(START, ZoneOffset.UTC)

    private class Sample(
        val file: MediaFile,
        val bytes: ByteArray,
        val drivePath: String,
        val chosenDate: String,
        val kindWire: String,
    )

    private class SettableClock(start: Instant) : Clock() {
        var now: Instant = start

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }

    private class SimulatedProcessKill(step: DriveFaultStep) : RuntimeException("killed at $step")

    companion object {
        private val START: Instant = Instant.parse("2026-10-11T18:30:00Z")
    }
}
