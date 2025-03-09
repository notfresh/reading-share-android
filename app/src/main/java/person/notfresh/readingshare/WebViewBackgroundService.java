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
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class WebViewBackgroundService extends Service {
    private static final String TAG = "WebViewBgService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "WebViewAudioChannel";

    private PowerManager.WakeLock serviceLock;
    private MediaSessionCompat mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "服务已创建");
        
        // 初始化MediaSession
        mediaSession = new MediaSessionCompat(this, "WebViewAudioService");
        mediaSession.setActive(true);
        
        // 获取服务专用WakeLock
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        serviceLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, 
                "WebViewBackgroundService::ServiceLock");
        serviceLock.acquire();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "服务已启动");
        createNotificationChannel();
        
        // 获取URL
        String currentUrl = "";
        if (intent != null) {
            currentUrl = intent.getStringExtra("current_url");
        }
        
        // 创建返回到应用的Intent
        Intent notificationIntent = new Intent(this, WebViewActivity.class);
        if (currentUrl != null && !currentUrl.isEmpty()) {
            notificationIntent.putExtra("url", currentUrl);
        }
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                (int) System.currentTimeMillis(),
                notificationIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // 创建通知
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("音频播放中")
                .setContentText("点击返回应用")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        
        // 将服务设为前台服务
        startForeground(NOTIFICATION_ID, notification);
        
        // 返回START_STICKY，如果服务被杀死，系统会尝试重新创建它
        return START_STICKY;
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
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        
        if (serviceLock != null && serviceLock.isHeld()) {
            serviceLock.release();
        }
        
        super.onDestroy();
        Log.d(TAG, "服务已销毁");
    }
} 