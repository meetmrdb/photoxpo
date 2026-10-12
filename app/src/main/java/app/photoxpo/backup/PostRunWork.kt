package app.photoxpo.backup

import android.content.Context
import app.photoxpo.PhotoxpoApp
import app.photoxpo.core.ArchiveDrive
import app.photoxpo.core.BackedUpOriginal
import app.photoxpo.core.PreviewPipeline
import app.photoxpo.core.PreviewStore
import app.photoxpo.core.previewFileName
import app.photoxpo.data.AndroidPreviewMaker
import app.photoxpo.data.DriveStatus
import app.photoxpo.data.MediaStoreSource
import app.photoxpo.data.PhoneFilePreviewStore
import app.photoxpo.data.PreviewMirror
import app.photoxpo.data.probeArchiveDrive

/**
 * Work after a Backup run: make a Preview for every Backed up Original (one at a time, Backed up never
 * waits for it), copy that Preview onto the Archive Drive when the drive is ready, and fill the
 * on-phone Photos cache. Safe to repeat: an existing Preview is skipped, including one already
 * on the Archive Drive. A failure to copy the Preview does not change Backed up.
 */
class PostRunWork(
    private val context: Context,
    private val settings: BackupSettingsStore,
) {
    suspend fun run() {
        val mediaSource = MediaStoreSource(context)
        val kindByMediaId = mediaSource.listCameraFolder().associate { it.id to it.kind }
        val originals = settings.ledgerEntries().mapNotNull { (mediaId, hash) ->
            kindByMediaId[mediaId]?.let { BackedUpOriginal(hash = hash, kind = it, mediaId = mediaId) }
        }
        val store = PhoneFilePreviewStore(context)
        PreviewPipeline(
            mediaSource = mediaSource,
            previewMaker = AndroidPreviewMaker(context, settings.previewPreset()),
            previewStore = store,
        ).run(originals)

        val services = (context.applicationContext as PhotoxpoApp).services
        try {
            val uri = settings.treeUri()
            val driveReady = uri != null && probeArchiveDrive(context, uri) == DriveStatus.Ready
            if (driveReady) {
                mirrorPreviews(services.archiveDrive(), store, originals.map { it.hash })
            }
        } catch (_: Exception) {
            // The Preview on the Archive Drive is best effort. Backed up stays as it is.
        }
        val repository = services.photosRepository
        try {
            repository.refreshFromManifests(services.archiveDrive())
        } catch (_: Exception) {
            // Drive out or unreadable: keep the cache as it is.
        }
        for (original in originals) {
            val preview = if (store.exists(original.hash)) previewFileName(original.hash) else null
            repository.linkPhone(original.hash, original.mediaId.toLongOrNull(), preview)
        }
    }
}

/**
 * Copies each phone Preview to `Photoxpo/.photoxpo/previews/<hash>.jpg` on [archiveDrive].
 * Skips a hash with no Preview, and a Preview already stored on the drive.
 * One failure does not stop the others.
 */
internal fun mirrorPreviews(
    archiveDrive: ArchiveDrive,
    previews: PreviewStore,
    hashes: List<String>,
) {
    val mirror = PreviewMirror(archiveDrive)
    for (hash in hashes) {
        try {
            if (!previews.exists(hash)) continue
            val path = "Photoxpo/.photoxpo/previews/${previewFileName(hash)}"
            if (archiveDrive.exists(path)) continue
            val bytes = previews.read(hash) ?: continue
            mirror.write(hash, bytes)
        } catch (_: Exception) {
            // The next Backup run can try this Preview again.
        }
    }
}
