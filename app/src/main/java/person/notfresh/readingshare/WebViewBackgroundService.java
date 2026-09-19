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

import android.view.WindowManager;

public class WebViewBackgroundService extends Service {
    private static final String TAG = "WebViewBgService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "WebViewAudioChannel";
    public static final String ACTION_PLAY_PAUSE = "person.notfresh.readingshare.PLAY_PAUSE";
    public static final String ACTION_STOP = "person.notfresh.readingshare.STOP";
    public static final String ACTION_SHOW_MINI = "person.notfresh.readingshare.SHOW_MINI";
    public static final String ACTION_HIDE_MINI = "person.notfresh.readingshare.HIDE_MINI";

    private PowerManager.WakeLock serviceLock;
    private boolean isPaused = false;
    private FloatingMiniPlayerView mMiniView;

    public static boolean hasCollapsedSession() {
        return CollapsedPlaybackSession.getInstance().isActive();
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
                CollapsedPlaybackSession.PlaybackController controller =
                        CollapsedPlaybackSession.getInstance().getOwnerController();
                Log.d(TAG, "Play/Pause 按钮点击, isPaused=" + isPaused + ", controller=" + (controller != null));
                if (controller != null) {
                    if (isPaused) {
                        controller.pause();
                    } else {
                        controller.play();
                    }
                }
                // 刷新通知
                startForeground(NOTIFICATION_ID, buildNotification());
                return START_STICKY;
            } else if (ACTION_STOP.equals(action)) {
                CollapsedPlaybackSession.PlaybackController controller =
                        CollapsedPlaybackSession.getInstance().getOwnerController();
                Log.d(TAG, "Stop 按钮点击, controller=" + (controller != null));
                if (controller != null) {
                    controller.stop();
                }
                CollapsedPlaybackSession.getInstance().clear();
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_SHOW_MINI.equals(action)) {
                String url = CollapsedPlaybackSession.getInstance().getOwnerUrl();
                Log.d(TAG, "SHOW_MINI action, url=" + url);
                if (url != null) {
                    showMiniPlayer(url);
                }
                // 不 return,继续走到 startForeground 让通知显示
            } else if (ACTION_HIDE_MINI.equals(action)) {
                Log.d(TAG, "HIDE_MINI action");
                WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                if (mMiniView != null) {
                    mMiniView.detachFrom(wm);
                    mMiniView = null;
                }
                // 不 return,继续走到 startForeground
            }
        }

        isPaused = false;

        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    private Notification buildNotification() {
        // 点击通知返回应用
        Intent notificationIntent = new Intent(this, WebViewActivity.class);
        String ownerUrl = CollapsedPlaybackSession.getInstance().getOwnerUrl();
        if (ownerUrl != null && !ownerUrl.isEmpty()) {
            notificationIntent.putExtra("url", ownerUrl);
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

    private void showMiniPlayer(String url) {
        Log.d(TAG, "showMiniPlayer url=" + url);
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            Log.e(TAG, "WindowManager 为空,无法显示 mini");
            return;
        }
        if (mMiniView != null && mMiniView.getParent() != null) {
            mMiniView.detachFrom(wm);
        }
        mMiniView = new FloatingMiniPlayerView(this, v -> handleMiniClick());
        mMiniView.attachTo(wm);
        Log.d(TAG, "showMiniPlayer 完成, mMiniView.getParent()=" + (mMiniView == null ? "null" : mMiniView.getParent()));
    }

    private void handleMiniClick() {
        CollapsedPlaybackSession session = CollapsedPlaybackSession.getInstance();
        int ownerTaskId = session.getOwnerTaskId();
        String ownerUrl = session.getOwnerUrl();
        Log.d(TAG, "mini 被点击,展开 WebView, taskId=" + ownerTaskId);
        // 先卸载 mini
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (mMiniView != null) {
            mMiniView.detachFrom(wm);
            mMiniView = null;
        }
        // 折叠的 WebViewActivity 在后台 task 中存活,直接拉回前台,原样恢复,音频不断.
        boolean restored = false;
        if (ownerTaskId != -1) {
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    am.moveTaskToFront(ownerTaskId, 0);
                    restored = true;
                    Log.d(TAG, "已 moveTaskToFront taskId=" + ownerTaskId);
                }
            } catch (Exception e) {
                Log.e(TAG, "moveTaskToFront 失败", e);
            }
        }
        // 兜底:task 已被系统回收,重新打开页面(会重新加载,音频需手动重播)
        if (!restored && ownerUrl != null && !ownerUrl.isEmpty()) {
            Intent expandIntent = new Intent(this, WebViewActivity.class);
            expandIntent.putExtra("url", ownerUrl);
            expandIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(expandIntent);
            } catch (Exception e) {
                Log.e(TAG, "展开 WebViewActivity 失败", e);
            }
        }
        // Service 继续运行(保活音频),由 WebViewActivity onResume 停止
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
        Log.d(TAG, "onDestroy 被调用, mMiniView=" + mMiniView + ", serviceLock held=" + (serviceLock != null && serviceLock.isHeld()));
        Log.d(TAG, "onDestroy stacktrace", new Throwable("trace"));
        if (mMiniView != null) {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            mMiniView.detachFrom(wm);
            mMiniView = null;
        }
        if (serviceLock != null && serviceLock.isHeld()) {
            serviceLock.release();
        }
        super.onDestroy();
        Log.d(TAG, "服务已销毁");
    }
} 