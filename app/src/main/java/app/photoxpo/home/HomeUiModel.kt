package app.photoxpo.home

import java.util.Locale
import app.photoxpo.backup.BackupStatus
import app.photoxpo.backup.BackupUiState
import app.photoxpo.backup.PauseReason
import app.photoxpo.backup.SessionAccess
import app.photoxpo.backup.backupProgressText
import app.photoxpo.backup.runFiles
import app.photoxpo.backup.sessionAccessMessage
import app.photoxpo.core.LOW_BATTERY_PERCENT
import app.photoxpo.core.RunStage
import app.photoxpo.core.StopReason
import app.photoxpo.data.DriveStatus
import app.photoxpo.onboarding.MediaAccessState
import app.photoxpo.onboarding.accessWarning

data class HomeOfflineBanner(
    val message: String,
    val lastBackedUpLine: String,
)

data class HomeAccessWarning(
    val title: String,
    val message: String,
    val actionLabel: String,
)

data class HomeUiModel(
    val statusSentence: String,
    val primaryLabel: String,
    val primaryPauses: Boolean,
    val progressText: String,
    val progressFraction: Float?,
    val lastBackedUpLine: String,
    val offlineBanner: HomeOfflineBanner?,
    val showDriveMismatch: Boolean,
    val accessWarning: HomeAccessWarning?,
    val driveConnected: Boolean = true,
    val driveFull: Boolean = false,
    val allBackedUp: Boolean = false,
    val spaceLine: String? = null,
    val lastRunLine: String? = null,
    val spaceWarning: String? = null,
    val driveChipLabel: String = "Drive connected",
    val showProgress: Boolean = false,
    val longRunWarning: String? = null,
    /** What is waiting to be Backed up, e.g. "12 new photos and videos · 1.4 GB to back up". */
    val newLine: String? = null,
    /** Said while a run is going, so unplugging the drive never feels risky. */
    val unplugReassurance: String? = null,
    /** The one line under the headline: nothing left to do, or a stop that is normal. */
    val heroSubline: String? = null,
    /** Home's only instruction for a gesture, shown once there is nothing to start. */
    val pullHint: String? = null,
    /** What is waiting, e.g. "12 new photos and videos", for the state card. */
    val newTitle: String? = null,
    /** How big it is, e.g. "1.4 GB to back up". Null when the size is unknown. */
    val newDetail: String? = null,
    /** "12 min so far" and "about 20 min left", for the run card. */
    val paceElapsed: String? = null,
    val paceLeft: String? = null,
    /** The lines on the Backup history card: when it last ran, and the free space. */
    val historyLines: List<String> = emptyList(),
)

fun homeUiModel(
    state: BackupUiState,
    nowEpochMillis: Long,
    locale: Locale = Locale.getDefault(),
): HomeUiModel {
    val lastBackedUpLine = lastBackedUpLine(state.lastBackedUpAtEpochMillis, nowEpochMillis)
    return HomeUiModel(
        statusSentence = statusSentence(state, locale),
        primaryLabel = primaryLabel(state),
        primaryPauses = state.status == BackupStatus.Running,
        progressText = backupProgressText(state),
        progressFraction = progressFraction(state),
        lastBackedUpLine = lastBackedUpLine,
        offlineBanner = offlineBanner(state, lastBackedUpLine),
        showDriveMismatch = state.status == BackupStatus.DriveMismatch,
        accessWarning = homeAccessWarning(state.sessionAccess),
        driveConnected = state.driveStatus == DriveStatus.Ready,
        driveFull = state.status == BackupStatus.DriveFull,
        allBackedUp = allBackedUp(state),
        spaceLine = spaceLine(state),
        lastRunLine = lastRunLine(state),
        spaceWarning = spaceWarning(state),
        driveChipLabel = driveChipLabel(state.driveStatus),
        showProgress = state.status == BackupStatus.Running ||
            state.status is BackupStatus.Paused ||
            stopHeadline(state, locale) != null,
        longRunWarning = longRunWarning(state),
        newLine = newLine(state, locale),
        unplugReassurance = unplugReassurance(state),
        heroSubline = heroSubline(state, locale),
        pullHint = if (allBackedUp(state)) PULL_HINT else null,
        newTitle = newParts(state, locale)?.title,
        newDetail = newParts(state, locale)?.detail,
        paceElapsed = runPaceParts(state, nowEpochMillis)?.elapsed,
        paceLeft = runPaceParts(state, nowEpochMillis)?.left,
        historyLines = listOfNotNull(lastBackedUpLine, spaceLine(state)),
    )
}

