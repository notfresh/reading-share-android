package person.notfresh.readingshare.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Process;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 崩溃即复制栈到剪切板（2.3.19.6）。
 *
 * <p>为什么不是"下次启动再复制"：崩溃时界面根本打不开，用户没有第二次机会看到提示，
 * 所以必须在 {@link Thread.UncaughtExceptionHandler} 里当场把栈写进剪贴板，
 * 用户重新打开聊天窗口直接粘贴即可。</p>
 *
 * <p>在 Application.onCreate 里调用一次 {@link #install(Context)}。
 * 写入失败不影响原崩溃流程（依旧交给系统默认 handler）。</p>
 */
public final class CrashClipboard {

    private static final String TAG = "CrashClipboard";
    private static final int MAX_CHARS = 12000;

    private CrashClipboard() {
        // utility class
    }

    public static void install(Context context) {
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                String text = describe(thread, throwable);
                ClipboardManager clipboard =
                        (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("crash", text));
                    Log.e(TAG, "crash stack copied to clipboard");
                }
            } catch (Throwable copyFailed) {
                Log.e(TAG, "copy crash stack failed", copyFailed);
            }

            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                // 没有系统默认 handler 时自己了结进程，保持与默认行为一致
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        });
    }

    private static String describe(Thread thread, Throwable throwable) {
        StringWriter buffer = new StringWriter();
        buffer.append("thread=");
        buffer.append(thread == null ? "?" : thread.getName());
        buffer.append("\n");
        if (throwable != null) {
            PrintWriter writer = new PrintWriter(buffer);
            throwable.printStackTrace(writer);
            writer.flush();
        }
        String text = buffer.toString();
        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS);
        }
        return text;
    }
}
