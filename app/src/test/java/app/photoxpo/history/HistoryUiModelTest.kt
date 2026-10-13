package app.photoxpo.history

import app.photoxpo.core.BackupRunSummary
import app.photoxpo.core.RunEnd
import app.photoxpo.core.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class HistoryUiModelTest {
    @Test
    fun driveOut_labelsTheListAsOfTheLastConnection() {
        val model = historyUiModel(
            summaries = listOf(run(startedAt = "2026-10-11T18:30:00.000Z", fileCount = 1, bytes = 1)),
            grouping = HistoryGrouping.ByBackup,
            driveAttached = false,
            lastConnectedAtEpochMillis = Instant.parse("2026-10-12T03:06:00Z").toEpochMilli(),
            zone = ZoneOffset.UTC,
        )
        assertEquals("As of 12 Oct 2026, 3:06 am", model.asOfLabel)
        val row = model.rows.single()
        assertEquals("11 Oct 2026, 6:30 pm", row.dateLabel)
        assertEquals("1 file", row.filesLabel)
        assertEquals("1 byte", row.sizeLabel)
    }

    @Test
    fun row_saysInPlainWordsHowTheRunEnded_andOlderRunsShowNoReason() {
        val model = historyUiModel(
            summaries = listOf(
                run(runFile = "a.jsonl", startedAt = "2026-10-11T18:30:00.000Z", runEnd = RunEnd.Stopped(StopReason.SizeLimit)),
                run(runFile = "b.jsonl", startedAt = "2026-10-10T18:30:00.000Z", runEnd = RunEnd.Finished),
                run(runFile = "c.jsonl", startedAt = "2026-10-09T18:30:00.000Z", runEnd = null),
            ),
            grouping = HistoryGrouping.ByBackup,
            driveAttached = true,
            lastConnectedAtEpochMillis = null,
            zone = ZoneOffset.UTC,
        )
        assertEquals(
            listOf("Stopped at the size limit", "Finished: everything new was backed up", null),
            model.rows.map { it.endReasonLabel },
        )
    }

    @Test
    fun driveAttached_hidesTheAsOfLabel() {
        val model = historyUiModel(
            summaries = listOf(run(startedAt = "2026-10-11T18:30:00.000Z")),
            grouping = HistoryGrouping.ByBackup,
            driveAttached = true,
            lastConnectedAtEpochMillis = Instant.parse("2026-10-12T03:06:00Z").toEpochMilli(),
            zone = ZoneOffset.UTC,
        )
        assertNull(model.asOfLabel)
    }

    @Test
    fun neverConnected_namesTheLastConnectionWithoutATime() {
        val model = historyUiModel(
            summaries = emptyList(),
            grouping = HistoryGrouping.ByMonth,
            driveAttached = false,
            lastConnectedAtEpochMillis = null,
            zone = ZoneOffset.UTC,
        )
        assertEquals("As of the last time the Archive Drive was connected", model.asOfLabel)
        assertTrue(model.sections.isEmpty())
    }

    @Test
    fun interruptedRun_isStoppedEarly_withoutSpeed() {
        val model = historyUiModel(
            summaries = listOf(
                run(
                    runFile = "stopped.jsonl",
                    startedAt = "2026-10-01T08:00:00.000Z",
                    fileCount = 2,
                    bytes = 1_572_864, // 1.5 MB (1024-based, shared with Photo info)
                    interrupted = true,
                ),
            ),
            grouping = HistoryGrouping.ByBackup,
            driveAttached = true,
            lastConnectedAtEpochMillis = null,
            zone = ZoneOffset.UTC,
        )
        val row = model.rows.single()
        assertEquals("Stopped early", row.stoppedEarlyLabel)
        assertEquals("2 files", row.filesLabel)
        assertEquals("1.5 MB", row.sizeLabel)
        assertNull(row.speedLabel)
        assertEquals("1 Oct 2026, 8:00 am", row.dateLabel)
    }

    @Test
    fun footerSpeed_isShownOnTheBackupRow() {
        val model = historyUiModel(
            summaries = listOf(
                run(
                    startedAt = "2026-09-01T00:00:00.000Z",
                    fileCount = 4,
                    bytes = 10_485_760, // 10 MB
                    mbPerSecond = 2.5,
                ),
            ),
            grouping = HistoryGrouping.ByBackup,
            driveAttached = true,
            lastConnectedAtEpochMillis = null,
            zone = ZoneOffset.UTC,
        )
        val row = model.rows.single()
        assertNull(row.stoppedEarlyLabel)
        assertEquals("4 files", row.filesLabel)
        assertEquals("10 MB", row.sizeLabel)
        assertEquals("2.5 MB/s", row.speedLabel)
        assertEquals("1 Sep 2026, 12:00 am", row.dateLabel)
    }

    @Test
    fun byMonth_isNewestFirst_withFileAndByteTotals() {
        val model = historyUiModel(
            summaries = sampleRuns(),
            grouping = HistoryGrouping.ByMonth,
            driveAttached = true,
            lastConnectedAtEpochMillis = null,
            zone = ZoneOffset.UTC,
        )
        assertTrue(model.rows.isEmpty())
        assertEquals(listOf("October 2026", "September 2026"), model.sections.map { it.monthLabel })
        assertEquals("3 files, 55 bytes", model.sections[0].totalsLabel)
        assertEquals(
            listOf("20261011T230000000Z.jsonl", "20261001T080000000Z.jsonl"),
            model.sections[0].rows.map { it.runFile },
        )
        assertEquals("1.25 MB/s", model.sections[0].rows[0].speedLabel)
        assertNull(model.sections[0].rows[0].stoppedEarlyLabel)
        assertEquals("Stopped early", model.sections[0].rows[1].stoppedEarlyLabel)
        assertNull(model.sections[0].rows[1].speedLabel)
        assertEquals("1 file, 10 bytes", model.sections[1].totalsLabel)
    }

    @Test
    fun byBackup_isNewestFirst() {
        val model = historyUiModel(
            summaries = sampleRuns(),
            grouping = HistoryGrouping.ByBackup,
            driveAttached = false,
            lastConnectedAtEpochMillis = Instant.parse("2026-10-12T03:06:00Z").toEpochMilli(),
            zone = ZoneOffset.UTC,
        )
        assertTrue(model.sections.isEmpty())
        assertEquals("As of 12 Oct 2026, 3:06 am", model.asOfLabel)
        assertEquals(
            listOf(
                "20261011T230000000Z.jsonl",
                "20261001T080000000Z.jsonl",
                "20260901T000000000Z.jsonl",
            ),
            model.rows.map { it.runFile },
        )
        assertEquals("Stopped early", model.rows[1].stoppedEarlyLabel)
        assertNull(model.rows[0].stoppedEarlyLabel)
    }

    private fun sampleRuns(): List<BackupRunSummary> {
        return listOf(
            run(
                runFile = "20260901T000000000Z.jsonl",
                startedAt = "2026-09-01T00:00:00.000Z",
                fileCount = 1,
                bytes = 10,
            ),
            run(
                runFile = "20261001T080000000Z.jsonl",
                startedAt = "2026-10-01T08:00:00.000Z",
                fileCount = 2,
                bytes = 35,
                interrupted = true,
            ),
            run(
                runFile = "20261011T230000000Z.jsonl",
                startedAt = "2026-10-11T23:00:00.000Z",
                fileCount = 1,
                bytes = 20,
                mbPerSecond = 1.25,
            ),
        )
    }

    private fun run(
        runFile: String = "run.jsonl",
        startedAt: String,
        fileCount: Long = 1,
        bytes: Long = 5,
        mbPerSecond: Double? = 1.25,
        interrupted: Boolean = false,
        runEnd: RunEnd? = null,
    ): BackupRunSummary {
        return BackupRunSummary(
            runFile = runFile,
            startedAt = startedAt,
            endedAt = if (interrupted) null else startedAt,
            fileCount = fileCount,
            bytes = bytes,
            checked = !interrupted,
            elapsedMs = if (interrupted) null else 120_000,
            mbPerSecond = if (interrupted) null else mbPerSecond,
            interrupted = interrupted,
            runEnd = runEnd,
        )
    }
}
