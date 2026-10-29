package person.notfresh.readingshare.ui.agent;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.agent.bridge.MainThreadConfirmGate;

/**
 * 写操作确认弹窗 —— 把"agent 想干什么"用中文说清楚,让用户能判断。
 *
 * <p>**按钮放在布局里**(见 dialog_agent_confirm.xml 的注释):
 * AlertDialog 的默认按钮(setPositiveButton)点击即 dismiss,回调回来时
 * 宿主可能已 detach —— reading-share §30 踩过的坑。这里用普通 AlertDialog
 * + 布局内按钮,行为可控。</p>
 */
public class AgentConfirmDialog implements MainThreadConfirmGate.ConfirmDialogPresenter {

    private final Context context;

    public AgentConfirmDialog(Context context) {
        this.context = context;
    }

    @Override
    public void present(String toolName, String argsJson, MainThreadConfirmGate.ConfirmResultCallback onResult) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_agent_confirm, null);
        TextView detail = view.findViewById(R.id.confirm_detail);
        detail.setText(buildDetail(toolName, argsJson));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .setCancelable(true)
                .create();

        view.findViewById(R.id.confirm_ok).setOnClickListener(v -> {
            dialog.dismiss();
            onResult.onResult(null);   // null = 同意
        });
        view.findViewById(R.id.confirm_cancel).setOnClickListener(v -> {
            dialog.dismiss();
            onResult.onResult(context.getString(R.string.agent_confirm_rejected));
        });
        // 点外面/返回键 = 取消
        dialog.setOnCancelListener(d -> onResult.onResult(context.getString(R.string.agent_confirm_rejected)));

        dialog.show();
    }

    /**
     * 把工具名 + 参数拼成人能看懂的描述。
     *
     * <p>不直接展示原始 JSON —— 用户看到的是"给链接 42 添加标签「小红书」"
     * 这种句子,而不是 {@code {"link_id":42,"tag":"小红书"}}。</p>
     */
    private String buildDetail(String toolName, String argsJson) {
        StringBuilder sb = new StringBuilder();
        sb.append(toolName);
        sb.append('\n');
        try {
            org.json.JSONObject args = new org.json.JSONObject(
                    argsJson == null ? "{}" : argsJson);
            java.util.Iterator<String> keys = args.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                sb.append(key).append(": ").append(args.optString(key)).append('\n');
            }
        } catch (org.json.JSONException e) {
            sb.append(argsJson);
        }
        return sb.toString().trim();
    }
}
