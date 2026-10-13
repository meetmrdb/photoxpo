package app.photoxpo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class RunEndHistoryTest {
    @Rule
    @JvmField
    val folders = TemporaryFolder()

    @Test
    fun runStoppedAtTheSizeLimit_isReadBackFromTheManifestFooterWithItsReason() {
        val drive = FakeArchiveDrive(folders.newFolder("size").toPath())
        runOver(drive, DefaultStopPolicy(maxCopiedBytes = 10, maxElapsed = Duration.ofHours(1)))

        val summary = BackupHistoryReader().read(drive, emptySet()).single()

        assertEquals(RunEnd.Stopped(StopReason.SizeLimit), summary.runEnd)
        assertEquals("Stopped at the size limit", runEndWords(summary.runEnd!!))
    }

    @Test
    fun runThatCopiedEverything_isReadBackAsFinished() {
        val drive = FakeArchiveDrive(folders.newFolder("done").toPath())
        runOver(drive, DefaultStopPolicy())

        val summary = BackupHistoryReader().read(drive, emptySet()).single()

        assertEquals(RunEnd.Finished, summary.runEnd)
    }

    @Test
    fun olderFooterWithoutAReason_stillReadsWithNoRunEnd() {
        val drive = FakeArchiveDrive(folders.newFolder("old").toPath())
        drive.appendText(
            "$RUNS/20261011T183000000Z.jsonl",
            "{\"type\":\"header\",\"startedAt\":\"2026-10-11T18:30:00.000Z\"}\n" +
                "{\"type\":\"footer\",\"startedAt\":\"2026-10-11T18:30:00.000Z\",\"endedAt\":\"2026-10-11T18:32:00.000Z\"," +
                "\"fileCount\":0,\"bytes\":0,\"checked\":true,\"elapsedMs\":1,\"mbPerSecond\":1.0}\n",
        )

        val summary = BackupHistoryReader().read(drive, emptySet()).single()

        assertNull(summary.runEnd)
        assertEquals(false, summary.interrupted)
    }

    @Test
    fun aReasonThisVersionDoesNotKnow_readsWithNoRunEnd() {
        assertNull(RunEnd.fromWire("from-a-newer-app"))
    }

    @Test
    fun everyStopReason_hasWordsAndSurvivesTheFooterRoundTrip() {
        for (reason in StopReason.entries) {
            val end = RunEnd.Stopped(reason)
            assertTrue("no words for $reason", runEndWords(end).isNotBlank())
            assertEquals(end, RunEnd.fromWire(end.wire))
        }
        assertEquals(RunEnd.Finished, RunEnd.fromWire(RunEnd.Finished.wire))
    }

    @Test
    fun runRecordsItsBatteryDrop_andHistoryReadsItBack() {
        val drive = FakeArchiveDrive(folders.newFolder("drop").toPath())
        var reads = 0
        // 80% when the run starts, 74% from then on.
        runOver(drive, DefaultStopPolicy(), BatteryInput { BatteryStatus(if (reads++ == 0) 80 else 74, charging = false) })

        assertEquals(6, BackupHistoryReader().read(drive, emptySet()).single().batteryDropPercent)
    }

    @Test
    fun noBatteryDropIsRecordedWhileChargingOrWhenTheBatteryIsUnknown() {
        val charging = FakeArchiveDrive(folders.newFolder("charging").toPath())
        runOver(charging, DefaultStopPolicy(), BatteryInput { BatteryStatus(60, charging = true) })
        assertNull(BackupHistoryReader().read(charging, emptySet()).single().batteryDropPercent)

        val unknown = FakeArchiveDrive(folders.newFolder("unknown").toPath())
        runOver(unknown, DefaultStopPolicy(), BatteryInput { null })
        assertNull(BackupHistoryReader().read(unknown, emptySet()).single().batteryDropPercent)
    }

    @Test
    fun olderFooterWithoutABatteryDrop_stillReads() {
        val drive = FakeArchiveDrive(folders.newFolder("nodrop").toPath())
        drive.appendText(
            "$RUNS/20261011T183000000Z.jsonl",
            "{\"type\":\"header\",\"startedAt\":\"2026-10-11T18:30:00.000Z\"}\n" +
                "{\"type\":\"footer\",\"startedAt\":\"2026-10-11T18:30:00.000Z\",\"endedAt\":\"2026-10-11T18:32:00.000Z\"," +
                "\"fileCount\":0,\"bytes\":0,\"checked\":true,\"elapsedMs\":1,\"mbPerSecond\":1.0,\"stopReason\":\"finished\"}\n",
        )
        val summary = BackupHistoryReader().read(drive, emptySet()).single()
        assertNull(summary.batteryDropPercent)
        assertEquals(RunEnd.Finished, summary.runEnd)
    }

    private fun runOver(drive: ArchiveDrive, policy: StopPolicy, battery: BatteryInput = BatteryInput { null }) {
        val files = (1..5).map { i ->
            MediaFile(
                id = "media-$i",
                name = "IMG_000$i.jpg",
                sizeBytes = 4,
                modifiedAtEpochMillis = Instant.parse("2020-01-0${i}T10:00:00Z").toEpochMilli(),
                kind = MediaKind.PHOTO,
            ) to ByteArray(4) { (i * 31 + it).toByte() }
        }
        BackupRunner(
            mediaSource = FakeMediaSource(files),
            archiveDrive = drive,
            clock = Clock.fixed(Instant.parse("2026-10-11T18:30:00Z"), ZoneOffset.UTC),
            stopPolicy = policy,
            batteryInput = battery,
        ).start()
    }

    private companion object {
        const val RUNS = "Photoxpo/.photoxpo/runs"
    }
}
