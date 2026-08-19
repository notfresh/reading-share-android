package person.notfresh.readingshare;

import android.app.Application;

import person.notfresh.readingshare.db.DbConnection;

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
        DbConnection.get(this);
    }
}