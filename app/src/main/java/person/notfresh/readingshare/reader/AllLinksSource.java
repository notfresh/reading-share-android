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
 * 来源：全部链接，按 timestamp DESC。
 *
 * <p>走 {@link LinkDao#getLinksPage(int, int)} 分页，绝不 getAllLinks
 * （AGENTS.md 第六条：3000+ 链接禁止一次性加载）。</p>
 */
final class AllLinksSource implements ReaderSource {

    private static final String TAG = "AllLinksSource";

    private final LinkDao linkDao;

    AllLinksSource(Context ctx) {
        this.linkDao = new LinkDao(DbConnection.get(ctx).writable());
    }

    @Override
    public String type() {
        return ReaderSourceSpec.TYPE_ALL;
    }

    @Override
    public List<Long> loadPage(int offset, int limit) {
        List<Long> ids = new ArrayList<>();
        if (limit <= 0 || offset < 0) return ids;
        try {
            for (LinkItem item : linkDao.getLinksPage(offset, limit)) {
                ids.add(item.getId());
            }
        } catch (Exception e) {
            Log.e(TAG, "loadPage failed offset=" + offset + " limit=" + limit, e);
        }
        return ids;
    }
}
