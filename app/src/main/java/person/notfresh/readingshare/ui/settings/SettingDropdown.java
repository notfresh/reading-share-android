package person.notfresh.readingshare.ui.settings;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.R;

/**
 * 设置页的下拉选择（Material exposed dropdown，2.3.19.4）。
 *
 * <p>替换原来裸 {@code Spinner + android.R.layout.simple_spinner_item} 的 Holo 观感：
 * 现在是 Material 填充框 + 展开菜单，与项目里既有的 {@code TextInputLayout} 输入框同一套视觉。
 * 只在选中项变化时回调，程序化 setText 不会触发回调（内部 suppress）。</p>
 */
public final class SettingDropdown {

    public interface OnSelected {
        void onSelected(int index);
    }

    private final Context ctx;
    private final TextInputLayout layout;
    private final MaterialAutoCompleteTextView input;
    private final List<String> options = new ArrayList<>();

    private OnSelected listener;
    private boolean suppress = true;

    private SettingDropdown(Context ctx, TextInputLayout layout, MaterialAutoCompleteTextView input) {
        this.ctx = ctx;
        this.layout = layout;
        this.input = input;
    }

    /** 在当前容器末尾追加一个下拉，返回可操作句柄。 */
    public static SettingDropdown attach(Context ctx, ViewGroup container) {
        View view = LayoutInflater.from(ctx).inflate(R.layout.view_setting_dropdown, container, false);
        container.addView(view);
        TextInputLayout layout = (TextInputLayout) view;
        MaterialAutoCompleteTextView input = view.findViewById(R.id.dropdown_input);

        final SettingDropdown dropdown = new SettingDropdown(ctx, layout, input);
        input.setOnItemClickListener((parent, itemView, position, id) -> {
            if (dropdown.suppress) return;
            dropdown.showSelection(position);
            if (dropdown.listener != null) dropdown.listener.onSelected(position);
        });
        return dropdown;
    }

    public SettingDropdown setHint(int hintRes) {
        layout.setHint(ctx.getString(hintRes));
        return this;
    }

    /** 重填选项；selected 会被夹到合法范围。options 为空时整个下拉隐藏。 */
    public void setOptions(List<String> newOptions, int selected) {
        options.clear();
        if (newOptions != null) options.addAll(newOptions);
        if (options.isEmpty()) {
            layout.setVisibility(View.GONE);
            return;
        }
        layout.setVisibility(View.VISIBLE);
        input.setAdapter(new ArrayAdapter<>(ctx, R.layout.item_dropdown_option, options));
        showSelection(clamp(selected));
    }

    public int selectedIndex() {
        String text = input.getText() == null ? "" : input.getText().toString();
        int index = options.indexOf(text);
        return index < 0 ? 0 : index;
    }

    public void setOnSelected(OnSelected listener) {
        this.listener = listener;
    }

    public void setVisibility(int visibility) {
        layout.setVisibility(visibility);
    }

    /** 程序化改文字，不触发 OnSelected。 */
    private void showSelection(int index) {
        if (options.isEmpty()) return;
        boolean previous = suppress;
        suppress = true;
        input.setText(options.get(clamp(index)), false);
        suppress = previous;
    }

    private int clamp(int index) {
        if (index < 0) return 0;
        return Math.min(index, Math.max(0, options.size() - 1));
    }
}
