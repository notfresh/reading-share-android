package person.notfresh.readingshare;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * 后台保活服务（2.3.21 简化）。
 *
 * <p>只做一件事：WebViewActivity 退到后台/锁屏时，用前台通知 + WakeLock 让进程别被系统冻结，
 * 从而让 WebView 里的音频继续播。点击通知回到应用。</p>
 *
 * <p>折叠成悬浮 mini 播放器那套（CollapsedPlaybackSession / FloatingMiniPlayerView /
 * 通知栏播放控制按钮）已经删掉 —— 功能不再需要，删掉后本服务从 237 行降到约 90 行。</p>
 */
public class WebViewBackgroundService extends Service {

    private static final String TAG = "WebViewBgService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "WebViewAudioChannel";

    private PowerManager.WakeLock serviceLock;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "服务已创建");
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        serviceLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "WebViewBackgroundService::ServiceLock");
        serviceLock.acquire(30 * 60 * 1000L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        // 被系统回收后重建:继续保活即可,不需要恢复具体状态
        return START_STICKY;
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.webview_bg_service_title))
                .setContentText(getString(R.string.webview_bg_service_text))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.webview_bg_service_title),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.webview_bg_service_text));
        manager.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        if (serviceLock != null && serviceLock.isHeld()) {
            serviceLock.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
