package person.notfresh.readingshare.embedding;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.json.JSONArray;
import person.notfresh.readingshare.db.LinkDbHelper;
import java.util.ArrayList;
import java.util.List;

public class TagEmbeddingDao {
    private static final String TAG = "TagEmbeddingDao";
    private LinkDbHelper dbHelper;

    public TagEmbeddingDao(Context context) {
        this.dbHelper = new LinkDbHelper(context);
    }

    public void open() {
        dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    public void saveEmbedding(long tagId, float[] embedding) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(TagEmbeddingDbHelper.COLUMN_TAG_ID, tagId);
        values.put(TagEmbeddingDbHelper.COLUMN_EMBEDDING, arrayToJson(embedding));
        values.put(TagEmbeddingDbHelper.COLUMN_CREATED_AT, System.currentTimeMillis());

        db.insertWithOnConflict(
            TagEmbeddingDbHelper.TABLE_NAME,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        );
        Log.d(TAG, "Saved embedding for tag_id=" + tagId);
    }

    public float[] getEmbedding(long tagId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
            TagEmbeddingDbHelper.TABLE_NAME,
            new String[]{TagEmbeddingDbHelper.COLUMN_EMBEDDING},
            TagEmbeddingDbHelper.COLUMN_TAG_ID + " = ?",
            new String[]{String.valueOf(tagId)},
            null, null, null
        );

        float[] result = null;
        if (cursor.moveToFirst()) {
            String json = cursor.getString(0);
            result = jsonToArray(json);
        }
        cursor.close();
        return result;
    }

    public List<Long> getAllTagIdsWithEmbeddings() {
        List<Long> tagIds = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
            TagEmbeddingDbHelper.TABLE_NAME,
            new String[]{TagEmbeddingDbHelper.COLUMN_TAG_ID},
            null, null, null, null, null
        );

        if (cursor.moveToFirst()) {
            do {
                tagIds.add(cursor.getLong(0));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return tagIds;
    }

    public void deleteEmbedding(long tagId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(
            TagEmbeddingDbHelper.TABLE_NAME,
            TagEmbeddingDbHelper.COLUMN_TAG_ID + " = ?",
            new String[]{String.valueOf(tagId)}
        );
    }

    private String arrayToJson(float[] arr) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (float v : arr) {
                jsonArray.put(v);
            }
            return jsonArray.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create embedding JSON", e);
            return null;
        }
    }

    private float[] jsonToArray(String json) {
        try {
            JSONArray jsonArray = new JSONArray(json);
            float[] arr = new float[jsonArray.length()];
            for (int i = 0; i < jsonArray.length(); i++) {
                arr[i] = (float) jsonArray.getDouble(i);
            }
            return arr;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse embedding JSON", e);
            return null;
        }
    }
}