package person.notfresh.readingshare.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.model.OverrideAction;
import person.notfresh.readingshare.model.WebViewOverrideRule;

/**
 * 拦截名单 RecyclerView Adapter。
 * 单一职责:把 List<WebViewOverrideRule> 渲染成行 + 把行变更回调给宿主。
 *
 * <p>主真相源 = 入参 rules List;外部调用方全量 setRules(...) 后本 adapter 重渲染。
 * 行内 RadioGroup 选中变化通过 onRuleActionChanged 回调给宿主;宿主负责 update(idx, rule) 持久化。</p>
 */
public class OverrideRuleAdapter extends RecyclerView.Adapter<OverrideRuleAdapter.VH> {

    public interface OnRuleChangeListener {
        void onRuleActionChanged(int position, OverrideAction newAction);
        void onRuleDeleteClicked(int position);
    }

    private final List<WebViewOverrideRule> data = new ArrayList<>();
    private final OnRuleChangeListener listener;

    public OverrideRuleAdapter(OnRuleChangeListener listener) {
        this.listener = listener;
    }

    /** 全量替换 + 重渲染。仅在宿主层规则列表真正变化时调。 */
    public void setRules(List<WebViewOverrideRule> rules) {
        data.clear();
        if (rules != null) data.addAll(rules);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_override_rule, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        WebViewOverrideRule r = data.get(position);
        h.pattern.setText(r.getPattern());

        // 解绑→ setChecked → 装新 listener(防止复用 ViewHolder 触发残留 listener)
        h.actionGroup.setOnCheckedChangeListener(null);
        h.askRb.setChecked(r.getAction() == OverrideAction.ASK);
        h.openRb.setChecked(r.getAction() == OverrideAction.OPEN);
        h.blockRb.setChecked(r.getAction() == OverrideAction.BLOCK);
        h.actionGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int pos = h.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            OverrideAction a;
            if (checkedId == R.id.override_rule_action_open) a = OverrideAction.OPEN;
            else if (checkedId == R.id.override_rule_action_block) a = OverrideAction.BLOCK;
            else a = OverrideAction.ASK;
            listener.onRuleActionChanged(pos, a);
        });

        h.deleteBtn.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            listener.onRuleDeleteClicked(pos);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView pattern;
        final RadioGroup actionGroup;
        final RadioButton askRb;
        final RadioButton openRb;
        final RadioButton blockRb;
        final Button deleteBtn;

        VH(@NonNull View itemView) {
            super(itemView);
            pattern = itemView.findViewById(R.id.override_rule_pattern_text);
            actionGroup = itemView.findViewById(R.id.override_rule_action_group);
            askRb = itemView.findViewById(R.id.override_rule_action_ask);
            openRb = itemView.findViewById(R.id.override_rule_action_open);
            blockRb = itemView.findViewById(R.id.override_rule_action_block);
            deleteBtn = itemView.findViewById(R.id.override_rule_delete_btn);
        }
    }
}
