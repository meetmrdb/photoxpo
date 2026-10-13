package app.photoxpo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class BackupRecentCheckTest {
    @Rule
    @JvmField
    val folders = TemporaryFolder()

    private val clock = SettableClock(Instant.parse("2026-10-11T18:30:00Z"))

    @Test
    fun secondRunWithinTheWindow_doesNotReadBackedUpOriginalsAgain() {
        val setup = setup()
        val first = setup.runner().start()
        assertEquals(ItemState.COMMITTED, first.items.single().state)
        assertTrue(setup.source.opens > 0)

        clock.now = clock.now.plus(RECENTLY_CHECKED_WINDOW.dividedBy(2))
        setup.source.opens = 0
        setup.drive.reads = 0
        val second = setup.runner().start()

        assertEquals(ItemState.COMMITTED, second.items.single().state)
        assertEquals(0, setup.source.opens)
        assertEquals(0, setup.drive.reads)
    }

    @Test
    fun originalNotCheckedRecently_isStillVerified() {
        val setup = setup()
        setup.runner().start()

        clock.now = clock.now.plus(RECENTLY_CHECKED_WINDOW).plusSeconds(1)
        setup.source.opens = 0
        setup.drive.reads = 0
        val second = setup.runner().start()

        assertEquals(ItemState.COMMITTED, second.items.single().state)
        assertTrue(setup.source.opens > 0)
        assertTrue(setup.drive.reads > 0)
    }

    @Test
    fun recordWithoutACheckTime_countsAsNotCheckedRecentlyAndIsVerified() {
        val setup = setup()
        setup.runner().start()
        // An older phone record: hash only, no check time.
        val oldLedger = InMemoryBackedUpLedger()
        oldLedger.record("media-1", "any-hash")

        setup.source.opens = 0
        val second = setup.runner(ledger = oldLedger).start()

        assertEquals(ItemState.COMMITTED, second.items.single().state)
        assertTrue(setup.source.opens > 0)
    }

    @Test
    fun newOriginals_areCopiedAndVerifiedAsBefore() {
        val setup = setup()
        setup.runner().start()
        val extra = file("media-2", "IMG_0002.jpg", byteArrayOf(9, 8, 7, 6))
        setup.source.add(extra.first, extra.second)

        clock.now = clock.now.plusSeconds(60)
        setup.drive.writes = 0
        val second = setup.runner().start()

        assertEquals(2, second.items.size)
        assertTrue(second.items.all { it.state == ItemState.COMMITTED })
        assertEquals(1, setup.drive.writes)
        val names = ManifestReader().readAll(setup.drive).flatMap { it.entries }.map { it.originalFilename }
        assertEquals(listOf("IMG_0001.jpg", "IMG_0002.jpg"), names.sorted())
    }

    @Test
    fun originalChangedOnThePhoneSinceTheCheck_isReadAgain() {
        val setup = setup()
        setup.runner().start()
        setup.source.replace(
            MediaFile("media-1", "IMG_0001.jpg", BYTES.size.toLong(), MODIFIED + 5_000, MediaKind.PHOTO),
            BYTES,
        )

        clock.now = clock.now.plusSeconds(60)
        setup.source.opens = 0
        setup.runner().start()

        assertTrue(setup.source.opens > 0)
    }

    @Test
    fun originalMissingFromTheArchiveDrive_isNotSkippedEvenWhenCheckedRecently() {
        val setup = setup()
        setup.runner().start()
        setup.drive.delete(PATH)

        clock.now = clock.now.plusSeconds(60)
        setup.drive.writes = 0
        val second = setup.runner().start()

        assertEquals(ItemState.COMMITTED, second.items.single().state)
        assertTrue(setup.drive.exists(PATH))
        assertEquals(1, setup.drive.writes)
    }

    @Test
    fun verifyEverythingPolicy_rereadsOriginalsCheckedJustNow() {
        val setup = setup()
        setup.runner().start()

        setup.source.opens = 0
        val second = setup.runner(policy = CheckPolicy.verifyEverything).start()

        assertEquals(ItemState.COMMITTED, second.items.single().state)
        assertTrue(setup.source.opens > 0)
    }

    @Test
    fun checkPolicy_canBeAskedPerOriginalForReuseByOtherFlows() {
        val now = Instant.parse("2026-10-11T18:30:00Z")
        val file = MediaFile("m", "a.jpg", 4, MODIFIED, MediaKind.PHOTO)
        val fresh = CheckedOriginal("h", 4, MODIFIED, now.minusSeconds(10))
        val policy = CheckPolicy.skipRecentlyChecked(Duration.ofDays(1))

        assertTrue(policy.isRecentlyChecked(fresh, file, now))
        assertFalse(policy.isRecentlyChecked(null, file, now))
        assertFalse(policy.isRecentlyChecked(fresh.copy(checkedAt = null), file, now))
        assertFalse(policy.isRecentlyChecked(fresh.copy(checkedAt = now.minus(Duration.ofDays(2))), file, now))
        assertFalse(policy.isRecentlyChecked(fresh.copy(checkedAt = now.plusSeconds(60)), file, now))
        assertFalse(CheckPolicy.verifyEverything.isRecentlyChecked(fresh, file, now))
    }

    @Test
    fun newOriginalsAreCopiedBeforeBackedUpOnesAreChecked_andEachStepIsReported() {
        val setup = setup()
        setup.runner().start()
        val extra = file("media-2", "IMG_0002.jpg", byteArrayOf(9, 8, 7, 6))
        setup.source.add(extra.first, extra.second)

        clock.now = clock.now.plus(RECENTLY_CHECKED_WINDOW).plusSeconds(1)
        val steps = mutableListOf<RunStep>()
        val runner = BackupRunner(
            mediaSource = setup.source,
            archiveDrive = setup.drive,
            clock = clock,
            backedUpLedger = setup.ledger,
            newDriveId = { "drive-1" },
            onStep = { steps += it },
        )
        runner.start()

        assertEquals(
            listOf(RunStep(RunStage.Copying, 0, 1), RunStep(RunStage.Checking, 0, 1)),
            steps,
        )
    }

    @Test
    fun aStopEndsTheRunBeforeBackedUpOriginalsAreChecked() {
        val setup = setup()
        setup.runner().start()
        val extra = file("media-2", "IMG_0002.jpg", byteArrayOf(9, 8, 7, 6))
        setup.source.add(extra.first, extra.second)

        clock.now = clock.now.plus(RECENTLY_CHECKED_WINDOW).plusSeconds(1)
        setup.source.opens = 0
        val result = BackupRunner(
            mediaSource = setup.source,
            archiveDrive = setup.drive,
            clock = clock,
            backedUpLedger = setup.ledger,
            newDriveId = { "drive-1" },
            batteryInput = BatteryInput { BatteryStatus(percent = 10, charging = false) },
        ).start()

        assertEquals(StopReason.LowBattery, result.stopReason)
        assertEquals(1, result.filesLeft)
        // Only the new Original is read, to see it is not on the drive yet; the backed up one is left alone.
        assertEquals(1, setup.source.opens)
    }

    private fun setup(): Setup {
        val root = folders.newFolder().toPath()
        val drive = CountingDrive(FakeArchiveDrive(root))
        val (file, bytes) = file("media-1", "IMG_0001.jpg", BYTES)
        return Setup(drive, CountingSource(mutableListOf(file to bytes)), InMemoryBackedUpLedger())
    }

    private fun file(id: String, name: String, bytes: ByteArray) =
        MediaFile(id, name, bytes.size.toLong(), MODIFIED, MediaKind.PHOTO) to bytes

    private inner class Setup(
        val drive: CountingDrive,
        val source: CountingSource,
        val ledger: InMemoryBackedUpLedger,
    ) {
        fun runner(
            ledger: BackedUpLedger = this.ledger,
            policy: CheckPolicy = CheckPolicy.skipRecentlyChecked(),
        ) = BackupRunner(
            mediaSource = source,
            archiveDrive = drive,
            clock = clock,
            backedUpLedger = ledger,
            checkPolicy = policy,
            newDriveId = { "drive-1" },
        )
    }

    private class CountingSource(private val files: MutableList<Pair<MediaFile, ByteArray>>) : MediaSource {
        var opens = 0

        fun add(file: MediaFile, bytes: ByteArray) {
            files += file to bytes
        }

        fun replace(file: MediaFile, bytes: ByteArray) {
            files.removeAll { it.first.id == file.id }
            files += file to bytes
        }

        override fun listCameraFolder(): List<MediaFile> = files.map { it.first }

        override fun openOriginal(id: String): InputStream {
            opens += 1
            return ByteArrayInputStream(files.first { it.first.id == id }.second.copyOf())
        }
    }

    private class CountingDrive(private val inner: FakeArchiveDrive) : ArchiveDrive by inner {
        var reads = 0
        var writes = 0

        override fun read(path: String): InputStream {
            if (!path.startsWith("Photoxpo/.photoxpo/")) reads += 1
            return inner.read(path)
        }

        override fun write(path: String): java.io.OutputStream {
            writes += 1
            return inner.write(path)
        }
    }

    private class SettableClock(start: Instant) : Clock() {
        var now: Instant = start

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }

    private companion object {
        val BYTES = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val MODIFIED: Long = Instant.parse("2020-01-15T10:00:00Z").toEpochMilli()
        const val PATH = "Photoxpo/Originals/2020/2020-01/IMG_0001.jpg"
    }
}
