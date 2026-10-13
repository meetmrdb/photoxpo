package app.photoxpo.backup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.ContextCompat
import app.photoxpo.core.BackupRunResult
import app.photoxpo.core.BackupRunStatus
import app.photoxpo.core.BackupRunner
import app.photoxpo.core.DriveFullException
import app.photoxpo.core.BackedUpLedger
import app.photoxpo.core.MediaFile
import app.photoxpo.core.MediaKind
import app.photoxpo.core.ItemState
import app.photoxpo.core.RecentRun
import app.photoxpo.core.StopReason
import app.photoxpo.core.byBackup
import java.time.Duration
import app.photoxpo.PhotoxpoApp
import app.photoxpo.data.DriveStatus
import app.photoxpo.data.MediaStoreSource
import app.photoxpo.data.SafArchiveDrive
import app.photoxpo.data.hasAccessMediaLocation
import app.photoxpo.data.hasFullMediaAccess
import app.photoxpo.data.isPersistedTreeGrantHeld
import app.photoxpo.data.archiveDriveFreeBytes
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
    private val timeLimitStop = AtomicBoolean(false)
    private val libraryRefreshing = AtomicBoolean(false)
    private val pauseReason = AtomicReference(PauseReason.User)
    private val wakeLockGate = Any()
    private var wakeLock: PowerManager.WakeLock? = null
    private var runJob: Job? = null

    // A run the system cut off (a pulled cable gets the app killed) is a pause for the drive, not a fresh start.
    private val cutOff = settings.runWasCutOff()

    private val _state = MutableStateFlow(
        BackupUiState(
            status = if (cutOff) BackupStatus.Paused(PauseReason.DriveOut) else BackupStatus.Idle,
            message = if (cutOff) MESSAGE_DRIVE_OUT else null,
            // The phone's cache of Backed up Originals survives a restart; the drive stays the truth.
            backedUpCount = settings.ledgerEntries().size,
            lastBackedUpAtEpochMillis = settings.lastBackedUpAtEpochMillis(),
        ),
    )
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    init {
        // The foreground service only watches the drive during a run. Home must notice a cable
        // going in or out while the app is simply open.
        appContext.getSystemService(StorageManager::class.java)?.registerStorageVolumeCallback(
            appContext.mainExecutor,
            object : StorageManager.StorageVolumeCallback() {
                override fun onStateChanged(volume: StorageVolume) {
                    onVolumesChanged()
                }
            },
        )
    }

    fun start() {
        if (runJob?.isActive == true) return
        userStop.set(false)
        timeLimitStop.set(false)
        cancel.set(false)
        pauseReason.set(PauseReason.User)
        val blocked = currentSessionAccess()
        if (blocked != null) {
            refuseSessionAccess(blocked)
            return
        }
        val uri = settings.treeUri()
        if (uri == null || !isPersistedTreeGrantHeld(appContext.contentResolver, uri)) {
            _state.update {
                it.copy(
                    status = BackupStatus.Idle,
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
                    message = MESSAGE_DRIVE_OUT,
                    sessionAccess = null,
                )
            }
            return
        }
        _state.update {
            it.runStarted(totalCount = it.totalCount, driveStatus = drive)
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

    /**
     * Android's time limit on the foreground service: ask the run to end cleanly. The run finishes the
     * Original it is copying, then stops with [StopReason.TimeLimit] and the files left, like any other stop.
     */
    internal fun requestTimeLimitStop() {
        timeLimitStop.set(true)
    }

    /**
     * Waits up to [timeoutMs] for the copying to stop after [requestTimeLimitStop]. If the current Original
     * is not finished in time, pauses between Originals instead, so the service can always stop promptly.
     * Returns true when the run ended cleanly as a time-limit stop.
     */
    internal suspend fun awaitTimeLimitStop(timeoutMs: Long): Boolean {
        val ended = withTimeoutOrNull(timeoutMs) {
            _state.first { it.status != BackupStatus.Running }
            true
        } ?: false
        if (!ended) pause()
        return ended
    }

    internal fun beginRun() {
        synchronized(runGate) {
            if (userStop.get()) return
            if (runJob?.isActive == true) return
            val blocked = currentSessionAccess()
            if (blocked != null) {
                refuseSessionAccess(blocked)
                stopService()
                return
            }
            val uri = settings.treeUri() ?: return
            cancel.set(false)
            timeLimitStop.set(false)
            runJob = scope.launch {
                settings.setRunActive(true)
                // Free space on the drive shifts as files land; keep Home's number fresh during a long run.
                val spaceTicker = launch {
                    refreshSpace()
                    while (true) {
                        delay(SPACE_REFRESH_MS)
                        refreshSpace()
                    }
                }
                try {
                    executeRun(uri)
                } finally {
                    spaceTicker.cancel()
                    settings.setRunActive(false)
                }
                refreshSpace()
            }
        }
    }

    /** Probes the chosen Archive Drive now, for example after the person picks a folder. */
    fun refreshDrive(): Job = onVolumesChanged()

    /** Pull-to-refresh: probe the drive, recount what is left to copy, and re-read the drive's Manifests. */
    suspend fun refreshNow() {
        onVolumesChanged().join()
    }

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

    internal fun onVolumesChanged(): Job {
        return scope.launch {
            val uri = settings.treeUri() ?: return@launch
            val probed = withContext(Dispatchers.IO) { probeOrAbsent(uri) }
            _state.update { it.copy(driveStatus = probed) }
            val status = _state.value.status
            when {
                probed != DriveStatus.Ready &&
                    (status is BackupStatus.Running || runJob?.isActive == true) &&
                    !userStop.get() -> pauseForDrive(probed)
                // The copy failed first and the system only now says the drive is gone.
                probed != DriveStatus.Ready &&
                    status == BackupStatus.NeedsAttention &&
                    !userStop.get() -> _state.update {
                    it.copy(status = BackupStatus.Paused(PauseReason.DriveOut), message = MESSAGE_DRIVE_OUT)
                }
                probed == DriveStatus.Ready &&
                    status is BackupStatus.Paused &&
                    status.reason == PauseReason.DriveOut &&
                    !userStop.get() -> scheduleResume()
            }
            if (probed == DriveStatus.Ready) {
                refreshSpace()
                refreshLibraryWhenIdle()?.join()
            }
        }
    }

    /**
     * The Photos tab fills from the Archive Drive's Manifests, which are only read after a Backup run.
     * A run cut short by a pulled cable never got there, so read them again once the drive is back.
     */
    private fun refreshLibraryWhenIdle(): Job? {
        if (runJob?.isActive == true || _state.value.status == BackupStatus.Running) return null
        if (!libraryRefreshing.compareAndSet(false, true)) return null
        return scope.launch {
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

    private suspend fun executeRun(uri: Uri) {
        if (shouldAbandon()) {
            finishAbandoned()
            return
        }
        val camera = withContext(Dispatchers.IO) {
            try {
                MediaStoreSource(appContext).listCameraFolder()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                emptyList()
            }
        }
        val total = if (camera.isEmpty()) _state.value.totalCount else camera.size
        val sizeById = camera.associate { it.id to it.sizeBytes }
        val ledgerKeys = settings.ledgerEntries().keys
        val waiting = camera.filter { it.id !in ledgerKeys }
        val bytesToCopy = waiting.sumOf { it.sizeBytes }
        if (shouldAbandon()) {
            finishAbandoned()
            return
        }
        val alreadyBackedUp = backedUpOnThisPhone()
        val recorded = AtomicInteger(alreadyBackedUp)
        _state.update {
            it.runStarted(
                totalCount = total,
                driveStatus = it.driveStatus,
                bytesToCopy = bytesToCopy,
                filesToCopy = waiting.size,
            ).copy(backedUpCount = alreadyBackedUp)
        }
        val outcome = try {
            withContext(Dispatchers.IO) {
                acquireWakeLock()
                try {
                    val result = BackupRunner(
                        mediaSource = MediaStoreSource(appContext),
                        archiveDrive = SafArchiveDrive(appContext, uri),
                        driveIdentity = settings,
                        backedUpLedger = progressLedger(recorded, sizeById),
                        previewPreset = settings.previewPreset(),
                        isCancelled = { cancel.get() },
                        externalStop = { if (timeLimitStop.get()) StopReason.TimeLimit else null },
                        batteryInput = AndroidBatteryInput(appContext),
                        onStep = { step ->
                            _state.update { it.copy(runStage = step.stage, stageDone = step.done, stageTotal = step.of) }
                        },
                    ).start()
                    RunOutcome(result, probeOrAbsent(uri))
                } finally {
                    releaseWakeLock()
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // A cable pulled in the middle of a copy can surface as a plain I/O error, and the system
            // may take a few seconds to say the drive is gone. Give it that time before calling this
            // a problem with an Original.
            val drive = if (error is DriveFullException) {
                DriveStatus.Ready
            } else {
                withContext(Dispatchers.IO) { probeUntilGone(uri) }
            }
            val onPhone = backedUpOnThisPhone()
            val copiedSome = onPhone > alreadyBackedUp
            val now = System.currentTimeMillis()
            if (copiedSome) settings.setLastBackedUpAtEpochMillis(now)
            if (drive != DriveStatus.Ready && !userStop.get()) {
                _state.update {
                    it.copy(
                        status = BackupStatus.Paused(PauseReason.DriveOut),
                        driveStatus = drive,
                        backedUpCount = onPhone,
                        lastBackedUpAtEpochMillis = if (copiedSome) now else it.lastBackedUpAtEpochMillis,
                        message = MESSAGE_DRIVE_OUT,
                    )
                }
                stopService()
                return
            }
            _state.update {
                it.copy(
                    status = if (error is DriveFullException) BackupStatus.DriveFull else BackupStatus.NeedsAttention,
                    driveStatus = drive,
                    backedUpCount = onPhone,
                    lastBackedUpAtEpochMillis = if (copiedSome) now else it.lastBackedUpAtEpochMillis,
                    message = if (error is DriveFullException) MESSAGE_DRIVE_FULL else error.message ?: MESSAGE_NEEDS_ATTENTION,
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
            ).let { next ->
                val started = it.runStartedAtMillis
                if (next.status == BackupStatus.Done && started != null) {
                    next.copy(lastRunTookMillis = System.currentTimeMillis() - started)
                } else {
                    next
                }
            }.copy(driveStatus = outcome.driveStatus, backedUpCount = onPhone)
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

    private fun progressLedger(recorded: AtomicInteger, sizeById: Map<String, Long> = emptyMap()): BackedUpLedger {
        // A run walks every photo again and re-confirms the old ones. Only a new one adds to the count.
        fun countIfNew(mediaId: String, isNew: Boolean) {
            if (!isNew) return
            val size = sizeById[mediaId] ?: 0L
            _state.update {
                it.withBackedUpCount(recorded.incrementAndGet())
                    .withCopied(size, System.currentTimeMillis())
            }
        }
        return object : BackedUpLedger {
            override fun record(mediaId: String, hash: String) {
                val isNew = !settings.hasRecorded(mediaId)
                settings.record(mediaId, hash)
                countIfNew(mediaId, isNew)
            }

            override fun recordChecked(file: MediaFile, hash: String, checkedAt: java.time.Instant) {
                val isNew = !settings.hasRecorded(file.id)
                // recordChecked writes the ledger entry too, so this is one write.
                settings.recordChecked(file, hash, checkedAt)
                countIfNew(file.id, isNew)
            }

            override fun checkedOriginal(mediaId: String) = settings.checkedOriginal(mediaId)

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

    private fun refuseSessionAccess(access: SessionAccess) {
        _state.update {
            val status = when (it.status) {
                BackupStatus.NeedsAttention -> BackupStatus.NeedsAttention
                else -> BackupStatus.Idle
            }
            it.copy(
                status = status,
                sessionAccess = access,
                message = sessionAccessMessage(access),
            )
        }
    }

    /** How much room the Archive Drive has, and how much of the phone's Camera folder is still to copy. */
    private suspend fun refreshSpace() {
        val uri = settings.treeUri() ?: return
        withContext(Dispatchers.IO) {
            try {
                val free = archiveDriveFreeBytes(appContext, uri)
                val ledger = settings.ledgerEntries().keys
                val left = MediaStoreSource(appContext).listCameraFolder()
                    .filter { it.id !in ledger }
                val battery = AndroidBatteryInput(appContext).read()
                _state.update {
                    it.copy(
                        driveFreeBytes = free,
                        bytesToCopy = left.sumOf { f -> f.sizeBytes },
                        filesToCopy = left.size,
                        videosToCopy = left.count { f -> f.kind == MediaKind.VIDEO },
                        batteryPercent = battery?.percent,
                        charging = battery?.charging ?: false,
                    )
                }
                // Not during a run: its run file has no footer yet and would be read again every tick.
                if (_state.value.status != BackupStatus.Running) refreshRecentRuns(uri)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
        }
    }

    /** Reads new run files from the Archive Drive and keeps the latest few for Home's time and battery estimate. */
    private suspend fun refreshRecentRuns(uri: Uri) {
        try {
            val all = BackupHistoryRepository(
                dao = PhotosDatabase.get(appContext).backupRunDao(),
                settings = settings,
            ).refresh(SafArchiveDrive(appContext, uri))
            val recent = byBackup(all.filter { !it.interrupted })
                .take(RECENT_RUNS_KEPT)
                .map { RecentRun(it.bytes, it.batteryDropPercent, it.elapsedMs?.let(Duration::ofMillis)) }
            _state.update { it.copy(recentRuns = recent) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // No history just means the cautious fallback estimate.
        }
    }

    /** The first probe after a pull can still say Ready, so look a few times before believing it. */
    private suspend fun probeUntilGone(uri: Uri): DriveStatus {
        var status = probeOrAbsent(uri)
        repeat(DRIVE_GONE_PROBES) {
            if (status != DriveStatus.Ready) return status
            delay(DRIVE_GONE_PROBE_GAP_MS)
            status = probeOrAbsent(uri)
        }
        return status
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
        private const val SPACE_REFRESH_MS = 120_000L
        private const val RECENT_RUNS_KEPT = 5
        private const val DRIVE_GONE_PROBES = 8
        private const val DRIVE_GONE_PROBE_GAP_MS = 750L
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
