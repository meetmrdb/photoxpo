package app.photoxpo.photos

data class LoadUiText(
    val message: String?,
    val loadLabel: String?,
    val loadEnabled: Boolean,
    val showCancel: Boolean,
    val showRetry: Boolean,
    val showLoadedOriginal: Boolean,
    val progressFraction: Float?,
)

fun loadButtonLabel(sizeBytes: Long): String = "Load · ${formatByteCount(sizeBytes)}"

/**
 * Maps a Load and whether the Archive Drive is attached to the photo view.
 * [LoadUiState.Loading.progress] is bytes copied so far.
 */
fun loadUiText(
    state: LoadUiState,
    sizeBytes: Long,
    driveOnline: Boolean,
    originalOnPhone: Boolean,
): LoadUiText {
    val label = loadButtonLabel(sizeBytes)
    if (state is LoadUiState.Loaded) {
        return placed(message = LOAD_ON_PHONE_AND_DRIVE, showLoadedOriginal = state.uri.isNotBlank())
    }
    if (originalOnPhone && state is LoadUiState.Idle) {
        return placed(message = LOAD_ON_PHONE_AND_DRIVE, showLoadedOriginal = false)
    }
    if (state is LoadUiState.Loading) {
        return LoadUiText(
            message = "${formatByteCount(state.progress)} of ${formatByteCount(sizeBytes)}",
            loadLabel = null,
            loadEnabled = false,
            showCancel = true,
            showRetry = false,
            showLoadedOriginal = false,
            progressFraction = progressFraction(state.progress, sizeBytes),
        )
    }
    if (state is LoadUiState.Failed) {
        return LoadUiText(
            message = state.message,
            loadLabel = null,
            loadEnabled = false,
            showCancel = false,
            showRetry = state.canRetry,
            showLoadedOriginal = false,
            progressFraction = null,
        )
    }
    if (!driveOnline) {
        val message = if (state is LoadUiState.Disabled) state.reason else LOAD_NEEDS_DRIVE
        return LoadUiText(
            message = message,
            loadLabel = label,
            loadEnabled = false,
            showCancel = false,
            showRetry = false,
            showLoadedOriginal = false,
            progressFraction = null,
        )
    }
    return LoadUiText(
        message = null,
        loadLabel = label,
        loadEnabled = true,
        showCancel = false,
        showRetry = false,
        showLoadedOriginal = false,
        progressFraction = null,
    )
}

private fun placed(message: String, showLoadedOriginal: Boolean): LoadUiText {
    return LoadUiText(
        message = message,
        loadLabel = null,
        loadEnabled = false,
        showCancel = false,
        showRetry = false,
        showLoadedOriginal = showLoadedOriginal,
        progressFraction = null,
    )
}

private fun progressFraction(copied: Long, sizeBytes: Long): Float {
    if (sizeBytes <= 0L) return if (copied > 0L) 1f else 0f
    return (copied.toDouble() / sizeBytes.toDouble()).toFloat().coerceIn(0f, 1f)
}
