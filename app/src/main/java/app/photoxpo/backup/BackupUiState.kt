package app.photoxpo.backup

import app.photoxpo.core.BackupRunResult
import app.photoxpo.core.BackupRunStatus
import app.photoxpo.core.ItemState
import app.photoxpo.data.DriveStatus
import app.photoxpo.onboarding.MediaAccessState
import app.photoxpo.onboarding.accessWarning
import app.photoxpo.onboarding.classifyMediaAccess

const val DEFAULT_FILE_CAP = 200

const val MESSAGE_DRIVE_OUT = "Archive Drive disconnected."

const val MESSAGE_PAUSED_USER = "Backup run paused."

const val MESSAGE_DRIVE_MISMATCH = "This Archive Drive is not the one this phone remembers."

const val MESSAGE_NEEDS_ATTENTION = "An Original needs attention."
const val MESSAGE_DRIVE_FULL = "Your Archive Drive is full."

const val MESSAGE_CHOOSE_DRIVE = "Choose an Archive Drive first."

const val MESSAGE_CONNECTED_DEVICE_FALLBACK =
    "Backing up your photos. Keep your Archive Drive plugged in."

enum class PauseReason {
    DriveOut,
    User,
}

/** Why this session cannot start a Backup run. */
enum class SessionAccess {
    PartialPhotos,
    DeniedPhotos,
    Location,
}

/**
 * Full photo and video access, then photo location access.
 * [imagesGranted], [videosGranted], and [selectedGranted] only choose the warning
 * when full access is already false.
 */
fun sessionAccessBlock(
    hasFullMediaAccess: Boolean,
    hasAccessMediaLocation: Boolean,
    imagesGranted: Boolean = false,
    videosGranted: Boolean = false,
    selectedGranted: Boolean = false,
): SessionAccess? {
    if (!hasFullMediaAccess) {
        val photos = classifyMediaAccess(
            fullAccess = false,
            imagesGranted = imagesGranted,
            videosGranted = videosGranted,
            selectedGranted = selectedGranted,
        )
        return if (photos == MediaAccessState.Denied) {
            SessionAccess.DeniedPhotos
        } else {
            SessionAccess.PartialPhotos
        }
    }
    if (!hasAccessMediaLocation) return SessionAccess.Location
    return null
}

fun sessionAccessMessage(access: SessionAccess): String {
    return when (access) {
        SessionAccess.PartialPhotos ->
            checkNotNull(accessWarning(MediaAccessState.Partial)).message
        SessionAccess.DeniedPhotos ->
            checkNotNull(accessWarning(MediaAccessState.Denied)).message
        SessionAccess.Location ->
            "Photoxpo cannot see where your photos were taken, so the Original on the Archive Drive would not include it."
    }
}

sealed interface BackupStatus {
    data object Idle : BackupStatus

    data object Running : BackupStatus

    data class Paused(val reason: PauseReason) : BackupStatus

    data object Done : BackupStatus

    data object DriveMismatch : BackupStatus

    data object DriveFull : BackupStatus

    data object NeedsAttention : BackupStatus
}

data class BackupUiState(
    val status: BackupStatus = BackupStatus.Idle,
    val backedUpCount: Int = 0,
    val totalCount: Int = 0,
    val lastBackedUpAtEpochMillis: Long? = null,
    val driveStatus: DriveStatus = DriveStatus.Absent,
    val fileCap: Int = DEFAULT_FILE_CAP,
    val message: String? = null,
    val sessionAccess: SessionAccess? = null,
    val driveFreeBytes: Long? = null,
    val bytesToCopy: Long? = null,
    /** When this run began, and what it had to copy then, so Home can say how long and how much is left. */
    val runStartedAtMillis: Long? = null,
    val runBytesToCopy: Long = 0L,
    val runBytesCopied: Long = 0L,
    val firstCopyAtMillis: Long? = null,
    val firstCopyBytes: Long = 0L,
    val lastRunTookMillis: Long? = null,
)

fun BackupUiState.runStarted(
    totalCount: Int,
    fileCap: Int,
    driveStatus: DriveStatus,
    bytesToCopy: Long = 0L,
    nowEpochMillis: Long = System.currentTimeMillis(),
): BackupUiState {
    val kept = if (message == MESSAGE_CONNECTED_DEVICE_FALLBACK) message else null
    return copy(
        status = BackupStatus.Running,
        backedUpCount = 0,
        totalCount = totalCount,
        fileCap = fileCap,
        driveStatus = driveStatus,
        message = kept,
        runStartedAtMillis = nowEpochMillis,
        runBytesToCopy = bytesToCopy,
        runBytesCopied = 0L,
        firstCopyAtMillis = null,
        firstCopyBytes = 0L,
    )
}

/** One more Original was newly copied during this run. */
fun BackupUiState.withCopied(sizeBytes: Long, nowEpochMillis: Long): BackupUiState {
    if (status !is BackupStatus.Running) return this
    val first = firstCopyAtMillis == null
    return copy(
        runBytesCopied = runBytesCopied + sizeBytes,
        firstCopyAtMillis = firstCopyAtMillis ?: nowEpochMillis,
        firstCopyBytes = if (first) sizeBytes else firstCopyBytes,
    )
}

fun BackupUiState.withBackedUpCount(backedUpCount: Int): BackupUiState {
    if (status !is BackupStatus.Running) return this
    return copy(backedUpCount = backedUpCount)
}

fun backupProgressText(state: BackupUiState): String {
    if (state.totalCount > 0) {
        return "Backed up ${state.backedUpCount} of ${state.totalCount}"
    }
    return "Backed up ${state.backedUpCount}"
}

fun reduceBackupRun(
    state: BackupUiState,
    result: BackupRunResult,
    nowEpochMillis: Long,
    pauseReason: PauseReason = PauseReason.User,
): BackupUiState {
    if (result.status == BackupRunStatus.DriveMismatch) {
        return state.copy(
            status = BackupStatus.DriveMismatch,
            message = MESSAGE_DRIVE_MISMATCH,
        )
    }
    val committed = result.items.count { it.state == ItemState.COMMITTED }
    val needsAttention = result.items.any { it.state == ItemState.NEEDS_ATTENTION }
    val status = when (result.status) {
        BackupRunStatus.Paused -> BackupStatus.Paused(pauseReason)
        BackupRunStatus.Completed ->
            if (needsAttention) BackupStatus.NeedsAttention else BackupStatus.Done
        BackupRunStatus.DriveMismatch -> BackupStatus.DriveMismatch
    }
    val message = when {
        status is BackupStatus.Paused && pauseReason == PauseReason.DriveOut -> MESSAGE_DRIVE_OUT
        status is BackupStatus.Paused -> MESSAGE_PAUSED_USER
        status is BackupStatus.NeedsAttention -> MESSAGE_NEEDS_ATTENTION
        status is BackupStatus.DriveMismatch -> MESSAGE_DRIVE_MISMATCH
        else -> null
    }
    val total = if (result.items.isEmpty()) {
        state.totalCount
    } else {
        maxOf(state.totalCount, result.items.size)
    }
    return state.copy(
        status = status,
        backedUpCount = committed,
        totalCount = total,
        lastBackedUpAtEpochMillis = if (committed > 0) nowEpochMillis else state.lastBackedUpAtEpochMillis,
        message = message,
    )
}
