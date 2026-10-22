package person.notfresh.readingshare.ui.reader;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.core.reader.ReaderQueue;
import person.notfresh.readingshare.core.reader.ReaderSource;
import person.notfresh.readingshare.core.reader.UrlRoute;
import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.model.ReaderSourceSpec;
import person.notfresh.readingshare.model.WebViewOverrideRule;
import person.notfresh.readingshare.reader.ReaderSources;
import person.notfresh.readingshare.util.ExternalLinkOpener;
import person.notfresh.readingshare.util.ReaderSourceStore;
import person.notfresh.readingshare.util.WebViewDefaults;
import person.notfresh.readingshare.util.WebViewOverrideRules;

/**
 * 阅读页（内嵌 WebView）：按"可配置来源"组成队列，支持上一条 / 下一条 / 随机。
 *
 * <p>职责划分（高内聚）：
 * <ul>
 *   <li>队列与翻页语义 → {@link ReaderQueue}（内核，纯 Java）</li>
 *   <li>URL 判定 → {@link UrlRoute}；打开外部应用 → {@link ExternalLinkOpener}</li>
 *   <li>本类只做 UI 接线：WebView 生命周期、把内核结果 loadUrl、按钮可用状态</li>
 * </ul>
 */
public class ReaderFragment extends Fragment implements ReaderMenuActions.Page {

    private static final String TAG = "ReaderFragment";

    private WebView webView;
    private ProgressBar progress;
    private TextView emptyView;
    private View bottomControls;
    private View floatingControls;
    private Button prevButton;
    private Button nextButton;
    private ImageButton randomButton;
    private ReaderControlsController controlsController;

    private ReaderQueue queue;
    private LinkDao linkDao;
    private ReaderMenuActions menuActions;

    /** 当前页面所属的队列链接 id；用户点了页内链接后置空，避免误给别的链接打标签。 */
    private Long currentLinkId;
    private String currentLinkUrl;
    private String pageTitle = "";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean moving = new AtomicBoolean(false);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reader, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setHasOptionsMenu(true);

        webView = view.findViewById(R.id.reader_webview);
        progress = view.findViewById(R.id.reader_progress);
        emptyView = view.findViewById(R.id.reader_empty);
        bottomControls = view.findViewById(R.id.reader_navigation_controls);
        floatingControls = view.findViewById(R.id.reader_floating_nav_controls);
        prevButton = view.findViewById(R.id.reader_button_previous);
        nextButton = view.findViewById(R.id.reader_button_next);
        randomButton = view.findViewById(R.id.reader_floating_random);

        linkDao = new LinkDao(DbConnection.get(requireContext()).writable());
        menuActions = new ReaderMenuActions(this, this);

