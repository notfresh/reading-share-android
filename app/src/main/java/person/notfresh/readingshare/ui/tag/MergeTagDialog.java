package person.notfresh.readingshare.ui.tag;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.List;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.eventlog.EventLogClient;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.model.LinkJson;
import person.notfresh.readingshare.util.CrashClipboard;
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
 *
 * <p><b>生命周期陷阱(2.3.25 实测秒退的根因)</b>:本弹窗要弹「二次确认」,而
 * {@link AlertDialog} 的 {@code setPositiveButton} 一旦点击就自动 dismiss ——
 * dismiss 会让 DialogFragment 被移除、detach。此时二次确认框(独立的 AlertDialog,
 * 不受 fragment 生命周期管)再回调回来,{@code requireContext()} 直接抛
 * {@code IllegalStateException: Fragment ... not attached to a context}。
 * 所以:① 确认/取消按钮放在布局里(自定义按钮点击不会自动 dismiss);
 * ② 回调里一律用缓存的 {@link #appContext},不用 {@code requireContext()};
 * ③ {@link #linkDao} 在 {@code onDetach} 不置 null —— 二次确认回调可能发生在
 * detach 之后。</p>
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
    /**
     * 缓存的 application context —— 回调可能发生在 fragment detach 之后,
     * 那时 {@code requireContext()} 会抛 IllegalStateException。
     */
    private Context appContext;

    /** 目标标签下拉(选标签专用组件,内部包通用 Dropdown)。 */
    private TagSelectDropdown tagDropdown;

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
        appContext = context.getApplicationContext();
        // 新代码一律用 DbConnection 注入连接(不再用 new LinkDao(context) + open())
        linkDao = new LinkDao(DbConnection.get(context).writable());
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // 故意不置 null:二次确认框的回调可能发生在 detach 之后
        // (AlertDialog 的按钮点击会让本 fragment dismiss → detach),
        // 那时 linkDao 还是 null 的话 doMerge 直接 NPE。
        appContext = null;
    }

    /** 拿一个安全的 context:优先 fragment 的,拿不到就用缓存的 application context。 */
    private Context safeContext() {
        if (isAdded()) {
            return requireContext();
        }
        return appContext;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        try {
            return buildDialog(savedInstanceState);
        } catch (Throwable t) {
            // 弹窗构建阶段崩(如 Material 下拉在 Dialog 里测量异常)—— 兜住,
            // 把栈复制到剪切板,不让整个 app 挂掉(2.3.24)
            Context ctx = safeContext();
            CrashClipboard.copyException(ctx, "mergeTag:onCreateDialog", t);
            if (ctx != null) {
                Toast.makeText(ctx,
                        ctx.getString(R.string.tag_merge_toast_failed_generic),
                        Toast.LENGTH_LONG).show();
            }
            return new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.tag_merge_dialog_title)
                    .setMessage(R.string.tag_merge_toast_failed_generic)
                    .setNegativeButton(R.string.cancel, null)
                    .create();
        }
    }

    /** 构建合并标签弹窗(由 {@link #onCreateDialog} 统一兜底)。 */
    private Dialog buildDialog(Bundle savedInstanceState) {
        final String source = sourceTag();
        final Context ctx = requireContext();
        View view = LayoutInflater.from(ctx)
                .inflate(R.layout.dialog_merge_tag, null, false);

        TextView subtitle = view.findViewById(R.id.text_merge_subtitle);
        ViewGroup dropdownHost = view.findViewById(R.id.merge_dropdown_host);
        TextView empty = view.findViewById(R.id.text_merge_empty);
        View confirmButton = view.findViewById(R.id.button_merge_confirm);
        View cancelButton = view.findViewById(R.id.button_merge_cancel);

        if (subtitle != null) {
            subtitle.setText(ctx.getString(R.string.tag_merge_dialog_subtitle,
                    source == null ? "" : source));
        }

        // 选标签下拉:候选 = 所有标签去掉 source 自己
        tagDropdown = TagSelectDropdown
                .attach(ctx, dropdownHost)
                .setHint(R.string.tag_merge_target_hint);
        tagDropdown.loadTags(source);

        if (tagDropdown.isEmpty()) {
            if (empty != null) empty.setVisibility(View.VISIBLE);
            if (confirmButton != null) confirmButton.setEnabled(false);
        } else {
            if (empty != null) empty.setVisibility(View.GONE);
        }

        // 自定义按钮(不能用 AlertDialog 的 setPositiveButton —— 见类注释的生命周期陷阱)
        if (confirmButton != null) {
            confirmButton.setOnClickListener(v -> {
                try {
                    onMergeButtonClicked(source);
                } catch (Throwable t) {
                    handleError("mergeTag:onMergeButton", t);
                }
            });
        }
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dismiss());
        }

        return new AlertDialog.Builder(ctx)
                .setTitle(R.string.tag_merge_dialog_title)
                .setView(view)
                .create();
    }

    /** 点「合并」:取下拉当前选中的目标标签 → 二次确认。 */
    private void onMergeButtonClicked(String source) {
        if (source == null || tagDropdown == null) {
            return;
        }
        String target = tagDropdown.selectedTag();
        if (target == null) {
            return;
        }
        confirmMerge(source, target, tagDropdown.selectedCount());
    }

    /** 二次确认:显示源标签 / 目标标签 / 链接条数,确认后才真合并。 */
    private void confirmMerge(String source, String target, int linkCount) {
        Context ctx = safeContext();
        if (ctx == null) {
            return;
        }
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.tag_merge_confirm_title)
                .setMessage(ctx.getString(R.string.tag_merge_confirm_msg,
                        source, linkCount, target))
                .setPositiveButton(R.string.tag_merge_confirm_ok,
                        (d, w) -> doMerge(source, target))  // doMerge 内部已兜底
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 执行合并:DB 事务 → 本地配置同步 → 写事件日志 → 通知宿主刷新。 */
    private void doMerge(String source, String target) {
        // 整个合并流程包一层:任何一段抛异常都不让 app 崩,把栈复制到剪切板,
        // 用户粘一下就知道断在哪一段(2.3.24)
        try {
            doMergeInternal(source, target);
        } catch (Throwable t) {
            handleError("mergeTag:" + source + "->" + target, t);
        }
    }

    /** 合并的具体步骤(由 {@link #doMerge} 统一兜底)。 */
    private void doMergeInternal(String source, String target) {
        if (linkDao == null) {
            throw new IllegalStateException("linkDao is null (fragment detached?)");
        }
        Context ctx = safeContext();

        // source 的 tag_id 必须在合并「之前」取 —— 合并后它已被删除,查不到了
        long sourceId = linkDao.getTagIdByName(source);
        // 受影响的 link id 也要在合并前取:合并后 source 已不存在,
        // 只能按 target 查 —— 那会把 target 原有的链接也捞进来(多写一堆无变化的事件)
        List<Long> affectedIds = linkDao.getLinkIdsByTag(source);

        int moved = linkDao.mergeTag(source, target);
        if (moved <= 0 && linkDao.getTagIdByName(source) != -1L) {
            if (ctx != null) {
                Toast.makeText(ctx,
                        ctx.getString(R.string.tag_merge_toast_failed, "no links moved"),
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // 本地配置同步(置顶/高亮/选中/最近/排序)
        TagMergeCleanup.apply(ctx, source, target, sourceId);

        // 写事件日志:每条受影响的 link 一条 update,给后续联机模块用
        writeMergeEvents(affectedIds);

        if (ctx != null) {
            Toast.makeText(ctx, R.string.tag_merge_toast_success, Toast.LENGTH_SHORT).show();
        }

        Fragment parent = getParentFragment();
        if (parent instanceof OnMergeListener) {
            ((OnMergeListener) parent).onTagMerged(source, target, moved);
        }
        dismiss();
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
            } catch (Throwable t) {
                // 事件日志失败不该让合并回滚(DB 已提交),但要把栈留下来 ——
                // EventLogClient.get() 在未 init 时抛 EventLogException(2.3.24)
                CrashClipboard.copyException(safeContext(), "mergeTag:eventLog", t);
            }
        }
    }

    /** 统一异常处理:复制栈到剪切板 + toast 提示,不让 app 挂掉。 */
    private void handleError(String label, Throwable t) {
        Context ctx = safeContext();
        CrashClipboard.copyException(ctx, label, t);
        if (ctx != null) {
            Toast.makeText(ctx,
                    ctx.getString(R.string.tag_merge_toast_failed_generic),
                    Toast.LENGTH_LONG).show();
        }
    }
}