/**
 * A backup that stopped early is normal progress, so it is Home's headline: why, and how many files are left.
 * Null when nothing was left behind or the stop has no wording of its own here (a full drive has its own status).
 */
private fun stopHeadline(state: BackupUiState, locale: Locale): String? {
    val reason = state.stopReason ?: return null
    if (state.filesLeft <= 0) return null
    val count = java.text.NumberFormat.getIntegerInstance(locale).format(state.filesLeft)
    val left = if (state.filesLeft == 1) "$count file left" else "$count files left"
    return when (reason) {
        StopReason.LowBattery -> "Paused at $LOW_BATTERY_PERCENT% battery. $left. Charge, then reconnect your drive."
        StopReason.SizeLimit -> "Size limit reached. $left."
        StopReason.TimeLimit -> "Time limit reached. $left."
        StopReason.DriveFull -> null
    }
}

internal data class NewParts(val title: String, val detail: String?)

/** Shown only when idle: while a run goes the progress text already says how much is left. */
internal fun newParts(state: BackupUiState, locale: Locale = Locale.getDefault()): NewParts? {
    val settled = state.status == BackupStatus.Idle || state.status == BackupStatus.Done
    if (!settled || state.driveStatus != DriveStatus.Ready) return null
    val count = state.filesToCopy ?: return null
    if (count <= 0) return null
    val videos = (state.videosToCopy ?: 0).coerceIn(0, count)
    val noun = when {
        videos == 0 -> if (count == 1) "photo" else "photos"
        videos == count -> if (count == 1) "video" else "videos"
        else -> "photos and videos"
    }
    val number = java.text.NumberFormat.getIntegerInstance(locale).format(count)
    return NewParts(
        title = "$number new $noun",
        detail = state.bytesToCopy?.let { "${formatBytes(it)} to back up" },
    )
}

internal fun newLine(state: BackupUiState, locale: Locale = Locale.getDefault()): String? {
    val parts = newParts(state, locale) ?: return null
    return parts.detail?.let { "${parts.title} · $it" } ?: "${parts.title} to back up"
}

internal const val UNPLUG_REASSURANCE =
    "You can unplug your drive any time. Backup carries on when you plug it back in."

private fun unplugReassurance(state: BackupUiState): String? {
    return if (state.status == BackupStatus.Running) UNPLUG_REASSURANCE else null
}

internal const val PULL_HINT = "Pull down to check for new photos"

private const val EVERYTHING_SAFE = "Everything on your phone is safe on your Archive Drive."
private const val NORMAL_STOP = "That is a normal stop. Back up more continues where it left off."

private fun heroSubline(state: BackupUiState, locale: Locale): String? {
    if (allBackedUp(state)) return EVERYTHING_SAFE
    val atALimit = stopHeadline(state, locale) != null &&
        (state.stopReason == StopReason.SizeLimit || state.stopReason == StopReason.TimeLimit)
    return if (atALimit) NORMAL_STOP else null
}

/** Everything in the Camera folder is Backed up, so there is nothing to start until a new photo appears. */
private fun allBackedUp(state: BackupUiState): Boolean {
    val settled = state.status == BackupStatus.Idle || state.status == BackupStatus.Done
    return settled && state.backedUpCount > 0 && state.filesToCopy == 0
}

internal fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    if (gb >= 1.0) return String.format(Locale.US, "%.1f GB", gb)
    return "${(bytes / 1_000_000L).coerceAtLeast(0)} MB"
}

private fun spaceLine(state: BackupUiState): String? {
    val free = state.driveFreeBytes ?: return null
    if (state.driveStatus != DriveStatus.Ready) return null
    return "${formatBytes(free)} free on your Archive Drive"
}

private fun spaceWarning(state: BackupUiState): String? {
    val free = state.driveFreeBytes ?: return null
    val toCopy = state.bytesToCopy ?: return null
    if (state.driveStatus != DriveStatus.Ready || toCopy <= free) return null
    return "${formatBytes(toCopy)} still to back up, but only ${formatBytes(free)} is free on this drive"
}

