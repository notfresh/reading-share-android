package person.notfresh.readingshare.ui.tag;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.eventlog.EventLogClient;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.model.LinkJson;
import person.notfresh.readingshare.ui.Dropdown;
import person.notfresh.readingshare.util.TagMergeCleanup;

/**
 * 「合并标签」对话框 —— 选一个目标标签,把源标签下的链接全部搬过去,并删掉源标签。
 *
 * <p>流程:长按标签 → 标签操作弹窗 → 「合并到其他标签」→ 本对话框 → 下拉选目标
 * 标签 → 点「合并」→ 二次确认(带链接条数)→ 执行合并。</p>
 *
 * <p>目标标签下拉复用设置页的 {@link Dropdown}(Material exposed dropdown,
 * 与设置页「阅读页数据来源」同一套视觉),不再自造 RecyclerView 选单。</p>
 *
 * <p>合并成功后给每条受影响的 link 写一条 {@code links} topic 的 update 事件
 * (payload 带合并后的新标签集)—— 这是后续「联机同步」模块的接口,不能省。</p>
 *
 * <p>宿主通过 {@link OnMergeListener} 拿到结果并刷新 UI。</p>
 */
public class MergeTagDialog extends DialogFragment {

    /** 宿主实现:合并完成后回调,用于刷新标签区 + 链接列表。 */
    public interface OnMergeListener {
        /**
         * @param sourceTag 被合并掉的标签
         * @param targetTag 目标标签
         * @param movedCount 被搬动的链接条数
         */
        void onTagMerged(String sourceTag, String targetTag, int movedCount);
    }

    private static final String ARG_SOURCE_TAG = "source_tag";

    private LinkDao linkDao;

    /** 候选目标标签名(下拉选项,按项目标签顺序)。 */
    private final List<String> targetNames = new ArrayList<>();
    /** 候选目标标签的链接数(与 targetNames 同下标)。 */
    private final List<Integer> targetCounts = new ArrayList<>();
    /** 目标标签下拉(复用设置页组件),用于读当前选中项。 */
    private Dropdown dropdown;

    /** 静态工厂(名字不能与父类 DialogFragment.show 同名,否则 javac 报 cannot override)。 */
    public static MergeTagDialog newInstance(String sourceTag) {
        MergeTagDialog dialog = new MergeTagDialog();
        Bundle args = new Bundle();
        args.putString(ARG_SOURCE_TAG, sourceTag);
        dialog.setArguments(args);
        return dialog;
    }

    private String sourceTag() {
        Bundle args = getArguments();
        return args == null ? null : args.getString(ARG_SOURCE_TAG);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // 新代码一律用 DbConnection 注入连接(不再用 new LinkDao(context) + open())
        linkDao = new LinkDao(DbConnection.get(context).writable());
    }

    @Override
    public void onDetach() {
        super.onDetach();
        linkDao = null;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final String source = sourceTag();
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_merge_tag, null, false);

        TextView subtitle = view.findViewById(R.id.text_merge_subtitle);
        ViewGroup dropdownHost = view.findViewById(R.id.merge_dropdown_host);
        TextView empty = view.findViewById(R.id.text_merge_empty);

        if (subtitle != null) {
            subtitle.setText(getString(R.string.tag_merge_dialog_subtitle, source == null ? "" : source));
        }

        // 候选目标标签:所有标签去掉 source 自己
        Map<String, Integer> allTags = linkDao.getTagsWithCount();
        targetNames.clear();
        targetCounts.clear();
        for (Map.Entry<String, Integer> entry : allTags.entrySet()) {
            if (source != null && source.equals(entry.getKey())) {
                continue;
            }
            targetNames.add(entry.getKey());
            targetCounts.add(entry.getValue());
        }

