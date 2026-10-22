package person.notfresh.readingshare.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 崩溃日志记录（排障用，2.3.19.5）。
 *
 * <p>用户设备上拿不到 logcat 时的兜底：未捕获异常写进 SharedPreferences，
 * 下次启动时由 {@link #consume(Context)} 取出并复制到剪贴板，用户直接粘贴即可回传。</p>
 *
 * <p>只记录最后一次；不联网、不上报、不新增权限。</p>
 */
public final class CrashRecorder {

    private static final String PREFS = "crash_reports";
    private static final String KEY_LAST = "last_crash";
    private static final int MAX_CHARS = 12000;

    private CrashRecorder() {
        // utility class
    }

    /** 在 Application.onCreate 里调用一次。 */
    public static void install(Context context) {
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringWriter buffer = new StringWriter();
                throwable.printStackTrace(new PrintWriter(buffer));
                String text = "thread=" + thread.getName() + "\n" + buffer;
                if (text.length() > MAX_CHARS) {
                    text = text.substring(0, MAX_CHARS);
                }
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString(KEY_LAST, text).commit();
            } catch (Throwable ignored) {
                // 崩溃处理里再崩就放弃记录，保证原异常继续抛给系统
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    /** 取出上次崩溃文本并清空；没有则返回 null。 */
    public static String consume(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String text = prefs.getString(KEY_LAST, null);
        if (text == null || text.isEmpty()) return null;
        prefs.edit().remove(KEY_LAST).apply();
        return text;
    }
}
