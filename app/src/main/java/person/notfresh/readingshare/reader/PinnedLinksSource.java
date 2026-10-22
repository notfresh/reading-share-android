package person.notfresh.readingshare.reader;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.core.reader.ReaderSource;
import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.model.ReaderSourceSpec;

/**
 * 来源：置顶链接。
 *
 * <p>置顶是用户手动挑选的小集合（不是全表），一次性取快照后按 offset 切片；
 * 快照在本次阅读会话内有效，置顶变化在下次进页面时生效。</p>
 */
final class PinnedLinksSource implements ReaderSource {

    private static final String TAG = "PinnedLinksSource";

    private final LinkDao linkDao;
    private List<Long> snapshot;

    PinnedLinksSource(Context ctx) {
        this.linkDao = new LinkDao(DbConnection.get(ctx).writable());
    }

    @Override
    public String type() {
        return ReaderSourceSpec.TYPE_PINNED;
    }

    @Override
    public List<Long> loadPage(int offset, int limit) {
        List<Long> all = snapshot();
        if (limit <= 0 || offset < 0 || offset >= all.size()) return new ArrayList<>();
        return new ArrayList<>(all.subList(offset, Math.min(all.size(), offset + limit)));
    }

    private List<Long> snapshot() {
        if (snapshot == null) {
            snapshot = new ArrayList<>();
            try {
                for (LinkItem item : linkDao.getPinnedLinks()) {
                    snapshot.add(item.getId());
                }
            } catch (Exception e) {
                Log.e(TAG, "pinned snapshot failed", e);
            }
        }
        return snapshot;
    }
}
