package person.notfresh.readingshare.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class LinkDbHelper extends SQLiteOpenHelper {
    private static final String DEFAULT_DATABASE_NAME = "links.db";
    private static String databaseName = "links.db";
    //private static final int DATABASE_VERSION = 4;
    // private static final int DATABASE_VERSION = 5; // 添加summary字段
    //private static final int DATABASE_VERSION = 10; // 添加文档表
    //private static final int DATABASE_VERSION = 11; // 添加主题表

    private static final int DATABASE_VERSION = 12; // 主题项增加归档字段
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
    
    // 配置表（KV存储）
    public static final String TABLE_CONFIG = "config";
    public static final String COLUMN_CONFIG_KEY = "key";
    public static final String COLUMN_CONFIG_VALUE = "value";
    
    // 文档表
    public static final String TABLE_DOCUMENTS = "documents";
    public static final String COLUMN_DOC_ID = "_id";
    public static final String COLUMN_DOC_TITLE = "title";
    public static final String COLUMN_DOC_FILE_PATH = "file_path";
    public static final String COLUMN_DOC_TYPE = "document_type";
    public static final String COLUMN_DOC_TIMESTAMP = "timestamp";
    public static final String COLUMN_DOC_FILE_SIZE = "file_size";
    public static final String COLUMN_DOC_REMARK = "remark";
    public static final String COLUMN_DOC_IS_PINNED = "is_pinned";
    public static final String COLUMN_DOC_CLICK_COUNT = "click_count";

    // 主题表
    public static final String TABLE_SUBJECTS = "subjects";
    public static final String COLUMN_SUBJECT_ID = "_id";
    public static final String COLUMN_SUBJECT_TITLE = "title";
    public static final String COLUMN_SUBJECT_DESCRIBE = "describe";
    public static final String COLUMN_SUBJECT_CREATE_TIME = "create_time";

    // 主题项表
    public static final String TABLE_SUBJECT_ITEMS = "subject_items";
    public static final String COLUMN_SUBJECT_ITEM_ID = "_id";
    public static final String COLUMN_SUBJECT_ITEM_SUBJECT_ID = "subject_id";
    public static final String COLUMN_SUBJECT_ITEM_LINK_ID = "link_id";
    public static final String COLUMN_SUBJECT_ITEM_IS_LINK_DELETED = "is_link_deleted";
    public static final String COLUMN_SUBJECT_ITEM_REMARK = "remark";
    public static final String COLUMN_SUBJECT_ITEM_ADD_TIME = "add_time";
    public static final String COLUMN_SUBJECT_ITEM_ORDER_INDEX = "order_index";
    public static final String COLUMN_SUBJECT_ITEM_IS_ARCHIVED = "is_archived";
    public static final String COLUMN_SUBJECT_ITEM_ARCHIVED_AT = "archived_at";

    // 主题项图片表
    public static final String TABLE_SUBJECT_ITEM_IMAGES = "subject_item_images";
    public static final String COLUMN_SUBJECT_ITEM_IMAGE_ID = "_id";
    public static final String COLUMN_SUBJECT_ITEM_IMAGE_ITEM_ID = "subject_item_id";
    public static final String COLUMN_SUBJECT_ITEM_IMAGE_PATH = "image_path";

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
                    "is_pinned INTEGER DEFAULT 0, " +
                    "click_count INTEGER DEFAULT 0)";

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

    private static final String SQL_CREATE_CONFIG =
            "CREATE TABLE " + TABLE_CONFIG + " (" +
                    COLUMN_CONFIG_KEY + " TEXT PRIMARY KEY, " +
                    COLUMN_CONFIG_VALUE + " TEXT NOT NULL)";

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

    private static final String SQL_CREATE_DOCUMENTS =
            "CREATE TABLE " + TABLE_DOCUMENTS + " (" +
                    COLUMN_DOC_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_DOC_TITLE + " TEXT NOT NULL, " +
                    COLUMN_DOC_FILE_PATH + " TEXT NOT NULL UNIQUE, " +
                    COLUMN_DOC_TYPE + " TEXT NOT NULL, " +
                    COLUMN_DOC_TIMESTAMP + " INTEGER NOT NULL, " +
                    COLUMN_DOC_FILE_SIZE + " INTEGER DEFAULT 0, " +
                    COLUMN_DOC_REMARK + " TEXT, " +
                    COLUMN_DOC_IS_PINNED + " INTEGER DEFAULT 0, " +
                    COLUMN_DOC_CLICK_COUNT + " INTEGER DEFAULT 0)";

    private static final String SQL_CREATE_SUBJECTS =
            "CREATE TABLE " + TABLE_SUBJECTS + " (" +
                    COLUMN_SUBJECT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_SUBJECT_TITLE + " TEXT NOT NULL, " +
                    COLUMN_SUBJECT_DESCRIBE + " TEXT, " +
                    COLUMN_SUBJECT_CREATE_TIME + " INTEGER NOT NULL)";

    private static final String SQL_CREATE_SUBJECT_ITEMS =
            "CREATE TABLE " + TABLE_SUBJECT_ITEMS + " (" +
                    COLUMN_SUBJECT_ITEM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_SUBJECT_ITEM_SUBJECT_ID + " INTEGER NOT NULL, " +
                    COLUMN_SUBJECT_ITEM_LINK_ID + " INTEGER, " +
                    COLUMN_SUBJECT_ITEM_IS_LINK_DELETED + " INTEGER DEFAULT 0, " +
                    COLUMN_SUBJECT_ITEM_REMARK + " TEXT, " +
                    COLUMN_SUBJECT_ITEM_ADD_TIME + " INTEGER NOT NULL, " +
                COLUMN_SUBJECT_ITEM_ORDER_INDEX + " INTEGER DEFAULT 0, " +
                COLUMN_SUBJECT_ITEM_IS_ARCHIVED + " INTEGER DEFAULT 0, " +
                COLUMN_SUBJECT_ITEM_ARCHIVED_AT + " INTEGER DEFAULT 0, " +
                    "FOREIGN KEY (" + COLUMN_SUBJECT_ITEM_SUBJECT_ID + ") REFERENCES " + TABLE_SUBJECTS + "(" + COLUMN_SUBJECT_ID + "))";

    private static final String SQL_CREATE_SUBJECT_ITEM_IMAGES =
            "CREATE TABLE " + TABLE_SUBJECT_ITEM_IMAGES + " (" +
                    COLUMN_SUBJECT_ITEM_IMAGE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_SUBJECT_ITEM_IMAGE_ITEM_ID + " INTEGER NOT NULL, " +
                    COLUMN_SUBJECT_ITEM_IMAGE_PATH + " TEXT NOT NULL, " +
                    "FOREIGN KEY (" + COLUMN_SUBJECT_ITEM_IMAGE_ITEM_ID + ") REFERENCES " + TABLE_SUBJECT_ITEMS + "(" + COLUMN_SUBJECT_ITEM_ID + "))";

    // 原有构造函数，使用默认数据库名
    public LinkDbHelper(Context context) {
        this(context, DEFAULT_DATABASE_NAME);
        Log.d("LinkDbHelper", "使用默认数据库: " + DEFAULT_DATABASE_NAME);
    }

    // 新增构造函数，允许指定数据库名
    public LinkDbHelper(Context context, String databaseName) {
        super(context, databaseName, null, DATABASE_VERSION);
        this.databaseName = databaseName;
        Log.d("LinkDbHelper", "使用自定义数据库: " + databaseName + ", 版本: " + DATABASE_VERSION);

    }


    // 获取当前数据库名称
    public String getDatabaseName() {
        return this.databaseName;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            Log.d("LinkDbHelper", "Creating database tables");
            
            // 创建现有的表
            db.execSQL(SQL_CREATE_LINKS);
            db.execSQL(SQL_CREATE_TAGS);
            db.execSQL(SQL_CREATE_LINK_TAGS);
            
            // 创建配置表
            db.execSQL(SQL_CREATE_CONFIG);
            
            // 创建 RSS 相关的表
            Log.d("LinkDbHelper", "Creating RSS tables");
            db.execSQL(CREATE_RSS_SOURCES_TABLE);
            db.execSQL(CREATE_RSS_ENTRIES_TABLE);
            
            // 创建文档表
            Log.d("LinkDbHelper", "Creating documents table");
            db.execSQL(SQL_CREATE_DOCUMENTS);
            
            // 创建主题相关表
            Log.d("LinkDbHelper", "Creating subject tables");
            db.execSQL(SQL_CREATE_SUBJECTS);
            db.execSQL(SQL_CREATE_SUBJECT_ITEMS);
            db.execSQL(SQL_CREATE_SUBJECT_ITEM_IMAGES);
            
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

            if (oldVersion < 11) {
                // 版本11：添加主题相关表
                db.execSQL(SQL_CREATE_SUBJECTS);
                db.execSQL(SQL_CREATE_SUBJECT_ITEMS);
                db.execSQL(SQL_CREATE_SUBJECT_ITEM_IMAGES);
                Log.d("LinkDbHelper", "Created subject tables");
            }

            if (oldVersion >= 11 && oldVersion < 12) {
                // 版本12：主题项表增加归档字段
                db.execSQL("ALTER TABLE " + TABLE_SUBJECT_ITEMS + " ADD COLUMN " + COLUMN_SUBJECT_ITEM_IS_ARCHIVED + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_SUBJECT_ITEMS + " ADD COLUMN " + COLUMN_SUBJECT_ITEM_ARCHIVED_AT + " INTEGER DEFAULT 0");
                Log.d("LinkDbHelper", "Added subject item archive columns");
            }

            if (oldVersion < 10) {
                // 版本10：添加文档表
                db.execSQL(SQL_CREATE_DOCUMENTS);
                Log.d("LinkDbHelper", "Created documents table");
            }
            
            if (oldVersion < 9) {
                // 版本9：添加配置表
                db.execSQL(SQL_CREATE_CONFIG);
                Log.d("LinkDbHelper", "Created config table");
            }
            
            // 如果版本差异较大，使用原来的删除重建方式
            if (oldVersion < 8) {
                // 备份现有数据（如果需要的话） TODO
                
                // 删除旧表
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_LINKS);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_TAGS);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_LINK_TAGS);
                db.execSQL("DROP TABLE IF EXISTS rss_sources");
                db.execSQL("DROP TABLE IF EXISTS rss_entries");
                
                // 重新创建所有表
                onCreate(db);
            }
            
            Log.d("LinkDbHelper", "Database upgrade completed successfully");
        } catch (Exception e) {
            Log.e("LinkDbHelper", "Error upgrading database", e);
        }
    }
} 