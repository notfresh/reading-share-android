package person.notfresh.readingshare.db;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 数据库连接单例(进程级)。
 *
 * 解决启动期多处 DAO 各自调用 getWritableDatabase() 带来的重复
 * acquireReference + 版本检查开销。
 *
 * 生命周期:Application 级,与进程同寿。不提供 close()。
 * 线程安全:首次构造用 DCL;写操作加 ReentrantLock 串行化。
 *
 * 用法:DbConnection.get(context).writable() 拿到默认 links.db 的可写连接,
 * 传给 DAO 的 SQLiteDatabase 构造重载。
 */
public final class DbConnection {

    private static volatile DbConnection instance;

    private final Application app;
    private final LinkDbHelper defaultHelper;
    private final SQLiteDatabase defaultDb;
    private final ReentrantLock writeLock;

    private DbConnection(Application app) {
        this.app = app;
        this.defaultHelper = new LinkDbHelper(app, LinkDbHelper.DEFAULT_DATABASE_NAME);
        // 首次构造时建立连接,同时触发 onCreate / onUpgrade
        this.defaultDb = defaultHelper.getWritableDatabase();
        this.writeLock = new ReentrantLock();
    }

    /**
     * 双重检查锁定。多线程并发调用安全,且保证只构造一次。
     *
     * 接受任意 Context(Activity / Fragment / Application),
     * 内部统一转 Application。后续调用此参数会被忽略(已实例化)。
     */
    public static DbConnection get(Context anyContext) {
        DbConnection local = instance;
        if (local != null) return local;
        synchronized (DbConnection.class) {
            if (instance == null) {
                Application app = (Application) anyContext.getApplicationContext();
                instance = new DbConnection(app);
            }
            return instance;
        }
    }

    /** 默认数据库的可写连接。直接复用,不要再 close。 */
    public SQLiteDatabase writable() {
        return defaultDb;
    }

    /** 默认数据库的可读连接(与 writable 同一实例,SQLite 不区分读写连接)。 */
    public SQLiteDatabase readable() {
        return defaultDb;
    }

    /**
     * 写操作加锁。DAO 内部事务代码块用此串行执行,
     * 避免多线程并发事务触发 SQLite 内部锁等待。
     */
    public <T> T withWriteLock(Callable<T> task) throws Exception {
        writeLock.lock();
        try {
            return task.call();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 非默认数据库(如归档库 archive_db)按需创建 helper。
     * 不在单例内缓存,因为这些库使用频率低,且 helper 内部自行管理。
     */
    public LinkDbHelper helperFor(String databaseName) {
        return new LinkDbHelper(app, databaseName);
    }

    /**
     * 强制 WAL checkpoint,把 WAL 中的所有页面写回主 db 文件。
     *
     * 调用时机:app 进入后台 / onActivityStopped / 关键写入完成后。
     * 配合 LinkDbHelper 启用的 WAL,即使后续进程被 SIGKILL,已 checkpoint 的
     * 数据一定在主 db 文件中,不会再被 SIGKILL 留在 wal 文件里。
     *
     * 注意:对 "多任务面板一键全部关闭" 这种直接 SIGKILL 仍然来不及,但对
     * "上滑退出" / "返回键退出" / 系统因内存压力杀进程是有效的。
     */
    public void checkpoint() {
        try {
            defaultDb.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null).close();
        } catch (Exception e) {
            android.util.Log.e("DbConnection", "checkpoint failed", e);
        }
    }
}