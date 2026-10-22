package person.notfresh.readingshare.ui.webview;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.FlexboxLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.util.RecentTagsManager;
import person.notfresh.readingshare.util.TagSuggestionHelper;

/**
 * 「给链接打标签」弹窗 —— 全 app 唯一实现（2.3.21.6）。
 *
 * <p><b>为什么收敛到一起</b>：这个弹窗历史上被复制了三份（WebViewActivity 内联版、
 * ReaderMenuActions 版、LinksAdapter 内联版），同 layout、同联想 helper、同 recent 逻辑、
 * 同「当前标签点击删除」，只差「已有标签从哪来」和「保存走谁」。UI 层统一放这里，
 * caller 只提供这两件事，行为差异不再各写一遍。</p>
 *
 * <p><b>两个入口</b>：
 * <ul>
 *   <li>{@link #show(Context, PageTarget)} —— WebView 两页用。已有标签按 linkId/URL 查 DAO，
 *       保存也走 DAO（查不到链接就新建一条）。</li>
 *   <li>{@link #show(Context, PageTarget, List, OnSaved)} —— 列表页（LinksAdapter）用。
 *       已有标签本来就在内存的 LinkItem 上，不必回查；保存走 caller 的回调
 *       （它还要 notifyDataSetChanged + 记事件日志）。</li>
 * </ul>
 *
 * <p><b>链接归属</b>：有 linkId 就用它（页面重定向不改变链接身份）；没有才按 URL 查。
 * 页面被 302 重定向过时当前 URL 查不到，会退回重定向前原 URL 再查一次
 * （与 {@link EditTitleDialog} 同一条规则）。</p>
 */
public final class AddTagDialog {

