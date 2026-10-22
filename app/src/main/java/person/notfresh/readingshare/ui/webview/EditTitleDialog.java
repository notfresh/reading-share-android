package person.notfresh.readingshare.ui.webview;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;

/**
 * 「修改标题」弹窗（目标2 抽出的共享实现）。
 *
 * <p>原来 WebViewActivity 与 ReaderFragment 各一份：Activity 那份硬编码中文且只用 URL 定位，
 * 页面重定向后改不中；阅读页那份按 linkId 定位。这里统一：<b>有 linkId 就按 id 改</b>，
 * 没有才退回 URL（再加一层 originalUrl 兜底），并支持回调把新标题同步回调用方的缓存。</p>
 */
public final class EditTitleDialog {

    private static final String TAG = "EditTitleDialog";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "edit-title-dialog");
        t.setDaemon(true);
        return t;
    });

    /** 标题改成功后回调（调用方可更新自己的标题缓存）。 */
    public interface OnUpdated {
        void onUpdated(String newTitle);
    }

    private EditTitleDialog() {
        // utility class
    }

    public static void show(final Context ctx, final PageTarget target, final OnUpdated onUpdated) {
        if (ctx == null || target == null) return;
        if (target.linkId() == null && (target.url() == null || target.url().isEmpty())) {
            Toast.makeText(ctx, R.string.reader_action_no_url, Toast.LENGTH_SHORT).show();
            return;
        }

        final EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(target.title() == null ? "" : target.title());
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.edit_title)
                .setView(input)
                .setPositiveButton(R.string.link_dialog_ok, (dialog, which) -> {
                    String newTitle = input.getText().toString().trim();
                    if (newTitle.isEmpty()) {
                        Toast.makeText(ctx, R.string.reader_action_title_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    save(ctx, target, newTitle, onUpdated);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void save(final Context ctx, final PageTarget target,
                             final String newTitle, final OnUpdated onUpdated) {
        final Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            int updated = 0;
            try {
                LinkDao dao = new LinkDao(DbConnection.get(app).writable());
                Long linkId = target.linkId();
                if (linkId != null) {
                    updated = dao.updateLinkTitleById(linkId, newTitle);
                } else {
                    updated = dao.updateLinkTitle(target.url(), newTitle);
                    if (updated == 0 && target.originalUrl() != null
                            && !target.originalUrl().equals(target.url())) {
                        updated = dao.updateLinkTitle(target.originalUrl(), newTitle);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "update title failed", e);
            }
            final int rows = updated;
            MAIN.post(() -> {
                if (rows > 0) {
                    if (onUpdated != null) onUpdated.onUpdated(newTitle);
                    Toast.makeText(app, R.string.reader_action_title_updated, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(app, R.string.reader_action_title_not_saved, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
