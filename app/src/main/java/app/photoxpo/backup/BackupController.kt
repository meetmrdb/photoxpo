package app.photoxpo.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.photoxpo.core.BackupRunResult
import app.photoxpo.core.BackupRunStatus
import app.photoxpo.core.BackupRunner
import app.photoxpo.core.BackedUpLedger
import app.photoxpo.core.ItemState
import app.photoxpo.data.DriveStatus
import app.photoxpo.data.MediaStoreSource
import app.photoxpo.data.SafArchiveDrive
import app.photoxpo.data.isPersistedTreeGrantHeld
import app.photoxpo.data.probeArchiveDrive
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs a Backup run and publishes [BackupUiState].
 * [start] is the visible tap. [pause] stops between Originals.
 */
class BackupController private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val settings = BackupSettingsStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val runGate = Any()
    private val cancel = AtomicBoolean(false)
    private val userStop = AtomicBoolean(false)
    private val pauseReason = AtomicReference(PauseReason.User)
    private val wakeLockGate = Any()
    private var wakeLock: PowerManager.WakeLock? = null
    private var runJob: Job? = null

    private val _state = MutableStateFlow(
        BackupUiState(
            lastBackedUpAtEpochMillis = settings.lastBackedUpAtEpochMillis(),
            fileCap = settings.fileCap(),
        ),
    )
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun start(fileCap: Int = settings.fileCap()) {
        val cap = if (fileCap < 1) DEFAULT_FILE_CAP else fileCap
        settings.setFileCap(cap)
        if (runJob?.isActive == true) {
            _state.update { it.copy(fileCap = cap) }
            return
        }
        userStop.set(false)
        cancel.set(false)
        pauseReason.set(PauseReason.User)
        val uri = settings.treeUri()
        if (uri == null || !isPersistedTreeGrantHeld(appContext.contentResolver, uri)) {
            _state.update {
                it.copy(
                    status = BackupStatus.Idle,
                    fileCap = cap,
                    driveStatus = DriveStatus.Absent,
                    message = MESSAGE_CHOOSE_DRIVE,
                )
            }
            return
        }
        val drive = probeOrAbsent(uri)
        if (drive != DriveStatus.Ready) {
            _state.update {
                it.copy(
                    status = BackupStatus.Paused(PauseReason.DriveOut),
                    driveStatus = drive,
                    fileCap = cap,
                    message = MESSAGE_DRIVE_OUT,
                )
            }
            return
        }
        _state.update {
            it.runStarted(totalCount = it.totalCount, fileCap = cap, driveStatus = drive).copy(message = null)
        }
        try {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, BackupForegroundService::class.java)
                    .setAction(BackupForegroundService.ACTION_START),
            )
        } catch (error: SecurityException) {
            noteStartFailed(error)
        } catch (error: android.app.ForegroundServiceStartNotAllowedException) {
            noteStartFailed(error)
        }
    }

    fun pause() {
        userStop.set(true)
        pauseReason.set(PauseReason.User)
        cancel.set(true)
        releaseWakeLock()
        _state.update {
            it.copy(
                status = BackupStatus.Paused(PauseReason.User),
                message = MESSAGE_PAUSED_USER,
            )
        }
        if (runJob?.isActive != true) {
            stopService()
        }
    }

    internal fun beginRun() {
        synchronized(runGate) {
            if (userStop.get()) return
            if (runJob?.isActive == true) return
            val uri = settings.treeUri() ?: return
            cancel.set(false)
            val cap = settings.fileCap()
            runJob = scope.launch {
                executeRun(uri, cap)
            }
        }
    }

    internal fun onVolumesChanged() {
        scope.launch {
            val uri = settings.treeUri() ?: return@launch
            val probed = withContext(Dispatchers.IO) { probeOrAbsent(uri) }
            _state.update { it.copy(driveStatus = probed) }
            val status = _state.value.status
            when {
                probed != DriveStatus.Ready &&
                    (status is BackupStatus.Running || runJob?.isActive == true) &&
                    !userStop.get() -> pauseForDrive(probed)
                probed == DriveStatus.Ready &&
                    status is BackupStatus.Paused &&
                    status.reason == PauseReason.DriveOut &&
                    !userStop.get() -> scheduleResume()
            }
        }
    }

    internal fun noteConnectedDeviceFallback() {
        _state.update { it.copy(message = MESSAGE_CONNECTED_DEVICE_FALLBACK) }
    }

    private suspend fun executeRun(uri: Uri, cap: Int) {
        if (shouldAbandon()) {
            finishAbandoned()
            return
        }
        val total = withContext(Dispatchers.IO) {
            try {
                MediaStoreSource(appContext).listCameraFolder().size
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.value.totalCount
            }
        }
        if (shouldAbandon()) {
            finishAbandoned()
            return
        }
        val recorded = AtomicInteger(0)
        _state.update {
            it.runStarted(totalCount = total, fileCap = cap, driveStatus = it.driveStatus)
        }
        val outcome = try {
            withContext(Dispatchers.IO) {
                acquireWakeLock()
                try {
                    val result = BackupRunner(
                        mediaSource = MediaStoreSource(appContext),
                        archiveDrive = SafArchiveDrive(appContext, uri),
                        driveIdentity = settings,
                        backedUpLedger = progressLedger(recorded),
                        fileCap = cap,
                        isCancelled = { cancel.get() },
                    ).start()
                    RunOutcome(result, probeOrAbsent(uri))
                } finally {
                    releaseWakeLock()
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _state.update {
                it.copy(
                    status = BackupStatus.NeedsAttention,
                    message = error.message ?: MESSAGE_NEEDS_ATTENTION,
                )
            }
            stopService()
            return
        }
        val reason = pauseReasonFor(outcome)
        _state.update {
            reduceBackupRun(
                state = it,
                result = outcome.result,
                nowEpochMillis = System.currentTimeMillis(),
                pauseReason = reason,
            ).copy(driveStatus = outcome.driveStatus)
        }
        persistLastBackedUp(outcome.result)
        val status = _state.value.status
        if (status is BackupStatus.Paused && status.reason == PauseReason.DriveOut) {
            if (outcome.driveStatus == DriveStatus.Ready && !userStop.get()) {
                scheduleResume()
            }
            return
        }
        stopService()
    }

    private fun shouldAbandon(): Boolean = userStop.get() || cancel.get()

    private fun finishAbandoned() {
        if (!userStop.get() && pauseReason.get() == PauseReason.DriveOut) {
            _state.update {
                it.copy(
                    status = BackupStatus.Paused(PauseReason.DriveOut),
                    message = MESSAGE_DRIVE_OUT,
                )
            }
            return
        }
        _state.update {
            it.copy(
                status = BackupStatus.Paused(PauseReason.User),
                message = MESSAGE_PAUSED_USER,
            )
        }
        stopService()
    }

    private fun pauseReasonFor(outcome: RunOutcome): PauseReason {
        if (outcome.result.status != BackupRunStatus.Paused) return PauseReason.User
        if (userStop.get()) return PauseReason.User
        if (pauseReason.get() == PauseReason.DriveOut) return PauseReason.DriveOut
        if (outcome.driveStatus != DriveStatus.Ready) return PauseReason.DriveOut
        return PauseReason.User
    }

    private fun pauseForDrive(driveStatus: DriveStatus) {
        if (userStop.get()) return
        pauseReason.set(PauseReason.DriveOut)
        cancel.set(true)
        releaseWakeLock()
        _state.update {
            it.copy(
                status = BackupStatus.Paused(PauseReason.DriveOut),
                driveStatus = driveStatus,
                message = MESSAGE_DRIVE_OUT,
            )
        }
    }

    private fun scheduleResume() {
        scope.launch {
            runJob?.join()
            val status = _state.value.status
            if (userStop.get()) return@launch
            if (status is BackupStatus.Paused &&
                status.reason == PauseReason.DriveOut &&
                _state.value.driveStatus == DriveStatus.Ready
            ) {
                beginRun()
            }
        }
    }

    private fun persistLastBackedUp(result: BackupRunResult) {
        if (result.items.none { it.state == ItemState.COMMITTED }) return
        val backedUpAt = _state.value.lastBackedUpAtEpochMillis ?: return
        settings.setLastBackedUpAtEpochMillis(backedUpAt)
    }

    private fun progressLedger(recorded: AtomicInteger): BackedUpLedger {
        return object : BackedUpLedger {
            override fun record(mediaId: String, hash: String) {
                settings.record(mediaId, hash)
                _state.update { it.withBackedUpCount(recorded.incrementAndGet()) }
            }

            override fun clear() {
                settings.clear()
                recorded.set(0)
            }
        }
    }

    private fun probeOrAbsent(uri: Uri): DriveStatus {
        return try {
            probeArchiveDrive(appContext, uri)
        } catch (_: Exception) {
            DriveStatus.Absent
        }
    }

    private fun acquireWakeLock() {
        val power = appContext.getSystemService(PowerManager::class.java) ?: return
        synchronized(wakeLockGate) {
            val existing = wakeLock
            if (existing != null && existing.isHeld) return
            val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            lock.setReferenceCounted(false)
            lock.acquire(WAKE_LOCK_LIMIT_MS)
            wakeLock = lock
        }
    }

    private fun releaseWakeLock() {
        synchronized(wakeLockGate) {
            val lock = wakeLock ?: return
            if (lock.isHeld) lock.release()
            wakeLock = null
        }
    }

    private fun noteStartFailed(error: Exception) {
        _state.update {
            it.copy(
                status = BackupStatus.NeedsAttention,
                message = error.message ?: MESSAGE_NEEDS_ATTENTION,
            )
        }
    }

    private fun stopService() {
        appContext.stopService(Intent(appContext, BackupForegroundService::class.java))
    }

    private data class RunOutcome(
        val result: BackupRunResult,
        val driveStatus: DriveStatus,
    )

    companion object {
        private const val WAKE_LOCK_TAG = "app.photoxpo:backup-run"
        private const val WAKE_LOCK_LIMIT_MS = 8L * 60L * 60L * 1000L

        @Volatile
        private var instance: BackupController? = null

        fun get(context: Context): BackupController {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: BackupController(context.applicationContext).also { instance = it }
            }
        }
    }
}
