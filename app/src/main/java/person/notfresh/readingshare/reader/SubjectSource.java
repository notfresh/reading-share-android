package person.notfresh.readingshare.reader;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.core.model.SubjectItem;
import person.notfresh.readingshare.core.reader.ReaderSource;
import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.model.ReaderSourceSpec;

/**
 * 来源：某个主题下的链接。
 *
 * <p>数据在 subject_items 表：只取 linkId 非空、链接未被删、且未归档的项。
 * 主题规模是人工收集的，一次性取快照后按 offset 切片。</p>
 */
final class SubjectSource implements ReaderSource {

    private static final String TAG = "SubjectSource";

    private final SubjectDao subjectDao;
    private final long subjectId;
    private List<Long> snapshot;

    SubjectSource(Context ctx, long subjectId) {
        // 必须走 DbConnection 注入共享连接：SubjectDao(Context) 是 @Deprecated 构造，
        // database 字段要等 open() 才有值，漏 open 会 NPE 被吃掉 → 表现为"来源里没有链接"。
        this.subjectDao = new SubjectDao(DbConnection.get(ctx).writable());
        this.subjectId = subjectId;
    }

    @Override
    public String type() {
        return ReaderSourceSpec.TYPE_SUBJECT;
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
            if (subjectId <= 0) return snapshot;
            try {
                for (SubjectItem item : subjectDao.getSubjectItemsBySubjectId(subjectId)) {
                    Long linkId = item.getLinkId();
                    if (linkId != null && linkId > 0 && !item.isLinkDeleted() && !item.isArchived()) {
                        snapshot.add(linkId);
                    }
                }
                Log.d(TAG, "subject snapshot subjectId=" + subjectId + " links=" + snapshot.size());
            } catch (Exception e) {
                Log.e(TAG, "subject snapshot failed subjectId=" + subjectId, e);
            }
        }
        return snapshot;
    }
}
