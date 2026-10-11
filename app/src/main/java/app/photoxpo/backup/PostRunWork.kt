package app.photoxpo.backup

import android.content.Context
import app.photoxpo.PhotoxpoApp
import app.photoxpo.core.BackedUpOriginal
import app.photoxpo.core.PreviewPipeline
import app.photoxpo.core.previewFileName
import app.photoxpo.data.AndroidPreviewMaker
import app.photoxpo.data.MediaStoreSource
import app.photoxpo.data.PhoneFilePreviewStore

/**
 * Work after a Backup run: make a Preview for every Backed up Original (one at a time, Backed up never
 * waits for it) and fill the on-phone Photos cache. Safe to repeat: existing Previews are skipped.
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
