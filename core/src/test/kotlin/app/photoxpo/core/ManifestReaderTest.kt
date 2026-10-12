package app.photoxpo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ManifestReaderTest {
    @Rule
    @JvmField
    val folders = TemporaryFolder()

    @Test
    fun truncatedLastLine_isIgnored_andMissingFooter_isInterrupted() {
        val file = fileLine(hash = "abc", drivePath = "Photoxpo/Originals/2020/2020-01/a.jpg", size = 4, name = "a.jpg")
        val truncated = "{\"type\":\"file\",\"hash\":\"def\",\"drivePath\":\"nope\",\"size\":1,\"originalFilename\":\"cut.jpg\"}"
        val text = header + "\n" + file + "\n" + truncated
        val contents = ManifestReader().read(text)
        assertEquals(listOf("a.jpg"), contents.entries.map { it.originalFilename })
        assertEquals("abc", contents.entries.single().hash)
        assertTrue(contents.interrupted)
    }

    @Test
    fun fileLineWithoutNewline_isIgnored() {
        val text = fileLine(hash = "ab", drivePath = "p", size = 1, name = "a.jpg")
        val contents = ManifestReader().read(text)
        assertTrue(contents.entries.isEmpty())
        assertTrue(contents.interrupted)
    }

    @Test
    fun unparseableLine_isIgnored() {
        val file = fileLine(hash = "abc", drivePath = "p", size = 4, name = "a.jpg")
        val text = header + "\n{not-json}\n" + file + "\n"
        val contents = ManifestReader().read(text)
        assertEquals(1, contents.entries.size)
        assertTrue(contents.interrupted)
    }

    @Test
    fun footerWithoutNewline_isIgnored_soTheRunStaysInterrupted() {
        val file = fileLine(hash = "abc", drivePath = "p", size = 4, name = "a.jpg")
        val text = header + "\n" + file + "\n" + footer
        val contents = ManifestReader().read(text)
        assertEquals(1, contents.entries.size)
        assertTrue(contents.interrupted)
    }

    @Test
    fun footerPresent_isNotInterrupted() {
        val file = fileLine(
            hash = "abc",
            drivePath = "Photoxpo/Originals/2020/2020-01/a.jpg",
            size = 4,
            name = "a.jpg",
            date = "2020-01-31",
        )
        val text = header + "\n" + file + "\n" + footer + "\n"
        val contents = ManifestReader().read(text)
        assertFalse(contents.interrupted)
        val entry = contents.entries.single()
        assertEquals("abc", entry.hash)
        assertEquals("Photoxpo/Originals/2020/2020-01/a.jpg", entry.drivePath)
        assertEquals(4L, entry.size)
        assertEquals("a.jpg", entry.originalFilename)
        assertEquals("photo", entry.kind)
        assertEquals("2020-01-31", entry.chosenDate)
        assertEquals("modified", entry.sourceTag)
        assertEquals(listOf("a.jpg"), entry.originalFilenames)
    }

    @Test
    fun escapedStrings_roundTrip() {
        val json = "{\"type\":\"file\",\"hash\":\"ab\",\"drivePath\":\"p\\u0001q\",\"size\":2," +
            "\"originalFilename\":\"a\\\"b\\\\c\",\"kind\":\"photo\",\"chosenDate\":\"2020-01-02\"," +
            "\"sourceTag\":\"modified\"}\n"
        val entry = ManifestReader().read(json).entries.single()
        assertEquals("p\u0001q", entry.drivePath)
        assertEquals("a\"b\\c", entry.originalFilename)
        assertEquals(2L, entry.size)
        assertEquals("photo", entry.kind)
        assertEquals("2020-01-02", entry.chosenDate)
        assertEquals("modified", entry.sourceTag)
    }

    @Test
    fun incompleteFileLine_isNotCommitted() {
        val text = "{\"type\":\"file\",\"drivePath\":\"p\",\"size\":1,\"originalFilename\":\"a.jpg\"}\n"
        val contents = ManifestReader().read(text)
        assertTrue(contents.entries.isEmpty())
        assertTrue(contents.interrupted)
    }

    @Test
    fun sameHashLines_mergeIntoOneEntry_andTheFirstDrivePathWins() {
        val first = fileLine(hash = "abc", drivePath = "Photoxpo/Originals/2020/2020-01/a.jpg", size = 4, name = "a.jpg")
        val second = "{\"type\":\"file\",\"hash\":\"abc\",\"drivePath\":\"Photoxpo/Originals/2020/2020-05/b.jpg\"," +
            "\"size\":9,\"kind\":\"photo\",\"chosenDate\":\"2020-05-02\",\"sourceTag\":\"modified\"," +
            "\"originalFilename\":\"b.jpg\",\"originalFilenames\":[\"b.jpg\"],\"previewFileName\":null}"
        val other = fileLine(hash = "def", drivePath = "Photoxpo/Originals/2020/2020-06/c.jpg", size = 3, name = "c.jpg")
        val text = header + "\n" + first + "\n" + second + "\n" + other + "\n"
        val contents = ManifestReader().read(text)
        assertEquals(2, contents.entries.size)
        val merged = contents.entries[0]
        assertEquals("abc", merged.hash)
        assertEquals("Photoxpo/Originals/2020/2020-01/a.jpg", merged.drivePath)
        assertEquals(4L, merged.size)
        assertEquals("a.jpg", merged.originalFilename)
        assertEquals(listOf("a.jpg", "b.jpg"), merged.originalFilenames)
        assertEquals("c.jpg", contents.entries[1].originalFilename)
    }

    @Test
    fun originalFilenamesArray_listsEverySourceName() {
        val json = "{\"type\":\"file\",\"hash\":\"ab\",\"drivePath\":\"p\",\"size\":2," +
            "\"kind\":\"photo\",\"chosenDate\":\"2020-01-02\",\"sourceTag\":\"modified\"," +
            "\"originalFilename\":\"a.jpg\",\"originalFilenames\":[\"a.jpg\",\"b.jpg\"]," +
            "\"previewFileName\":null}\n"
        val entry = ManifestReader().read(json).entries.single()
        assertEquals("a.jpg", entry.originalFilename)
        assertEquals(listOf("a.jpg", "b.jpg"), entry.originalFilenames)
        assertEquals("ab", entry.hash)
        assertEquals("p", entry.drivePath)
    }

    @Test
    fun readAll_readsEveryRunManifest_andSkipsOtherFiles() {
        val drive = FakeArchiveDrive(folders.newFolder("drive").toPath())
        val runs = "Photoxpo/.photoxpo/runs"
        val older = header + "\n" +
            fileLine(hash = "aaa", drivePath = "one", size = 1, name = "one.jpg") + "\n" +
            "{\"type\":\"file\",\"hash\":\"zzz\""
        val newer = header + "\n" +
            fileLine(hash = "bbb", drivePath = "two", size = 2, name = "two.jpg") + "\n" +
            footer + "\n"
        drive.appendText("$runs/20260101T000000000Z.jsonl", older)
        drive.appendText("$runs/20260102T000000000Z.jsonl", newer)
        drive.appendText("$runs/notes.txt", "hello\n")

        val contents = ManifestReader().readAll(drive)
        assertEquals(2, contents.size)
        assertTrue(contents[0].interrupted)
        assertEquals("one.jpg", contents[0].entries.single().originalFilename)
        assertEquals("aaa", contents[0].entries.single().hash)
        assertFalse(contents[1].interrupted)
        assertEquals("two.jpg", contents[1].entries.single().originalFilename)
    }

    @Test
    fun realManifest_roundTripsThroughTheReader() {
        val drive = FakeArchiveDrive(folders.newFolder("drive").toPath())
        val bytes = byteArrayOf(0x00, 0x01, 0xFF.toByte(), 0x0A, 0x41)
        val file = MediaFile(
            id = "media-1",
            name = "IMG_0001.jpg",
            sizeBytes = bytes.size.toLong(),
            modifiedAtEpochMillis = Instant.parse("2020-01-31T20:00:00Z").toEpochMilli(),
            kind = MediaKind.PHOTO,
        )
        val clock = Clock.fixed(Instant.parse("2026-10-11T18:30:00Z"), ZoneOffset.UTC)
        val result = startBackupRun(FakeMediaSource(listOf(file to bytes)), drive, clock = clock)
        val contents = ManifestReader().read(drive.readText(result.manifestPath))
        assertFalse(contents.interrupted)
        val entry = contents.entries.single()
        assertEquals(
            "c4deec4e2222596ddb146449a39db308d94413ff0f538d7faa3db02100aeaece",
            entry.hash,
        )
        assertEquals("Photoxpo/Originals/2020/2020-01/IMG_0001.jpg", entry.drivePath)
        assertEquals(5L, entry.size)
        assertEquals("IMG_0001.jpg", entry.originalFilename)
        assertEquals(listOf("IMG_0001.jpg"), entry.originalFilenames)
        assertEquals("photo", entry.kind)
        assertEquals("2020-01-31", entry.chosenDate)
        assertEquals("modified", entry.sourceTag)
        assertEquals(listOf(contents), ManifestReader().readAll(drive))
    }

    private fun fileLine(
        hash: String,
        drivePath: String,
        size: Long,
        name: String,
        date: String = "2020-01-31",
    ): String {
        return "{\"type\":\"file\",\"hash\":\"$hash\",\"drivePath\":\"$drivePath\",\"size\":$size," +
            "\"kind\":\"photo\",\"chosenDate\":\"$date\",\"sourceTag\":\"modified\"," +
            "\"originalFilename\":\"$name\",\"previewFileName\":null}"
    }

    companion object {
        private const val header =
            "{\"type\":\"header\",\"startedAt\":\"2026-10-11T18:30:00.000Z\",\"previewPreset\":null}"
        private const val footer =
            "{\"type\":\"footer\",\"startedAt\":\"2026-10-11T18:30:00.000Z\"," +
                "\"endedAt\":\"2026-10-11T18:30:00.000Z\",\"fileCount\":1,\"bytes\":5," +
                "\"checked\":true,\"elapsedMs\":0,\"mbPerSecond\":1.25}"
    }
}
