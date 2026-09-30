package person.notfresh.readingshare;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;
import android.provider.Settings;

import java.util.List;

import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.eventlog.EventAction;
import person.notfresh.readingshare.eventlog.EventLogClient;
import person.notfresh.readingshare.eventlog.EventRecord;
import person.notfresh.readingshare.eventlog.SqliteEventLogStore;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.model.LinkJson;

/**
 * 应用入口。
 *
 * 在 onCreate 中预热 DbConnection,触发默认数据库 links.db 的首次打开、
 * onCreate / onUpgrade 检查。后续 DAO 调用直接拿现成连接,不再重复 acquireReference。
 */
public class App extends Application {
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
        EventLogClient.init(new SqliteEventLogStore(db, deviceId));
        bootstrapEventLogIfNeeded(db, deviceId);
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
