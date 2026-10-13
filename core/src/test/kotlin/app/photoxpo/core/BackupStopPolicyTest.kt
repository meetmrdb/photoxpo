package app.photoxpo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class BackupStopPolicyTest {
    @Rule
    @JvmField
    val folders = TemporaryFolder()

    @Test
    fun namedLimits_areAboutTenGigabytesAndFortyFiveMinutes() {
        assertEquals(10_000_000_000L, RUN_SIZE_LIMIT_BYTES)
        assertEquals(Duration.ofMinutes(45), RUN_TIME_LIMIT)
    }

    @Test
    fun runEndsAtTheSizeLimit_withReasonAndFilesLeft_andNeverCopiesMoreThanTheLimit() {
        val root = folders.newFolder("size").toPath()
        val drive = FakeArchiveDrive(root)
        val files = (1..5).map { sample(it, size = 4) }
        val result = BackupRunner(
            mediaSource = FakeMediaSource(files.map { it.file to it.bytes }),
            archiveDrive = drive,
            clock = Clock.fixed(START, ZoneOffset.UTC),
            stopPolicy = DefaultStopPolicy(maxCopiedBytes = 10, maxElapsed = Duration.ofHours(1)),
        ).start()

        assertEquals(BackupRunStatus.Completed, result.status)
        assertEquals(StopReason.SizeLimit, result.stopReason)
        assertEquals(3, result.filesLeft)
        assertEquals(2, result.items.count { it.state == ItemState.COMMITTED })
        assertTrue(drive.exists(files[0].path))
        assertTrue(drive.exists(files[1].path))
        assertFalse(drive.exists(files[2].path))
        assertNoPartFiles(root)
    }

    @Test
    fun runEndsAtTheTimeLimit_withReasonAndFilesLeft() {
        val root = folders.newFolder("time").toPath()
        val drive = FakeArchiveDrive(root)
        val files = (1..5).map { sample(it, size = 4) }
        val clock = MutableClock(START)
        val result = BackupRunner(
            mediaSource = FakeMediaSource(files.map { it.file to it.bytes }),
            archiveDrive = drive,
            clock = clock,
            // Each Original takes 20 minutes.
            backedUpLedger = object : BackedUpLedger {
                override fun record(mediaId: String, hash: String) = clock.advance(Duration.ofMinutes(20))

                override fun clear() = Unit
            },
            stopPolicy = DefaultStopPolicy(maxCopiedBytes = 1_000, maxElapsed = Duration.ofMinutes(45)),
        ).start()

        assertEquals(BackupRunStatus.Completed, result.status)
        assertEquals(StopReason.TimeLimit, result.stopReason)
        // Started at 0, 20, 40 minutes; the 60 minute mark is past the limit.
        assertEquals(2, result.filesLeft)
        assertEquals(3, result.items.count { it.state == ItemState.COMMITTED })
        assertFalse(drive.exists(files[3].path))
        assertNoPartFiles(root)
    }

    @Test
    fun runEndsAtTheFiveHourSafetyStop_withTimeLimitReasonAndFilesLeft_evenIfTheNormalLimitIsLonger() {
        assertEquals(Duration.ofHours(5), RUN_HARD_STOP)
        val root = folders.newFolder("hard").toPath()
        val drive = FakeArchiveDrive(root)
        val files = (1..5).map { sample(it, size = 4) }
        val clock = MutableClock(START)
        val result = BackupRunner(
            mediaSource = FakeMediaSource(files.map { it.file to it.bytes }),
            archiveDrive = drive,
            clock = clock,
            // Each Original takes two hours.
            backedUpLedger = object : BackedUpLedger {
                override fun record(mediaId: String, hash: String) = clock.advance(Duration.ofHours(2))

                override fun clear() = Unit
            },
            stopPolicy = DefaultStopPolicy(maxCopiedBytes = 1_000, maxElapsed = Duration.ofHours(24)),
        ).start()

        assertEquals(BackupRunStatus.Completed, result.status)
        assertEquals(StopReason.TimeLimit, result.stopReason)
        // Started at 0, 2, 4 hours; the 6 hour mark is past the safety stop.
        assertEquals(2, result.filesLeft)
        assertEquals(3, result.items.count { it.state == ItemState.COMMITTED })
        assertNoPartFiles(root)
    }

    @Test
    fun nextRun_continuesWhereTheStoppedRunLeftOff_andEndsWithNoReason() {
        val root = folders.newFolder("next").toPath()
        val files = (1..5).map { sample(it, size = 4) }
        val source = FakeMediaSource(files.map { it.file to it.bytes })
        val first = BackupRunner(
            mediaSource = source,
            archiveDrive = FakeArchiveDrive(root),
            clock = Clock.fixed(START, ZoneOffset.UTC),
            stopPolicy = DefaultStopPolicy(maxCopiedBytes = 10, maxElapsed = Duration.ofHours(1)),
        ).start()
        assertEquals(3, first.filesLeft)

        val drive = FakeArchiveDrive(root)
        val second = BackupRunner(
            mediaSource = source,
            archiveDrive = drive,
            clock = Clock.fixed(START.plusSeconds(60), ZoneOffset.UTC),
        ).start()

        assertNull(second.stopReason)
        assertEquals(0, second.filesLeft)
        assertTrue(second.items.all { it.state == ItemState.COMMITTED })
        assertTrue(files.all { drive.exists(it.path) })
    }

    @Test
    fun aFileLargerThanTheSizeLimit_isStillCopiedWhenItIsFirst() {
        val root = folders.newFolder("big").toPath()
        val drive = FakeArchiveDrive(root)
        val files = (1..2).map { sample(it, size = 20) }
        val result = BackupRunner(
            mediaSource = FakeMediaSource(files.map { it.file to it.bytes }),
            archiveDrive = drive,
            clock = Clock.fixed(START, ZoneOffset.UTC),
            stopPolicy = DefaultStopPolicy(maxCopiedBytes = 10, maxElapsed = Duration.ofHours(1)),
        ).start()

        assertEquals(StopReason.SizeLimit, result.stopReason)
        assertEquals(1, result.filesLeft)
        assertTrue(drive.exists(files[0].path))
    }

    @Test
    fun externalStopRequest_endsTheRunAfterTheCurrentFile_withTimeLimitFilesLeftAndFooter() {
        val root = folders.newFolder("external").toPath()
        val drive = FakeArchiveDrive(root)
        val files = (1..5).map { sample(it, size = 4) }
        var requested = false
        val result = BackupRunner(
            mediaSource = FakeMediaSource(files.map { it.file to it.bytes }),
            archiveDrive = drive,
            clock = Clock.fixed(START, ZoneOffset.UTC),
            // Android asks for the stop while the second Original is being copied.
            backedUpLedger = object : BackedUpLedger {
                override fun record(mediaId: String, hash: String) {
                    if (mediaId == "media-2") requested = true
                }

                override fun clear() = Unit
            },
            externalStop = { if (requested) StopReason.TimeLimit else null },
        ).start()

        assertEquals(BackupRunStatus.Completed, result.status)
        assertEquals(StopReason.TimeLimit, result.stopReason)
        assertEquals(3, result.filesLeft)
        assertEquals(2, result.items.count { it.state == ItemState.COMMITTED })
        assertTrue(drive.exists(files[1].path))
        assertFalse(drive.exists(files[2].path))
        assertTrue(drive.readText(result.manifestPath).contains("\"stopReason\":\"time-limit\""))
        assertNoPartFiles(root)
    }

    @Test
    fun noExternalStop_changesNothing() {
        val root = folders.newFolder("noexternal").toPath()
        val drive = FakeArchiveDrive(root)
        val files = (1..3).map { sample(it, size = 4) }
        val result = BackupRunner(
            mediaSource = FakeMediaSource(files.map { it.file to it.bytes }),
            archiveDrive = drive,
            clock = Clock.fixed(START, ZoneOffset.UTC),
            externalStop = { null },
        ).start()

        assertNull(result.stopReason)
        assertTrue(files.all { drive.exists(it.path) })
    }

    @Test
    fun lowBatteryConstant_isFifteenPercent() {
        assertEquals(15, LOW_BATTERY_PERCENT)
    }

    @Test
    fun runStopsAfterTheCurrentFile_atLowBatteryWhileNotCharging() {
        val root = folders.newFolder("battery").toPath()
        val drive = FakeArchiveDrive(root)
        val files = (1..5).map { sample(it, size = 4) }
        // 20% before the first file, 16% before the second, 15% before the third.
        val levels = ArrayDeque(listOf(20, 16, 15, 14, 13))
        val result = BackupRunner(
            mediaSource = FakeMediaSource(files.map { it.file to it.bytes }),
            archiveDrive = drive,
            clock = Clock.fixed(START, ZoneOffset.UTC),
            batteryInput = BatteryInput { BatteryStatus(levels.removeFirst(), charging = false) },
        ).start()

        assertEquals(BackupRunStatus.Completed, result.status)
        assertEquals(StopReason.LowBattery, result.stopReason)
        assertEquals(3, result.filesLeft)
        assertEquals(2, result.items.count { it.state == ItemState.COMMITTED })
        assertFalse(drive.exists(files[2].path))
        assertNoPartFiles(root)
    }

    @Test
    fun noStopAtLowBattery_whileCharging() {
        val root = folders.newFolder("charging").toPath()
        val drive = FakeArchiveDrive(root)
        val files = (1..3).map { sample(it, size = 4) }
        val result = BackupRunner(
            mediaSource = FakeMediaSource(files.map { it.file to it.bytes }),
            archiveDrive = drive,
            clock = Clock.fixed(START, ZoneOffset.UTC),
            batteryInput = BatteryInput { BatteryStatus(5, charging = true) },
        ).start()

        assertNull(result.stopReason)
        assertEquals(0, result.filesLeft)
        assertTrue(files.all { drive.exists(it.path) })
    }

    @Test
    fun unknownBattery_neverStops() {
        val progress = RunProgress(copiedBytes = 0, elapsed = Duration.ZERO, nextFileBytes = 1)
        assertNull(DefaultStopPolicy().reasonToStop(progress))
        assertEquals(
            StopReason.LowBattery,
            DefaultStopPolicy().reasonToStop(progress.copy(batteryPercent = 15, charging = false)),
        )
        assertNull(DefaultStopPolicy().reasonToStop(progress.copy(batteryPercent = 16, charging = false)))
        assertNull(DefaultStopPolicy().reasonToStop(progress.copy(batteryPercent = 3, charging = true)))
    }

    private fun assertNoPartFiles(root: java.nio.file.Path) {
        val parts = Files.walk(root).use { stream ->
            stream.filter { it.fileName?.toString()?.endsWith(".photoxpo-part") == true }.toList()
        }
        assertTrue("leftover parts: $parts", parts.isEmpty())
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

    private class MutableClock(private var now: Instant) : Clock() {
        fun advance(by: Duration) {
            now = now.plus(by)
        }

        override fun instant(): Instant = now

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }

    private companion object {
        val START: Instant = Instant.parse("2026-10-11T18:30:00Z")
    }
}
