package person.notfresh.readingshare.embedding;

import android.provider.BaseColumns;
import person.notfresh.readingshare.db.LinkDbHelper;

public final class TagEmbeddingDbHelper implements BaseColumns {
    public static final String TABLE_NAME = "tag_embeddings";
    public static final String COLUMN_TAG_ID = "tag_id";
    public static final String COLUMN_EMBEDDING = "embedding";
    public static final String COLUMN_CREATED_AT = "created_at";

    public static final String SQL_CREATE_TABLE =
        "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
        COLUMN_TAG_ID + " INTEGER PRIMARY KEY," +
        COLUMN_EMBEDDING + " TEXT NOT NULL," +
        COLUMN_CREATED_AT + " INTEGER NOT NULL," +
        "FOREIGN KEY (" + COLUMN_TAG_ID + ") REFERENCES " +
        LinkDbHelper.TABLE_TAGS + "(" + LinkDbHelper.COLUMN_TAG_ID + ")" +
        ")";

    public static final String SQL_DROP_TABLE =
        "DROP TABLE IF EXISTS " + TABLE_NAME;

    private TagEmbeddingDbHelper() {}
}