package app.photoxpo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupHistoryReaderTest {
    @Rule
    @JvmField
    val folders = TemporaryFolder()

    private val reader = BackupHistoryReader()

    @Test
    fun truncatedLastLine_isIgnored_andMissingFooter_isInterrupted() {
        val drive = drive()
        val text = header("2026-10-11T18:30:00.000Z") + "\n" +
            fileLine(hash = "abc", size = 4, name = "a.jpg") + "\n" +
            fileLine(hash = "def", size = 9, name = "cut.jpg")
        drive.appendText("$RUNS/20261011T183000000Z.jsonl", text)

        val summary = reader.read(drive, emptySet()).single()

        assertEquals("20261011T183000000Z.jsonl", summary.runFile)
        assertEquals("2026-10-11T18:30:00.000Z", summary.startedAt)
        assertNull(summary.endedAt)
        assertEquals(1L, summary.fileCount)
        assertEquals(4L, summary.bytes)
        assertFalse(summary.checked)
        assertNull(summary.elapsedMs)
        assertNull(summary.mbPerSecond)
        assertTrue(summary.interrupted)
    }

    @Test
    fun footerWithoutNewline_isIgnored_soTheRunStaysInterrupted() {
        val drive = drive()
        val text = header("2026-10-11T18:30:00.000Z") + "\n" +
            fileLine(hash = "abc", size = 4, name = "a.jpg") + "\n" +
            footer(elapsedMs = 1500, mbPerSecond = "1.25")
        drive.appendText("$RUNS/20261011T183000000Z.jsonl", text)

        val summary = reader.read(drive, emptySet()).single()

        assertTrue(summary.interrupted)
        assertEquals(1L, summary.fileCount)
        assertEquals(4L, summary.bytes)
        assertNull(summary.elapsedMs)
        assertNull(summary.mbPerSecond)
    }

    @Test
    fun footer_carriesElapsedTimeAndMegabytesPerSecond_andCountsFileLines() {
        val drive = drive()
        val text = header("2026-10-11T18:30:00.000Z") + "\n" +
            fileLine(hash = "abc", size = 4, name = "a.jpg") + "\n" +
            fileLine(hash = "def", size = 6, name = "b.jpg") + "\n" +
            footer(
                startedAt = "2026-10-11T18:30:00.000Z",
                endedAt = "2026-10-11T18:32:00.000Z",
                elapsedMs = 120000,
                mbPerSecond = "1.25",
                fileCount = 99,
                bytes = 1,
            ) + "\n" +
            "{\"type\":\"file\",\"hash\":\"cut\""
        drive.appendText("$RUNS/20261011T183000000Z.jsonl", text)

        val summary = reader.read(drive, emptySet()).single()

        assertFalse(summary.interrupted)
        assertEquals("2026-10-11T18:30:00.000Z", summary.startedAt)
        assertEquals("2026-10-11T18:32:00.000Z", summary.endedAt)
        assertEquals(2L, summary.fileCount)
        assertEquals(10L, summary.bytes)
        assertTrue(summary.checked)
        assertEquals(120000L, summary.elapsedMs)
        assertEquals(1.25, summary.mbPerSecond!!, 0.0)
    }

    @Test
    fun sameHashOnTwoLines_countsAsOneFile() {
        val drive = drive()
        val text = header("2026-10-11T18:30:00.000Z") + "\n" +
            fileLine(hash = "abc", size = 4, name = "a.jpg") + "\n" +
            fileLine(hash = "abc", size = 99, name = "b.jpg") + "\n" +
            fileLine(hash = "def", size = 6, name = "c.jpg") + "\n" +
            footer(elapsedMs = 120000, mbPerSecond = "1.25") + "\n"
        drive.appendText("$RUNS/20261011T183000000Z.jsonl", text)

        val summary = reader.read(drive, emptySet()).single()

        assertEquals(2L, summary.fileCount)
        assertEquals(10L, summary.bytes)
    }

    @Test
    fun footerIntegerSpeed_isCarried() {
        val drive = drive()
        val text = header("2026-10-11T18:30:00.000Z") + "\n" +
            fileLine(hash = "abc", size = 4, name = "a.jpg") + "\n" +
            "{\"type\":\"footer\",\"startedAt\":\"2026-10-11T18:30:00.000Z\"," +
            "\"endedAt\":\"2026-10-11T18:30:00.000Z\",\"fileCount\":1,\"bytes\":4," +
            "\"checked\":true,\"elapsedMs\":0,\"mbPerSecond\":0}\n"
        drive.appendText("$RUNS/20261011T183000000Z.jsonl", text)

        val summary = reader.read(drive, emptySet()).single()

        assertFalse(summary.interrupted)
        assertEquals(0L, summary.elapsedMs)
        assertEquals(0.0, summary.mbPerSecond!!, 0.0)
        assertEquals(1L, summary.fileCount)
        assertEquals(4L, summary.bytes)
    }

    @Test
    fun refresh_readsOnlyRunFiles_missingFromKnownRunFiles() {
        val drive = drive()
        write(drive, "20260101T000000000Z.jsonl", finished("2026-01-01T00:00:00.000Z", size = 4))
        write(drive, "20260201T000000000Z.jsonl", finished("2026-02-01T00:00:00.000Z", size = 8))
        drive.appendText("$RUNS/notes.txt", "hello\n")

        val first = reader.read(drive, emptySet())
        assertEquals(
            listOf("20260101T000000000Z.jsonl", "20260201T000000000Z.jsonl"),
            first.map { it.runFile },
        )

        val older = "$RUNS/20260101T000000000Z.jsonl"
        drive.delete(older)
        write(drive, "20260101T000000000Z.jsonl", finished("2026-01-01T00:00:00.000Z", size = 100))
        write(drive, "20260301T000000000Z.jsonl", finished("2026-03-01T00:00:00.000Z", size = 3))

        val known = setOf("20260101T000000000Z.jsonl", "20260201T000000000Z.jsonl")
        val newer = reader.read(drive, known)
        assertEquals(listOf("20260301T000000000Z.jsonl"), newer.map { it.runFile })
        assertEquals(3L, newer.single().bytes)
    }

    @Test
    fun interruptedRun_isReadAgain_whenTheCallerLeavesItOutOfKnownRunFiles() {
        val drive = drive()
        val name = "20261011T183000000Z.jsonl"
        write(
            drive,
            name,
            header("2026-10-11T18:30:00.000Z") + "\n" +
                fileLine(hash = "abc", size = 4, name = "a.jpg") + "\n",
        )

        val first = reader.read(drive, emptySet()).single()
        assertTrue(first.interrupted)
        assertEquals(1L, first.fileCount)

        val path = "$RUNS/$name"
        drive.delete(path)
        write(
            drive,
            name,
            header("2026-10-11T18:30:00.000Z") + "\n" +
                fileLine(hash = "abc", size = 4, name = "a.jpg") + "\n" +
                fileLine(hash = "def", size = 6, name = "b.jpg") + "\n" +
                footer(elapsedMs = 4000, mbPerSecond = "2.5") + "\n",
        )

        assertTrue(reader.read(drive, setOf(name)).isEmpty())

        val again = reader.read(drive, emptySet()).single()
        assertFalse(again.interrupted)
        assertEquals(2L, again.fileCount)
        assertEquals(10L, again.bytes)
        assertEquals(4000L, again.elapsedMs)
        assertEquals(2.5, again.mbPerSecond!!, 0.0)
    }

    @Test
    fun byMonth_andByBackup_areNewestFirst() {
        val drive = drive()
        write(drive, "20260901T000000000Z.jsonl", finished("2026-09-01T00:00:00.000Z", size = 10))
        write(
            drive,
            "20261001T000000000Z.jsonl",
            finished("2026-10-01T08:00:00.000Z", size = 30, extraFileSize = 5),
        )
        write(drive, "20261011T230000000Z.jsonl", finished("2026-10-11T23:00:00.000Z", size = 20))

        val summaries = reader.read(drive, emptySet())

        assertEquals(
            listOf("2026-10-11T23:00:00.000Z", "2026-10-01T08:00:00.000Z", "2026-09-01T00:00:00.000Z"),
            byBackup(summaries).map { it.startedAt },
        )

        val months = byMonth(summaries)
        assertEquals(listOf("2026-10", "2026-09"), months.map { it.month })
        assertEquals(3L, months[0].fileCount)
        assertEquals(55L, months[0].bytes)
        assertEquals(
            listOf("2026-10-11T23:00:00.000Z", "2026-10-01T08:00:00.000Z"),
            months[0].runs.map { it.startedAt },
        )
        assertEquals(1L, months[1].fileCount)
        assertEquals(10L, months[1].bytes)
        assertEquals(120000L, months[0].runs[0].elapsedMs)
        assertEquals(1.25, months[0].runs[0].mbPerSecond!!, 0.0)
    }

    private fun drive(): FakeArchiveDrive = FakeArchiveDrive(folders.newFolder("drive").toPath())

    private fun write(drive: FakeArchiveDrive, name: String, text: String) {
        drive.appendText("$RUNS/$name", text)
    }

    private fun finished(startedAt: String, size: Long, extraFileSize: Long? = null): String {
        val files = StringBuilder()
        files.append(fileLine(hash = "aaa", size = size, name = "a.jpg")).append('\n')
        if (extraFileSize != null) {
            files.append(fileLine(hash = "bbb", size = extraFileSize, name = "b.jpg")).append('\n')
        }
        return header(startedAt) + "\n" + files + footer(elapsedMs = 120000, mbPerSecond = "1.25") + "\n"
    }

    private fun header(startedAt: String): String {
        return "{\"type\":\"header\",\"startedAt\":\"$startedAt\",\"previewPreset\":null}"
    }

    private fun fileLine(hash: String, size: Long, name: String): String {
        return "{\"type\":\"file\",\"hash\":\"$hash\",\"drivePath\":\"Photoxpo/Originals/2026/2026-10/$name\"," +
            "\"size\":$size,\"kind\":\"photo\",\"chosenDate\":\"2026-10-11\",\"sourceTag\":\"modified\"," +
            "\"originalFilename\":\"$name\",\"originalFilenames\":[\"$name\"],\"previewFileName\":null}"
    }

    private fun footer(
        elapsedMs: Long,
        mbPerSecond: String,
        startedAt: String = "2026-10-11T18:30:00.000Z",
        endedAt: String = "2026-10-11T18:32:00.000Z",
        fileCount: Long = 1,
        bytes: Long = 5,
    ): String {
        return "{\"type\":\"footer\",\"startedAt\":\"$startedAt\",\"endedAt\":\"$endedAt\"," +
            "\"fileCount\":$fileCount,\"bytes\":$bytes,\"checked\":true," +
            "\"elapsedMs\":$elapsedMs,\"mbPerSecond\":$mbPerSecond}"
    }

    private companion object {
        const val RUNS = "Photoxpo/.photoxpo/runs"
    }
}
