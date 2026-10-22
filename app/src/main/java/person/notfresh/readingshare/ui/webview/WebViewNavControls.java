package person.notfresh.readingshare.ui.webview;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * 翻页控件（底部横排 + 右侧悬浮层）的显隐策略 —— 目标2 唯一实现。
 *
 * <ul>
 *   <li>reading_mode=normal → 控件永不显示</li>
 *   <li>reading_mode=smooth → 按 nav_button_style(bottom|floating|both) 显示</li>
 *   <li>显示后 3 秒自动隐藏；触摸网页临时唤起；手动隐藏状态持久化(nav_controls_hidden)</li>
 * </ul>
 *
 * <p>原来 Activity 与阅读页各写一份(语义相同但代码两份),现由本类统一,
 * 两个宿主只负责提供 View 与翻页动作。</p>
 */
public final class WebViewNavControls {

    /** 与 WebViewActivity.CONTROLS_AUTO_HIDE_MS 对齐。 */
    private static final int AUTO_HIDE_MS = 3000;
    private static final String PREFS_NAME = "settings";
    private static final String KEY_NAV_CONTROLS_HIDDEN = "nav_controls_hidden";

    private static final String STYLE_BOTTOM = "bottom";
    private static final String STYLE_FLOATING = "floating";

    private final View bottomControls;
    private final View floatingControls;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            setVisible(bottomControls, false);
            setVisible(floatingControls, false);
        }
    };

    private boolean hasContent = false;

    public WebViewNavControls(View bottomControls, View floatingControls) {
        this.bottomControls = bottomControls;
        this.floatingControls = floatingControls;
    }

    /** 内容是否可翻页（队列 ≥2 条）。内容变化后重新按策略刷新显隐。 */
    public void setContentAvailable(Context ctx, boolean available) {
        this.hasContent = available;
        handler.removeCallbacks(hideRunnable);
        if (!available || !isSmoothMode(ctx)) {
            setVisible(bottomControls, false);
            setVisible(floatingControls, false);
            return;
        }
        boolean showBottom = showBottomNav(ctx);
        boolean showFloating = showFloatingNav(ctx);
        boolean manuallyHidden = isManuallyHidden(ctx);

        setVisible(bottomControls, showBottom && !manuallyHidden);
        setVisible(floatingControls, showFloating);
        if (showBottom || showFloating) {
            scheduleAutoHide();
        }
    }

    /** 触摸网页时临时唤起（丝滑模式、未手动隐藏才有效）。 */
    public void onWebViewTouched(Context ctx) {
        if (!hasContent || !isSmoothMode(ctx) || isManuallyHidden(ctx)) return;
        boolean any = false;
        if (showBottomNav(ctx)) {
            setVisible(bottomControls, true);
            any = true;
        }
        if (showFloatingNav(ctx)) {
            setVisible(floatingControls, true);
            any = true;
        }
        if (any) scheduleAutoHide();
    }

    /** 菜单项「显示/隐藏导航控件」：翻转手动隐藏状态并持久化（与 Activity 共用同一 SP key）。 */
    public void toggleManually(Context ctx) {
        if (!hasContent || !isSmoothMode(ctx)) return;
        if (showFloatingNav(ctx) && !showBottomNav(ctx)) return; // 只有悬浮层时无底部条可切
        boolean hidden = !isManuallyHidden(ctx);
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_NAV_CONTROLS_HIDDEN, hidden).apply();
        handler.removeCallbacks(hideRunnable);
        setVisible(bottomControls, !hidden);
        if (showFloatingNav(ctx)) {
            setVisible(floatingControls, true);
            if (!hidden) scheduleAutoHide();
        }
    }

    public boolean isSmoothMode(Context ctx) {
        String mode = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("reading_mode", "normal");
        return "smooth".equals(mode);
    }

    public boolean isManuallyHidden(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_NAV_CONTROLS_HIDDEN, false);
    }

    private boolean showBottomNav(Context ctx) {
        String style = navStyle(ctx);
        return STYLE_BOTTOM.equals(style) || "both".equals(style);
    }

    private boolean showFloatingNav(Context ctx) {
        String style = navStyle(ctx);
        return STYLE_FLOATING.equals(style) || "both".equals(style);
    }

    private String navStyle(Context ctx) {
        return ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("nav_button_style", "both");
    }

    private void scheduleAutoHide() {
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, AUTO_HIDE_MS);
    }

    private void setVisible(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /** 视图销毁时清掉待执行的自动隐藏任务，避免泄漏 Activity。 */
    public void detach() {
        handler.removeCallbacks(hideRunnable);
    }
}
