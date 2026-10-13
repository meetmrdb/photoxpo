package app.photoxpo.core

data class BackedUpOriginal(
    val hash: String,
    val kind: MediaKind,
    val mediaId: String,
)

data class PreviewPipelineResult(
    val made: List<String>,
    val skipped: List<String>,
    val failed: List<String>,
)

/**
 * Makes Previews for Originals that are already Backed up.
 * One Preview at a time. A failure leaves that item with no Preview yet.
 * The next run tries it again. Backed up does not wait on this.
 */
class PreviewPipeline(
    private val mediaSource: MediaSource,
    private val previewMaker: PreviewMaker,
    private val previewStore: PreviewStore,
) {
    fun run(originals: List<BackedUpOriginal>): PreviewPipelineResult {
        val made = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (original in originals) {
            if (previewStore.exists(original.hash)) {
                skipped += original.hash
                continue
            }
            try {
                val bytes = mediaSource.openOriginal(original.mediaId).use { stream ->
                    previewMaker.makePreview(stream, original.kind)
                }
                previewStore.write(original.hash, bytes)
                made += original.hash
            } catch (_: Exception) {
                failed += original.hash
            }
        }
        return PreviewPipelineResult(made, skipped, failed)
    }
}