private fun driveChipLabel(status: DriveStatus): String {
    return when (status) {
        DriveStatus.Ready -> "Drive connected"
        DriveStatus.Absent -> "Drive not connected"
        DriveStatus.ReadOnly -> "Can't copy to this drive"
    }
}

fun homeAccessWarning(access: SessionAccess?): HomeAccessWarning? {
    access ?: return null
    val copied = when (access) {
        SessionAccess.PartialPhotos -> accessWarning(MediaAccessState.Partial)
        SessionAccess.DeniedPhotos -> accessWarning(MediaAccessState.Denied)
        SessionAccess.Location -> null
    }
    return HomeAccessWarning(
        title = copied?.title ?: "Photo locations are not visible",
        message = sessionAccessMessage(access),
        actionLabel = "Open Settings",
    )
}

private fun statusSentence(state: BackupUiState, locale: Locale): String {
    return when (val status = state.status) {
        BackupStatus.Idle,
        BackupStatus.Done,
        -> stopHeadline(state, locale) ?: backedUpSentence(state.backedUpCount)
        BackupStatus.Running ->
            if (state.runStage == RunStage.Checking) "Checking your backed up photos" else "Backing up your photos"
        is BackupStatus.Paused -> when (status.reason) {
            PauseReason.User -> "Backup paused"
            PauseReason.DriveOut ->
                if (state.driveStatus == DriveStatus.Ready) {
                    "Your Archive Drive is back"
                } else {
                    "Your Archive Drive is not connected"
                }
        }
        BackupStatus.DriveMismatch ->
            "This Archive Drive is not the one this phone remembers"
        BackupStatus.DriveFull -> "Your Archive Drive is full"
        BackupStatus.NeedsAttention -> "An Original needs attention"
    }
}

private fun backedUpSentence(count: Int): String {
    return when (count) {
        0 -> "No photos backed up yet"
        1 -> "1 photo backed up"
        else -> "$count photos backed up"
    }
}

private fun primaryLabel(state: BackupUiState): String {
    if (state.status == BackupStatus.Running) return "Pause"
    if (state.status == BackupStatus.DriveFull) return "Choose another drive"
    val more = state.status == BackupStatus.Done
    val status = state.status
    if (status is BackupStatus.Paused && status.reason == PauseReason.DriveOut && state.driveStatus == DriveStatus.Ready) {
        return "Continue backing up"
    }
    return if (more) "Back up more" else "Back up"
}

private fun progressFraction(state: BackupUiState): Float? {
    if (state.status == BackupStatus.Running && state.runStage == RunStage.Checking && state.stageTotal > 0) {
        return (state.stageDone.toFloat() / state.stageTotal.toFloat()).coerceIn(0f, 1f)
    }
    state.runFiles()?.let { return (it.done.toFloat() / it.total.toFloat()).coerceIn(0f, 1f) }
    if (state.totalCount <= 0) return null
    return (state.backedUpCount.toFloat() / state.totalCount.toFloat()).coerceIn(0f, 1f)
}

private fun offlineBanner(state: BackupUiState, lastBackedUpLine: String): HomeOfflineBanner? {
    val message = when (state.driveStatus) {
        DriveStatus.Ready -> return null
        DriveStatus.Absent -> "Your Archive Drive is not connected"
        DriveStatus.ReadOnly -> "Photoxpo cannot copy onto this Archive Drive"
    }
    return HomeOfflineBanner(
        message = message,
        lastBackedUpLine = lastBackedUpLine,
    )
}

private fun lastBackedUpLine(lastEpochMillis: Long?, nowEpochMillis: Long): String {
    return "Last backed up ${relativeTime(lastEpochMillis, nowEpochMillis)}"
}

private fun relativeTime(lastEpochMillis: Long?, nowEpochMillis: Long): String {
    if (lastEpochMillis == null) return "never"
    val elapsed = nowEpochMillis - lastEpochMillis
    if (elapsed < MINUTE_MS) return "just now"
    val minutes = elapsed / MINUTE_MS
    if (minutes < 60) {
        return if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
    }
    val hours = elapsed / HOUR_MS
    if (hours < 24) {
        return if (hours == 1L) "1 hour ago" else "$hours hours ago"
    }
    val days = elapsed / DAY_MS
    if (days == 1L) return "yesterday"
    return "$days days ago"
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60L * MINUTE_MS
private const val DAY_MS = 24L * HOUR_MS
