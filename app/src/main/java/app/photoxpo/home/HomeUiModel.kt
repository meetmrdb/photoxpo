package app.photoxpo.home

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import app.photoxpo.backup.BackupStatus
import app.photoxpo.backup.BackupUiState
import app.photoxpo.backup.PauseReason
import app.photoxpo.backup.SessionAccess
import app.photoxpo.backup.backupProgressText
import app.photoxpo.backup.sessionAccessMessage
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
    val spaceLine: String? = null,
    val spaceWarning: String? = null,
    val driveChipLabel: String = "Drive connected",
    val showProgress: Boolean = false,
    val lastBackedUpDate: String? = null,
)

fun homeUiModel(
    state: BackupUiState,
    nowEpochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): HomeUiModel {
    val lastBackedUpLine = lastBackedUpLine(state.lastBackedUpAtEpochMillis, nowEpochMillis)
    return HomeUiModel(
        statusSentence = statusSentence(state),
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
        spaceLine = spaceLine(state),
        spaceWarning = spaceWarning(state),
        driveChipLabel = driveChipLabel(state.driveStatus),
        showProgress = state.status == BackupStatus.Running || state.status is BackupStatus.Paused,
        lastBackedUpDate = lastBackedUpDate(state.lastBackedUpAtEpochMillis, zone, locale),
    )
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

private fun lastBackedUpDate(epochMillis: Long?, zone: ZoneId, locale: Locale): String? {
    epochMillis ?: return null
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", locale)
    return formatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
        .replace("AM", "am")
        .replace("PM", "pm")
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

private fun statusSentence(state: BackupUiState): String {
    return when (val status = state.status) {
        BackupStatus.Idle,
        BackupStatus.Done,
        -> backedUpSentence(state.backedUpCount)
        BackupStatus.Running -> "Backing up your photos"
        is BackupStatus.Paused -> when (status.reason) {
            PauseReason.User -> "Backup run paused"
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
    return startLabel(state.fileCap, more)
}

private fun startLabel(fileCap: Int, more: Boolean): String {
    val files = if (fileCap == 1) "file" else "files"
    return if (more) {
        "Back up $fileCap more $files"
    } else {
        "Back up $fileCap $files"
    }
}

private fun progressFraction(state: BackupUiState): Float? {
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
