package person.notfresh.readingshare.ui.agent;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.agent.bridge.AgentBridgeHost;
import person.notfresh.readingshare.agent.bridge.AgentConfig;
import person.notfresh.readingshare.agent.bridge.MainThreadConfirmGate;
import person.notfresh.readingshare.agent.bridge.PythonAgentBridge;

/**
 * AI 助手页 —— 自然语言操作阅读库。
 *
 * <p><b>线程模型</b>:</p>
 * <ul>
 *   <li>agent 内核(Python)跑在**后台单线程 executor** —— 里面要发网络请求</li>
 *   <li>UI 更新统一回主线程(Handler.post)</li>
 *   <li>写操作确认:后台线程 → {@link MainThreadConfirmGate} post 到主线程弹窗
 *       → 阻塞等结果 → 回灌给 Python(详见该类的死锁防护说明)</li>
 * </ul>
 *
 * <p><b>生命周期</b>:Python 解释器是进程级的,Fragment 销毁不影响它;
 * 但确认门必须反注册(否则泄漏 Activity)。</p>
 */
public class AgentFragment extends Fragment {

    private static final String TAG = "AgentFragment";

    private RecyclerView messagesView;
    private AgentMessageAdapter adapter;
    private TextInputEditText input;
    private MaterialButton sendButton;
    private ProgressBar progress;
    private TextView notConfiguredBanner;
    private View emptyState;

    private ExecutorService agentExecutor;
    private Handler mainHandler;

    private AgentBridgeHost bridgeHost;
    private PythonAgentBridge bridge;
    private AgentConfirmDialog confirmDialog;
    /** 确认门 —— 做成字段,onStop 要拿它反注册(防 Activity 泄漏)。 */
    private MainThreadConfirmGate confirmGate;

