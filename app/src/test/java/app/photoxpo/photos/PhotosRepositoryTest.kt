package app.photoxpo.photos

import app.photoxpo.core.ArchiveDrive
import app.photoxpo.core.CreateResult
import app.photoxpo.core.ManifestFileEntry
import app.photoxpo.core.ManifestReader
import app.photoxpo.core.RenameResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class PhotosRepositoryTest {
    @Test
    fun emptyManifest_clearsBackedUp_andKeepsPreview() {
        val cached = listOf(photo("aaa", preview = "aaa.jpg"), photo("bbb", preview = "bbb.jpg"))
        val updated = applyDriveManifest(cached, emptyList()).associateBy { it.hash }

        assertEquals(setOf("aaa", "bbb"), updated.keys)
        assertFalse(updated.getValue("aaa").backedUp)
        assertFalse(updated.getValue("aaa").originalOnThisDrive)
        assertEquals("aaa.jpg", updated.getValue("aaa").previewFile)
        assertEquals("aaa.jpg", updated.getValue("aaa").name)
        assertTrue(updated.getValue("aaa").originalOnPhone)
        assertFalse(updated.getValue("bbb").backedUp)
        assertFalse(updated.getValue("bbb").originalOnThisDrive)
        assertEquals("bbb.jpg", updated.getValue("bbb").previewFile)
    }

    @Test
    fun hashMissingFromManifest_isNotBackedUp_andListedHashKeepsPreview() {
        val cached = listOf(photo("aaa", preview = "aaa.jpg"), photo("bbb", preview = "bbb.jpg"))
        val updated = applyDriveManifest(
            cached,
            listOf(entry("aaa", drivePath = "Photoxpo/Originals/2020/2020-01/a.jpg", name = "a.jpg")),
        ).associateBy { it.hash }

        val onDrive = updated.getValue("aaa")
        assertTrue(onDrive.backedUp)
        assertTrue(onDrive.originalOnThisDrive)
        assertEquals("a.jpg", onDrive.name)
        assertEquals("Photoxpo/Originals/2020/2020-01/a.jpg", onDrive.drivePath)
        assertEquals("aaa.jpg", onDrive.previewFile)
        assertEquals(7L, onDrive.mediaId)
        assertTrue(onDrive.originalOnPhone)

        val missing = updated.getValue("bbb")
        assertFalse(missing.backedUp)
        assertFalse(missing.originalOnThisDrive)
        assertEquals("bbb.jpg", missing.previewFile)
        assertEquals("bbb.jpg", missing.name)
    }

    @Test
    fun laterEntryForTheSameHash_wins() {
        val updated = applyDriveManifest(
            cached = listOf(photo("aaa")),
            entries = listOf(
                entry("aaa", drivePath = "Photoxpo/Originals/first.jpg", name = "first.jpg"),
                entry("aaa", drivePath = "Photoxpo/Originals/later.jpg", name = "later.jpg"),
            ),
        )
        assertEquals(1, updated.size)
        assertEquals("later.jpg", updated.single().name)
        assertEquals("Photoxpo/Originals/later.jpg", updated.single().drivePath)
        assertTrue(updated.single().backedUp)
    }

    @Test
    fun refreshFromManifests_withNoFileLines_clearsCachedPhotos() = runBlocking {
        val dao = FakePhotoDao(listOf(photo("aaa", preview = "keep.jpg")))
        val scope = CoroutineScope(SupervisorJob())
        val repository = repository(dao, scope)
        try {
            repository.refreshFromManifests(RunFileDrive(emptyMap()))
            val row = dao.all().single()
            assertFalse(row.backedUp)
            assertFalse(row.originalOnThisDrive)
            assertEquals("keep.jpg", row.previewFile)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun refreshFromManifests_driveWinsForHashesItDoesNotList() = runBlocking {
        val dao = FakePhotoDao(listOf(photo("aaa", preview = "aaa.jpg"), photo("bbb", preview = "bbb.jpg")))
        val scope = CoroutineScope(SupervisorJob())
        val repository = repository(dao, scope)
        try {
            repository.refreshFromManifests(RunFileDrive(mapOf("20261011T183000000Z.jsonl" to manifest("aaa"))))
            val rows = dao.all().associateBy { it.hash }
            assertTrue(rows.getValue("aaa").backedUp)
            assertTrue(rows.getValue("aaa").originalOnThisDrive)
            assertEquals("aaa.jpg", rows.getValue("aaa").previewFile)
            assertFalse(rows.getValue("bbb").backedUp)
            assertFalse(rows.getValue("bbb").originalOnThisDrive)
            assertEquals("bbb.jpg", rows.getValue("bbb").previewFile)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun refreshFromManifests_leavesRowsUnchangedWhenTheDriveCannotBeRead() = runBlocking {
        val dao = FakePhotoDao(listOf(photo("aaa", preview = "aaa.jpg")))
        val scope = CoroutineScope(SupervisorJob())
        val repository = repository(dao, scope)
        try {
            var thrown = false
            try {
                repository.refreshFromManifests(RunFileDrive(emptyMap(), failList = true))
            } catch (_: IOException) {
                thrown = true
            }
            assertTrue(thrown)
            val row = dao.all().single()
            assertTrue(row.backedUp)
            assertTrue(row.originalOnThisDrive)
            assertEquals("aaa.jpg", row.previewFile)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun markOriginalsNotOnThisDrive_clearsFlagsAndKeepsTheRow() = runBlocking {
        val dao = FakePhotoDao(listOf(photo("aaa", preview = "aaa.jpg"), photo("bbb", preview = "bbb.jpg")))
        val scope = CoroutineScope(SupervisorJob())
        val repository = repository(dao, scope)
        try {
            repository.markOriginalsNotOnThisDrive()
            val rows = dao.all()
            assertEquals(2, rows.size)
            assertTrue(rows.all { !it.backedUp && !it.originalOnThisDrive })
            assertEquals(setOf("aaa.jpg", "bbb.jpg"), rows.map { it.previewFile }.toSet())
        } finally {
            scope.cancel()
        }
    }

    private fun repository(dao: PhotoDao, scope: CoroutineScope): PhotosRepository {
        return PhotosRepository(
            dao = dao,
            manifestReader = ManifestReader(),
            driveOnlineSource = MutableStateFlow(false),
            scope = scope,
        )
    }
}

private fun photo(
    hash: String,
    preview: String? = "$hash.jpg",
): PhotoEntity {
    return PhotoEntity(
        hash = hash,
        mediaId = 7L,
        name = "$hash.jpg",
        chosenDate = "2020-01-02",
        sourceTag = "exif",
        kind = "photo",
        sizeBytes = 10,
        drivePath = "Photoxpo/Originals/2020/2020-01/$hash.jpg",
        backedUp = true,
        previewFile = preview,
        originalOnThisDrive = true,
        originalOnPhone = true,
    )
}

private fun entry(hash: String, drivePath: String, name: String): ManifestFileEntry {
    return ManifestFileEntry(
        hash = hash,
        drivePath = drivePath,
        size = 10,
        originalFilename = name,
        originalFilenames = listOf(name),
        kind = "photo",
        chosenDate = "2020-01-02",
        sourceTag = "modified",
    )
}

private fun manifest(hash: String): String {
    val header = "{\"type\":\"header\",\"startedAt\":\"2026-10-11T18:30:00.000Z\",\"previewPreset\":null}"
    val file = "{\"type\":\"file\",\"hash\":\"$hash\",\"drivePath\":\"Photoxpo/Originals/2020/2020-01/$hash.jpg\"," +
        "\"size\":10,\"kind\":\"photo\",\"chosenDate\":\"2020-01-02\",\"sourceTag\":\"modified\"," +
        "\"originalFilename\":\"$hash.jpg\",\"originalFilenames\":[\"$hash.jpg\"],\"previewFileName\":\"$hash.jpg\"}"
    val footer = "{\"type\":\"footer\",\"startedAt\":\"2026-10-11T18:30:00.000Z\"," +
        "\"endedAt\":\"2026-10-11T18:30:00.000Z\",\"fileCount\":1,\"bytes\":10," +
        "\"checked\":true,\"elapsedMs\":0,\"mbPerSecond\":0}"
    return "$header\n$file\n$footer\n"
}

private class FakePhotoDao(seed: List<PhotoEntity>) : PhotoDao {
    private val rows = linkedMapOf<String, PhotoEntity>()
    private val published = MutableStateFlow<List<PhotoEntity>>(emptyList())

    init {
        seed.forEach { rows[it.hash] = it }
        publish()
    }

    override fun observeAll(): Flow<List<PhotoEntity>> = published

    override fun observeByHash(hash: String): Flow<PhotoEntity?> = published.map { list ->
        list.firstOrNull { it.hash == hash }
    }

    override suspend fun get(hash: String): PhotoEntity? = rows[hash]

    override suspend fun getByHashes(hashes: List<String>): List<PhotoEntity> {
        return hashes.mapNotNull { rows[it] }
    }

    override suspend fun count(): Int = rows.size

    override suspend fun all(): List<PhotoEntity> = rows.values.toList()

    override suspend fun clearBackedUpAndDriveFlags() {
        val hashes = rows.keys.toList()
        for (hash in hashes) {
            rows[hash] = rows.getValue(hash).copy(backedUp = false, originalOnThisDrive = false)
        }
        publish()
    }

    override suspend fun linkPhone(hash: String, mediaId: Long?, previewFile: String?) {
        val current = rows[hash] ?: return
        rows[hash] = current.copy(mediaId = mediaId, previewFile = previewFile)
        publish()
    }

    override suspend fun upsert(photos: List<PhotoEntity>) {
        photos.forEach { rows[it.hash] = it }
        publish()
    }

    private fun publish() {
        published.value = rows.values.toList()
    }
}

private class RunFileDrive(
    private val runs: Map<String, String>,
    private val failList: Boolean = false,
) : ArchiveDrive {
    override fun list(directory: String): List<String> {
        if (failList) throw IOException("Archive Drive is absent")
        if (directory != "Photoxpo/.photoxpo/runs") return emptyList()
        return runs.keys.toList()
    }

    override fun readText(path: String): String = runs.getValue(path.substringAfterLast('/'))

    override fun create(path: String): CreateResult = error("unused")

    override fun write(path: String): OutputStream = error("unused")

    override fun read(path: String): InputStream = error("unused")

    override fun byteCount(path: String): Long = error("unused")

    override fun rename(from: String, to: String): RenameResult = error("unused")

    override fun exists(path: String): Boolean = error("unused")

    override fun appendText(path: String, text: String) = error("unused")

    override fun delete(path: String) = error("unused")
}
