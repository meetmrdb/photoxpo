package app.photoxpo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Free up space re-verifies each Original it is about to remove. A failed or skipped check never removes one.
 */
class FreeUpSpaceVerifyTest {
    @Rule
    @JvmField
    val folders = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse("2026-10-11T18:30:00Z"), ZoneOffset.UTC)

    @Test
    fun identicalOriginal_isRemovedAfterBeingReadAgain() {
        val world = backedUp(PHOTO_A, PHOTO_B)
        world.source.opens = 0
        world.drive.reads = 0
        val removed = mutableListOf<String>()

        val result = world.freeUp().removeVerified(world.files) { removed += it.id }

        assertEquals(listOf("a", "b"), removed)
        assertEquals(listOf("a", "b"), result.removed.map { it.id })
        assertTrue(result.skipped.isEmpty())
        // Checked "just now", yet both sides were still read again.
        assertEquals(2, world.source.opens)
        assertEquals(2, world.drive.reads)
    }

    @Test
    fun copyOnDriveChangedSinceTheCheck_isSkippedAndNotRemoved() {
        val world = backedUp(PHOTO_A, PHOTO_B)
        corruptOnDrive(world, "a.jpg")
        val removed = mutableListOf<String>()

        val result = world.freeUp().removeVerified(world.files) { removed += it.id }

        assertEquals(listOf("b"), removed)
        assertEquals(listOf("a"), result.skipped.map { it.file.id })
    }

    @Test
    fun copyMissingFromDrive_isSkippedAndNotRemoved() {
        val world = backedUp(PHOTO_A)
        world.drive.delete(drivePath("a.jpg"))
        val removed = mutableListOf<String>()

        val result = world.freeUp().removeVerified(world.files) { removed += it.id }

        assertTrue(removed.isEmpty())
        assertEquals(1, result.skipped.size)
    }

    @Test
    fun originalEditedOnThePhoneSinceTheBackup_isSkippedAndNotRemoved() {
        val world = backedUp(PHOTO_A)
        world.source.replaceBytes("a", byteArrayOf(9, 9, 9, 9))
        val removed = mutableListOf<String>()

        val result = world.freeUp().removeVerified(world.files) { removed += it.id }

        assertTrue(removed.isEmpty())
        assertEquals(1, result.skipped.size)
    }

    @Test
    fun driveUnavailable_removesNothing() {
        val world = backedUp(PHOTO_A, PHOTO_B)
        world.fake.unavailable = true
        val removed = mutableListOf<String>()

        val result = world.freeUp().removeVerified(world.files) { removed += it.id }

        assertTrue(removed.isEmpty())
        assertEquals(listOf("a", "b"), result.skipped.map { it.file.id })
    }

    @Test
    fun driveReadFailsMidway_isSkippedAndNotRemoved() {
        val world = backedUp(PHOTO_A)
        world.fake.unavailableAfterBytes = 2
        val removed = mutableListOf<String>()

        val result = world.freeUp().removeVerified(world.files) { removed += it.id }

        assertTrue(removed.isEmpty())
        assertEquals(1, result.skipped.size)
    }

    @Test
    fun phoneOriginalCannotBeRead_isSkippedAndNotRemoved() {
        val world = backedUp(PHOTO_A)
        world.source.failOpens = true
        val removed = mutableListOf<String>()

        val result = world.freeUp().removeVerified(world.files) { removed += it.id }

        assertTrue(removed.isEmpty())
        assertEquals(1, result.skipped.size)
    }

    @Test
    fun originalThatWasNeverBackedUp_isSkippedAndNotRemoved() {
        val world = backedUp(PHOTO_A)
        val stranger = MediaFile("zzz", "NEW.jpg", 4, MODIFIED, MediaKind.PHOTO)
        world.source.add(stranger, byteArrayOf(5, 5, 5, 5))
        val removed = mutableListOf<String>()

        val result = world.freeUp().removeVerified(world.files + stranger) { removed += it.id }

        assertEquals(listOf("a"), removed)
        assertEquals(listOf("zzz"), result.skipped.map { it.file.id })
    }

    @Test
    fun ledgerRecordWithoutACheckTime_isStillVerifiedBeforeRemoval() {
        val world = backedUp(PHOTO_A)
        val old = InMemoryBackedUpLedger()
        old.record("a", world.ledger.checkedOriginal("a")!!.hash)
        val removed = mutableListOf<String>()

        world.freeUp(old).removeVerified(world.files) { removed += it.id }

        assertEquals(listOf("a"), removed)
    }

    @Test
    fun removalThatThrows_isNotReportedAsRemoved() {
        val world = backedUp(PHOTO_A, PHOTO_B)

        val result = world.freeUp().removeVerified(world.files) { file ->
            if (file.id == "a") throw IOException("denied")
        }

        assertEquals(listOf("b"), result.removed.map { it.id })
        assertEquals(listOf("a"), result.skipped.map { it.file.id })
    }

    @Test
    fun verifyEverything_reportsOnlyTheOriginalsThatDiffer() {
        val world = backedUp(PHOTO_A, PHOTO_B)
        corruptOnDrive(world, "b.jpg")

        val report = world.verifyEverything().run()

        assertEquals(2, report.checked)
        assertEquals(listOf("b"), report.differing.map { it.file.id })
        assertTrue(report.couldNotCheck.isEmpty())
    }

    @Test
    fun verifyEverything_rereadsOriginalsCheckedJustNow() {
        val world = backedUp(PHOTO_A, PHOTO_B)
        world.source.opens = 0
        world.drive.reads = 0

        val report = world.verifyEverything().run()

        assertTrue(report.differing.isEmpty())
        assertEquals(2, world.source.opens)
        assertEquals(2, world.drive.reads)
    }

    @Test
    fun verifyEverything_withNoDrive_reportsNothingDiffersButEverythingCouldNotBeChecked() {
        val world = backedUp(PHOTO_A, PHOTO_B)
        world.fake.unavailable = true

        val report = world.verifyEverything().run()

        assertTrue(report.differing.isEmpty())
        assertEquals(0, report.checked)
        assertTrue(report.couldNotCheck.isNotEmpty())
    }

    @Test
    fun verifyEverything_recordsANewCheckTimeForIdenticalOriginals() {
        val world = backedUp(PHOTO_A)
        val later = Clock.fixed(clock.instant().plusSeconds(3600), ZoneOffset.UTC)

        VerifyEverything(world.source, world.drive, world.ledger, later).run()

        assertEquals(later.instant(), world.ledger.checkedOriginal("a")!!.checkedAt)
    }

    @Test
    fun verifyEverything_doesNotRefreshTheCheckTimeOfAnOriginalThatDiffers() {
        val world = backedUp(PHOTO_A)
        corruptOnDrive(world, "a.jpg")
        val later = Clock.fixed(clock.instant().plusSeconds(3600), ZoneOffset.UTC)

        VerifyEverything(world.source, world.drive, world.ledger, later).run()

        assertEquals(clock.instant(), world.ledger.checkedOriginal("a")!!.checkedAt)
    }

    private fun corruptOnDrive(world: World, name: String) {
        val path = world.root.resolve(drivePath(name))
        val bytes = Files.readAllBytes(path)
        bytes[0] = (bytes[0] + 1).toByte()
        Files.write(path, bytes)
    }

    private fun backedUp(vararg specs: Pair<String, ByteArray>): World {
        val root = folders.newFolder().toPath()
        val fake = FakeArchiveDrive(root)
        val drive = CountingDrive(fake)
        val files = specs.map { (name, bytes) ->
            MediaFile(name.substringBefore('.'), name, bytes.size.toLong(), MODIFIED, MediaKind.PHOTO) to bytes
        }
        val source = CountingSource(files.toMutableList())
        val ledger = InMemoryBackedUpLedger()
        val run = BackupRunner(
            mediaSource = source,
            archiveDrive = drive,
            clock = clock,
            backedUpLedger = ledger,
            newDriveId = { "drive-1" },
        ).start()
        assertTrue(run.items.all { it.state == ItemState.COMMITTED })
        return World(root, fake, drive, source, ledger, files.map { it.first })
    }

    private inner class World(
        val root: java.nio.file.Path,
        val fake: FakeArchiveDrive,
        val drive: CountingDrive,
        val source: CountingSource,
        val ledger: InMemoryBackedUpLedger,
        val files: List<MediaFile>,
    ) {
        fun freeUp(withLedger: BackedUpLedger = ledger) = FreeUpSpace(source, drive, withLedger, clock)

        fun verifyEverything() = VerifyEverything(source, drive, ledger, clock)
    }

    private class CountingSource(private val files: MutableList<Pair<MediaFile, ByteArray>>) : MediaSource {
        var opens = 0
        var failOpens = false

        fun add(file: MediaFile, bytes: ByteArray) {
            files += file to bytes
        }

        fun replaceBytes(id: String, bytes: ByteArray) {
            val index = files.indexOfFirst { it.first.id == id }
            files[index] = files[index].first to bytes
        }

        override fun listCameraFolder(): List<MediaFile> = files.map { it.first }

        override fun openOriginal(id: String): InputStream {
            opens += 1
            if (failOpens) throw IOException("cannot read")
            return ByteArrayInputStream(files.first { it.first.id == id }.second.copyOf())
        }
    }

    private class CountingDrive(private val inner: FakeArchiveDrive) : ArchiveDrive by inner {
        var reads = 0

        override fun read(path: String): InputStream {
            if (!path.startsWith("Photoxpo/.photoxpo/")) reads += 1
            return inner.read(path)
        }
    }

    private companion object {
        val PHOTO_A = "a.jpg" to byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val PHOTO_B = "b.jpg" to byteArrayOf(8, 7, 6, 5, 4, 3, 2, 1)
        val MODIFIED: Long = Instant.parse("2020-01-15T10:00:00Z").toEpochMilli()

        fun drivePath(name: String) = "Photoxpo/Originals/2020/2020-01/$name"
    }
}
