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
import person.notfresh.readingshare.ui.webview.AddTagDialog;
import person.notfresh.readingshare.ui.webview.EditTitleDialog;
import person.notfresh.readingshare.ui.webview.PageTarget;
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

    /** 加标签：走共享实现（目标2），链接身份由 page.linkId() 提供，不随重定向变化。 */
    private void showAddTagDialog() {
        AddTagDialog.show(requireContext(), target());
    }

    /** 当前页面 → 共享弹窗需要的目标描述。 */
    private PageTarget target() {
        return new PageTarget() {
            @Override
            public Long linkId() {
                return page.linkId();
            }

            @Override
            public String url() {
                return page.url();
            }

            @Override
            public String originalUrl() {
                return null;
            }

            @Override
            public String title() {
                return page.title();
            }
        };
    }

    /** 改标题：走共享实现（目标2），按 linkId 改，重定向也不影响。 */
    private void showEditTitleDialog() {
        EditTitleDialog.show(requireContext(), target(), null);
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
