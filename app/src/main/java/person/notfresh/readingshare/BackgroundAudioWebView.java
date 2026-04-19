package person.notfresh.readingshare;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebView;

/**
 * 自定义 WebView，当 App 进入后台时拦截 onWindowVisibilityChanged(GONE)，
 * 防止系统自动暂停媒体播放，从而实现后台音频播放。
 */
public class BackgroundAudioWebView extends WebView {

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
}
