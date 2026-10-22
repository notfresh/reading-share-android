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
 * 来源：某个标签下的链接（含被置顶的标签，置顶只影响标签顺序，不影响查询）。
 * 单标签是子集查询，不是全表，一次性取快照后按 offset 切片。
 */
final class TagSource implements ReaderSource {

    private static final String TAG = "TagSource";

    private final LinkDao linkDao;
    private final String tagName;
    private List<Long> snapshot;

    TagSource(Context ctx, String tagName) {
        this.linkDao = new LinkDao(DbConnection.get(ctx).writable());
        this.tagName = tagName == null ? "" : tagName;
    }

    @Override
    public String type() {
        return ReaderSourceSpec.TYPE_TAG;
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
            if (tagName.isEmpty()) return snapshot;
            try {
                for (LinkItem item : linkDao.getLinksByTag(tagName)) {
                    snapshot.add(item.getId());
                }
            } catch (Exception e) {
                Log.e(TAG, "tag snapshot failed tag=" + tagName, e);
            }
        }
        return snapshot;
    }
}
