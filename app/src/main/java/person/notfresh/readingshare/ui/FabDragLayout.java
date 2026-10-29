package person.notfresh.readingshare.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

/**
 * 可长按拖拽的 FAB 容器。
 *
 * <p>**为什么要有这个类**:底部那组悬浮按钮(FAB)原来固定在右下角(或左下角),
 * 会挡住输入行、列表条目。用户希望能挪开并记住位置。</p>
 *
 * <p>**交互契约**:
 * <ul>
 *   <li>短按/点按 —— 跟以前完全一样,事件原样交给里面的 FAB(不消费 ACTION_DOWN)</li>
 *   <li>按住 {@link ViewConfiguration#getLongPressTimeout()} 毫秒 → 震动一下 + 轻微放大,进入拖拽</li>
 *   <li>拖拽中位置夹在父容器内,松手落盘(SharedPreferences),下次进 app 还在原位</li>
 *   <li>长按后直接抬手 —— 吞掉,不会误触发某个 FAB 的点击</li>
 * </ul>
 *
 * <p>**为什么要 onInterceptTouchEvent 而不是 OnTouchListener**:里面的子 View 是
 * MaterialButton / FloatingActionButton,自己在 DOWN 上就会消费事件,容器根本收不到
 * 后续事件。只有靠父容器"先旁观、长按成立再拦截"这条路(子 View 会收到 CANCEL)。</p>
 *
 * <p>**位置怎么存**:存的是 {@code getX()/getY()}(= 布局位置 + translation)的绝对像素。
 * 左手/右手模式改的是 layoutParams 的 gravity/margin,那只会改变布局位置,
 * 我们每次 onLayout 后重新夹紧一次,两者不会打架。</p>
 */
public final class FabDragLayout extends LinearLayout {

    /** 项目统一用 "settings" 这个 SharedPreferences。 */
    private static final String PREFS_NAME = "settings";
    private static final String KEY_X = "fab_offset_x";
    private static final String KEY_Y = "fab_offset_y";

    private static final float DRAG_ALPHA = 0.92f;
    private static final float DRAG_SCALE = 1.04f;
    private static final long FEEDBACK_MS = 120L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int longPressTimeout;
    private final int touchSlop;

    private float downRawX;
    private float downRawY;
    private float grabOffsetX;
    private float grabOffsetY;

    private boolean longPressed;
    private boolean dragging;
    private boolean offsetsRestored;

    private float restingAlpha = 1f;

    private final Runnable longPressRunnable = () -> {
        longPressed = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        animate().alpha(DRAG_ALPHA)
                .scaleX(DRAG_SCALE).scaleY(DRAG_SCALE)
                .setDuration(FEEDBACK_MS)
                .start();
    };

    public FabDragLayout(Context context) {
        this(context, null);
    }

    public FabDragLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FabDragLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ViewConfiguration vc = ViewConfiguration.get(context);
        longPressTimeout = vc.getLongPressTimeout();
        touchSlop = vc.getScaledTouchSlop();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        restingAlpha = getAlpha();
    }

    // ── 手势:旁观 → 长按成立才拦截 ────────────────────────────────────

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = ev.getRawX();
                downRawY = ev.getRawY();
                longPressed = false;
                dragging = false;
                handler.postDelayed(longPressRunnable, longPressTimeout);
                return false;

            case MotionEvent.ACTION_MOVE:
                if (!longPressed) {
                    // 长按没成立就先移动 → 用户是在滑列表/误触,取消计时,不抢事件
                    if (Math.abs(ev.getRawX() - downRawX) > touchSlop
                            || Math.abs(ev.getRawY() - downRawY) > touchSlop) {
                        handler.removeCallbacks(longPressRunnable);
                    }
                    return false;
                }
                beginDrag(ev);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);
                if (longPressed) {
                    // 长按成立后直接抬手:拦下来,子 FAB 收到 CANCEL,不会误点击
                    longPressed = false;
                    endVisualFeedback();
                    return true;
                }
                return false;

            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    moveTo(ev.getRawX() - grabOffsetX, ev.getRawY() - grabOffsetY);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);
                if (dragging) {
                    dragging = false;
                    persistOffsets();
                }
                longPressed = false;
                endVisualFeedback();
                return true;

            default:
                return true;
        }
    }

    private void beginDrag(MotionEvent ev) {
        dragging = true;
        grabOffsetX = ev.getRawX() - getX();
        grabOffsetY = ev.getRawY() - getY();
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
    }

    // ── 位置:夹紧 + 落盘 + 恢复 ────────────────────────────────────────

    /** 移动到指定位置(传的是绝对 x/y),自动夹在父容器内。 */
    private void moveTo(float x, float y) {
        setX(clampX(x));
        setY(clampY(y));
    }

    private float clampX(float x) {
        View parent = (View) getParent();
        if (parent == null || getWidth() == 0) return x;
        float max = parent.getWidth() - getWidth();
        return Math.max(0f, Math.min(x, Math.max(0f, max)));
    }

    private float clampY(float y) {
        View parent = (View) getParent();
        if (parent == null || getHeight() == 0) return y;
        float max = parent.getHeight() - getHeight();
        return Math.max(0f, Math.min(y, Math.max(0f, max)));
    }

    private void persistOffsets() {
        prefs().edit()
                .putFloat(KEY_X, getX())
                .putFloat(KEY_Y, getY())
                .apply();
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (getWidth() == 0 || getParent() == null) return;
        if (!offsetsRestored) {
            offsetsRestored = true;
            SharedPreferences p = prefs();
            if (p.contains(KEY_X) || p.contains(KEY_Y)) {
                moveTo(p.getFloat(KEY_X, getX()), p.getFloat(KEY_Y, getY()));
            }
            return;
        }
        // 展开/收起、左右手切换后尺寸或布局位置会变 —— 重新夹一次,避免跑出屏幕
        moveTo(getX(), getY());
    }

    private void endVisualFeedback() {
        animate().alpha(restingAlpha)
                .scaleX(1f).scaleY(1f)
                .setDuration(FEEDBACK_MS)
                .start();
    }
}
