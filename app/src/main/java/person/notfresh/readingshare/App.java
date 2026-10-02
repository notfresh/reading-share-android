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
import person.notfresh.readingshare.eventlog.LinkApplier;
import person.notfresh.readingshare.eventlog.SqliteEventLogStore;
import person.notfresh.readingshare.eventlog.SqliteSyncLogStore;
import person.notfresh.readingshare.eventlog.SqliteSyncPointStore;
import person.notfresh.readingshare.eventlog.SyncConfig;
import person.notfresh.readingshare.eventlog.SyncLogStore;
import person.notfresh.readingshare.eventlog.SyncPointStore;
import person.notfresh.readingshare.links.LinkEventApplier;
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

    private static App instance;

    private ExecutorService syncExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // 预热数据库连接(进程级单例)
        DbConnection dbConnection = DbConnection.get(this);
        SQLiteDatabase db = dbConnection.writable();
        String deviceId = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ANDROID_ID);
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = "unknown";
        }
        EventLogStore store = new SqliteEventLogStore(db, deviceId);
        // 单例：db / LinkDao / LinkApplier 整个 app 生命周期共用一份
        LinkDao linkDao = new LinkDao(db);
        LinkApplier linkApplier = new LinkEventApplier(linkDao);

        // syncLogStore 永远 init — 这样 sync 弹窗/未配置时也能写日志
        // pusher / puller / syncConfig 仅在配置完整时初始化,未配置时 push/pull 会抛
        // "sync not initialized" 异常被 catch 掉,但 sync_log 至少能记录下来
        SyncLogStore syncLogStore = new SqliteSyncLogStore(db);
        android.content.SharedPreferences bootstrapPrefs = getSharedPreferences(
                "eventlog_bootstrap_prefs", MODE_PRIVATE);

        boolean syncWired = SimpleSyncManager.hasConfig(this);
        if (syncWired) {
            initFullSync(store, linkApplier, syncLogStore, bootstrapPrefs);
        } else {
            // 没配置时也走完整 init,但 syncConfig/pusher/puller 传 null
            // — syncLogStore 仍然在,弹窗能查;push/pull 会抛异常被 catch
            EventLogClient.init(store, (SyncPointStore) null, null, null, null,
                    syncLogStore, linkApplier, bootstrapPrefs);
        }
        bootstrapEventLogIfNeeded(db, deviceId);
        if (syncWired) {
            triggerStartupSync();
        }
    }

    /**
     * SimpleSyncManager 写入 server_url / secret 后,回调这里重新初始化
     * EventLogClient(把 pusher/puller/syncConfig 接上)。配置没变(null)就什么都不做。
     */
    public static App getInstance() {
        return instance;
    }

    public static void reinitIfConfigured(android.content.Context ctx) {
        if (ctx == null) return;
        if (!SimpleSyncManager.hasConfig(ctx)) return;
        DbConnection dbConnection = DbConnection.get(ctx);
        SQLiteDatabase db = dbConnection.writable();
        String deviceId = Settings.Secure.getString(
                ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = "unknown";
        }
        EventLogStore store = new SqliteEventLogStore(db, deviceId);
        LinkDao linkDao = new LinkDao(db);
        LinkApplier linkApplier = new LinkEventApplier(linkDao);
        SyncLogStore syncLogStore = new SqliteSyncLogStore(db);
        android.content.SharedPreferences bootstrapPrefs = ctx.getSharedPreferences(
                "eventlog_bootstrap_prefs", android.content.Context.MODE_PRIVATE);
        initFullSync(store, linkApplier, syncLogStore, bootstrapPrefs);
    }

    /** 完整 init — 从 SimpleSyncManager 读 url/secret,装配 SyncConfig + pusher/puller + 调 EventLogClient.init */
    private static void initFullSync(EventLogStore store, LinkApplier linkApplier,
                                     SyncLogStore syncLogStore,
                                     android.content.SharedPreferences bootstrapPrefs) {
        android.content.Context ctx = person.notfresh.readingshare.App.getInstance();
        if (ctx == null) return;
        String url = SimpleSyncManager.getServerUrl(ctx);
        String secret = SimpleSyncManager.getSecretKey(ctx);
        SyncConfig syncConfig = new SyncConfig(url, secret);
        DbConnection dbConnection = DbConnection.get(ctx);
        SQLiteDatabase db = dbConnection.writable();
        SyncPointStore syncStore = new SqliteSyncPointStore(db);
        EventLogPusher pusher = new HttpEventLogPusher(url, secret);
        EventLogPuller puller = new HttpEventLogPuller(url, secret);
        EventLogClient.init(store, syncStore, pusher, puller, syncConfig, syncLogStore,
                linkApplier, bootstrapPrefs);
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
