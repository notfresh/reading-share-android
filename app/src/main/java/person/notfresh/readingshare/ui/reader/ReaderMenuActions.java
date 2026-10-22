package person.notfresh.readingshare.ui.reader;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.FlexboxLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import person.notfresh.readingshare.LinksHistoryActivity;
import person.notfresh.readingshare.R;
import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.dialog.AddOverrideRuleDialog;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.util.CrawlUtil;
import person.notfresh.readingshare.util.ExternalLinkOpener;
import person.notfresh.readingshare.util.ShareUtil;
import person.notfresh.readingshare.util.RecentTagsManager;
import person.notfresh.readingshare.util.TagSuggestionHelper;

/**
 * 阅读页顶部菜单的能力集合（2.3.19 第二轮）。
 *
 * <p>为什么独立成类：ReaderFragment 只做"接线"，菜单动作本身有 9 个，
 * 塞进 Fragment 会让它同时承担页面装配 + 9 个业务动作，违反单一职责。</p>
 *
 * <p>与 WebViewActivity 的差异（本次刻意不做）：
 * 强制返回 / 存档返回（Activity finish 语义）、折叠 mini player、
 * 丝滑模式的导航控件开关（阅读页已有固定底部控件）。</p>
 */
public final class ReaderMenuActions {

    /** Fragment 提供给菜单的"当前页面事实"，菜单动作只读它，不直接碰 WebView 字段。 */
    public interface Page {
        /** 当前 WebView；可能为 null（视图已销毁）。 */
        WebView webView();

        /** 当前页面所属的队列链接 id；null = 页面已离开队列（用户点了别的链接）。 */
        Long linkId();

        /** 当前页面 URL；可能为空。 */
        String url();

        /** 当前页面标题；可能为空。 */
        String title();
    }

    private static final String TAG = "ReaderMenuActions";

    private final Fragment host;
    private final Page page;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ReaderMenuActions(Fragment host, Page page) {
        this.host = host;
        this.page = page;
    }

    /** @return true 表示该菜单项由本类消费 */
    public boolean handle(int itemId) {
        if (itemId == R.id.action_reader_refresh) {
            refresh();
            return true;
        }
        if (itemId == R.id.action_reader_open_browser) {
            openInBrowser();
            return true;
        }
        if (itemId == R.id.action_reader_extract) {
            extractContent();
            return true;
        }
        if (itemId == R.id.action_reader_share) {
            share();
            return true;
        }
        if (itemId == R.id.action_reader_override_rule) {
            configureOverrideRule();
            return true;
        }
        if (itemId == R.id.action_reader_history) {
            startActivity(new Intent(requireContext(), LinksHistoryActivity.class));
            return true;
        }
        if (itemId == R.id.action_reader_add_tag) {
            showAddTagDialog();
            return true;
        }
        if (itemId == R.id.action_reader_edit_title) {
            showEditTitleDialog();
            return true;
        }
        if (itemId == R.id.action_reader_toggle_block) {
            toggleExternalMode();
            return true;
        }
        return false;
    }

