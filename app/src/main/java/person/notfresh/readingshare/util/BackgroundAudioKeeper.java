package person.notfresh.readingshare.util;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import person.notfresh.readingshare.WebViewBackgroundService;

/**
 * WebView 音频后台保活(2026-11-01,2.4.8)。
 *
 * <p>单一来源:Activity 与阅读页(ReaderFragment)共用的「切后台继续播」三件套 ——
 * 前台服务 {@link WebViewBackgroundService} + PARTIAL WakeLock。原来这套逻辑
 * 只写在 WebViewActivity.onPause/onResume,阅读页重写时没搬过去,切桌面音频必断。</p>
 *
 * <p>用法:onPause(或宿主失去前台)调 {@link #acquire},onResume/onDestroy 调 {@link #release}。
 * 静态单实例即可 —— 服务和 WakeLock 都是进程级的,不需要每宿主一份。</p>
 *
 * <p>MediaSession / JS 桥(mini player 折叠)不在这里 —— 那是 Activity 私有的折叠体验,
 * 后台播放本身不依赖它们。</p>
 */
public final class BackgroundAudioKeeper {

    private static final String TAG = "BackgroundAudioKeeper";
    private static final long WAKELOCK_TIMEOUT_MS = 30 * 60 * 1000L;

    private static PowerManager.WakeLock wakeLock;

    private BackgroundAudioKeeper() {
        // utility class
    }

    /** 进后台:启前台服务 + 拿 WakeLock(重复调用安全)。 */
    public static void acquire(Context context) {
        Context app = context.getApplicationContext();
        try {
            app.startForegroundService(new Intent(app, WebViewBackgroundService.class));
        } catch (Exception e) {
            // Android 12+ 前台服务限制等场景:保活失败只影响后台播放,不让宿主崩
            android.util.Log.w(TAG, "startForegroundService failed", e);
        }
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebViewAudio::WakeLock");
            }
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(WAKELOCK_TIMEOUT_MS);
        }
    }

    /** 回前台/销毁:停服务 + 释放 WakeLock(重复调用安全)。 */
    public static void release(Context context) {
        Context app = context.getApplicationContext();
        try {
            app.stopService(new Intent(app, WebViewBackgroundService.class));
        } catch (Exception e) {
            android.util.Log.w(TAG, "stopService failed", e);
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
