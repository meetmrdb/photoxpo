package app.photoxpo.home

import app.photoxpo.backup.BackupStatus
import app.photoxpo.backup.BackupUiState
import app.photoxpo.core.RunStage

private const val MINUTE_MS = 60_000L

/** How long this backup has been going and, once it can be worked out honestly, how long is left. */
internal fun runPaceLine(state: BackupUiState, nowEpochMillis: Long): String? {
    if (state.status != BackupStatus.Running) return null
    val started = state.runStartedAtMillis ?: return null
    val elapsed = (nowEpochMillis - started).coerceAtLeast(0L)
    if (elapsed < MINUTE_MS) return null
    val sofar = "${formatDuration(elapsed)} so far"
    return "$sofar · ${runLeftPhrase(state, nowEpochMillis, elapsed)}"
}

private fun runLeftPhrase(state: BackupUiState, now: Long, elapsed: Long): String {
    if (state.runStage == RunStage.Checking) {
        return if (state.runBytesToCopy > 0L) "everything new is already safe" else "nothing new to copy"
    }
    val firstAt = state.firstCopyAtMillis
    if (firstAt == null) return "checking photos already on your drive"
    val remaining = (state.runBytesToCopy - state.runBytesCopied).coerceAtLeast(0L)
    if (remaining == 0L) return "finishing up"
    val steadyBytes = state.runBytesCopied - state.firstCopyBytes
    val steadyMillis = now - firstAt
    if (steadyBytes <= 0L || steadyMillis < MINUTE_MS) return "working out the time left"
    val leftMillis = remaining * steadyMillis / steadyBytes
    return "about ${formatDuration(roundedUp(leftMillis))} left"
}

/** Whole minutes up to ten, then the nearest five, so the estimate does not twitch. */
private fun roundedUp(millis: Long): Long {
    val minutes = (millis + MINUTE_MS - 1) / MINUTE_MS
    val rounded = if (minutes <= 10) minutes else ((minutes + 4) / 5) * 5
    return rounded.coerceAtLeast(1L) * MINUTE_MS
}

internal fun formatDuration(millis: Long): String {
    val minutes = millis / MINUTE_MS
    if (minutes < 60) return "${minutes.coerceAtLeast(1L)} min"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) "$hours h" else "$hours h ${rest.toString().padStart(2, '0')} min"
}

internal fun lastRunLine(state: BackupUiState): String? {
    val took = state.lastRunTookMillis ?: return null
    if (state.status != BackupStatus.Done) return null
    return "Last backup took ${formatDuration(took)}"
}
