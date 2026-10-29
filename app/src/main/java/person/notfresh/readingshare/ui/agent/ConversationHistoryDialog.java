package person.notfresh.readingshare.ui.agent;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.R;

/**
 * 对话历史弹窗:搜索框 + 会话列表。
 *
 * <p><b>跟宿主怎么通信</b>:走 {@code getParentFragment() instanceof Host} ——
 * 项目约定(DialogFragment 取宿主的唯一正路),不用 static Map / token 注册。</p>
 *
 * <p><b>为什么不用 AlertDialog 的默认按钮</b>:这里根本没有按钮,整块内容都是自定义视图;
 * 点条目 → 先回调宿主再 dismiss,保证回调里宿主还 attached(§30 那次 detach 崩溃的反面案例)。</p>
 */
public class ConversationHistoryDialog extends DialogFragment {

    public interface Host {
        /** 拉全量会话列表(JSON,见 Python list_conversations 的返回结构)。 */
        String onLoadConversations();

        /** 用户选了某段会话 —— 宿主负责载入 + 重建界面。 */
        void onConversationPicked(String conversationId);
    }

    public static final String TAG = "ConversationHistoryDialog";

    private static final int LIST_MAX_HEIGHT_DP = 420;
    private static final int ITEM_HEIGHT_DP = 64;

    private ConversationAdapter adapter;
    private RecyclerView list;
    private TextView emptyView;
    private TextInputEditText searchInput;
    private boolean loaded;

    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.dialog_conversation_history, container, false);
        list = root.findViewById(R.id.history_list);
        emptyView = root.findViewById(R.id.history_empty);
        searchInput = root.findViewById(R.id.history_search_input);

        adapter = new ConversationAdapter(item -> {
            Host host = hostOrNull();
            if (host != null) {
                host.onConversationPicked(item.id);
            }
            dismissAllowingStateLoss();
        });
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                adapter.filter(s == null ? "" : s.toString());
                refreshEmptyState();
                resizeList();
            }
        });

        load();
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window != null) {
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    /** 从宿主拉一次全量列表(只拉一次,过滤在内存里做)。 */
    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Host host = hostOrNull();
        if (host == null) {
            return;
        }
        List<ConversationAdapter.Item> items = new ArrayList<>();
        String currentId = "";
        try {
            JSONObject payload = new JSONObject(host.onLoadConversations());
            currentId = payload.optString("current_id", "");
            JSONArray arr = payload.optJSONArray("conversations");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) {
                        continue;
                    }
                    items.add(new ConversationAdapter.Item(
                            o.optString("id", ""),
                            o.optString("title", getString(R.string.agent_title_default)),
                            (long) o.optDouble("updated_at", 0),
                            o.optInt("rounds", 0)));
                }
            }
        } catch (JSONException ignored) {
            // 解析失败 = 列表为空,空态会兜住
        }
        adapter.submit(items, currentId);
        refreshEmptyState();
        resizeList();
    }

    private void refreshEmptyState() {
        boolean empty = adapter.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /** 列表限高:条目少时自适应,多了不超过 LIST_MAX_HEIGHT_DP。 */
    private void resizeList() {
        int rows = Math.min(adapter.size(), LIST_MAX_HEIGHT_DP / ITEM_HEIGHT_DP);
        int heightPx = Math.max(1, (int) (rows * ITEM_HEIGHT_DP * getResources().getDisplayMetrics().density));
        ViewGroup.LayoutParams lp = list.getLayoutParams();
        lp.height = heightPx;
        list.setLayoutParams(lp);
    }

    @Nullable
    private Host hostOrNull() {
        Fragment parent = getParentFragment();
        return parent instanceof Host ? (Host) parent : null;
    }
}
