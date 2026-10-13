package app.photoxpo.home

import app.photoxpo.backup.BackupStatus
import app.photoxpo.backup.BackupUiState
import app.photoxpo.core.estimateRun
import app.photoxpo.data.DriveStatus
import java.time.Duration

/** Warn only for a run longer than this... */
private val LONG_RUN: Duration = Duration.ofMinutes(30)

/** ...that would leave the battery under this. */
private const val LEAVE_AT_LEAST_PERCENT = 20

/** The charge target is the estimated use, the level to stay above, and some room for a wrong guess. */
private const val CHARGE_MARGIN_PERCENT = 10

private const val TARGET_STEP_PERCENT = 5

/**
 * A plain line before a long run on a low battery, with a charge target. Null for a short run, a
 * comfortable battery, a charging phone or an unknown battery. Back up stays enabled either way.
 */
internal fun longRunWarning(state: BackupUiState): String? {
    if (state.status == BackupStatus.Running) return null
    if (state.driveStatus != DriveStatus.Ready) return null
    val toCopy = state.bytesToCopy ?: return null
    val battery = state.batteryPercent ?: return null
    if (state.charging || toCopy <= 0L) return null
    val estimate = estimateRun(toCopy, state.recentRuns)
    if (estimate.duration <= LONG_RUN) return null
    if (battery - estimate.batteryPercent >= LEAVE_AT_LEAST_PERCENT) return null
    val target = chargeTarget(estimate.batteryPercent)
    return "This will take about ${formatDuration(estimate.duration.toMillis())} and use about " +
        "${estimate.batteryPercent}% battery. Charge to at least $target% first."
}

private fun chargeTarget(usePercent: Int): Int {
    val needed = usePercent + LEAVE_AT_LEAST_PERCENT + CHARGE_MARGIN_PERCENT
    val rounded = ((needed + TARGET_STEP_PERCENT - 1) / TARGET_STEP_PERCENT) * TARGET_STEP_PERCENT
    return rounded.coerceAtMost(100)
}
