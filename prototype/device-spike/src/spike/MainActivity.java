package spike;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/** PROTOTYPE (ticket #12): one screen of buttons, one log. Throwaway. */
public class MainActivity extends Activity {
    static final int PICK = 1;
    TextView logView;
    ScrollView scroll;
    SharedPreferences prefs;
    Uri tree;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("spike", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 260, 16, 80);
        scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        scroll.addView(logView);

        LinearLayout row1 = new LinearLayout(this), row2 = new LinearLayout(this), row3 = new LinearLayout(this);
        btn(row1, "1 Pick drive", () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION), PICK));
        btn(row1, "1 Check grant", this::checkGrant);
        btn(row1, "7 Device", this::device);
        btn(row2, "3 List", () -> bg(() -> Work.list(this, tree)));
        btn(row2, "3 Write 256MB", () -> bg(() -> Work.throughput(this, tree, 256)));
        btn(row2, "3 Write 2GB", () -> bg(() -> Work.throughput(this, tree, 2048)));
        btn(row3, "2 Svc connDev 5m", () -> service(CopyService.class, true));
        btn(row3, "2 Svc dataSync", () -> service(CopyServiceDataSync.class, true));
        btn(row3, "2 No wake lock", () -> service(CopyService.class, false));
        LinearLayout row4 = new LinearLayout(this), row5 = new LinearLayout(this);
        btn(row4, "Perms", () -> requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.ACCESS_MEDIA_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS}, 2));
        btn(row4, "4 Samples (30)", () -> bg(this::samples));
        btn(row4, "5 Gallery test", () -> bg(this::galleryTest));
        btn(row5, "6 Make test photo", () -> bg(this::makeTestPhoto));
        btn(row5, "6 Free space", this::freeSpace);
        btn(row5, "Stop svc", () -> CopyService.stop = true);
        btn(row5, "Copy log", () -> {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("log", Log.SB));
            Log.i("log copied to clipboard");
        });
        for (LinearLayout r : new LinearLayout[]{row1, row2, row3, row4, row5}) root.addView(r);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        Log.onChange = () -> runOnUiThread(() -> {
            logView.setText(Log.SB);
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
        String t = prefs.getString("tree", null);
        if (t != null) tree = Uri.parse(t);
        Log.i("started. boot count=" + android.provider.Settings.Global.getInt(getContentResolver(),
                android.provider.Settings.Global.BOOT_COUNT, -1) + " saved tree=" + t);
        checkGrant();
    }

    void btn(LinearLayout row, String label, Runnable r) {
        Button x = new Button(this);
        x.setText(label);
        x.setTextSize(10);
        x.setAllCaps(false);
        x.setOnClickListener(v -> r.run());
        row.addView(x, new LinearLayout.LayoutParams(0, -2, 1f));
    }

    interface Job { void run() throws Exception; }

    void bg(Job j) {
        if (tree == null) Log.i("(some tests need a drive: press 1 Pick drive first)");
        new Thread(() -> {
            try { j.run(); } catch (Throwable e) { Log.i("ERROR: " + e); }
        }).start();
    }

    void service(Class<?> c, boolean wake) {
        if (tree == null) { Log.i("pick the drive first"); return; }
        startForegroundService(new Intent(this, c).putExtra("tree", tree.toString())
                .putExtra("minutes", 5).putExtra("wake", wake));
    }

    @Override protected void onActivityResult(int req, int res, Intent d) {
        if (req == PICK && res == RESULT_OK && d != null) {
            tree = d.getData();
            getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            prefs.edit().putString("tree", tree.toString()).apply();
            Log.i("PICKED: " + tree + " (the picker offered this drive root, Q1a = yes)");
            checkGrant();
        } else if (req == PICK) Log.i("picker cancelled or did not offer the drive");
    }

    /** Question 1: after replug or reboot, is the saved grant still there and usable? */
    void checkGrant() {
        List<UriPermission> ps = getContentResolver().getPersistedUriPermissions();
        Log.i("GRANTS: " + ps.size() + " persisted");
        for (UriPermission p : ps) Log.i("  " + p.getUri() + " r=" + p.isReadPermission() + " w=" + p.isWritePermission());
        if (tree == null) return;
        new Thread(() -> {
            try {
                Uri root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
                try (Cursor q = getContentResolver().query(root, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                    Log.i("GRANT USABLE: root query " + (q != null && q.moveToFirst() ? "ok, name=" + q.getString(0) : "returned nothing (drive not mounted?)"));
                }
            } catch (Throwable e) { Log.i("GRANT NOT USABLE: " + e); }
        }).start();
    }

    /** Question 7 (and context for everything else). */
    void device() {
        Log.i("DEVICE: " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE
                + " (SDK " + Build.VERSION.SDK_INT + "), build " + Build.DISPLAY);
        Log.i("  One UI version code: " + oneUi() + "   (My Files > drive > Storage info tells FAT32 or exFAT: note it by hand)");
    }

    String oneUi() {
        try {
            Object v = Class.forName("android.os.SystemProperties").getMethod("get", String.class)
                    .invoke(null, "ro.build.version.oneui");
            return String.valueOf(v);
        } catch (Throwable e) { return "unknown"; }
    }

    /** Question 4: ~30 real files from DCIM/Camera. */
    void samples() throws Exception {
        ContentResolver cr = getContentResolver();
        Uri[] sources = {MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI};
        for (Uri src : sources) {
            boolean video = src == sources[1];
            int limit = video ? 8 : 22;
            String[] proj = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED};
            // newest first, but spread: take every Nth row so the sample covers different ages
            try (Cursor q = cr.query(src, proj, MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                    new String[]{"DCIM/Camera%"}, MediaStore.MediaColumns.DATE_TAKEN + " DESC")) {
                int i = 0, done = 0, stride = Math.max(1, q.getCount() / limit);
                while (q != null && q.moveToNext() && done < limit) {
                    if (i++ % stride != 0) continue;
                    done++;
                    Uri u = ContentUris.withAppendedId(src, q.getLong(0));
                    one(cr, u, q.getString(1), q.getString(2), q.getLong(3), q.getLong(4), video);
                }
                Log.i("SAMPLES: " + (video ? "videos" : "photos") + " done=" + done);
            }
        }
    }

    void one(ContentResolver cr, Uri u, String name, String mime, long msSize, long taken, boolean video) {
        StringBuilder s = new StringBuilder(name + " " + mime + " store=" + (msSize >> 10) + "KB");
        try {
            // bytes actually delivered vs the size MediaStore claims (transcode check)
            long n = 0;
            byte[] head = new byte[65536];
            int headLen = 0;
            try (InputStream in = cr.openInputStream(u)) {
                byte[] buf = new byte[1 << 16];
                int r;
                while ((r = in.read(buf)) > 0) {
                    if (headLen < head.length) { int c = Math.min(r, head.length - headLen); System.arraycopy(buf, 0, head, headLen, c); headLen += c; }
                    n += r;
                }
            }
            s.append(" read=").append(n >> 10).append("KB").append(n == msSize ? "" : " SIZE-MISMATCH");
            String h = new String(head, 0, headLen, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (h.contains("MotionPhoto") || h.contains("MicroVideo")) s.append(" MOTION");
            if (!video) {
                android.media.ExifInterface ex;
                try (InputStream in = cr.openInputStream(u)) { ex = new android.media.ExifInterface(in); }
                s.append(" exifDate=").append(ex.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL))
                        .append(" offset=").append(ex.getAttribute(android.media.ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
                        .append(" gpsPlain=").append(ex.getLatLong(new float[2]));
                try {
                    Uri orig = MediaStore.setRequireOriginal(u);
                    try (InputStream in = cr.openInputStream(orig)) {
                        s.append(" gpsOriginal=").append(new android.media.ExifInterface(in).getLatLong(new float[2]));
                    }
                } catch (Throwable e) { s.append(" gpsOriginal=ERR ").append(e.getClass().getSimpleName()); }
                final long t0 = System.nanoTime();
                Bitmap bm = ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, u), (dec, info, src) -> {
                    int w = info.getSize().getWidth(), hh = info.getSize().getHeight();
                    float f = 720f / Math.max(w, hh);
                    if (f < 1) dec.setTargetSize(Math.round(w * f), Math.round(hh * f));
                });
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.JPEG, 70, bo);
                s.append(" preview=").append(bm.getWidth()).append("x").append(bm.getHeight())
                        .append(" ").append(bo.size() >> 10).append("KB in ").append((System.nanoTime() - t0) / 1_000_000).append("ms");
            } else {
                MediaMetadataRetriever m = new MediaMetadataRetriever();
                m.setDataSource(this, u);
                s.append(" date=").append(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE))
                        .append(" codec=").append(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE))
                        .append(" dur=").append(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).append("ms");
                Bitmap f = m.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                s.append(" poster=").append(f == null ? "NONE" : f.getWidth() + "x" + f.getHeight());
                m.release();
            }
        } catch (Throwable e) { s.append(" ERROR ").append(e); }
        Log.i(s.toString());
    }

    /** Question 5: write a jpg into the drive, then see if MediaStore picked it up. */
    void galleryTest() throws Exception {
        ContentResolver cr = getContentResolver();
        Uri root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        String name = "spike-gallery-" + (System.currentTimeMillis() / 1000) + ".jpg";
        Uri doc = DocumentsContract.createDocument(cr, root, "image/jpeg", name);
        Bitmap bm = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888);
        bm.eraseColor(0xff3366cc);
        try (OutputStream o = cr.openOutputStream(doc, "wt")) { bm.compress(Bitmap.CompressFormat.JPEG, 80, o); }
        Log.i("GALLERY: wrote " + name + " to the drive root. Waiting 20 s, then asking MediaStore. "
                + "Also look in Samsung Gallery and Google Photos by hand (leave the file on the drive).");
        Thread.sleep(20_000);
        try (Cursor q = cr.query(MediaStore.Files.getContentUri("external"), new String[]{MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[]{name}, null)) {
            Log.i("GALLERY: MediaStore " + (q != null && q.getCount() > 0 ? "DOES list it" : "does NOT list it"));
        }
    }

    /** Question 6: a photo we own, in Pictures/PhotoxpoSpike, so it can be deleted from My Files. */
    void makeTestPhoto() throws Exception {
        ContentResolver cr = getContentResolver();
        String name = "spike-recycle-" + (System.currentTimeMillis() / 1000) + ".jpg";
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/PhotoxpoSpike");
        Uri u = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        Bitmap bm = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888);
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 400; i++) bm.setPixel(r.nextInt(1200), r.nextInt(900), r.nextInt() | 0xff000000);
        bm.eraseColor(0xffcc3366);
        try (OutputStream o = cr.openOutputStream(u)) { bm.compress(Bitmap.CompressFormat.JPEG, 90, o); }
        Log.i("RECYCLE: created Pictures/PhotoxpoSpike/" + name + ". Press 6 Free space, delete it in My Files, "
                + "check Gallery's Recycle bin, press 6 Free space, empty the bin, press 6 Free space.");
    }

    void freeSpace() {
        StatFs s = new StatFs(Environment.getExternalStorageDirectory().getPath());
        Log.i("FREE SPACE: " + (s.getAvailableBytes() / 1024) + " KB of " + (s.getTotalBytes() >> 20) + " MB");
    }
}
