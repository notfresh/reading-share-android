package person.notfresh.readingshare.ui.tag;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.util.PinnedTagsManager;

/**
 * 选择要置顶的 tag 的对话框。
 * <p>
 * 列表布局:已置顶(按顺序)在前 → 未置顶(按字母)在后。每行一个 CheckBox。
 * 限制:最多 {@link PinnedTagsManager#MAX_PINNED} 个。
 * <p>
 * 关闭方式:点取消 → dismiss;点保存 → 写入 SharedPreferences,通过 {@link OnPinnedChangedListener} 回调宿主刷新 tag 区。
 */
public class PinnedTagsPickerDialog extends DialogFragment {

    /** 宿主实现:保存完毕后回调,用于刷新 tag 列表。 */
    public interface OnPinnedChangedListener {
        void onPinnedChanged();
    }

    private PinnedTagsManager pinnedManager;
    private LinkDao linkDao;

    private final List<TagRow> rows = new ArrayList<>();
    private PinnedTagsAdapter adapter;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        pinnedManager = new PinnedTagsManager(context);
        linkDao = new LinkDao(context);
        linkDao.open();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (linkDao != null) {
            linkDao.close();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_pinned_tags, null, false);

        // 1. 从 DAO 拉所有 tag(name -> count)
        Map<String, Integer> allTags = linkDao.getTagsWithCount(); // 不传 pinned,因为我们这里就是要改 pinned
        List<String> names = new ArrayList<>(allTags.keySet());
        Collections.sort(names, String::compareTo); // 未置顶按字母排序

        // 2. 构造 rows:已置顶(按配置顺序)在前
        Set<String> pinned = pinnedManager.getPinnedTags();
        rows.clear();
        for (String pinName : pinned) {
            if (allTags.containsKey(pinName)) {
                rows.add(new TagRow(pinName, allTags.get(pinName), true));
            }
        }
        for (String name : names) {
            if (!pinned.contains(name)) {
                rows.add(new TagRow(name, allTags.get(name), false));
            }
        }

        // 3. RecyclerView 配 Adapter
        RecyclerView recycler = view.findViewById(R.id.recycler_pinned_tags);
        TextView hint = view.findViewById(R.id.text_pinned_hint);
        adapter = new PinnedTagsAdapter(rows, pinnedManager, () -> updateHint(hint));
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        updateHint(hint);

        // 4. Dialog
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("置顶标签(最多 " + PinnedTagsManager.MAX_PINNED + " 个)")
                .setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    Fragment parent = getParentFragment();
                    if (parent instanceof OnPinnedChangedListener) {
                        ((OnPinnedChangedListener) parent).onPinnedChanged();
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        return dialog;
    }

    private void updateHint(TextView hint) {
        if (hint == null) return;
        int cur = pinnedManager.getPinnedTags().size();
        hint.setText("已选 " + cur + " / " + PinnedTagsManager.MAX_PINNED);
    }

    // ===================== model + adapter =====================

    private static class TagRow {
        final String name;
        final int count;
        boolean checked; // 当前勾选状态(独立于 manager,确保滑动不丢)

        TagRow(String name, int count, boolean pinned) {
            this.name = name;
            this.count = count;
            this.checked = pinned;
        }
    }

    private static class PinnedTagsAdapter extends RecyclerView.Adapter<PinnedTagsAdapter.VH> {

        private final List<TagRow> rows;
        private final PinnedTagsManager manager;
        private final Runnable onChange;

        PinnedTagsAdapter(List<TagRow> rows, PinnedTagsManager manager, Runnable onChange) {
            this.rows = rows;
            this.manager = manager;
            this.onChange = onChange;
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            // 同一 tag 永远同一 ID,RecyclerView 才能正确复用 ViewHolder
            return rows.get(position).name.hashCode();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pinned_tag, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            TagRow r = rows.get(position);
            // 关键:绑之前彻底解绑,防止 onCheckedChange 误触 / viewHolder 复用错位
            h.checkbox.setOnCheckedChangeListener(null);
            h.checkbox.setText(r.name + " (" + r.count + ")");
            // 用 row 自己的状态,而不是 manager(滑动不丢)
            h.checkbox.setChecked(r.checked);
            h.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // 1. 先更新 row 状态(主真相源)
                r.checked = isChecked;
                // 2. 再写 manager(用户触发"立刻持久化",实时)
                if (isChecked) {
                    boolean ok = manager.add(r.name);
                    if (!ok) {
                        // 已达上限:rollback row + 勾选框
                        r.checked = false;
                        buttonView.setChecked(false);
                        Toast.makeText(buttonView.getContext(),
                                "最多只能置顶 " + PinnedTagsManager.MAX_PINNED + " 个", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    manager.remove(r.name);
                }
                onChange.run();
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final CheckBox checkbox;
            VH(View itemView) {
                super(itemView);
                checkbox = itemView.findViewById(R.id.checkbox_pinned);
            }
        }
    }
}