package person.notfresh.readingshare.agent.bridge;

import android.content.Context;
import android.util.Log;

/**
 * Java 侧的能力宿主 —— **Python 持有的那个 Java 对象**。
 *
 * <p>Python 侧(agent_bridge.py)通过本对象拿三样东西:</p>
 * <ol>
 *   <li>工具 schema —— {@link #schemas_json()}</li>
 *   <li>工具执行 —— {@link #invoke(String, String)}</li>
 *   <li>写操作确认 —— {@link #confirm(String, String)}</li>
 * </ol>
 *
 * <p>**为什么单独一个类而不是把 PythonAgentBridge 直接塞给 Python**:
 * PythonAgentBridge 持有 PyObject(Python 侧的引用),Python 再持有它
 * 就构成跨语言引用环 —— Chaquopy 文档明确警告这种环**两个语言的 GC 都检测不到**,
 * 会内存泄漏。拆开之后:Python 只持有本类(不持有任何 Python 引用),
 * 环就断了。</p>
 *
 * <p>本类的生命周期跟随 Application(只持有 application context),
 * 不会泄漏 Activity。确认门由 UI 层注册/反注册。</p>
 */
public final class AgentBridgeHost {

    private static final String TAG = "AgentBridgeHost";

    private final Context appContext;
    private final AgentToolRegistry toolRegistry;
    private final AgentTools tools;

    /** 当前确认门 —— UI 层注册进来,用完反注册。volatile 保证跨线程可见。 */
    private volatile AgentConfirmGate confirmGate;

    /** LLM 配置。 */
    private volatile String modelName = "deepseek-chat";
    private volatile String baseUrl = "https://api.deepseek.com/v1";
    private volatile String apiKey = "";

    public AgentBridgeHost(Context context) {
        this.appContext = context.getApplicationContext();
        this.toolRegistry = new AgentToolRegistry();
        this.tools = new AgentTools(this.appContext);
    }

    // ── 生命周期 ──────────────────────────────────────────────────────

    /** 注册确认门(UI 层 onStart 调用)。 */
    public void setConfirmGate(AgentConfirmGate gate) {
        this.confirmGate = gate;
    }

    /** 反注册确认门(UI 层 onStop 调用)—— 防止 Activity 泄漏。 */
    public void clearConfirmGate(AgentConfirmGate gate) {
        if (this.confirmGate == gate) {
            this.confirmGate = null;
        }
    }

    /** 设置 LLM 配置。 */
    public void configureLlm(String model, String baseUrl, String apiKey) {
        if (model != null && !model.isEmpty()) this.modelName = model;
        if (baseUrl != null && !baseUrl.isEmpty()) this.baseUrl = baseUrl;
        if (apiKey != null) this.apiKey = apiKey;
    }

    // ── 给 Python 调用的方法 ──────────────────────────────────────────
    // 方法名用 snake_case,Python 侧直接 bridge.invoke(...) 即可。

    /** 工具 schema 列表(JSON 数组字符串)。 */
    public String schemas_json() {
        return toolRegistry.schemasJson();
    }

    /** 执行一个工具,返回结果 JSON 字符串。 */
    public String invoke(String name, String argsJson) {
        return tools.invoke(name, argsJson);
    }

    /**
     * 请求用户确认写操作。
     *
     * @return 空串 = 同意;非空 = 拒绝原因
     */
    public String confirm(String toolName, String argsJson) {
        AgentConfirmGate gate = this.confirmGate;
        if (gate == null) {
            // 没有确认门 = 不放行写操作(宁可失败也不能偷偷改用户的库)
            return "当前没有可用的确认通道,拒绝执行写操作";
        }
        try {
            String reason = gate.requestConfirm(toolName, argsJson);
            return reason == null ? "" : reason;
        } catch (Exception e) {
            Log.e(TAG, "确认流程异常: " + toolName, e);
            return "确认流程异常:" + e.getMessage();
        }
    }

    /**
     * 该工具是否需要确认 —— **以 Java 侧 registry 为准**,
     * Python 侧不再硬编码一份工具名列表(单一来源)。
     */
    public boolean requires_confirm(String toolName) {
        return toolRegistry.requiresConfirm(toolName);
    }

    public String model_name() {
        return modelName;
    }

    public String base_url() {
        return baseUrl;
    }

    public String api_key() {
        return apiKey;
    }

    /** 可用工具名(逗号分隔)—— 供日志排查。 */
    public String tool_names() {
        return String.join(",", toolRegistry.names());
    }
}
