package spike;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayDeque;

/** PROTOTYPE: the raw drive tests (listing, write, read-back). */
final class Work {
    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }

    /** Question 3a: recursive listing with raw DocumentsContract. */
    static void list(Context c, Uri tree) {
        long t0 = System.nanoTime();
        ContentResolver cr = c.getContentResolver();
        ArrayDeque<String> dirs = new ArrayDeque<>();
        dirs.add(DocumentsContract.getTreeDocumentId(tree));
        long files = 0, folders = 0, bytes = 0;
        String[] proj = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE};
        while (!dirs.isEmpty()) {
            String id = dirs.poll();
            Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
            try (Cursor q = cr.query(kids, proj, null, null, null)) {
                if (q == null) continue;
                while (q.moveToNext()) {
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(q.getString(1))) {
                        folders++;
                        dirs.add(q.getString(0));
                    } else {
                        files++;
                        bytes += q.getLong(2);
                    }
                }
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        Log.i("LIST: " + files + " files, " + folders + " folders, "
                + (bytes >> 20) + " MB in " + ms + " ms");
    }

    /** Question 3b: write to temp name with "wt", fsync, read back, hash, rename, delete. */
    static void throughput(Context c, Uri tree, int mb) throws Exception {
        ContentResolver cr = c.getContentResolver();
        Uri root = DocumentsContract.buildDocumentUriUsingTree(tree,
                DocumentsContract.getTreeDocumentId(tree));
        Uri doc = DocumentsContract.createDocument(cr, root, "application/octet-stream",
                "spike-test.tmp");
        byte[] buf = new byte[1 << 20];
        new java.util.Random(1).nextBytes(buf);
        MessageDigest w = MessageDigest.getInstance("SHA-256");
        long t0 = System.nanoTime();
        try (ParcelFileDescriptor pfd = cr.openFileDescriptor(doc, "wt");
             FileOutputStream out = new FileOutputStream(pfd.getFileDescriptor())) {
            for (int i = 0; i < mb; i++) {
                out.write(buf);
                w.update(buf);
            }
            out.getFD().sync();
        }
        long tw = (System.nanoTime() - t0) / 1_000_000;
        Log.i("WRITE: " + mb + " MB in " + tw + " ms = " + (mb * 1000L / Math.max(tw, 1)) + " MB/s");
        MessageDigest r = MessageDigest.getInstance("SHA-256");
        t0 = System.nanoTime();
        try (ParcelFileDescriptor pfd = cr.openFileDescriptor(doc, "r");
             FileInputStream in = new FileInputStream(pfd.getFileDescriptor())) {
            int n;
            while ((n = in.read(buf)) > 0) r.update(buf, 0, n);
        }
        long tr = (System.nanoTime() - t0) / 1_000_000;
        boolean ok = hex(w.digest()).equals(hex(r.digest()));
        Log.i("READ-BACK: " + mb + " MB in " + tr + " ms = " + (mb * 1000L / Math.max(tr, 1))
                + " MB/s, hash match = " + ok);
        Uri renamed = DocumentsContract.renameDocument(cr, doc, "spike-test.done");
        Log.i("RENAME: " + (renamed != null ? "ok" : "returned null"));
        Uri target = renamed != null ? renamed : doc;
        Log.i("DELETE: " + DocumentsContract.deleteDocument(cr, target));
    }
}
