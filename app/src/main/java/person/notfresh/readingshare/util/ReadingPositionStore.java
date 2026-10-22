package person.notfresh.readingshare.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 「读到哪了」的持久化（2.3.20）。
 *
 * <p>为什么只存这个：原来那套「把整个 WebView 实例寄存进 Map」的做法，命中后回调还绑在
 * 已销毁的 Activity 上、还泄漏 Activity、还只在「存档返回」一条路径生效。简化为
 * 「URL + 滚动比例」这一条最小信息：重新加载后滚回原位置即可，跨杀进程也有效。</p>
 *
 * <p>存储：SharedPreferences 里一个 JSON 数组，元素 [url, percent]，按写入顺序保留最近
 * {@link #MAX_ENTRIES} 条（FIFO）。不新增数据库、不新增权限。</p>
 */
public final class ReadingPositionStore {

    private static final String TAG = "ReadingPosition";
    private static final String PREFS = "reading_position";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 50;

    private ReadingPositionStore() {
        // utility class
    }

    /** 记录某 URL 的阅读位置；percent 为 0..1，非法值直接忽略。 */
    public static void save(Context context, String url, float percent) {
        if (context == null || url == null || url.isEmpty()) return;
        if (percent < 0f) return;
        try {
            List<String[]> entries = readEntries(context);
            removeUrl(entries, url);
            entries.add(new String[]{url, String.valueOf(Math.min(1f, percent))});
            while (entries.size() > MAX_ENTRIES) {
                entries.remove(0);
            }
            writeEntries(context, entries);
        } catch (Exception e) {
            Log.w(TAG, "save failed url=" + url, e);
        }
    }

    /** 读取阅读位置；没有记录返回 -1。 */
    public static float load(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) return -1f;
        try {
            for (String[] entry : readEntries(context)) {
                if (url.equals(entry[0])) {
                    return Float.parseFloat(entry[1]);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "load failed url=" + url, e);
        }
        return -1f;
    }

    private static List<String[]> readEntries(Context context) {
        List<String[]> entries = new ArrayList<>();
        SharedPreferences prefs = prefs(context);
        String raw = prefs.getString(KEY_ENTRIES, null);
        if (raw == null || raw.isEmpty()) return entries;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONArray pair = array.optJSONArray(i);
                if (pair == null || pair.length() < 2) continue;
                entries.add(new String[]{pair.getString(0), pair.getString(1)});
            }
        } catch (Exception e) {
            Log.w(TAG, "corrupted entries, reset", e);
            prefs.edit().remove(KEY_ENTRIES).apply();
        }
        return entries;
    }

    private static void writeEntries(Context context, List<String[]> entries) throws Exception {
        JSONArray array = new JSONArray();
        for (String[] entry : entries) {
            JSONArray pair = new JSONArray();
            pair.put(entry[0]);
            pair.put(entry[1]);
            array.put(pair);
        }
        prefs(context).edit().putString(KEY_ENTRIES, array.toString()).apply();
    }

    private static void removeUrl(List<String[]> entries, String url) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (url.equals(entries.get(i)[0])) entries.remove(i);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
