package spike;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** PROTOTYPE: in-memory log, shown in the UI and copyable. */
final class Log {
    static final StringBuilder SB = new StringBuilder();
    static Runnable onChange;
    private static final SimpleDateFormat F = new SimpleDateFormat("HH:mm:ss", Locale.US);

    static synchronized void i(String s) {
        SB.append(F.format(new Date())).append("  ").append(s).append('\n');
        android.util.Log.i("SPIKE", s);
        Runnable r = onChange;
        if (r != null) r.run();
    }
}
