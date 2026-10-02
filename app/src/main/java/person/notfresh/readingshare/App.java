package person.notfresh.readingshare;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;
import android.provider.Settings;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.eventlog.EventAction;
import person.notfresh.readingshare.eventlog.EventLogClient;
import person.notfresh.readingshare.eventlog.EventLogPuller;
import person.notfresh.readingshare.eventlog.EventLogPusher;
import person.notfresh.readingshare.eventlog.EventLogStore;
import person.notfresh.readingshare.eventlog.EventRecord;
import person.notfresh.readingshare.eventlog.HttpEventLogPuller;
import person.notfresh.readingshare.eventlog.HttpEventLogPusher;
import person.notfresh.readingshare.eventlog.SqliteEventLogStore;
import person.notfresh.readingshare.eventlog.SqliteSyncLogStore;
import person.notfresh.readingshare.eventlog.SqliteSyncPointStore;
import person.notfresh.readingshare.eventlog.SyncConfig;
import person.notfresh.readingshare.eventlog.SyncLogStore;
import person.notfresh.readingshare.eventlog.SyncPointStore;
import person.notfresh.readingshare.sync.SimpleSyncManager;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.model.LinkJson;

/**
 * 应用入口。
 *
 * 在 onCreate 中预热 DbConnection,触发默认数据库 links.db 的首次打开、
 * onCreate / onUpgrade 检查。后续 DAO 调用直接拿现成连接,不再重复 acquireReference。
 */
public class App extends Application {

    private ExecutorService syncExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        // 预热数据库连接(进程级单例)
        DbConnection dbConnection = DbConnection.get(this);
        SQLiteDatabase db = dbConnection.writable();
        String deviceId = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ANDROID_ID);
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = "unknown";
        }
        EventLogStore store = new SqliteEventLogStore(db, deviceId);
        // Sync config reuses the SharedPreferences that SettingFragment writes
        // to via SimpleSyncManager — same server, same secret, different sync
        // layer (eventlog vs link full-exchange).
        boolean syncWired = SimpleSyncManager.hasConfig(this);
        if (syncWired) {
            String url = SimpleSyncManager.getServerUrl(this);
            String secret = SimpleSyncManager.getSecretKey(this);
            SyncConfig syncConfig = new SyncConfig(url, secret);
            SyncPointStore syncStore = new SqliteSyncPointStore(db);
            SyncLogStore syncLogStore = new SqliteSyncLogStore(db);
            EventLogPusher pusher = new HttpEventLogPusher(url, secret);
            EventLogPuller puller = new HttpEventLogPuller(url, secret);
            EventLogClient.init(store, syncStore, pusher, puller, syncConfig, syncLogStore);
        } else {
            EventLogClient.init(store);
        }
        bootstrapEventLogIfNeeded(db, deviceId);
        if (syncWired) {
            triggerStartupSync();
        }
    }

    /**
     * Kick off one push + pull cycle on a background thread, per
     * PROTOCOL §10.5 客户端同步触发策略 (启动一次). Failures are swallowed
     * — this is best-effort; the next app launch will retry.
     */
    private void triggerStartupSync() {
        if (syncExecutor == null) {
            syncExecutor = Executors.newSingleThreadExecutor();
        }
        syncExecutor.submit(() -> {
            try {
                EventLogClient.get().pushPending("links");
                EventLogClient.get().pull("links");
            } catch (Exception ignored) {
                // best-effort: log to Logcat in future iteration
            }
        });
    }

    private void bootstrapEventLogIfNeeded(SQLiteDatabase db, String deviceId) {
        if (EventLogClient.get().isBootstrapped()) return;
        new Thread(() -> {
            try {
                LinkDao linkDao = new LinkDao(db);
                List<LinkItem> all = linkDao.getAllLinks();
                long now = System.currentTimeMillis();
                String processTime = EventLogClient.formatIso8601(now);
                for (LinkItem link : all) {
                    String entityId = String.valueOf(link.getId());
                    // event_time = 实体真实创建时间(本地时区)
                    String eventTime = EventLogClient.formatLocalIso8601(link.getTimestamp());
                    // process_time = 日志生成时刻(UTC),三者共享同一 processTime
                    String id = EventLogClient.computeId("links", deviceId,
                            eventTime, entityId, EventAction.CREATE.name());
                    EventLogClient.get().store().append(new EventRecord(
                            id, "links", processTime, eventTime,
                            deviceId, entityId, EventAction.CREATE,
                            LinkJson.toJsonString(link)));
                }
                EventLogClient.get().setBootstrapFlag();
            } catch (Exception ignored) {
            }
        }, "eventlog-bootstrap").start();
    }
}
