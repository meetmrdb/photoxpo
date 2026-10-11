package app.photoxpo.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class BackupConflictTest {
    @Rule
    @JvmField
    val folders = TemporaryFolder()

    @Test
    fun samePathAndSameHash_isSkippedAndCountsAsBackedUp() {
        val root = folders.newFolder("same-hash").toPath()
        val drive = FakeArchiveDrive(root)
        val ledger = InMemoryBackedUpLedger()
        val clock = SettableClock(Instant.parse("2026-10-11T18:30:00Z"))
        val runner = BackupRunner(
            mediaSource = FakeMediaSource(listOf(photo("media-1", "IMG_0001.jpg", "2020-01-31T20:00:00Z", SAME_BYTES))),
            archiveDrive = drive,
            clock = clock,
            backedUpLedger = ledger,
            newDriveId = { "drive-same" },
        )

        val first = runner.start()
        assertEquals(BackupRunStatus.Completed, first.status)
        assertEquals(ItemState.COMMITTED, first.items.single().state)
        assertEquals(1, drive.writeCalls)
        val stored = Files.readAllBytes(root.resolve(SAME_PATH))

        ledger.clear()
        clock.now = clock.now.plusSeconds(60)
        val second = runner.start()

        assertEquals(BackupRunStatus.Completed, second.status)
        assertEquals(ItemState.COMMITTED, second.items.single().state)
        assertEquals(1, drive.writeCalls)
        assertArrayEquals(stored, Files.readAllBytes(root.resolve(SAME_PATH)))
        assertEquals(mapOf("media-1" to SAME_HASH), ledger.hashes())
        val entries = ManifestReader().readAll(drive).flatMap { it.entries }
        assertEquals(1, entries.size)
        assertEquals(SAME_PATH, entries.single().drivePath)
        assertEquals(SAME_HASH, entries.single().hash)
        assertFalse(secondManifestHasFileLine(drive, second.manifestPath))
    }

    @Test
    fun samePathAndDifferentHash_addsShortHashAndKeepsBoth() {
        val root = folders.newFolder("different-hash").toPath()
        val drive = FakeArchiveDrive(root)
        val older = photo("older", "IMG_1.jpg", "2020-01-15T08:00:00Z", OLD_BYTES)
        val newer = photo("newer", "IMG_1.jpg", "2020-01-15T18:00:00Z", NEW_BYTES)
        val result = startBackupRun(
            FakeMediaSource(listOf(older, newer)),
            drive,
            clock = fixed("2026-10-11T18:30:00Z"),
            newDriveId = { "drive-conflict" },
        )

        val plain = "Photoxpo/Originals/2020/2020-01/IMG_1.jpg"
        val version = "Photoxpo/Originals/2020/2020-01/IMG_1-$NEW_HASH8.jpg"
        assertEquals(BackupRunStatus.Completed, result.status)
        assertEquals(listOf(ItemState.COMMITTED, ItemState.COMMITTED), result.items.map { it.state })
        assertEquals(2, drive.writeCalls)
        assertArrayEquals(OLD_BYTES, drive.read(plain).use { it.readAllBytes() })
        assertArrayEquals(NEW_BYTES, drive.read(version).use { it.readAllBytes() })
        assertFalse(drive.exists("$plain.photoxpo-unproven-1"))
        val paths = ManifestReader().readAll(drive).flatMap { it.entries }.map { it.drivePath }.sorted()
        assertEquals(listOf(plain, version).sorted(), paths)
    }

    @Test
    fun duplicate_isStoredOnce_withOneManifestEntryListingBothNames() {
        val root = folders.newFolder("duplicate").toPath()
        val drive = FakeArchiveDrive(root)
        val first = photo("a", "IMG_A.jpg", "2019-04-01T00:00:00Z", OLD_BYTES)
        val second = photo("b", "IMG_B.jpg", "2020-05-02T00:00:00Z", OLD_BYTES)
        val result = startBackupRun(
            FakeMediaSource(listOf(second, first)),
            drive,
            clock = fixed("2026-10-11T18:30:00Z"),
            newDriveId = { "drive-dup" },
        )

        val storedPath = "Photoxpo/Originals/2019/2019-04/IMG_A.jpg"
        val otherPath = "Photoxpo/Originals/2020/2020-05/IMG_B.jpg"
        assertEquals(BackupRunStatus.Completed, result.status)
        assertEquals(listOf("a", "b"), result.items.map { it.id }.sorted())
        assertTrue(result.items.all { it.state == ItemState.COMMITTED })
        assertEquals(1, drive.writeCalls)
        assertArrayEquals(OLD_BYTES, drive.read(storedPath).use { it.readAllBytes() })
        assertFalse(drive.exists(otherPath))
        val entry = ManifestReader().read(drive.readText(result.manifestPath)).entries.single()
        assertEquals(OLD_HASH, entry.hash)
        assertEquals(storedPath, entry.drivePath)
        assertEquals("IMG_A.jpg", entry.originalFilename)
        assertEquals(listOf("IMG_A.jpg", "IMG_B.jpg"), entry.originalFilenames)
        assertEquals(1, fileLines(drive, result.manifestPath).size)
    }

    @Test
    fun editedPhoto_isANewVersion_andEarlierOriginalRemains() {
        val root = folders.newFolder("edited").toPath()
        val drive = FakeArchiveDrive(root)
        val identity = InMemoryDriveIdentityStore()
        val clock = SettableClock(Instant.parse("2026-10-11T18:30:00Z"))
        val plain = "Photoxpo/Originals/2020/2020-04/IMG_1.jpg"
        val version = "Photoxpo/Originals/2020/2020-04/IMG_1-$NEW_HASH8.jpg"
        val original = photo(
            id = "media-1",
            name = "IMG_1.jpg",
            modified = "2020-04-01T10:00:00Z",
            bytes = OLD_BYTES,
            exif = "2020:04:01 10:00:00",
        )
        BackupRunner(
            mediaSource = FakeMediaSource(listOf(original)),
            archiveDrive = drive,
            clock = clock,
            driveIdentity = identity,
            newDriveId = { "drive-edit" },
        ).start()
        val earlier = Files.readAllBytes(root.resolve(plain))

        clock.now = clock.now.plusSeconds(3600)
        val edited = photo(
            id = "media-1",
            name = "IMG_1.jpg",
            modified = "2021-08-01T10:00:00Z",
            bytes = NEW_BYTES,
            exif = "2020:04:01 10:00:00",
        )
        val result = BackupRunner(
            mediaSource = FakeMediaSource(listOf(edited)),
            archiveDrive = drive,
            clock = clock,
            driveIdentity = identity,
            newDriveId = { "drive-edit" },
        ).start()

        assertEquals(BackupRunStatus.Completed, result.status)
        assertEquals(ItemState.COMMITTED, result.items.single().state)
        assertArrayEquals(earlier, Files.readAllBytes(root.resolve(plain)))
        assertArrayEquals(OLD_BYTES, earlier)
        assertArrayEquals(NEW_BYTES, Files.readAllBytes(root.resolve(version)))
        val entries = ManifestReader().readAll(drive).flatMap { it.entries }
        assertEquals(setOf(OLD_HASH, NEW_HASH), entries.map { it.hash }.toSet())
        assertTrue(entries.any { it.drivePath == plain && it.hash == OLD_HASH })
        assertTrue(entries.any { it.drivePath == version && it.hash == NEW_HASH })
    }

    @Test
    fun differentDriveId_copiesNothing() {
        val root = folders.newFolder("other-drive").toPath()
        val drive = FakeArchiveDrive(root)
        drive.appendText("Photoxpo/.photoxpo/drive-id", "drive-b\n")
        val untouched = root.resolve("Seagate/keep.jpg")
        Files.createDirectories(untouched.parent)
        Files.write(untouched, byteArrayOf(4, 4, 4))
        val identity = InMemoryDriveIdentityStore("drive-a")
        val result = startBackupRun(
            FakeMediaSource(listOf(photo("media-1", "IMG_0001.jpg", "2020-01-31T20:00:00Z", SAME_BYTES))),
            drive,
            clock = fixed("2026-10-11T18:30:00Z"),
            driveIdentity = identity,
            newDriveId = { "should-not-write" },
        )

        assertEquals(BackupRunStatus.DriveMismatch, result.status)
        assertTrue(result.items.isEmpty())
        assertEquals("", result.manifestPath)
        assertEquals(0, drive.writeCalls)
        assertEquals("drive-b", drive.readText("Photoxpo/.photoxpo/drive-id").trim())
        assertEquals("drive-a", identity.expected())
        assertFalse(drive.exists(SAME_PATH))
        assertTrue(drive.list("Photoxpo/.photoxpo/runs").isEmpty())
        assertArrayEquals(byteArrayOf(4, 4, 4), Files.readAllBytes(untouched))
    }

    @Test
    fun wipedDriveWithNoDriveId_copiesNothing() {
        val root = folders.newFolder("wiped").toPath()
        val drive = FakeArchiveDrive(root)
        val identity = InMemoryDriveIdentityStore("drive-a")
        val result = startBackupRun(
            FakeMediaSource(listOf(photo("media-1", "IMG_0001.jpg", "2020-01-31T20:00:00Z", SAME_BYTES))),
            drive,
            clock = fixed("2026-10-11T18:30:00Z"),
            driveIdentity = identity,
            newDriveId = { "should-not-write" },
        )

        assertEquals(BackupRunStatus.DriveMismatch, result.status)
        assertTrue(result.items.isEmpty())
        assertEquals(0, drive.writeCalls)
        assertFalse(drive.exists("Photoxpo/.photoxpo/drive-id"))
        assertFalse(drive.exists(SAME_PATH))
        assertEquals("drive-a", identity.expected())
        assertTrue(drive.list("Photoxpo/.photoxpo/runs").isEmpty())
    }

    @Test
    fun useThisDriveInstead_clearsBackedUp_flagsPreviews_andWritesNewDriveId() {
        val root = folders.newFolder("switch").toPath()
        val drive = FakeArchiveDrive(root)
        drive.appendText("Photoxpo/.photoxpo/drive-id", "other-drive\n")
        val ledger = InMemoryBackedUpLedger()
        ledger.record("media-1", OLD_HASH)
        ledger.record("media-2", NEW_HASH)
        val previews = InMemoryPreviewRecordStore(
            listOf(PreviewRecord("media-1"), PreviewRecord("media-2")),
        )
        val identity = InMemoryDriveIdentityStore("phone-drive")
        val runner = BackupRunner(
            mediaSource = FakeMediaSource(listOf(photo("media-1", "IMG_1.jpg", "2020-01-15T08:00:00Z", OLD_BYTES))),
            archiveDrive = drive,
            clock = fixed("2026-10-11T18:30:00Z"),
            driveIdentity = identity,
            backedUpLedger = ledger,
            previews = previews,
            newDriveId = { "fresh-drive" },
        )

        runner.useThisDriveInstead()

        assertTrue(ledger.hashes().isEmpty())
        assertEquals(listOf("media-1", "media-2"), previews.records().map { it.mediaId })
        assertTrue(previews.records().all { it.originalNotOnThisDrive })
        assertEquals("fresh-drive", drive.readText("Photoxpo/.photoxpo/drive-id").trim())
        assertEquals("fresh-drive", identity.expected())
        assertTrue(drive.list("Photoxpo/.photoxpo/runs").isEmpty())

        val result = runner.start()
        assertEquals(BackupRunStatus.Completed, result.status)
        assertEquals(ItemState.COMMITTED, result.items.single().state)
        assertEquals(OLD_HASH, ledger.hashes()["media-1"])
        assertTrue(previews.records().all { it.originalNotOnThisDrive })
        assertEquals(1, ManifestReader().read(drive.readText(result.manifestPath)).entries.size)
    }

    @Test
    fun fileCap_copiesAtMostTheCap_oldestCaptureDateFirst() {
        val root = folders.newFolder("cap").toPath()
        val already = photo(
            id = "A",
            name = "A.jpg",
            modified = "2020-01-01T00:00:00Z",
            bytes = byteArrayOf(2),
            exif = "2020:01:01 10:00:00",
        )
        val oldestCapture = photo(
            id = "P",
            name = "P.jpg",
            modified = "2024-06-01T00:00:00Z",
            bytes = byteArrayOf(1),
            exif = "2018:01:01 10:00:00",
        )
        val sameDayEarlier = photo(
            id = "S",
            name = "S.jpg",
            modified = "2021-05-05T01:00:00Z",
            bytes = byteArrayOf(3),
            exif = "2021:05:05 10:00:00",
        )
        val sameDayLater = photo(
            id = "T",
            name = "T.jpg",
            modified = "2021-05-05T20:00:00Z",
            bytes = byteArrayOf(4),
            exif = "2021:05:05 10:00:00",
        )
        val newerCaptureOlderModified = photo(
            id = "Q",
            name = "Q.jpg",
            modified = "2010-01-01T00:00:00Z",
            bytes = byteArrayOf(5),
            exif = "2022:06:06 10:00:00",
        )
        val identity = InMemoryDriveIdentityStore()
        val seeded = FakeArchiveDrive(root)
        startBackupRun(
            FakeMediaSource(listOf(already)),
            seeded,
            clock = fixed("2026-10-11T18:00:00Z"),
            driveIdentity = identity,
            newDriveId = { "drive-cap" },
        )
        val capped = FakeArchiveDrive(root)
        val result = startBackupRun(
            FakeMediaSource(listOf(newerCaptureOlderModified, sameDayLater, already, oldestCapture, sameDayEarlier)),
            capped,
            clock = fixed("2026-10-11T19:00:00Z"),
            driveIdentity = identity,
            newDriveId = { "drive-cap" },
            fileCap = 2,
        )

        assertEquals(BackupRunStatus.Completed, result.status)
        assertEquals(listOf("P", "A", "S"), result.items.map { it.id })
        assertTrue(result.items.all { it.state == ItemState.COMMITTED })
        assertEquals(2, capped.writeCalls)
        assertArrayEquals(byteArrayOf(1), capped.read(path("2018-01-01", "P.jpg")).use { it.readAllBytes() })
        assertArrayEquals(byteArrayOf(2), capped.read(path("2020-01-01", "A.jpg")).use { it.readAllBytes() })
        assertArrayEquals(byteArrayOf(3), capped.read(path("2021-05-05", "S.jpg")).use { it.readAllBytes() })
        assertFalse(capped.exists(path("2021-05-05", "T.jpg")))
        assertFalse(capped.exists(path("2022-06-06", "Q.jpg")))
        val secondRun = ManifestReader().read(capped.readText(result.manifestPath)).entries.map { it.originalFilename }
        assertEquals(listOf("P.jpg", "S.jpg"), secondRun)
    }

    @Test
    fun shortHashNameTakenByUnprovenFile_isMovedAside() {
        val root = folders.newFolder("aside").toPath()
        val plain = path("2020-01-15", "IMG_1.jpg")
        val version = path("2020-01-15", "IMG_1-$NEW_HASH8.jpg")
        seed(root, plain, OLD_BYTES)
        seed(root, version, byteArrayOf(9, 9, 9, 9, 9))
        val drive = FakeArchiveDrive(root)
        val result = startBackupRun(
            FakeMediaSource(listOf(photo("media-1", "IMG_1.jpg", "2020-01-15T08:00:00Z", NEW_BYTES))),
            drive,
            clock = fixed("2026-10-11T18:30:00Z"),
            newDriveId = { "drive-aside" },
        )

        assertEquals(ItemState.COMMITTED, result.items.single().state)
        assertArrayEquals(OLD_BYTES, Files.readAllBytes(root.resolve(plain)))
        assertArrayEquals(NEW_BYTES, Files.readAllBytes(root.resolve(version)))
        val aside = "$version.photoxpo-unproven-1"
        assertArrayEquals(byteArrayOf(9, 9, 9, 9, 9), Files.readAllBytes(root.resolve(aside)))
    }

    @Test
    fun messyDriveContents_areLeftUntouched() {
        val root = folders.newFolder("messy").toPath()
        val messy = mapOf(
            "Seagate/DCIM/IMG_9999.jpg" to byteArrayOf(7, 7, 7, 7),
            "readme.txt" to "leave me".toByteArray(),
            "Photoxpo-old/note.txt" to byteArrayOf(1, 2),
            "photoxpo/secret.txt" to byteArrayOf(8),
        )
        for ((relative, bytes) in messy) {
            seed(root, relative, bytes)
        }
        val before = snapshotOutsidePhotoxpo(root)
        val recording = RecordingDrive(FakeArchiveDrive(root))
        val result = startBackupRun(
            FakeMediaSource(listOf(photo("media-1", "IMG_0001.jpg", "2020-01-31T20:00:00Z", SAME_BYTES))),
            recording,
            clock = fixed("2026-10-11T18:30:00Z"),
            newDriveId = { "drive-messy" },
        )

        assertEquals(BackupRunStatus.Completed, result.status)
        assertEquals(ItemState.COMMITTED, result.items.single().state)
        assertArrayEquals(SAME_BYTES, Files.readAllBytes(root.resolve(SAME_PATH)))
        assertEquals(before.keys, snapshotOutsidePhotoxpo(root).keys)
        for ((relative, bytes) in before) {
            assertArrayEquals(bytes, Files.readAllBytes(root.resolve(relative)))
        }
        assertTrue(recording.paths.isNotEmpty())
        assertTrue(
            recording.paths.joinToString("\n"),
            recording.paths.all { it.startsWith("Photoxpo/") },
        )
    }

    private fun secondManifestHasFileLine(drive: ArchiveDrive, manifestPath: String): Boolean {
        return fileLines(drive, manifestPath).isNotEmpty()
    }

    private fun fileLines(drive: ArchiveDrive, manifestPath: String): List<String> {
        return drive.readText(manifestPath).lineSequence().filter { it.contains("\"type\":\"file\"") }.toList()
    }

    private fun photo(
        id: String,
        name: String,
        modified: String,
        bytes: ByteArray,
        exif: String? = null,
    ): Pair<MediaFile, ByteArray> {
        val file = MediaFile(
            id = id,
            name = name,
            sizeBytes = bytes.size.toLong(),
            modifiedAtEpochMillis = Instant.parse(modified).toEpochMilli(),
            kind = MediaKind.PHOTO,
            exifDateTimeOriginal = exif,
        )
        return file to bytes
    }

    private fun path(date: String, name: String): String {
        return "Photoxpo/Originals/${date.substring(0, 4)}/${date.substring(0, 7)}/$name"
    }

    private fun seed(root: Path, relative: String, bytes: ByteArray) {
        val target = root.resolve(relative)
        Files.createDirectories(target.parent)
        Files.write(target, bytes)
    }

    private fun snapshotOutsidePhotoxpo(root: Path): Map<String, ByteArray> {
        val found = linkedMapOf<String, ByteArray>()
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { file ->
                val relative = root.relativize(file).toString().replace('\\', '/')
                if (!relative.startsWith("Photoxpo/")) {
                    found[relative] = Files.readAllBytes(file)
                }
            }
        }
        return found
    }

    private fun fixed(instant: String): Clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)

    private class SettableClock(start: Instant) : Clock() {
        var now: Instant = start

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }

    private class RecordingDrive(private val delegate: ArchiveDrive) : ArchiveDrive {
        val paths = mutableListOf<String>()

        override fun create(path: String): CreateResult {
            paths += path
            return delegate.create(path)
        }

        override fun write(path: String): OutputStream {
            paths += path
            return delegate.write(path)
        }

        override fun read(path: String): InputStream {
            paths += path
            return delegate.read(path)
        }

        override fun byteCount(path: String): Long {
            paths += path
            return delegate.byteCount(path)
        }

        override fun rename(from: String, to: String): RenameResult {
            paths += from
            paths += to
            return delegate.rename(from, to)
        }

        override fun exists(path: String): Boolean {
            paths += path
            return delegate.exists(path)
        }

        override fun list(directory: String): List<String> {
            paths += directory
            return delegate.list(directory)
        }

        override fun readText(path: String): String {
            paths += path
            return delegate.readText(path)
        }

        override fun appendText(path: String, text: String) {
            paths += path
            delegate.appendText(path, text)
        }

        override fun delete(path: String) {
            paths += path
            delegate.delete(path)
        }
    }

    companion object {
        private val SAME_BYTES = byteArrayOf(0x00, 0x01, 0xFF.toByte(), 0x0A, 0x41)
        private const val SAME_HASH =
            "c4deec4e2222596ddb146449a39db308d94413ff0f538d7faa3db02100aeaece"
        private const val SAME_PATH = "Photoxpo/Originals/2020/2020-01/IMG_0001.jpg"
        private val OLD_BYTES = byteArrayOf(1, 2, 3, 4)
        private const val OLD_HASH =
            "9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a"
        private val NEW_BYTES = byteArrayOf(5, 6, 7, 8, 9)
        private const val NEW_HASH =
            "761ca8fd7dd51248e00a7dc1c746bbde94e51cb06aa67194843c495a863e0106"
        private const val NEW_HASH8 = "761ca8fd"
    }
}
