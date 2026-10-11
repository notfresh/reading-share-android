package person.notfresh.readingshare.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.model.OverrideAction;
import person.notfresh.readingshare.model.WebViewOverrideRule;

/**
 * 添加/编辑一条拦截名单规则的 DialogFragment(单一入口)。
 *
 * <p>两个使用方:</p>
 * <ul>
 *   <li>设置抽屉「+ 添加规则」按钮 — prefillUrl = null/空</li>
 *   <li>WebView toolbar「配置本页跳转」 — prefillUrl = 当前页面 URL,作 pattern 默认值</li>
 * </ul>
 *
 * <p>两种回调路径:</p>
 * <ol>
 *   <li>宿主是 fragment(含 SettingFragment):{@link OnRuleSubmitListener}</li>
 *   <li>宿主是 activity(WebViewActivity):{@link OnRuleSubmitListenerActivity}</li>
 * </ol>
 * 用 instanceof 探测,不引静态状态/不引回调注册机制(跟 §24 "三种合理路径优先级" 规则一致)。
 */
public class AddOverrideRuleDialog extends DialogFragment {

    public static final String TAG = "AddOverrideRuleDialog";
    private static final String ARG_PREFILL = "prefill_url";

    /** 父 fragment 宿主(SetFragment)实现的回调。 */
    public interface OnRuleSubmitListener {
        void onRuleSubmitted(WebViewOverrideRule rule);
    }

    /** Activity 宿主(WebViewActivity)实现的回调。 */
    public interface OnRuleSubmitListenerActivity {
        void onRuleSubmittedFromActivity(WebViewOverrideRule rule);
    }

    /**
     * 工厂方法。prefillUrl 为 null/空 = 不预填(抽屉入口);非空 = 预填到 pattern 输入框(WebView 入口)。
     */
    public static void showDialog(FragmentManager fm, String prefillUrl) {
        AddOverrideRuleDialog d = new AddOverrideRuleDialog();
        Bundle args = new Bundle();
        args.putString(ARG_PREFILL, prefillUrl);
        d.setArguments(args);
        d.show(fm, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String prefill = getArguments() != null ? getArguments().getString(ARG_PREFILL) : null;

        View view = requireActivity().getLayoutInflater()
                .inflate(R.layout.dialog_add_override_rule, null, false);
        EditText patternInput = view.findViewById(R.id.override_rule_pattern_input);
        RadioGroup actionGroup = view.findViewById(R.id.override_rule_action_group);
        RadioButton askRb = view.findViewById(R.id.override_rule_action_ask);
        RadioButton openRb = view.findViewById(R.id.override_rule_action_open);
        RadioButton blockRb = view.findViewById(R.id.override_rule_action_block);

        if (!TextUtils.isEmpty(prefill)) {
            patternInput.setText(prefill);
            patternInput.setSelection(prefill.length());
        }
        askRb.setChecked(true); // 默认 ASK

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setTitle("添加拦截规则")
                .setView(view)
                .setPositiveButton("确定", null) // 在 show() 后手动绑定,避免自动 dismiss 吞错
                .setNegativeButton("取消", (d, w) -> dismiss())
                .create();

        dialog.setOnShowListener(d -> {
            Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            ok.setOnClickListener(v -> {
                String pattern = patternInput.getText().toString().trim();
                if (TextUtils.isEmpty(pattern)) {
                    Toast.makeText(requireContext(), "pattern 不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                int checkedId = actionGroup.getCheckedRadioButtonId();
                OverrideAction action;
                if (checkedId == R.id.override_rule_action_open) {
                    action = OverrideAction.OPEN;
                } else if (checkedId == R.id.override_rule_action_block) {
                    action = OverrideAction.BLOCK;
                } else {
                    action = OverrideAction.ASK;
                }
                WebViewOverrideRule rule = new WebViewOverrideRule(pattern, action); // pattern 是 variable,作为 contextUrl 传入

                // 回调分两条路径:parent fragment / activity
                boolean delivered = false;
                if (getParentFragment() instanceof OnRuleSubmitListener) {
                    ((OnRuleSubmitListener) getParentFragment()).onRuleSubmitted(rule);
                    delivered = true;
                }
                if (!delivered && getActivity() instanceof OnRuleSubmitListenerActivity) {
                    ((OnRuleSubmitListenerActivity) getActivity()).onRuleSubmittedFromActivity(rule);
                    delivered = true;
                }
                if (!delivered) {
                    Toast.makeText(requireContext(), "未找到回调宿主,规则未保存", Toast.LENGTH_SHORT).show();
                    return;
                }
                dismiss();
            });
        });

        return dialog;
    }
}