    /** 给拦截模式菜单项染色：红=拦截 / 白=直接跳转 / 灰=弹窗确认（与 Activity 语义一致）。 */
    public void tintBlockIcon(Menu menu) {
        MenuItem item = menu == null ? null : menu.findItem(R.id.action_reader_toggle_block);
        if (item == null) return;
        Drawable drawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_external_block);
        if (drawable == null) return;
        drawable = DrawableCompat.wrap(drawable).mutate();
        DrawableCompat.setTint(drawable, tintForMode(ExternalLinkOpener.currentMode(requireContext())));
        item.setIcon(drawable);
    }

    // ---- 单个动作 ----

    private void refresh() {
        if (page.webView() == null) {
            toast(R.string.reader_action_page_loading);
            return;
        }
        page.webView().reload();
    }

    private void openInBrowser() {
        String url = page.url();
        if (url == null || url.isEmpty()) {
            toast(R.string.reader_action_no_url);
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Log.e(TAG, "openInBrowser failed for " + url, e);
            toast(R.string.reader_action_open_browser_failed);
        }
    }

    private void extractContent() {
        final String url = page.url();
        if (url == null || url.isEmpty()) {
            toast(R.string.reader_action_no_url);
            return;
        }
        final Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            String content = null;
            try {
                content = CrawlUtil.getArticleByUrl(url);
            } catch (Exception e) {
                Log.e(TAG, "extract failed for " + url, e);
            }
            final String extracted = content;
            mainHandler.post(() -> {
                if (!host.isAdded()) return;
                if (extracted == null || extracted.trim().isEmpty()) {
                    toast(R.string.reader_action_extract_failed);
                    return;
                }
                ClipboardManager clipboard =
                        (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard == null) {
                    toast(R.string.reader_action_extract_failed);
                    return;
                }
                clipboard.setPrimaryClip(ClipData.newPlainText("extracted_content", extracted));
                toast(R.string.reader_action_extract_ok);
            });
        }, "reader-extract").start();
    }

    private void share() {
        String url = page.url();
        if (url == null || url.isEmpty()) {
            toast(R.string.reader_action_no_url);
            return;
        }
        ShareUtil.shareText(requireContext(), page.title(), null, url);
    }

    private void configureOverrideRule() {
        WebView webView = page.webView();
        String contextUrl = null;
        if (webView != null) {
            String original = webView.getOriginalUrl();
            contextUrl = (original != null && !original.isEmpty()) ? original : webView.getUrl();
        }
        if (contextUrl == null || contextUrl.isEmpty()) contextUrl = page.url();
        AddOverrideRuleDialog.showDialog(host.getParentFragmentManager(), contextUrl);
    }

    /** 三种模式循环 0(弹窗) → 2(拦截) → 1(直接跳转) → 0，与 Activity 的 toggle 顺序一致。 */
    private void toggleExternalMode() {
        int current = ExternalLinkOpener.currentMode(requireContext());
        int next;
        int message;
        if (current == ExternalLinkOpener.MODE_ASK) {
            next = ExternalLinkOpener.MODE_BLOCK;
            message = R.string.reader_action_mode_block;
        } else if (current == ExternalLinkOpener.MODE_BLOCK) {
            next = ExternalLinkOpener.MODE_OPEN;
            message = R.string.reader_action_mode_open;
        } else {
            next = ExternalLinkOpener.MODE_ASK;
            message = R.string.reader_action_mode_ask;
        }
        requireContext().getSharedPreferences(ExternalLinkOpener.PREFS_SETTINGS, Context.MODE_PRIVATE)
                .edit().putInt(ExternalLinkOpener.KEY_EXTERNAL_LINK_MODE, next).apply();
        toast(message);
        host.requireActivity().invalidateOptionsMenu();
    }

    /**
     * 加标签弹窗：与 Activity / Home 的 dialog_add_tag 体验一致 ——
     * 输入框 + 联想 + 「当前标签」(可点删) + 「最近标签」(点击填入)。
     */
    private void showAddTagDialog() {
        final Long linkId = page.linkId();
        if (linkId == null) {
            toast(R.string.reader_action_not_in_queue);
            return;
        }
        final Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            List<String> existing = new ArrayList<>();
            try {
                existing.addAll(new LinkDao(DbConnection.get(app).writable()).getLinkTags(linkId));
            } catch (Exception e) {
                Log.e(TAG, "loadTags failed linkId=" + linkId, e);
            }
            final List<String> loaded = existing;
            mainHandler.post(() -> {
                if (host.isAdded()) buildTagDialog(linkId, loaded);
            });
        }, "reader-load-tags").start();
    }

    private void buildTagDialog(final long linkId, List<String> existingTags) {
        final Context ctx = requireContext();
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

        // 当前标签：点一下从列表移除（保存时按剩余集合整体替换）
        if (!current.isEmpty()) {
            currentLabel.setVisibility(View.VISIBLE);
            for (String tag : new ArrayList<>(current)) {
                addChip(ctx, currentContainer, tag, v -> {
                    currentContainer.removeView((View) v.getParent());
                    current.remove(tag);
                    excluded.remove(tag);
                });
            }
        }

        // 最近标签：点击填进输入框（不给空输入直接落库，保持与 Home 一致的手感）
        List<String> recent = new ArrayList<>();
        for (String tag : RecentTagsManager.getRecentTags(ctx)) {
            if (!excluded.contains(tag)) recent.add(tag);
        }
        if (!recent.isEmpty()) {
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
                    saveTags(linkId, tags, new ArrayList<>(current));
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> binder.detach())
                .show();
    }

    private void addChip(Context ctx, FlexboxLayout container, String text, View.OnClickListener onClick) {
        if (container == null) return;
        TextView chip = (TextView) LayoutInflater.from(ctx)
                .inflate(R.layout.item_tag, container, false);
        chip.setText(text);
        chip.setOnClickListener(onClick);
        container.addView(chip);
    }

    /** 整体替换该链接的标签集合（与 LinkDao.updateLinkTags 的"删除后重插"语义一致）。 */
    private void saveTags(final long linkId, final List<String> tags, final List<String> previousTags) {
        final Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            String message = null;
            try {
                LinkDao dao = new LinkDao(DbConnection.get(app).writable());
                LinkItem item = dao.getLinkById(linkId);
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
                Log.e(TAG, "saveTags failed linkId=" + linkId, e);
            }
            final String result = message;
            mainHandler.post(() -> {
                if (!host.isAdded()) return;
                if (result == null) {
                    toast(R.string.reader_action_tag_failed);
                } else {
                    Toast.makeText(requireContext(), result, Toast.LENGTH_SHORT).show();
                }
            });
        }, "reader-save-tags").start();
    }

    private void showEditTitleDialog() {
        final Long linkId = page.linkId();
        final String url = page.url();
        if (linkId == null && (url == null || url.isEmpty())) {
            toast(R.string.reader_action_no_url);
            return;
        }
        final Context ctx = requireContext();
        final EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(page.title() == null ? "" : page.title());
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.edit_title)
                .setView(input)
                .setPositiveButton(R.string.link_dialog_ok, (dialog, which) -> {
                    String newTitle = input.getText().toString().trim();
                    if (newTitle.isEmpty()) {
                        toast(R.string.reader_action_title_empty);
                        return;
                    }
                    updateTitle(linkId, url, newTitle);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 改标题：有 linkId 就按 id 更新（重定向后 URL 已经不是库里那条的地址），
     * 没有 linkId 才退回按 URL 更新。
     */
    private void updateTitle(final Long linkId, final String url, final String newTitle) {
        final Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            int updated = 0;
            try {
                LinkDao dao = new LinkDao(DbConnection.get(app).writable());
                if (linkId != null) {
                    updated = dao.updateLinkTitleById(linkId, newTitle);
                } else {
                    updated = dao.updateLinkTitle(url, newTitle);
                }
            } catch (Exception e) {
                Log.e(TAG, "updateTitle failed linkId=" + linkId + " url=" + url, e);
            }
            final int rows = updated;
            mainHandler.post(() -> {
                if (!host.isAdded()) return;
                toast(rows > 0 ? R.string.reader_action_title_updated : R.string.reader_action_title_not_saved);
            });
        }, "reader-update-title").start();
    }

    /** 逗号（中英文）分隔；去空白、去空项。 */
    private List<String> splitTags(String raw) {
        List<String> tags = new ArrayList<>();
        if (raw == null) return tags;
        for (String part : raw.split("[,，]")) {
            String tag = part.trim();
            if (!tag.isEmpty() && !tags.contains(tag)) tags.add(tag);
        }
        return tags;
    }

    private int tintForMode(int mode) {
        if (mode == ExternalLinkOpener.MODE_OPEN) {
            return ContextCompat.getColor(requireContext(), android.R.color.white);
        }
        if (mode == ExternalLinkOpener.MODE_ASK) {
            return ContextCompat.getColor(requireContext(), android.R.color.darker_gray);
        }
        return ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark);
    }

    private Context requireContext() {
        return host.requireContext();
    }

    private void startActivity(Intent intent) {
        host.startActivity(intent);
    }

    private void toast(int resId) {
        if (host.isAdded()) {
            Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show();
        }
    }
}
