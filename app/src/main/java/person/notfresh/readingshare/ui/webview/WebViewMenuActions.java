package person.notfresh.readingshare.ui.webview;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import person.notfresh.readingshare.LinksHistoryActivity;
import person.notfresh.readingshare.R;
import person.notfresh.readingshare.util.CrawlUtil;
import person.notfresh.readingshare.util.ExternalLinkOpener;
import person.notfresh.readingshare.util.ShareUtil;

/**
 * WebView 页共用菜单的动作实现 —— 目标2 唯一实现。
 *
 * <p>原来 WebViewActivity.onOptionsItemSelected 与 ReaderMenuActions 各写一份 if-else 链，
 * 语义大体相同、id 不同、还各自硬编码中文 toast。现在合并成一份：菜单资源
 * ({@code res/menu/webview_menu.xml},id 统一 {@code action_wv_*}) + 本类。</p>
 *
 * <p>宿主只提供 {@link Page} 的事实与 {@link Host} 的三个宿主能力(上下文 / 弹对话框 / 刷菜单)，
 * 动作本身不关心自己是 Activity 还是 Fragment。</p>
 *
 * <p><b>菜单 id 用 if-else 而不是 switch</b>：本项目 R 字段不是 final(解 R.jar 确认
 * {@code public static int action_wv_refresh = ...},无 final 关键字),不能作 case 常量;
 * 项目内既有 8 个 Activity/Fragment 也全是 {@code getItemId() ==} 写法,这里跟随既有约定。</p>
 */
public final class WebViewMenuActions {

    private static final String TAG = "WebViewMenuActions";

    /** 宿主提供的能力回调。 */
    public interface Host {
        Context context();

        /** 弹「配置跳转规则」对话框;contextUrl 已算好(重定向前原 URL 优先)。 */
        void showOverrideRuleDialog(String contextUrl);

        /** 拦截模式变化后刷新 toolbar 图标。 */
        void invalidateMenu();
    }

    /** 当前页面的事实。 */
    public interface Page {
        /** 当前 WebView;可能为 null(视图已销毁)。 */
        WebView webView();

        /** 当前页面所属的链接 id;null 表示需按 url 解析。 */
        Long linkId();

        /** 当前页面 URL;可能为空。 */
        String url();

        /** 重定向前的原始 URL;无则返回 null。 */
        String originalUrl();

        /** 当前页面标题;可能为空。 */
        String title();

        /** 改标题成功后宿主要不要同步自己的标题缓存;默认不管(阅读页每次现取 webview 标题)。 */
        default void onTitleUpdated(String newTitle) {
            // no-op
        }
    }

    private final Host host;
    private final Page page;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public WebViewMenuActions(Host host, Page page) {
        this.host = host;
        this.page = page;
    }

    // ---- 菜单组装与显示策略 ----

    public void inflate(Menu menu) {
        if (menu == null) return;
        new MenuInflater(host.context()).inflate(R.menu.webview_menu, menu);
        applyVisibility(menu);
        tintBlockIcon(menu);
    }

