package person.notfresh.readingshare.embedding;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Storage helper for custom tag similarity rules.
 *
 * Priority:
 * 1) internal storage file (user editable)
 * 2) assets default file (fallback)
 */
public final class TagSimilarityOverrideStore {
    private static final String TAG = "TagSimilarityStore";
    public static final String FILE_NAME = "tag_similarity_overrides.json";
    private static final String DEFAULT_ASSET_NAME = "tag_similarity_overrides.json";

    private TagSimilarityOverrideStore() {
    }

    public static final class Rule {
        public final String a;
        public final String b;
        public final float boost;

        public Rule(String a, String b, float boost) {
            this.a = a;
            this.b = b;
            this.boost = boost;
        }
    }

    public static List<Rule> loadRules(Context context) {
        try {
            String json = tryReadInternal(context);
            if (json == null) {
                json = tryReadAsset(context);
            }
            if (json == null) {
                return new ArrayList<>();
            }
            return parseRules(json);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load rules", e);
            return new ArrayList<>();
        }
    }

    public static void saveRules(Context context, List<Rule> rules) throws IOException {
        JSONObject root = new JSONObject();
        JSONArray pairBoosts = new JSONArray();
        try {
            for (Rule rule : rules) {
                if (rule == null) {
                    continue;
                }
                String a = normalize(rule.a);
                String b = normalize(rule.b);
                float boost = Math.max(0f, rule.boost);
                if (a.isEmpty() || b.isEmpty() || boost <= 0f) {
                    continue;
                }

                JSONObject item = new JSONObject();
                item.put("a", a);
                item.put("b", b);
                item.put("boost", boost);
                pairBoosts.put(item);
            }
            root.put("pairBoosts", pairBoosts);
        } catch (Exception e) {
            throw new IOException("Failed to serialize rules", e);
        }

        byte[] data = root.toString().getBytes("UTF-8");
        java.io.FileOutputStream outputStream = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE);
        try {
            outputStream.write(data);
            outputStream.flush();
        } finally {
            outputStream.close();
        }
    }

    public static Map<String, Float> toBoostMap(List<Rule> rules) {
        Map<String, Float> boosts = new HashMap<>();
        for (Rule rule : rules) {
            if (rule == null) {
                continue;
            }
            String a = normalize(rule.a);
            String b = normalize(rule.b);
            float boost = Math.max(0f, rule.boost);
            if (a.isEmpty() || b.isEmpty() || boost <= 0f) {
                continue;
            }

            String key = pairKey(a, b);
            Float existing = boosts.get(key);
            if (existing == null || boost > existing) {
                boosts.put(key, boost);
            }
        }
        return boosts;
    }

    public static String pairKey(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.compareTo(nb) <= 0) {
            return na + "||" + nb;
        }
        return nb + "||" + na;
    }

    private static List<Rule> parseRules(String json) throws Exception {
        List<Rule> rules = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray pairBoosts = root.optJSONArray("pairBoosts");
        if (pairBoosts == null) {
            return rules;
        }

        for (int i = 0; i < pairBoosts.length(); i++) {
            JSONObject item = pairBoosts.optJSONObject(i);
            if (item == null) {
                continue;
            }

            String a = normalize(item.optString("a", ""));
            String b = normalize(item.optString("b", ""));
            float boost = (float) item.optDouble("boost", 0.0);
            if (a.isEmpty() || b.isEmpty() || boost <= 0f) {
                continue;
            }
            rules.add(new Rule(a, b, boost));
        }
        return rules;
    }

    private static String tryReadInternal(Context context) {
        try {
            FileInputStream input = context.openFileInput(FILE_NAME);
            try {
                return readAll(input);
            } finally {
                input.close();
            }
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String tryReadAsset(Context context) {
        try {
            InputStream input = context.getAssets().open(DEFAULT_ASSET_NAME);
            try {
                return readAll(input);
            } finally {
                input.close();
            }
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, len);
        }
        return output.toString("UTF-8");
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT).trim();
    }
}
