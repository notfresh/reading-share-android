package person.notfresh.readingshare.agent.bridge;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Agent 的 LLM 配置存储。
 *
 * <p>沿用项目既有的 {@code getSharedPreferences("settings", MODE_PRIVATE)}
 * 约定(见 SettingFragment 的 reading_mode / nav_button_style 等 key)。</p>
 *
 * <p><b>关于 API key 的存储</b>:目前明文存 SharedPreferences。
 * Keystore 加密是更稳的做法,但那会引入一层新的持久化维度
 * (Q3 表面积 ∝ 价值)—— 第一刀自用场景先明文,后续再加固。
 * 真要加,应该单独一刀做,不要混在集成里。</p>
 */
public final class AgentConfig {

    private static final String PREFS_NAME = "settings";

    private static final String KEY_MODEL = "agent_model";
    private static final String KEY_BASE_URL = "agent_base_url";
    private static final String KEY_API_KEY = "agent_api_key";

    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com/v1";

    private final SharedPreferences prefs;

    public AgentConfig(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String model() {
        return prefs.getString(KEY_MODEL, DEFAULT_MODEL);
    }

    public String baseUrl() {
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL);
    }

    public String apiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }

    /** 是否已配置到可以跑 agent 的程度。 */
    public boolean isConfigured() {
        String key = apiKey();
        return key != null && !key.trim().isEmpty();
    }

    public void save(String model, String baseUrl, String apiKey) {
        SharedPreferences.Editor editor = prefs.edit();
        if (model != null && !model.trim().isEmpty()) {
            editor.putString(KEY_MODEL, model.trim());
        }
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            editor.putString(KEY_BASE_URL, baseUrl.trim());
        }
        if (apiKey != null) {
            editor.putString(KEY_API_KEY, apiKey.trim());
        }
        editor.apply();
    }
}
