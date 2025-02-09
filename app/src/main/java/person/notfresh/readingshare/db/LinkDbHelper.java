package person.notfresh.readingshare.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class LinkDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "links.db";
    //private static final int DATABASE_VERSION = 4;
    private static final int DATABASE_VERSION = 5; // 添加summary字段

    public static final String TABLE_LINKS = "links";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_URL = "url";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_SOURCE_APP = "source_app";
    public static final String COLUMN_ORIGINAL_INTENT = "original_intent";
    public static final String COLUMN_TARGET_ACTIVITY = "target_activity";
    public static final String COLUMN_REMARK = "remark";
    public static final String COLUMN_SUMMARY = "summary";

    // 标签表
    public static final String TABLE_TAGS = "tags";
    public static final String COLUMN_TAG_ID = "_id";
    public static final String COLUMN_TAG_NAME = "name";
    
    // 链接-标签关联表
    public static final String TABLE_LINK_TAGS = "link_tags";
    public static final String COLUMN_LINK_ID = "link_id";
    public static final String COLUMN_TAG_ID_REF = "tag_id";

    private static final String SQL_CREATE_LINKS =
            "CREATE TABLE " + TABLE_LINKS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_TITLE + " TEXT, " +
                    COLUMN_URL + " TEXT, " +
                    COLUMN_REMARK + " TEXT, " +
                    COLUMN_SOURCE_APP + " TEXT, " +
                    COLUMN_ORIGINAL_INTENT + " TEXT, " +
                    COLUMN_TARGET_ACTIVITY + " TEXT, " +
                    COLUMN_TIMESTAMP + " INTEGER, " +
                    COLUMN_SUMMARY + " TEXT, " +
                    "is_pinned INTEGER DEFAULT 0)";

    private static final String SQL_CREATE_TAGS =
            "CREATE TABLE " + TABLE_TAGS + " (" +
                    COLUMN_TAG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_TAG_NAME + " TEXT UNIQUE)";

    private static final String SQL_CREATE_LINK_TAGS =
            "CREATE TABLE " + TABLE_LINK_TAGS + " (" +
                    COLUMN_LINK_ID + " INTEGER, " +
                    COLUMN_TAG_ID_REF + " INTEGER, " +
                    "PRIMARY KEY (" + COLUMN_LINK_ID + ", " + COLUMN_TAG_ID_REF + "), " +
                    "FOREIGN KEY (" + COLUMN_LINK_ID + ") REFERENCES " + TABLE_LINKS + "(" + COLUMN_ID + "), " +
                    "FOREIGN KEY (" + COLUMN_TAG_ID_REF + ") REFERENCES " + TABLE_TAGS + "(" + COLUMN_TAG_ID + "))";

    private static final String CREATE_RSS_SOURCES_TABLE =
        "CREATE TABLE rss_sources (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
        "url TEXT NOT NULL," +
        "name TEXT NOT NULL," +
        "last_update INTEGER" +
        ")";

    private static final String CREATE_RSS_ENTRIES_TABLE =
        "CREATE TABLE rss_entries (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
        "source_id INTEGER," +
        "title TEXT NOT NULL," +
        "link TEXT NOT NULL," +
        "pub_date INTEGER," +
        "FOREIGN KEY(source_id) REFERENCES rss_sources(id)" +
        ")";

    public LinkDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        Log.d("LinkDbHelper", "数据库版本 " + DATABASE_VERSION);

    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            Log.d("LinkDbHelper", "Creating database tables");
            
            // 创建现有的表
            db.execSQL(SQL_CREATE_LINKS);
            db.execSQL(SQL_CREATE_TAGS);
            db.execSQL(SQL_CREATE_LINK_TAGS);
            
            // 创建 RSS 相关的表
            Log.d("LinkDbHelper", "Creating RSS tables");
            db.execSQL(CREATE_RSS_SOURCES_TABLE);
            db.execSQL(CREATE_RSS_ENTRIES_TABLE);
            
            Log.d("LinkDbHelper", "Database tables created successfully");
        } catch (Exception e) {
            Log.e("LinkDbHelper", "Error creating database tables", e);
            throw e; // 重新抛出异常，因为没有表将导致应用无法正常工作
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            Log.d("LinkDbHelper", "Upgrading database from " + oldVersion + " to " + newVersion);
            
            // 备份现有数据（如果需要的话）
            
            // 删除旧表
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_LINKS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_TAGS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_LINK_TAGS);
            db.execSQL("DROP TABLE IF EXISTS rss_sources");
            db.execSQL("DROP TABLE IF EXISTS rss_entries");
            
            // 重新创建所有表
            onCreate(db);
            
            Log.d("LinkDbHelper", "Database upgrade completed successfully");
        } catch (Exception e) {
            Log.e("LinkDbHelper", "Error upgrading database", e);
        }
    }
} 