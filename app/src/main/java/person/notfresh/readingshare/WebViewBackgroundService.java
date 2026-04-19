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

public class WebViewBackgroundService extends Service {
    private static final String TAG = "WebViewBgService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "WebViewAudioChannel";
    public static final String ACTION_PLAY_PAUSE = "person.notfresh.readingshare.PLAY_PAUSE";
    public static final String ACTION_STOP = "person.notfresh.readingshare.STOP";

    private PowerManager.WakeLock serviceLock;
    private String currentUrl;
    private boolean isPaused = false;

    // 同进程静态回调，直接调用 Activity 的方法，不经过广播或 IPC
    public interface MediaCallback {
        void onPlayRequested();
        void onPauseRequested();
        void onStopRequested();
    }
    private static MediaCallback sMediaCallback;
    public static void setMediaCallback(MediaCallback callback) {
        sMediaCallback = callback;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "服务已创建");

        // 获取服务专用WakeLock，保持CPU运转
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        serviceLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "WebViewBackgroundService::ServiceLock");
        serviceLock.acquire(30 * 60 * 1000L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "服务已启动");
        createNotificationChannel();

        // 处理通知栏按钮的 action
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_PLAY_PAUSE.equals(action)) {
                isPaused = !isPaused;
                Log.d(TAG, "Play/Pause 按钮点击, isPaused=" + isPaused + ", callback=" + (sMediaCallback != null));
                if (sMediaCallback != null) {
                    if (isPaused) {
                        sMediaCallback.onPauseRequested();
                    } else {
                        sMediaCallback.onPlayRequested();
                    }
                }
                // 刷新通知
                startForeground(NOTIFICATION_ID, buildNotification());
                return START_STICKY;
            } else if (ACTION_STOP.equals(action)) {
                Log.d(TAG, "Stop 按钮点击, callback=" + (sMediaCallback != null));
                if (sMediaCallback != null) {
                    sMediaCallback.onStopRequested();
                }
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        // 正常启动
        if (intent != null) {
            String url = intent.getStringExtra("current_url");
            if (url != null) currentUrl = url;
            isPaused = false;
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    private Notification buildNotification() {
        // 点击通知返回应用
        Intent notificationIntent = new Intent(this, WebViewActivity.class);
        if (currentUrl != null && !currentUrl.isEmpty()) {
            notificationIntent.putExtra("url", currentUrl);
        }
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 播放/暂停按钮
        Intent playPauseIntent = new Intent(this, WebViewBackgroundService.class);
        playPauseIntent.setAction(ACTION_PLAY_PAUSE);
        PendingIntent playPausePending = PendingIntent.getService(
                this, 1, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 停止按钮
        Intent stopIntent = new Intent(this, WebViewBackgroundService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, 2, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int playPauseIcon = isPaused ? R.drawable.ic_play : R.drawable.ic_pause;
        String playPauseLabel = isPaused ? "播放" : "暂停";
        String statusText = isPaused ? "已暂停" : "正在播放";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("音频播放中")
                .setContentText(statusText + " — 点击返回应用")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(playPauseIcon, playPauseLabel, playPausePending)
                .addAction(R.drawable.ic_close, "停止", stopPending)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1))
                .build();
    }

    private void createNotificationChannel() {
        // 在Android 8.0及以上版本，需要创建通知通道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WebView音频播放",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("用于在后台播放WebView中的音频");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (serviceLock != null && serviceLock.isHeld()) {
            serviceLock.release();
        }
        super.onDestroy();
        Log.d(TAG, "服务已销毁");
    }
} 