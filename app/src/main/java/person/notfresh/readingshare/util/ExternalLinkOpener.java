package person.notfresh.readingshare.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import person.notfresh.readingshare.R;

/**
 * 外部链接打开（共享单元）：intent:// 解析 + startActivity + 三档确认弹窗。
 *
 * <p>语义与 WebViewActivity.openExternalUri / showExternalOpenConfirmDialog 对齐。
 * Activity 侧待第二刀迁到本类，届时删掉它自己那份。</p>
 *
 * <p>本类只做"怎么打开"，"要不要打开"的判定在
 * {@link person.notfresh.readingshare.core.reader.UrlRoute}。</p>
 */
public final class ExternalLinkOpener {

    /** 全局外部链接模式所在的 SP（与 settings 页同源）。 */
    public static final String PREFS_SETTINGS = "settings";
    /** 0=弹窗确认 1=直接跳转 2=静默拦截（默认）。 */
    public static final String KEY_EXTERNAL_LINK_MODE = "external_link_mode";
    public static final int MODE_ASK = 0;
    public static final int MODE_OPEN = 1;
    public static final int MODE_BLOCK = 2;

    private static final String TAG = "ExternalLinkOpener";

    private ExternalLinkOpener() {
        // utility class
    }

    public static int currentMode(Context ctx) {
        if (ctx == null) return MODE_BLOCK;
        try {
            return prefs(ctx).getInt(KEY_EXTERNAL_LINK_MODE, MODE_BLOCK);
        } catch (Exception e) {
            return MODE_BLOCK;
        }
    }

    /** 用户选择"拒绝并禁止弹窗"：把全局模式钉到静默拦截。 */
    public static void forbidAll(Context ctx) {
        if (ctx == null) return;
        prefs(ctx).edit().putInt(KEY_EXTERNAL_LINK_MODE, MODE_BLOCK).apply();
    }

    /** 直接打开（含 intent:// 解析），失败兜底 toast。 */
    public static void open(Context ctx, String url) {
        if (ctx == null || url == null || url.isEmpty()) return;
        try {
            Intent intent;
            if (url.startsWith("intent://")) {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setComponent(null);
                intent.setSelector(null);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
            if (!(ctx instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            ctx.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "open failed for url=" + url, e);
            Toast.makeText(ctx, ctx.getString(R.string.reader_external_open_failed, url), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 三档确认弹窗：留在网页 / 拒绝并禁止弹窗 / 继续打开。
     *
     * <p>样式保持三按钮原样(2.3.20.2 用户要求)。英文文案必须短 ——
     * 「Stay on page / Deny and stop asking / Open」超宽会让系统把按钮竖排,
     * 所以英文用 Stay / Never ask / Open(见 values-en/strings.xml),
     * 不用"改布局/框定宽度"这种侵入手段。</p>
     *
     * @param onForbidAll 用户选"拒绝并禁止弹窗"时的回调（由调用方决定要不要持久化）
     */
    public static void confirm(Context ctx, String url, Runnable onForbidAll) {
        if (ctx == null || url == null || url.isEmpty()) return;
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.reader_external_confirm_title)
                .setMessage(ctx.getString(R.string.reader_external_confirm_message, url))
                .setNegativeButton(R.string.reader_external_confirm_stay, null)
                .setNeutralButton(R.string.reader_external_confirm_forbid, (dialog, which) -> {
                    forbidAll(ctx);
                    if (onForbidAll != null) onForbidAll.run();
                })
                .setPositiveButton(R.string.reader_external_confirm_open,
                        (dialog, which) -> open(ctx, url))
                .show();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
    }
}
