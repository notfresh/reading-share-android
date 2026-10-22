package person.notfresh.readingshare.ui.webview;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
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
import android.util.Log;

/**
 * 「加标签」弹窗（目标2 抽出的共享实现）。
 *
 * <p>原来 WebViewActivity 和 ReaderFragment 各有一份：Activity 那份是硬编码中文 + 用
 * {@code getAllLinks()} 线性找链接（3000 条时直接卡）；阅读页那份是中英双语 + 按 linkId 查。
 * 这里以阅读页的实现为准收敛成一份，两处都调它。</p>
 *
 * <p>链接归属规则（2.3.20.3 起）：有 linkId 就用它（页面重定向不改变链接身份）；
 * 没有 linkId 才按 URL 查，查不到就新建一条 —— 不再依赖页面当前 URL 与队列是否一致。</p>
 */
public final class AddTagDialog {

    private static final String TAG = "AddTagDialog";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "add-tag-dialog");
        t.setDaemon(true);
        return t;
    });

    private AddTagDialog() {
        // utility class
    }

    public static void show(final Context ctx, final PageTarget target) {
        if (ctx == null || target == null) return;
        final Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            List<String> existing = new ArrayList<>();
            Long linkId = target.linkId();
            try {
                LinkDao dao = new LinkDao(DbConnection.get(app).writable());
                if (linkId == null && target.url() != null && !target.url().isEmpty()) {
                    linkId = dao.getLinkIdByUrl(target.url());
                }
                if (linkId != null && linkId > 0) {
                    existing.addAll(dao.getLinkTags(linkId));
                }
            } catch (Exception e) {
                Log.e(TAG, "load existing tags failed", e);
            }
            final Long resolvedId = (linkId != null && linkId > 0) ? linkId : null;
            final List<String> loaded = existing;
            MAIN.post(() -> build(ctx, target, resolvedId, loaded));
        });
    }

    private static void build(final Context ctx, final PageTarget target,
                              final Long linkId, List<String> existingTags) {
        View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_tag, null);
        final EditText input = dialogView.findViewById(R.id.edit_tag_input);
        RecyclerView suggestionRecycler = dialogView.findViewById(R.id.suggestion_recycler);
        TextView suggestionLabel = dialogView.findViewById(R.id.text_suggestion_label);
        final FlexboxLayout currentContainer = dialogView.findViewById(R.id.current_tags_container);
        TextView currentLabel = dialogView.findViewById(R.id.text_current_tags_label);
        FlexboxLayout recentContainer = dialogView.findViewById(R.id.recent_tags_container);
        TextView recentLabel = dialogView.findViewById(R.id.text_recent_tags_label);

        final List<String> current = new ArrayList<>(existingTags);
        final Set<String> excluded = new HashSet<>(existingTags);

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
                    save(ctx, target, linkId, tags, new ArrayList<>(current));
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

    private static void save(final Context ctx, final PageTarget target, final Long knownLinkId,
                             final List<String> tags, final List<String> previousTags) {
        final Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            String message = null;
            try {
                LinkDao dao = new LinkDao(DbConnection.get(app).writable());
                Long linkId = knownLinkId;
                if (linkId == null) {
                    linkId = dao.getLinkIdByUrl(target.url());
                }
                LinkItem item = linkId != null && linkId > 0 ? dao.getLinkById(linkId) : null;
                if (item == null && target.url() != null && !target.url().isEmpty()) {
                    // 库里没有这条链接:按当前页面新建一条(与 Activity 旧行为一致)
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
