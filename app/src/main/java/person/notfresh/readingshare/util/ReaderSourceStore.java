package person.notfresh.readingshare.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import person.notfresh.readingshare.model.ReaderSourceSpec;

/**
 * 阅读页数据来源的持久化（单一真相源 = SharedPreferences "settings" / reader_source_json）。
 * 读写入口只有这一个，UI 与内核都不直接碰 SP。
 */
public final class ReaderSourceStore {

    public static final String PREFS_NAME = "settings";
    public static final String KEY_SPEC_JSON = "reader_source_json";

    private static final String TAG = "ReaderSourceStore";

    private ReaderSourceStore() {
        // utility class
    }

    public static ReaderSourceSpec load(Context ctx) {
        if (ctx == null) return ReaderSourceSpec.all();
        String raw = prefs(ctx).getString(KEY_SPEC_JSON, "");
        if (raw == null || raw.isEmpty()) return ReaderSourceSpec.all();
        try {
            return ReaderSourceSpec.fromJson(new JSONObject(raw));
        } catch (JSONException e) {
            Log.e(TAG, "load: bad json, falling back to ALL. raw=" + raw, e);
            return ReaderSourceSpec.all();
        }
    }

    public static void save(Context ctx, ReaderSourceSpec spec) {
        if (ctx == null || spec == null) return;
        try {
            prefs(ctx).edit().putString(KEY_SPEC_JSON, spec.toJson().toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "save failed for spec type=" + spec.getType(), e);
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