    private static final String TAG = "AddTagDialog";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "add-tag-dialog");
        t.setDaemon(true);
        return t;
    });

    /** 保存成功后回调，参数是这条链接保存后的完整标签集。 */
    public interface OnSaved {
        void onSaved(List<String> allTags);
    }

    private AddTagDialog() {
        // utility class
    }

    /** 入口一：已有标签与保存都走 DAO（WebView 页，链接可能不在库里）。 */
    public static void show(final Context ctx, final PageTarget target) {
        show(ctx, target, null, null);
    }

    /**
     * 入口二：已有标签由 caller 提供（列表页本来就在内存里），保存走 caller 回调。
     *
     * @param existingTags 该链接已打的标签；null 表示按 target 自己查 DAO
     * @param onSaved      保存回调；null 表示由本类按 DAO 落库
     */
    public static void show(final Context ctx, final PageTarget target,
                            List<String> existingTags, OnSaved onSaved) {
        if (ctx == null || target == null) return;

        // 外部已给标签（列表页）→ 直接弹；否则回 IO 线程查 DAO
        if (existingTags != null) {
            present(ctx, target, existingTags, onSaved);
            return;
        }
        loadThenPresent(ctx, target, onSaved);
    }

    /** 查 DAO 拿已有标签，再回主线程弹窗。 */
    private static void loadThenPresent(final Context ctx, final PageTarget target,
                                        final OnSaved onSaved) {
        final Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            List<String> existing = new ArrayList<>();
            Long linkId = target.linkId();
            try {
                LinkDao dao = new LinkDao(DbConnection.get(app).writable());
                if (linkId == null || linkId <= 0) {
                    linkId = resolveLinkId(dao, target);
                }
                if (linkId != null && linkId > 0) {
                    existing.addAll(dao.getLinkTags(linkId));
                }
            } catch (Exception e) {
                Log.e(TAG, "load existing tags failed", e);
            }
            final List<String> loaded = existing;
            MAIN.post(() -> present(ctx, target, loaded, onSaved));
        });
    }

    /**
     * 按 URL 解析链接 id；当前 URL 查不到再退回重定向前原 URL。
     *
     * <p>短链/302 场景下库里存的是短链、页面当前 URL 已是真页，只查前者会漏。</p>
     */
    private static Long resolveLinkId(LinkDao dao, PageTarget target) {
        Long id = safeGetLinkIdByUrl(dao, target.url());
        if (id == null && target.originalUrl() != null
                && !target.originalUrl().equals(target.url())) {
            id = safeGetLinkIdByUrl(dao, target.originalUrl());
        }
        return id;
    }

    private static Long safeGetLinkIdByUrl(LinkDao dao, String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            return dao.getLinkIdByUrl(url);
        } catch (Exception e) {
            Log.e(TAG, "getLinkIdByUrl failed url=" + url, e);
            return null;
        }
    }

    /** 弹窗本体：唯一一份 UI 组装（chip 渲染 + 联想 + 最近标签 + 确认按钮）。 */
    private static void present(final Context ctx, final PageTarget target,
                                List<String> existingTags, final OnSaved onSaved) {
        View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_tag, null);
        final EditText input = dialogView.findViewById(R.id.edit_tag_input);
        RecyclerView suggestionRecycler = dialogView.findViewById(R.id.suggestion_recycler);
        TextView suggestionLabel = dialogView.findViewById(R.id.text_suggestion_label);
        final FlexboxLayout currentContainer = dialogView.findViewById(R.id.current_tags_container);
        TextView currentLabel = dialogView.findViewById(R.id.text_current_tags_label);
        FlexboxLayout recentContainer = dialogView.findViewById(R.id.recent_tags_container);
        TextView recentLabel = dialogView.findViewById(R.id.text_recent_tags_label);

        final List<String> current = existingTags == null ? new ArrayList<>() : new ArrayList<>(existingTags);
        final Set<String> excluded = new HashSet<>(current);

        // ---- 当前标签：点击即从待保存集合移除 ----
        if (!current.isEmpty() && currentLabel != null) {
            currentLabel.setVisibility(View.VISIBLE);
            for (String tag : new ArrayList<>(current)) {
                addChip(ctx, currentContainer, tag, v -> {
                    if (v.getParent() instanceof android.view.ViewGroup) {
                        ((android.view.ViewGroup) v.getParent()).removeView(v);
                    }
                    current.remove(tag);
                    excluded.remove(tag);
                });
            }
        }

        // ---- 最近标签：点击填入输入框 ----
        List<String> recent = new ArrayList<>();
        for (String tag : RecentTagsManager.getRecentTags(ctx)) {
            if (!excluded.contains(tag)) recent.add(tag);
        }
        if (!recent.isEmpty() && recentLabel != null) {
            recentLabel.setVisibility(View.VISIBLE);
            for (String tag : recent) {
                addChip(ctx, recentContainer, tag, v -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) {
                        input.setText(tag);
                    } else if (!text.endsWith(",") && !text.endsWith("，")) {
                        input.setText(text + ", " + tag);
                    } else {
                        input.setText(text + " " + tag);
                    }
                    input.setSelection(input.getText().length());
                });
            }
        }

        final TagSuggestionHelper.Binder binder = TagSuggestionHelper.attach(
                input, suggestionRecycler, suggestionLabel, ctx, excluded);

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.link_dialog_title_add_tag)
                .setView(dialogView)
                .setPositiveButton(R.string.link_dialog_ok, (dialog, which) -> {
                    binder.detach();
                    List<String> tags = new ArrayList<>(current);
                    for (String tag : splitTags(input.getText().toString())) {
                        if (!tags.contains(tag)) tags.add(tag);
                    }
                    if (tags.isEmpty()) return;
                    List<String> previous = new ArrayList<>(current);
                    if (onSaved != null) {
                        onSaved.onSaved(tags);
                    } else {
                        saveToDao(ctx, target, tags, previous);
                    }
                })
                .setNegativeButton(R.string.cancel, (DialogInterface.OnClickListener) (dialog, which) -> binder.detach())
                .show();
    }

    private static void addChip(Context ctx, FlexboxLayout container, String text, View.OnClickListener onClick) {
        if (container == null) return;
        TextView chip = (TextView) LayoutInflater.from(ctx)
                .inflate(R.layout.item_tag, container, false);
        chip.setText(text);
        chip.setOnClickListener(onClick);
        container.addView(chip);
    }

    /** 按 DAO 落库（WebView 页路径）：有 linkId 用 id，没有则按当前页面 URL 新建一条。 */
    private static void saveToDao(final Context ctx, final PageTarget target,
                                  final List<String> tags, final List<String> previousTags) {
        final Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            String message = null;
            try {
                LinkDao dao = new LinkDao(DbConnection.get(app).writable());
                Long linkId = target.linkId();
                if (linkId == null || linkId <= 0) {
                    linkId = resolveLinkId(dao, target);
                }
                LinkItem item = linkId != null && linkId > 0 ? dao.getLinkById(linkId) : null;
                if (item == null && target.url() != null && !target.url().isEmpty()) {
                    // 库里没有这条链接：按当前页面新建一条
                    String title = target.title() != null && !target.title().isEmpty()
                            ? target.title() : app.getString(R.string.reader_action_untitled);
                    item = new LinkItem(title, target.url(), "", "", "");
                    item.setClickCount(0);
                    item.setSummary("");
                    long id = dao.insertLink(item);
                    item.setId(id);
                }
                if (item != null) {
                    item.setTags(new ArrayList<>(tags));
                    dao.updateLinkTags(item);
                    List<String> added = new ArrayList<>(tags);
                    added.removeAll(previousTags);
                    if (!added.isEmpty()) {
                        RecentTagsManager.addRecentTags(app, added);
                    }
                    message = app.getString(R.string.reader_action_tag_saved, String.join(", ", tags));
                }
            } catch (Exception e) {
                Log.e(TAG, "save tags failed", e);
            }
            final String result = message;
            MAIN.post(() -> Toast.makeText(app,
                    result != null ? result : app.getString(R.string.reader_action_tag_failed),
                    Toast.LENGTH_SHORT).show());
        });
    }

    private static List<String> splitTags(String raw) {
        List<String> tags = new ArrayList<>();
        if (raw == null) return tags;
        for (String part : raw.split("[,，]")) {
            String tag = part.trim();
            if (!tag.isEmpty() && !tags.contains(tag)) tags.add(tag);
        }
        return tags;
    }
}