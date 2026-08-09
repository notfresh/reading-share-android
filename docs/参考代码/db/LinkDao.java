package your.package.name.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据库访问类 - 每日统计查询参考
 *
 * 迁移说明：
 * 1. 将 getDailyStatistics() 方法复制到你的 DAO 类中
 * 2. 确保你的数据库中有一个 links 表，包含 timestamp 字段（毫秒级时间戳）
 */
public class LinkDao {

    private static final String TAG = "LinkDao";
    private SQLiteDatabase database;

    public LinkDao(Context context) {
        // 初始化数据库
    }

    /**
     * 获取每日链接统计
     * @return Map<String, Integer>  key: 日期 (yyyy-MM-dd), value: 该日链接数量
     */
    public Map<String, Integer> getDailyStatistics() {
        Log.d(TAG, "getDailyStatistics: 开始查询每日统计数据");
        Map<String, Integer> statistics = new HashMap<>();
        Cursor cursor = null;
        try {
            String query = "SELECT date(timestamp/1000, 'unixepoch') as date, COUNT(*) as count " +
                          "FROM links GROUP BY date(timestamp/1000, 'unixepoch')";
            Log.d(TAG, "getDailyStatistics: SQL查询: " + query);
            cursor = database.rawQuery(query, null);

            Log.d(TAG, "getDailyStatistics: 查询结果行数: " + cursor.getCount());
            while (cursor.moveToNext()) {
                String date = cursor.getString(0);
                int count = cursor.getInt(1);
                Log.d(TAG, "getDailyStatistics: 统计数据: " + date + " -> " + count);
                statistics.put(date, count);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        Log.d(TAG, "getDailyStatistics: 统计完成，共 " + statistics.size() + " 条数据");
        return statistics;
    }
}
