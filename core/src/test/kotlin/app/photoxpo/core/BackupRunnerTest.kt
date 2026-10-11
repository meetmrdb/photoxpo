package app.photoxpo.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BackupRunnerTest {
    @Rule
    @JvmField
    val folders = TemporaryFolder()

    @Test
    fun oneFile_isBackedUp_withHashAndManifestHeaderFileAndFooter() {
        val drive = drive()
        val result = startBackupRun(media(), drive, clock = fixedClock)

        val item = result.items.single()
        assertEquals("media-1", item.id)
        assertEquals("IMG_0001.jpg", item.name)
        assertEquals(ItemState.COMMITTED, item.state)
        assertTrue(drive.exists(FINAL_PATH))
        assertFalse(drive.exists(PART_PATH))

        val stored = drive.read(FINAL_PATH).use { it.readAllBytes() }
        assertEquals(ORIGINAL_HASH, sha256(stored))

        assertTrue(result.manifestPath.startsWith("Photoxpo/.photoxpo/runs/"))
        assertTrue(result.manifestPath.endsWith(".jsonl"))
        assertEquals(
            listOf(result.manifestPath.substringAfterLast('/')),
            drive.list("Photoxpo/.photoxpo/runs"),
        )

        val lines = manifestLines(drive, result.manifestPath)
        assertEquals(3, lines.size)
        assertEquals("header", field(lines[0], "type"))
        assertEquals("null", field(lines[0], "previewPreset"))
        assertEquals("file", field(lines[1], "type"))
        assertEquals("footer", field(lines[2], "type"))

        val fileLine = lines[1]
        assertEquals(ORIGINAL_HASH, field(fileLine, "hash"))
        assertEquals(FINAL_PATH, field(fileLine, "drivePath"))
        assertEquals(ORIGINAL_BYTES.size.toString(), field(fileLine, "size"))
        assertEquals("photo", field(fileLine, "kind"))
        assertEquals("2020-01-31", field(fileLine, "chosenDate"))
        assertEquals("modified", field(fileLine, "sourceTag"))
        assertEquals("IMG_0001.jpg", field(fileLine, "originalFilename"))
        assertEquals("null", field(fileLine, "previewFileName"))

        val footer = lines[2]
        assertEquals("1", field(footer, "fileCount"))
        assertEquals(ORIGINAL_BYTES.size.toString(), field(footer, "bytes"))
        assertEquals("true", field(footer, "checked"))
        assertEquals("2026-10-11T18:30:00.000Z", field(footer, "startedAt"))
        assertEquals("2026-10-11T18:30:00.000Z", field(footer, "endedAt"))
        assertEquals("0", field(footer, "elapsedMs"))
        assertEquals("0", field(footer, "mbPerSecond"))
    }

    @Test
    fun corruptedReadBack_needsAttention_andIsNotBackedUp() {
        val drive = drive(corruptReadBack = true)
        val result = startBackupRun(media(), drive, clock = fixedClock)

        val item = result.items.single()
        assertEquals(ItemState.NEEDS_ATTENTION, item.state)
        assertFalse(drive.exists(FINAL_PATH))
        assertFalse(drive.exists(PART_PATH))
        assertNoFileLine(drive, result.manifestPath)
    }

    @Test
    fun backedUpOriginal_matchesSourceBytes() {
        val drive = drive()
        startBackupRun(media(), drive, clock = fixedClock)

        val stored = drive.read(FINAL_PATH).use { it.readAllBytes() }
        assertArrayEquals(ORIGINAL_BYTES, stored)
    }

    @Test
    fun photoExifDate_choosesOriginalFolder_notModifiedTime() {
        val drive = drive()
        val file = MediaFile(
            id = "media-exif",
            name = "IMG_exif.jpg",
            sizeBytes = ORIGINAL_BYTES.size.toLong(),
            modifiedAtEpochMillis = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli(),
            kind = MediaKind.PHOTO,
            exifDateTimeOriginal = "2021:12:31 23:59:59",
        )
        val exifFinalPath = "Photoxpo/Originals/2021/2021-12/IMG_exif.jpg"
        val result = startBackupRun(FakeMediaSource(listOf(file to ORIGINAL_BYTES)), drive, clock = fixedClock)

        val item = result.items.single()
        assertEquals(ItemState.COMMITTED, item.state)
        assertTrue(drive.exists(exifFinalPath))
        val lines = manifestLines(drive, result.manifestPath)
        val fileLine = lines[1]
        assertEquals("2021-12-31", field(fileLine, "chosenDate"))
        assertEquals("exif", field(fileLine, "sourceTag"))
        assertEquals(exifFinalPath, field(fileLine, "drivePath"))
    }

    @Test
    fun byteCountMismatch_needsAttention() {
        val drive = drive(wrongByteCount = true)
        val result = startBackupRun(media(), drive, clock = fixedClock)

        assertEquals(ItemState.NEEDS_ATTENTION, result.items.single().state)
        assertFalse(drive.exists(FINAL_PATH))
        assertFalse(drive.exists(PART_PATH))
        assertNoFileLine(drive, result.manifestPath)
    }

    private fun media(): FakeMediaSource {
        val file = MediaFile(
            id = "media-1",
            name = "IMG_0001.jpg",
            sizeBytes = ORIGINAL_BYTES.size.toLong(),
            modifiedAtEpochMillis = Instant.parse("2020-01-31T20:00:00Z").toEpochMilli(),
            kind = MediaKind.PHOTO,
        )
        return FakeMediaSource(listOf(file to ORIGINAL_BYTES))
    }

    private fun drive(
        corruptReadBack: Boolean = false,
        wrongByteCount: Boolean = false,
    ): FakeArchiveDrive {
        return FakeArchiveDrive(
            root = folders.newFolder("drive").toPath(),
            corruptReadBack = corruptReadBack,
            wrongByteCount = wrongByteCount,
        )
    }

    private fun manifestLines(drive: ArchiveDrive, path: String): List<String> {
        return drive.readText(path).trim().lines().filter { it.isNotEmpty() }
    }

    private fun assertNoFileLine(drive: ArchiveDrive, path: String) {
        val lines = manifestLines(drive, path)
        assertTrue(lines.none { field(it, "type") == "file" })
        assertTrue(lines.none { it.contains("IMG_0001.jpg") })
        assertTrue(lines.none { it.contains(ORIGINAL_HASH) })
    }

    private fun field(line: String, key: String): String {
        val quoted = "\"$key\""
        val keyAt = line.indexOf(quoted)
        assertTrue("missing $key in $line", keyAt >= 0)
        var index = keyAt + quoted.length
        while (line[index].isWhitespace() || line[index] == ':') index++
        if (line.startsWith("null", index)) return "null"
        if (line[index] == '"') {
            val end = line.indexOf('"', index + 1)
            return line.substring(index + 1, end)
        }
        val start = index
        while (index < line.length && line[index] != ',' && line[index] != '}') index++
        return line.substring(start, index)
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

    companion object {
        private val ORIGINAL_BYTES = byteArrayOf(0x00, 0x01, 0xFF.toByte(), 0x0A, 0x41)
        private const val ORIGINAL_HASH =
            "c4deec4e2222596ddb146449a39db308d94413ff0f538d7faa3db02100aeaece"
        private const val FINAL_PATH = "Photoxpo/Originals/2020/2020-01/IMG_0001.jpg"
        private const val PART_PATH = "$FINAL_PATH.photoxpo-part"
        private val fixedClock: Clock =
            Clock.fixed(Instant.parse("2026-10-11T18:30:00Z"), ZoneOffset.UTC)
    }
}
