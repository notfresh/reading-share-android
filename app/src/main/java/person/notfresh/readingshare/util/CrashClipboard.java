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
 *
 * <p>2.3.24 新增 {@link #copyException(Context, String, Throwable)}：给「预期内但仍
 * 需要排查」的业务异常用 —— 比如合并标签点了崩了,不想整个 app 挂掉,但要把栈
 * 捞出来给开发者。调用方自己 try/catch,捕获后调这个方法,用户粘一下就知道断在哪。</p>
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
                writeToClipboard(app, "crash", text);
                Log.e(TAG, "crash stack copied to clipboard");
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

    /**
     * 把一个异常连同上下文描述复制到剪切板（2.3.24）。
     *
     * <p>给"已知会出错、但不该让 app 整个挂掉"的地方用：调用方 catch 住异常后调它，
     * 用户粘一下就能看到完整栈 + 出错位置，不用连 adb / logcat。</p>
     *
     * @param context   上下文
     * @param label     标签（剪切板里第一行，便于区分是哪处出的问题）
     * @param throwable 异常
     * @return 是否写入成功
     */
    public static boolean copyException(Context context, String label, Throwable throwable) {
        if (context == null) {
            return false;
        }
        try {
            Context app = context.getApplicationContext();
            StringWriter buffer = new StringWriter();
            buffer.append("label=").append(label == null ? "?" : label).append("\n");
            if (throwable != null) {
                PrintWriter writer = new PrintWriter(buffer);
                throwable.printStackTrace(writer);
                writer.flush();
            }
            String text = buffer.toString();
            if (text.length() > MAX_CHARS) {
                text = text.substring(0, MAX_CHARS);
            }
            writeToClipboard(app, label == null ? "exception" : label, text);
            Log.e(TAG, "exception copied to clipboard: " + label, throwable);
            return true;
        } catch (Throwable copyFailed) {
            Log.e(TAG, "copy exception failed", copyFailed);
            return false;
        }
    }

    private static void writeToClipboard(Context app, String label, String text) {
        ClipboardManager clipboard =
                (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        }
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
