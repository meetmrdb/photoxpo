package app.photoxpo.backup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.photoxpo.core.BackupRunResult
import app.photoxpo.core.BackupRunStatus
import app.photoxpo.core.BackupRunner
import app.photoxpo.core.BackedUpLedger
import app.photoxpo.core.ItemState
import app.photoxpo.PhotoxpoApp
import app.photoxpo.data.DriveStatus
import app.photoxpo.data.MediaStoreSource
import app.photoxpo.data.SafArchiveDrive
import app.photoxpo.data.hasAccessMediaLocation
import app.photoxpo.data.hasFullMediaAccess
import app.photoxpo.data.isPersistedTreeGrantHeld
import app.photoxpo.data.probeArchiveDrive
import app.photoxpo.history.BackupHistoryRepository
import app.photoxpo.photos.PhotosDatabase
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
    private val libraryRefreshing = AtomicBoolean(false)
    private val pauseReason = AtomicReference(PauseReason.User)
    private val wakeLockGate = Any()
    private var wakeLock: PowerManager.WakeLock? = null
    private var runJob: Job? = null

    private val _state = MutableStateFlow(
        BackupUiState(
            // The phone's cache of Backed up Originals survives a restart; the drive stays the truth.
            backedUpCount = settings.ledgerEntries().size,
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
        val blocked = currentSessionAccess()
        if (blocked != null) {
            refuseSessionAccess(blocked, cap)
            return
        }
        val uri = settings.treeUri()
        if (uri == null || !isPersistedTreeGrantHeld(appContext.contentResolver, uri)) {
            _state.update {
                it.copy(
                    status = BackupStatus.Idle,
                    fileCap = cap,
                    driveStatus = DriveStatus.Absent,
                    message = MESSAGE_CHOOSE_DRIVE,
                    sessionAccess = null,
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
                    sessionAccess = null,
                )
            }
            return
        }
        _state.update {
            it.runStarted(totalCount = it.totalCount, fileCap = cap, driveStatus = drive)
                .copy(sessionAccess = null, backedUpCount = it.backedUpCount)
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
            val blocked = currentSessionAccess()
            if (blocked != null) {
                refuseSessionAccess(blocked, settings.fileCap())
                stopService()
                return
            }
            val uri = settings.treeUri() ?: return
            cancel.set(false)
            val cap = settings.fileCap()
            runJob = scope.launch {
                executeRun(uri, cap)
            }
        }
    }

    /** Probes the chosen Archive Drive now, for example after the person picks a folder. */
    fun refreshDrive() = onVolumesChanged()

    /**
     * The person confirmed "Use this drive instead". The drive wins: every photo is not
     * Backed up and its Original is not on this Archive Drive. Previews stay. The phone's
     * Backup history cache is cleared, Home's count goes back to 0, and a new drive id starts.
     */
    fun useThisDriveInstead() {
        scope.launch {
            val uri = settings.treeUri() ?: return@launch
            withContext(Dispatchers.IO) {
                BackupRunner(
                    mediaSource = MediaStoreSource(appContext),
                    archiveDrive = SafArchiveDrive(appContext, uri),
                    driveIdentity = settings,
                    backedUpLedger = progressLedger(AtomicInteger(0)),
                    previewPreset = settings.previewPreset(),
                ).useThisDriveInstead()
                (appContext as PhotoxpoApp).services.photosRepository.markOriginalsNotOnThisDrive()
                BackupHistoryRepository(
                    dao = PhotosDatabase.get(appContext).backupRunDao(),
                    settings = settings,
                ).clear()
            }
            _state.update { it.copy(status = BackupStatus.Idle, backedUpCount = 0, message = null) }
            onVolumesChanged()
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
            if (probed == DriveStatus.Ready) refreshLibraryWhenIdle()
        }
    }

    /**
     * The Photos tab fills from the Archive Drive's Manifests, which are only read after a Backup run.
     * A run cut short by a pulled cable never got there, so read them again once the drive is back.
     */
    private fun refreshLibraryWhenIdle() {
        if (runJob?.isActive == true || _state.value.status == BackupStatus.Running) return
        if (!libraryRefreshing.compareAndSet(false, true)) return
        scope.launch {
            try {
                runPostRunWork()
            } finally {
                libraryRefreshing.set(false)
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
        val alreadyBackedUp = backedUpOnThisPhone()
        val recorded = AtomicInteger(alreadyBackedUp)
        _state.update {
            it.runStarted(totalCount = total, fileCap = cap, driveStatus = it.driveStatus)
                .copy(backedUpCount = alreadyBackedUp)
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
                        previewPreset = settings.previewPreset(),
                        isCancelled = { cancel.get() },
                    ).start()
                    RunOutcome(result, probeOrAbsent(uri))
                } finally {
                    releaseWakeLock()
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // A cable pulled in the middle of a copy can surface as a plain I/O error, not as a
            // missing drive. Look at the drive before calling it a problem with an Original.
            val drive = withContext(Dispatchers.IO) { probeOrAbsent(uri) }
            if (drive != DriveStatus.Ready && !userStop.get()) {
                val onPhone = backedUpOnThisPhone()
                _state.update {
                    it.copy(
                        status = BackupStatus.Paused(PauseReason.DriveOut),
                        driveStatus = drive,
                        backedUpCount = onPhone,
                        message = MESSAGE_DRIVE_OUT,
                    )
                }
                stopService()
                return
            }
            _state.update {
                it.copy(
                    status = BackupStatus.NeedsAttention,
                    driveStatus = drive,
                    message = error.message ?: MESSAGE_NEEDS_ATTENTION,
                )
            }
            stopService()
            return
        }
        val reason = pauseReasonFor(outcome)
        val onPhone = backedUpOnThisPhone()
        _state.update {
            reduceBackupRun(
                state = it,
                result = outcome.result,
                nowEpochMillis = System.currentTimeMillis(),
                pauseReason = reason,
            ).copy(driveStatus = outcome.driveStatus, backedUpCount = onPhone)
        }
        persistLastBackedUp(outcome.result)
        runPostRunWork()
        val status = _state.value.status
        if (status is BackupStatus.Paused && status.reason == PauseReason.DriveOut) {
            if (outcome.driveStatus == DriveStatus.Ready && !userStop.get()) {
                scheduleResume()
            }
            return
        }
        stopService()
    }

    private suspend fun runPostRunWork() {
        try {
            withContext(Dispatchers.IO) { PostRunWork(appContext, settings).run() }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Previews are retried on the next run. Backed up never depends on them.
        }
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

    /** Backed up photos that are still in the Camera folder, so the count never passes the total. */
    // MediaStore can stall for seconds while a pulled drive is torn down, so never ask it from the main thread.
    private suspend fun backedUpOnThisPhone(): Int = withContext(Dispatchers.IO) {
        val ledger = settings.ledgerEntries()
        try {
            val present = MediaStoreSource(appContext).listCameraFolder().map { it.id }.toSet()
            ledger.keys.count { it in present }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            ledger.size
        }
    }

    private fun progressLedger(recorded: AtomicInteger): BackedUpLedger {
        return object : BackedUpLedger {
            override fun record(mediaId: String, hash: String) {
                // A run walks every photo again and re-confirms the old ones. Only a new one adds to the count.
                val isNew = !settings.hasRecorded(mediaId)
                settings.record(mediaId, hash)
                if (isNew) _state.update { it.withBackedUpCount(recorded.incrementAndGet()) }
            }

            override fun clear() {
                settings.clear()
                recorded.set(0)
            }
        }
    }

    private fun currentSessionAccess(): SessionAccess? {
        return sessionAccessBlock(
            hasFullMediaAccess = hasFullMediaAccess(appContext),
            hasAccessMediaLocation = hasAccessMediaLocation(appContext),
            imagesGranted = isGranted(Manifest.permission.READ_MEDIA_IMAGES),
            videosGranted = isGranted(Manifest.permission.READ_MEDIA_VIDEO),
            selectedGranted = isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
        )
    }

    private fun isGranted(permission: String): Boolean {
        return appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun refuseSessionAccess(access: SessionAccess, fileCap: Int) {
        _state.update {
            val status = when (it.status) {
                BackupStatus.NeedsAttention -> BackupStatus.NeedsAttention
                else -> BackupStatus.Idle
            }
            it.copy(
                status = status,
                fileCap = fileCap,
                sessionAccess = access,
                message = sessionAccessMessage(access),
            )
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
