package spike;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.io.FileOutputStream;

/**
 * PROTOTYPE, question 2 and 3c: foreground service that keeps writing to the drive
 * for N minutes with a wake lock, so screen-off and cable-pull can be observed.
 * The progress is mirrored to a file in the app's own storage, so it survives a kill.
 */
public class CopyService extends Service {
    static volatile boolean running;
    static volatile boolean stop;

    int type() { return ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE; }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_NOT_STICKY;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("spike", "Spike", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "spike")
                .setContentTitle("Photoxpo spike copying")
                .setSmallIcon(android.R.drawable.stat_sys_upload).build();
        try {
            startForeground(1, n, type());
            Log.i("SERVICE: startForeground accepted, type " + type());
        } catch (Exception e) {
            Log.i("SERVICE: startForeground REFUSED: " + e);
            stopSelf();
            return START_NOT_STICKY;
        }
        Uri tree = Uri.parse(intent.getStringExtra("tree"));
        int minutes = intent.getIntExtra("minutes", 5);
        boolean wake = intent.getBooleanExtra("wake", true);
        running = true;
        stop = false;
        new Thread(() -> run(tree, minutes, wake)).start();
        return START_NOT_STICKY;
    }

    void run(Uri tree, int minutes, boolean wake) {
        PowerManager.WakeLock wl = null;
        if (wake) {
            wl = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "spike:copy");
            wl.acquire(minutes * 60_000L + 60_000L);
        }
        Log.i("SERVICE: copying for " + minutes + " min, wake lock = " + wake
                + ". Turn the screen off now; pull the cable if you want to test that.");
        long end = System.currentTimeMillis() + minutes * 60_000L;
        long total = 0, lastLog = 0;
        byte[] buf = new byte[1 << 20];
        try {
            Uri root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            int part = 0;
            while (System.currentTimeMillis() < end && !stop) {
                Uri doc = DocumentsContract.createDocument(getContentResolver(), root,
                        "application/octet-stream", "spike-service-" + (part++) + ".tmp");
                try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(doc, "wt");
                     FileOutputStream out = new FileOutputStream(pfd.getFileDescriptor())) {
                    for (int i = 0; i < 100 && System.currentTimeMillis() < end && !stop; i++) {
                        out.write(buf);
                        total += buf.length;
                        long now = System.currentTimeMillis();
                        if (now - lastLog > 30_000) {
                            lastLog = now;
                            Log.i("SERVICE: still writing, " + (total >> 20) + " MB so far");
                        }
                    }
                    out.getFD().sync();
                }
                DocumentsContract.deleteDocument(getContentResolver(), doc);
            }
            Log.i("SERVICE: finished normally, " + (total >> 20) + " MB written");
        } catch (Exception e) {
            Log.i("SERVICE: FAILED after " + (total >> 20) + " MB: " + e);
        } finally {
            if (wl != null && wl.isHeld()) wl.release();
            running = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }
}