        if (targetNames.isEmpty()) {
            if (empty != null) empty.setVisibility(View.VISIBLE);
        } else {
            if (empty != null) empty.setVisibility(View.GONE);
            // 复用设置页的下拉组件:选项 = 标签名(带链接数)
            dropdown = Dropdown
                    .attach(requireContext(), dropdownHost)
                    .setHint(R.string.tag_merge_target_hint);
            List<String> labels = new ArrayList<>(targetNames.size());
            for (int i = 0; i < targetNames.size(); i++) {
                labels.add(targetNames.get(i) + " (" + targetCounts.get(i) + ")");
            }
            dropdown.setOptions(labels, 0);
        }

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tag_merge_dialog_title)
                .setView(view)
                .setPositiveButton(R.string.tag_merge_confirm_ok,
                        (d, w) -> onMergeButtonClicked(source))
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    /** 点「合并」:取下拉当前选中的目标标签 → 二次确认。 */
    private void onMergeButtonClicked(String source) {
        if (source == null || targetNames.isEmpty()) {
            return;
        }
        int index = currentDropdownIndex();
        if (index < 0 || index >= targetNames.size()) {
            return;
        }
        confirmMerge(source, targetNames.get(index), targetCounts.get(index));
    }

    /** 下拉当前选中项的下标。 */
    private int currentDropdownIndex() {
        if (dropdown == null) {
            return -1;
        }
        return dropdown.selectedIndex();
    }

    /** 二次确认:显示源标签 / 目标标签 / 链接条数,确认后才真合并。 */
    private void confirmMerge(String source, String target, int linkCount) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tag_merge_confirm_title)
                .setMessage(getString(R.string.tag_merge_confirm_msg,
                        source, linkCount, target))
                .setPositiveButton(R.string.tag_merge_confirm_ok,
                        (d, w) -> doMerge(source, target))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 执行合并:DB 事务 → 本地配置同步 → 写事件日志 → 通知宿主刷新。 */
    private void doMerge(String source, String target) {
        try {
            // source 的 tag_id 必须在合并「之前」取 —— 合并后它已被删除,查不到了
            long sourceId = linkDao.getTagIdByName(source);
            // 受影响的 link id 也要在合并前取:合并后 source 已不存在,
            // 只能按 target 查 —— 那会把 target 原有的链接也捞进来(多写一堆无变化的事件)
            List<Long> affectedIds = linkDao.getLinkIdsByTag(source);

            int moved = linkDao.mergeTag(source, target);
            if (moved <= 0 && linkDao.getTagIdByName(source) != -1L) {
                Toast.makeText(requireContext(),
                        getString(R.string.tag_merge_toast_failed, "no links moved"),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // 本地配置同步(置顶/高亮/选中/最近/排序)
            TagMergeCleanup.apply(requireContext(), source, target, sourceId);

            // 写事件日志:每条受影响的 link 一条 update,给后续联机模块用
            writeMergeEvents(affectedIds);

            Toast.makeText(requireContext(), R.string.tag_merge_toast_success, Toast.LENGTH_SHORT).show();

            Fragment parent = getParentFragment();
            if (parent instanceof OnMergeListener) {
                ((OnMergeListener) parent).onTagMerged(source, target, moved);
            }
            dismiss();
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    getString(R.string.tag_merge_toast_failed, String.valueOf(e.getMessage())),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 给受影响的所有 link 写 {@code links} topic 的 update 事件。
     *
     * <p>payload 用合并「之后」的新标签集({@link LinkJson#toJsonString});{@code old}
     * 快照由 {@code EventLogClient.record()} 自动从本地事件流推导,不用手动传。</p>
     *
     * @param affectedIds 合并前属于 source 标签的 link id 列表
     */
    private void writeMergeEvents(List<Long> affectedIds) {
        if (affectedIds == null) return;
        for (Long id : affectedIds) {
            if (id == null) continue;
            LinkItem item = linkDao.getLinkById(id);
            if (item == null) continue;
            try {
                EventLogClient.get().update("links",
                        String.valueOf(item.getId()),
                        item.getTimestamp(),
                        LinkJson.toJsonString(item));
            } catch (Exception ignored) {
                // 事件日志失败不该让合并回滚(DB 已提交)
            }
        }
    }
}