        WebViewDefaults.apply(webView);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new RouteWebViewClient());

        prevButton.setOnClickListener(v -> step(Move.PREVIOUS));
        nextButton.setOnClickListener(v -> step(Move.NEXT));
        randomButton.setOnClickListener(v -> step(Move.RANDOM));
        View floatingPrev = view.findViewById(R.id.reader_floating_previous);
        View floatingNext = view.findViewById(R.id.reader_floating_next);
        if (floatingPrev != null) floatingPrev.setOnClickListener(v -> step(Move.PREVIOUS));
        if (floatingNext != null) floatingNext.setOnClickListener(v -> step(Move.NEXT));

        controlsController = new ReaderControlsController(bottomControls, floatingControls);
        webView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                controlsController.onWebViewTouched(requireContext());
            }
            return false;
        });

        installBackHandling();
        loadQueue();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    public void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(new WebViewClient());
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.destroy();
            webView = null;
        }
        queue = null;
        currentLinkId = null;
        currentLinkUrl = null;
        if (controlsController != null) {
            controlsController.detach();
            controlsController = null;
        }
        super.onDestroyView();
    }

    // ---- 顶部菜单 ----

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.reader_menu, menu);
        menuActions.tintBlockIcon(menu);
        MenuItem toggleItem = menu.findItem(R.id.action_reader_toggle_navigation);
        if (toggleItem != null && controlsController != null) {
            toggleItem.setVisible(controlsController.isSmoothMode(requireContext()));
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_reader_toggle_navigation) {
            if (controlsController != null) {
                controlsController.toggleManually(requireContext());
                requireActivity().invalidateOptionsMenu();
            }
            return true;
        }
        if (menuActions != null && menuActions.handle(item.getItemId())) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---- ReaderMenuActions.Page ----

    @Override
    public WebView webView() {
        return webView;
    }

    @Override
    public Long linkId() {
        return currentLinkId;
    }

    @Override
    public String url() {
        if (webView != null && webView.getUrl() != null && !webView.getUrl().isEmpty()) {
            return webView.getUrl();
        }
        return currentLinkUrl;
    }

    @Override
    public String title() {
        if (pageTitle != null && !pageTitle.isEmpty()) return pageTitle;
        if (webView != null && webView.getTitle() != null) return webView.getTitle();
        return "";
    }

    // ---- 队列装配 ----

    private void loadQueue() {
        showProgress(true);
        showEmpty(false);
        updateControls();
        final Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            ReaderQueue loaded = null;
            Long firstId = null;
            try {
                ReaderSourceSpec spec = ReaderSourceStore.load(app);
                ReaderSource source = ReaderSources.create(app, spec);
                loaded = new ReaderQueue(source);
                if (loaded.load()) {
                    firstId = loaded.current();
                }
            } catch (Exception e) {
                Log.e(TAG, "loadQueue failed", e);
            }
            final ReaderQueue resolvedQueue = loaded;
            final Long id = firstId;
            mainHandler.post(() -> {
                if (!isAdded()) return;
                queue = resolvedQueue;
                if (id == null) {
                    showProgress(false);
                    showEmpty(true);
                    updateControls();
                    return;
                }
                openById(id, false);
            });
        }, "reader-load-queue").start();
    }

    /** 取 URL（后台）→ 主线程 loadUrl。所有入口都收敛到这里，避免 4 处重复。 */
    private void openById(final long linkId, final boolean releaseMovingWhenDone) {
        final Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            String url = null;
            try {
                LinkItem item = linkDao.getLinkById(linkId);
                if (item != null && item.getUrl() != null && !item.getUrl().trim().isEmpty()) {
                    url = item.getUrl();
                }
            } catch (Exception e) {
                Log.w(TAG, "openById lookup failed linkId=" + linkId, e);
            }
            final String targetUrl = url;
            mainHandler.post(() -> {
                if (releaseMovingWhenDone) moving.set(false);
                if (!isAdded() || webView == null) return;
                if (targetUrl == null) {
                    showProgress(false);
                    Toast.makeText(requireContext(), R.string.reader_link_unavailable, Toast.LENGTH_SHORT).show();
                    updateControls();
                    return;
                }
                showEmpty(false);
                currentLinkId = linkId;
                currentLinkUrl = targetUrl;
                pageTitle = "";
                webView.loadUrl(targetUrl);
                updateControls();
            });
        }, "reader-open-" + linkId).start();
    }

    private enum Move { PREVIOUS, NEXT, RANDOM }

    private void step(Move move) {
        if (queue == null || queue.isEmpty()) return;
        if (!moving.compareAndSet(false, true)) return;
        Long id;
        switch (move) {
            case PREVIOUS:
                id = queue.previous();
                break;
            case RANDOM:
                id = queue.random();
                break;
            case NEXT:
            default:
                id = queue.next();
                break;
        }
        if (id == null) {
            moving.set(false);
            updateControls();
            return;
        }
        openById(id, true);
    }

    // ---- WebView 路由（判定在内核，执行在本类）----

    private final class RouteWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return route(view, url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request != null && request.getUrl() != null ? request.getUrl().toString() : null;
            return route(view, url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            showProgress(false);
            if (view.getTitle() != null && !view.getTitle().trim().isEmpty()) {
                pageTitle = view.getTitle().trim();
                if (isAdded()) {
                    requireActivity().setTitle(pageTitle);
                }
            }
        }

        /**
         * 命中白名单的任何 action 都返回 true（§27.3：否则内核会尝试加载 app scheme
         * 并渲染 ERR_UNKNOWN_URL_SCHEME）；只有 http(s) 交回 WebView。
         */
        private boolean route(WebView view, String url) {
            if (url == null || url.isEmpty()) return false;
            if (UrlRoute.isHttp(url)) {
                // 页内跳转：当前页面已不是队列里那条链接，清掉 linkId 防止误打标签
                currentLinkId = null;
                currentLinkUrl = url;
                return false;
            }

            Optional<WebViewOverrideRule> hit = WebViewOverrideRules.firstMatch(requireContext(), pageCandidates(view));
            if (hit.isPresent()) {
                WebViewOverrideRule rule = hit.get();
                String currentPageUrl = view.getUrl();
                if (currentPageUrl != null && !currentPageUrl.isEmpty()
                        && !currentPageUrl.equalsIgnoreCase(rule.getContextUrl())) {
                    WebViewOverrideRules.recordRedirectedUrl(requireContext(), rule.getContextUrl(), currentPageUrl);
                }
                execute(UrlRoute.decide(url, Optional.of(rule.getAction()), ExternalLinkOpener.currentMode(requireContext())), url);
                return true;
            }
            execute(UrlRoute.decide(url, Optional.empty(), ExternalLinkOpener.currentMode(requireContext())), url);
            return true;
        }
    }

    private List<String> pageCandidates(WebView view) {
        List<String> candidates = new ArrayList<>(2);
        String current = view.getUrl();
        String original = view.getOriginalUrl();
        if (current != null && !current.isEmpty()) candidates.add(current);
        if (original != null && !original.isEmpty()) candidates.add(original);
        return candidates;
    }

    private void execute(UrlRoute.Route route, String url) {
        switch (route) {
            case OPEN_EXTERNAL:
                ExternalLinkOpener.open(requireContext(), url);
                break;
            case ASK_USER:
                ExternalLinkOpener.confirm(requireContext(), url, null);
                break;
            case BLOCK:
            case LOAD_IN_WEBVIEW:
            default:
                break;
        }
    }

    // ---- UI ----

    private void installBackHandling() {
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (webView != null && webView.canGoBack()) {
                            webView.goBack();
                            return;
                        }
                        setEnabled(false);
                        requireActivity().getOnBackPressedDispatcher().onBackPressed();
                    }
                });
    }

    private void updateControls() {
        boolean available = queue != null && queue.size() > 1 && !moving.get();
        if (prevButton != null) prevButton.setEnabled(available);
        if (nextButton != null) nextButton.setEnabled(available);
        if (randomButton != null) randomButton.setEnabled(available);
        if (controlsController != null && isAdded()) {
            controlsController.setContentAvailable(requireContext(), available);
        }
    }

    private void showProgress(boolean visible) {
        if (progress == null) return;
        progress.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showEmpty(boolean visible) {
        if (emptyView == null) return;
        emptyView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