    /** 按阅读模式隐藏「显示/隐藏导航控件」——普通模式无丝滑控件可切。 */
    private void applyVisibility(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_wv_toggle_navigation);
        if (item != null && !isSmoothReadingMode(host.context())) {
            item.setVisible(false);
        }
    }

    /** 拦截模式图标染色:红=拦截 / 白=直接跳转 / 灰=弹窗确认。 */
    public void tintBlockIcon(Menu menu) {
        MenuItem item = menu == null ? null : menu.findItem(R.id.action_wv_toggle_block);
        if (item != null) {
            Drawable drawable = AppCompatResources.getDrawable(host.context(), R.drawable.ic_external_block);
            if (drawable != null) {
                drawable = DrawableCompat.wrap(drawable).mutate();
                DrawableCompat.setTint(drawable, tintColorForMode(ExternalLinkOpener.currentMode(host.context())));
                item.setIcon(drawable);
            }
        }
    }

    /** @return true 表示该菜单项由本类消费 */
    public boolean handle(int itemId) {
        if (itemId == R.id.action_wv_refresh) {
            refresh();
            return true;
        }
        if (itemId == R.id.action_wv_open_browser) {
            openInBrowser();
            return true;
        }
        if (itemId == R.id.action_wv_extract) {
            extractContent();
            return true;
        }
        if (itemId == R.id.action_wv_share) {
            share();
            return true;
        }
        if (itemId == R.id.action_wv_toggle_block) {
            toggleExternalMode();
            return true;
        }
        if (itemId == R.id.action_wv_add_tag) {
            AddTagDialog.show(host.context(), target());
            return true;
        }
        if (itemId == R.id.action_wv_edit_title) {
            showEditTitle();
            return true;
        }
        if (itemId == R.id.action_wv_history) {
            openHistory();
            return true;
        }
        if (itemId == R.id.action_wv_override_rule) {
            openOverrideRule();
            return true;
        }
        // action_wv_toggle_navigation 落这里 = false:翻页控件开关由宿主的
        // WebViewNavControls 处理(两个宿主各自触发 toggleManually)
        return false;
    }

    // ---- 单个动作 ----

    private void refresh() {
        WebView webView = page.webView();
        if (webView == null) {
            toast(R.string.reader_action_page_loading);
            return;
        }
        webView.reload();
    }

    private void openInBrowser() {
        String url = page.url();
        if (url == null || url.isEmpty()) {
            toast(R.string.reader_action_no_url);
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            host.context().startActivity(intent);
        } catch (Exception e) {
            toast(R.string.reader_action_open_browser_failed);
            Log.e(TAG, "openInBrowser failed for " + url, e);
        }
    }

    private void extractContent() {
        final String url = page.url();
        if (url == null || url.isEmpty()) {
            toast(R.string.reader_action_no_url);
            return;
        }
        final Context app = host.context().getApplicationContext();
        new Thread(() -> {
            String content = null;
            try {
                content = CrawlUtil.getArticleByUrl(url);
            } catch (Exception e) {
                Log.e(TAG, "extract failed for " + url, e);
            }
            final String extracted = content;
            mainHandler.post(() -> {
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
        }, "wv-extract").start();
    }

    private void share() {
        String url = page.url();
        if (url == null || url.isEmpty()) {
            toast(R.string.reader_action_no_url);
            return;
        }
        ShareUtil.shareText(host.context(), page.title(), null, url);
    }

    /** 三种模式循环 0(弹窗) → 2(拦截) → 1(直接跳转) → 0。 */
    private void toggleExternalMode() {
        int current = ExternalLinkOpener.currentMode(host.context());
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
        host.context().getSharedPreferences(
                        ExternalLinkOpener.PREFS_SETTINGS, Context.MODE_PRIVATE)
                .edit().putInt(ExternalLinkOpener.KEY_EXTERNAL_LINK_MODE, next).apply();
        toast(message);
        host.invalidateMenu();
    }

    private void showEditTitle() {
        EditTitleDialog.show(host.context(), target(), page::onTitleUpdated);
    }

    private void openHistory() {
        Intent intent = new Intent(host.context(), LinksHistoryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        host.context().startActivity(intent);
    }

    /** contextUrl 优先取重定向前原 URL(用户原始意图),退回当前 URL。 */
    private void openOverrideRule() {
        String contextUrl = page.originalUrl();
        if (contextUrl == null || contextUrl.isEmpty()) contextUrl = page.url();
        host.showOverrideRuleDialog(contextUrl);
    }

    // ---- 工具 ----

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
                return page.originalUrl();
            }

            @Override
            public String title() {
                return page.title();
            }
        };
    }

    private static boolean isSmoothReadingMode(Context ctx) {
        return "smooth".equals(ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("reading_mode", "normal"));
    }

    private int tintColorForMode(int mode) {
        if (mode == ExternalLinkOpener.MODE_OPEN) {
            return ContextCompat.getColor(host.context(), android.R.color.white);
        }
        if (mode == ExternalLinkOpener.MODE_ASK) {
            return ContextCompat.getColor(host.context(), android.R.color.darker_gray);
        }
        return ContextCompat.getColor(host.context(), android.R.color.holo_red_dark);
    }

    private void toast(int resId) {
        Context ctx = host.context();
        if (ctx != null) {
            Toast.makeText(ctx, resId, Toast.LENGTH_SHORT).show();
        }
    }
}