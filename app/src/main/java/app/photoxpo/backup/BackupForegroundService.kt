package app.photoxpo.backup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import app.photoxpo.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service for a Backup run. [BackupController.start] starts it from a visible tap.
 */
class BackupForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var promoted = false
    private var volumeCallback: StorageManager.StorageVolumeCallback? = null
    private var mediaReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        registerDriveWatch()
        val controller = BackupController.get(this)
        scope.launch {
            controller.state.collect { current ->
                if (!promoted) return@collect
                notificationManager().notify(NOTIFICATION_ID, buildNotification(current))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        when (intent?.action) {
            ACTION_STOP -> {
                ensureForeground()
                BackupController.get(this).pause()
            }
            else -> {
                ensureForeground()
                BackupController.get(this).beginRun()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        stopFromTimeout(startId)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopFromTimeout(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterDriveWatch()
        if (promoted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            promoted = false
        }
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Android's time limit. Ask the run to end cleanly after the current Original (a normal time-limit
     * stop, with files left, recorded in history), but never wait long: Android wants the service to stop
     * within a few seconds, so after [TIMEOUT_WAIT_MS] the run pauses between Originals instead.
     */
    private fun stopFromTimeout(startId: Int) {
        val controller = BackupController.get(this)
        controller.requestTimeLimitStop()
        scope.launch {
            controller.awaitTimeLimitStop(TIMEOUT_WAIT_MS)
            if (promoted) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                promoted = false
            }
            stopSelf(startId)
        }
    }

    private fun ensureForeground() {
        val controller = BackupController.get(this)
        val notification = buildNotification(controller.state.value)
        if (promoted) {
            notificationManager().notify(NOTIFICATION_ID, notification)
            return
        }
        var fellBack = false
        try {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } catch (security: SecurityException) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            fellBack = true
        } catch (notAllowed: android.app.ForegroundServiceStartNotAllowedException) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            fellBack = true
        }
        promoted = true
        if (fellBack) {
            controller.noteConnectedDeviceFallback()
            notificationManager().notify(
                NOTIFICATION_ID,
                buildNotification(controller.state.value),
            )
        }
    }

    private fun registerDriveWatch() {
        if (volumeCallback != null) return
        val storage = getSystemService(StorageManager::class.java)
        if (storage != null) {
            val callback = object : StorageManager.StorageVolumeCallback() {
                override fun onStateChanged(volume: StorageVolume) {
                    BackupController.get(this@BackupForegroundService).onVolumesChanged()
                }
            }
            storage.registerStorageVolumeCallback(mainExecutor, callback)
            volumeCallback = callback
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                BackupController.get(this@BackupForegroundService).onVolumesChanged()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addDataScheme("file")
        }
        registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        mediaReceiver = receiver
    }

    private fun unregisterDriveWatch() {
        val storage = getSystemService(StorageManager::class.java)
        volumeCallback?.let { callback ->
            storage?.unregisterStorageVolumeCallback(callback)
        }
        volumeCallback = null
        mediaReceiver?.let { receiver ->
            try {
                unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // The receiver was already removed.
            }
        }
        mediaReceiver = null
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Backup run",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = "Progress while Originals are Backed up to the Archive Drive"
        notificationManager().createNotificationChannel(channel)
    }

    private fun buildNotification(state: BackupUiState): Notification {
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, BackupForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val progress = backupProgressText(state)
        val running = state.status is BackupStatus.Running
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Backup run")
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(running || state.status is BackupStatus.Paused)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop)
        if (state.message != null && running) {
            builder.setContentText(progress)
            builder.setSubText(state.message)
        } else {
            builder.setContentText(state.message ?: progress)
        }
        val thisRun = state.runFiles()
        if (thisRun != null) {
            builder.setProgress(thisRun.total, thisRun.done, false)
        } else if (state.totalCount > 0) {
            builder.setProgress(
                state.totalCount,
                state.backedUpCount.coerceAtMost(state.totalCount),
                false,
            )
        } else if (running) {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun notificationManager(): NotificationManager {
        return getSystemService(NotificationManager::class.java)
    }

    companion object {
        const val ACTION_START = "app.photoxpo.backup.START"
        const val ACTION_STOP = "app.photoxpo.backup.STOP"

        private const val CHANNEL_ID = "backup-run"
        private const val NOTIFICATION_ID = 29

        /** Android gives a timed-out foreground service only a few seconds before it is treated as stuck. */
        private const val TIMEOUT_WAIT_MS = 4_000L
    }
}