    /** 正在跑的回合 —— 页面销毁时取消。 */
    private Future<?> runningTurn;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        agentExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.agent_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_agent_new_chat) {
            startNewChat();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 新对话 —— 清掉会话,agent 不记得之前的对话。
     *
     * <p>会话状态在 Python 侧(进程级),所以这里调 Python 的 reset_session。</p>
     */
    private void startNewChat() {
        if (bridge != null) {
            bridge.resetSession();
        }
        adapter.clear();
        showEmptyState();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_agent, container, false);

        messagesView = root.findViewById(R.id.agent_messages);
        adapter = new AgentMessageAdapter();
        messagesView.setLayoutManager(new LinearLayoutManager(requireContext()));
        messagesView.setAdapter(adapter);

        input = root.findViewById(R.id.agent_input);
        sendButton = root.findViewById(R.id.agent_send);
        progress = root.findViewById(R.id.agent_progress);
        notConfiguredBanner = root.findViewById(R.id.agent_not_configured);
        emptyState = root.findViewById(R.id.agent_empty_state);

        sendButton.setOnClickListener(v -> send());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send();
                return true;
            }
            return false;
        });

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        ensureBridge();
        refreshConfiguredBanner();
        showEmptyState();
    }

    /** 空态引导 —— 没有对话时显示。 */
    private void showEmptyState() {
        if (emptyState != null && adapter != null) {
            emptyState.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        // 反注册确认门 —— 不然会泄漏 Activity
        if (bridgeHost != null) {
            bridgeHost.clearConfirmGate(confirmGate);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (runningTurn != null) {
            runningTurn.cancel(true);
            runningTurn = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (agentExecutor != null) {
            agentExecutor.shutdownNow();
        }
    }

    // ── bridge 装配 ────────────────────────────────────────────────────

    /** 建(或复用)bridge,并把确认门挂上。 */
    private void ensureBridge() {
        if (bridgeHost == null) {
            bridgeHost = new AgentBridgeHost(requireContext());
            bridge = new PythonAgentBridge(bridgeHost);
            confirmDialog = new AgentConfirmDialog(requireContext());
        }
        // LLM 配置每次都对齐当前设置 —— 用户可能刚在设置页改了 key/模型
        AgentConfig config = new AgentConfig(requireContext());
        bridgeHost.configureLlm(config.model(), config.baseUrl(), config.apiKey());

        // 确认门注册 —— 只在 onStart ~ onStop 期间有效
        if (confirmGate == null) {
            confirmGate = new MainThreadConfirmGate(confirmDialog);
        }
        bridgeHost.setConfirmGate(confirmGate);

        try {
            bridge.start();
        } catch (RuntimeException e) {
            Log.e(TAG, "Python 内核启动失败", e);
            showError(getString(R.string.agent_error_generic, e.getMessage()));
        }
    }

    private void refreshConfiguredBanner() {
        AgentConfig config = new AgentConfig(requireContext());
        boolean configured = config.isConfigured();
        notConfiguredBanner.setVisibility(configured ? View.GONE : View.VISIBLE);
    }

    // ── 发消息 ────────────────────────────────────────────────────────

    private void send() {
        if (bridge == null || !bridge.isReady()) {
            showError(getString(R.string.agent_error_generic, "agent 内核未就绪"));
            return;
        }
        String text = input.getText() == null ? "" : input.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        input.setText("");
        adapter.addMessage(AgentMessageAdapter.Message.ROLE_USER, text);
        showEmptyState();
        setBusy(true);

        // agent 循环跑在后台 —— 里面要等 LLM 的网络响应
        runningTurn = agentExecutor.submit(() -> {
            String resultJson = bridge.run(text);
            mainHandler.post(() -> {
                setBusy(false);
                handleResult(resultJson);
            });
        });
    }

    /** 解析 agent 返回的结果并展示。 */
    private void handleResult(String resultJson) {
        try {
            JSONObject result = new JSONObject(resultJson);
            String error = result.optString("error", "");
            String exitReason = result.optString("exit_reason", "");
            String finalResponse = result.optString("final_response", "");

            if (!error.isEmpty() && !"completed".equals(exitReason)) {
                showError(getString(R.string.agent_error_generic, error));
                return;
            }
            showToolCalls(result.optJSONArray("messages"));
            if (finalResponse.trim().isEmpty()) {
                adapter.addMessage(AgentMessageAdapter.Message.ROLE_AGENT,
                        getString(R.string.agent_empty_response));
                return;
            }
            adapter.addMessage(AgentMessageAdapter.Message.ROLE_AGENT, finalResponse);
        } catch (JSONException e) {
            showError(getString(R.string.agent_error_generic, e.getMessage()));
        }
    }

    /**
     * 从消息流里抽出工具调用,显示成流水标签。
     *
     * <p>assistant 消息带 {@code tool_calls} 数组 —— 那是模型请求调用的工具;
     * 对应的 role=tool 消息是执行结果。两者按顺序配对,这里只显示工具名
     * 和次数(结果正文由 agent 自己在最终回答里总结)。</p>
     */
    private void showToolCalls(JSONArray messages) {
        if (messages == null) {
            return;
        }
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject msg = messages.optJSONObject(i);
            if (msg == null || !"assistant".equals(msg.optString("role"))) {
                continue;
            }
            JSONArray calls = msg.optJSONArray("tool_calls");
            if (calls == null) {
                continue;
            }
            for (int j = 0; j < calls.length(); j++) {
                JSONObject call = calls.optJSONObject(j);
                if (call == null) {
                    continue;
                }
                JSONObject fn = call.optJSONObject("function");
                String name = fn == null ? "" : fn.optString("name");
                if (!name.isEmpty()) {
                    counts.merge(name, 1, Integer::sum);
                }
            }
        }
        for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
            String label = entry.getKey()
                    + (entry.getValue() > 1 ? " × " + entry.getValue() : "");
            adapter.addMessage(AgentMessageAdapter.Message.ROLE_TOOL, label);
        }
    }

    private void showError(String message) {
        adapter.addMessage(AgentMessageAdapter.Message.ROLE_ERROR, message);
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!busy);
        input.setEnabled(!busy);
    }
}
