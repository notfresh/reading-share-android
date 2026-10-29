package person.notfresh.readingshare.agent.bridge;

import android.content.Context;
import android.util.Log;

import com.chaquo.python.GenericPlatform;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Chaquopy 封装 —— Java ↔ Python 的**启动与调度**通道。
 *
 * <p>Python 侧跑的是 minimal-agent-v2 的 agent 内核(拷进
 * {@code app/src/main/python/} 的 7 个模块 + {@code agent_bridge.py})。</p>
 *
 * <p>**职责边界**:
 * <ul>
 *   <li>本类:启动 Python、注入 {@link AgentBridgeHost}、跑回合、拿结果</li>
 *   <li>{@link AgentBridgeHost}:Python 持有的能力宿主(schema / 执行 / 确认)</li>
 * </ul>
 *
 * <p>拆两个类是为了**断开跨语言引用环** —— Python 持有 Host,
 * 而 Host 不持有任何 Python 引用(PyObject 只在本类里)。
 * Chaquopy 文档明确警告:Python 对象 ↔ Java 对象互相持有的环,
 * 两个语言的 GC 都检测不到 → 内存泄漏。</p>
 *
 * <p>**线程模型**:agent 循环跑在**后台线程**。工具执行碰 SQLite 没问题;
 * 写操作的确认弹窗必须在主线程 —— 由 {@link AgentConfirmGate} 的实现方切换。</p>
 */
public final class PythonAgentBridge {

    private static final String TAG = "PythonAgentBridge";

    /** Python 模块名(对应 app/src/main/python/agent_bridge.py)。 */
    private static final String PY_MODULE = "agent_bridge";

    private static boolean sPythonStarted = false;

    private final AgentBridgeHost host;
    private PyObject pyModule;

    public PythonAgentBridge(AgentBridgeHost host) {
        this.host = host;
    }

    // ── 启动 ──────────────────────────────────────────────────────────

    /**
     * 启动 Python 并把 {@link AgentBridgeHost} 注入 Python 侧。
     *
     * <p>**幂等** —— Python 解释器进程级只启动一次;重复调用只重注入 Host。</p>
     *
     * @throws RuntimeException Python 启动失败
     */
    public synchronized void start() {
        try {
            if (!sPythonStarted) {
                if (!Python.isStarted()) {
                    Python.start(new GenericPlatform());
                }
                sPythonStarted = true;
            }
            pyModule = Python.getInstance().getModule(PY_MODULE);
            // 把 Java 宿主对象交给 Python —— Python 侧存为全局 _JAVA_BRIDGE。
            // 用 PyObject.fromJava 包一层,Python 侧拿到的是 jclass 代理。
            pyModule.callAttr("set_java_bridge", PyObject.fromJava(host));
            Log.i(TAG, "Python agent 内核已启动,可用工具: " + host.tool_names());
        } catch (Exception e) {
            Log.e(TAG, "启动 Python 失败", e);
            throw new RuntimeException("启动 Python agent 内核失败: " + e.getMessage(), e);
        }
    }

    /** Python 是否已就绪。 */
    public boolean isReady() {
        return sPythonStarted && pyModule != null;
    }

    // ── 对外:跑一个回合 ───────────────────────────────────────────────

    /**
     * 跑一个完整 agent 回合。
     *
     * <p>**必须在后台线程调用** —— 里面会发网络请求(等 LLM 响应)。</p>
     *
     * @param userText 用户自然语言输入
     * @return 结果 JSON 字符串:
     *         {@code {final_response, messages, api_calls, tool_calls, exit_reason, error, tools}}
     */
    public String run(String userText) {
        if (!isReady()) {
            return errorJson("Python agent 内核未就绪");
        }
        try {
            return pyModule.callAttr("run", userText).toString();
        } catch (Exception e) {
            Log.e(TAG, "agent 回合异常", e);
            return errorJson(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String errorJson(String message) {
        try {
            JSONObject o = new JSONObject();
            o.put("final_response", "");
            o.put("messages", new JSONArray());
            o.put("api_calls", 0);
            o.put("tool_calls", 0);
            o.put("exit_reason", "error:bridge");
            o.put("error", message);
            o.put("tools", new JSONArray());
            return o.toString();
        } catch (JSONException e) {
            return "{\"exit_reason\":\"error:bridge\",\"error\":\"unknown\"}";
        }
    }
}
