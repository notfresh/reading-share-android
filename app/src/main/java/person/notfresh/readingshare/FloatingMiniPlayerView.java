package person.notfresh.readingshare;

import android.content.Context;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 悬浮 mini player 视图。
 * 点击 = 触发外部传入的 OnClickListener。
 * 拖动 = 移动 LayoutParams 坐标，松手自动吸左/右贴边。
 */
public class FloatingMiniPlayerView extends FrameLayout {

    private final WindowManager.LayoutParams params;
    private final int touchSlop;
    private final float density;

    private float downRawX, downRawY;
    private int initialX, initialY;
    private boolean isDragging;

    public FloatingMiniPlayerView(@NonNull Context context, @Nullable OnClickListener onClickListener) {
        super(context);
        this.density = context.getResources().getDisplayMetrics().density;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.params = buildLayoutParams();

        LayoutInflater.from(context).inflate(R.layout.floating_mini_player, this, true);
        setOnClickListener(onClickListener);

        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleTouch(event);
            }
        });
    }

    private WindowManager.LayoutParams buildLayoutParams() {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        int margin = (int) (16 * density + 0.5f);
        p.x = margin;
        p.y = margin;
        // 56dp 转 px
        p.width = (int) (56 * density + 0.5f);
        p.height = (int) (56 * density + 0.5f);
        return p;
    }

    private boolean handleTouch(MotionEvent event) {
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                initialX = params.x;
                initialY = params.y;
                isDragging = false;
                return false; // 让 click 事件也有机会触发
            case MotionEvent.ACTION_MOVE: {
                int dx = (int) (event.getRawX() - downRawX);
                int dy = (int) (event.getRawY() - downRawY);
                if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    isDragging = true;
                }
                if (isDragging) {
                    params.x = initialX + dx;
                    params.y = initialY + dy;
                    if (wm != null) {
                        try {
                            wm.updateViewLayout(this, params);
                        } catch (IllegalArgumentException ignored) {
                            // view 已被外部 remove
                        }
                    }
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    snapToEdge(wm);
                    isDragging = false;
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private void snapToEdge(WindowManager wm) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int viewWidth = params.width > 0 ? params.width : (int) (56 * density + 0.5f);
        int centerX = params.x + viewWidth / 2;
        params.x = centerX < screenWidth / 2 ? 0 : screenWidth - viewWidth;
        if (wm != null) {
            try {
                wm.updateViewLayout(this, params);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void attachTo(WindowManager wm) {
        try {
            wm.addView(this, params);
            android.util.Log.d("FloatingMini", "addView 成功,params=" + params.x + "," + params.y + " " + params.width + "x" + params.height);
        } catch (WindowManager.BadTokenException e) {
            android.util.Log.e("FloatingMini", "addView BadTokenException,通常是缺少 SYSTEM_ALERT_WINDOW 权限", e);
        } catch (IllegalStateException e) {
            android.util.Log.w("FloatingMini", "addView IllegalStateException,可能已 add 过", e);
        } catch (SecurityException e) {
            android.util.Log.e("FloatingMini", "addView SecurityException,缺少悬浮窗权限", e);
        } catch (Exception e) {
            android.util.Log.e("FloatingMini", "addView 异常", e);
        }
    }

    public void detachFrom(WindowManager wm) {
        if (getParent() != null) {
            try {
                wm.removeView(this);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
