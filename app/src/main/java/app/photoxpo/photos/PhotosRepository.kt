package app.photoxpo.photos

import app.photoxpo.core.ArchiveDrive
import app.photoxpo.core.ManifestFileEntry
import app.photoxpo.core.ManifestReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * On-phone cache of photos. [photos] and [photo] read only this cache, so they
 * work with no Archive Drive attached.
 *
 * [driveOnline] mirrors [driveOnlineSource]. A `() -> Boolean` probe can be
 * passed as a Flow that emits the probe result, or as a MutableStateFlow the
 * caller updates when a drive is plugged in.
 */
class PhotosRepository(
    private val dao: PhotoDao,
    private val manifestReader: ManifestReader,
    driveOnlineSource: Flow<Boolean>,
    scope: CoroutineScope,
) {
    val driveOnline: StateFlow<Boolean> = driveOnlineSource.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = (driveOnlineSource as? StateFlow<Boolean>)?.value ?: false,
    )

    fun photos(): Flow<List<PhotoEntity>> = dao.observeAll()

    fun photo(hash: String): Flow<PhotoEntity?> = dao.observeByHash(hash)

    /** Records that this Original is on the phone after Load. */
    suspend fun markOriginalOnPhone(hash: String) {
        val current = dao.get(hash) ?: return
        if (current.originalOnPhone) return
        dao.upsert(listOf(current.copy(originalOnPhone = true)))
    }

    /** Records which phone item an Original came from and which Preview file shows it. */
    suspend fun linkPhone(hash: String, mediaId: Long?, previewFile: String?) {
        dao.linkPhone(hash, mediaId, previewFile)
    }

    /**
     * The person chose "Use this drive instead". Every cached photo is not Backed up,
     * and its Original is not on this Archive Drive. The row and Preview stay.
     */
    suspend fun markOriginalsNotOnThisDrive() {
        dao.clearBackedUpAndDriveFlags()
    }

    /**
     * Reads Manifests on [archiveDrive] and stores those Originals.
     * The drive wins for name, date, size, kind, source tag, and drive path.
     * Phone-only facts already stored for that hash are kept: [PhotoEntity.mediaId],
     * [PhotoEntity.originalOnPhone], and [PhotoEntity.previewFile].
     * A hash listed on the drive is Backed up, with the Original on this Archive Drive.
     * A cached hash that is not in the Manifests is not Backed up, and its Original
     * is not on this Archive Drive. An empty run file still updates those cached photos.
     * If the drive cannot be read, this throws and leaves stored rows unchanged.
     */
    suspend fun refreshFromManifests(archiveDrive: ArchiveDrive) {
        val manifests = withContext(Dispatchers.IO) {
            manifestReader.readAll(archiveDrive)
        }
        val updated = applyDriveManifest(dao.all(), manifests.flatMap { it.entries })
        if (updated.isNotEmpty()) dao.upsert(updated)
    }
}

/**
 * Applies Manifest [entries] to [cached] photos.
 * The last entry for a hash wins. A cached hash with no entry keeps its row
 * and Preview, and is not Backed up on this Archive Drive.
 */
internal fun applyDriveManifest(
    cached: List<PhotoEntity>,
    entries: List<ManifestFileEntry>,
): List<PhotoEntity> {
    val latest = linkedMapOf<String, ManifestFileEntry>()
    for (entry in entries) {
        latest[entry.hash] = entry
    }
    val byHash = cached.associateBy { it.hash }
    val missing = cached
        .filter { it.hash !in latest }
        .map { it.copy(backedUp = false, originalOnThisDrive = false) }
    val present = latest.values.map { entry -> mergeDriveEntry(entry, byHash[entry.hash]) }
    return missing + present
}

private fun mergeDriveEntry(entry: ManifestFileEntry, prior: PhotoEntity?): PhotoEntity {
    return PhotoEntity(
        hash = entry.hash,
        mediaId = prior?.mediaId,
        name = entry.originalFilename,
        chosenDate = entry.chosenDate,
        sourceTag = entry.sourceTag,
        kind = entry.kind,
        sizeBytes = entry.size,
        drivePath = entry.drivePath,
        backedUp = true,
        previewFile = prior?.previewFile,
        originalOnThisDrive = true,
        originalOnPhone = prior?.originalOnPhone ?: false,
    )
}
