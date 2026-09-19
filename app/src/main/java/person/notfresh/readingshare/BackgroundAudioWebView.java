package person.notfresh.readingshare;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;

import androidx.annotation.NonNull;

/**
 * 自定义 WebView，拦截系统级的可见性/解绑事件，
 * 防止 WebView 在 App 进入后台或被 detach 时自动暂停媒体播放，
 * 实现后台音频播放 + Activity finish 后音频继续。
 */
public class BackgroundAudioWebView extends WebView {

    private static final String TAG = "BackgroundAudioWebView";

    public BackgroundAudioWebView(Context context) {
        super(context);
    }

    public BackgroundAudioWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BackgroundAudioWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        // 当 App 进入后台时，系统会传入 GONE，WebView 内部会暂停渲染和媒体播放。
        // 拦截 GONE 不传递给父类，WebView 就会认为自己仍然可见，继续播放音频。
        if (visibility != View.GONE) {
            super.onWindowVisibilityChanged(visibility);
        }
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        // 折叠 mini 场景：父容器被 setVisibility(GONE)，视图级可见性沿视图树向下传播，
        // Chromium WebView 收到后会隐藏 WebContents 并暂停媒体。
        // 拦截非 VISIBLE 状态不传给父类，让 WebView 继续认为自己可见，音频不断。
        if (visibility == View.VISIBLE) {
            super.onVisibilityChanged(changedView, visibility);
        }
    }
}

