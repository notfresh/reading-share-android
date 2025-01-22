package person.notfresh.myapplication.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class LinkDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "links.db";
    private static final int DATABASE_VERSION = 3;

    public static final String TABLE_LINKS = "links";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_URL = "url";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_SOURCE_APP = "source_app";
    public static final String COLUMN_ORIGINAL_INTENT = "original_intent";
    public static final String COLUMN_TARGET_ACTIVITY = "target_activity";
    public static final String COLUMN_REMARK = "remark";

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
                    COLUMN_TIMESTAMP + " INTEGER)";

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

    public LinkDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_LINKS);
        db.execSQL(SQL_CREATE_TAGS);
        db.execSQL(SQL_CREATE_LINK_TAGS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {  // 假设当前版本为2
            db.execSQL("ALTER TABLE " + TABLE_LINKS + 
                      " ADD COLUMN " + COLUMN_REMARK + " TEXT");
        }
        // 简单处理升级：删除旧表，创建新表
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LINKS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TAGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LINK_TAGS);
        onCreate(db);
    }
} 