package person.notfresh.readingshare.reader;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import person.notfresh.readingshare.core.reader.ReaderSource;
import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.model.ReaderSourceSpec;

/**
 * 来源：猜你喜欢（2026-11-01,docs/261101-feature-Guess you like.md）。
 *
 * <p>算法：你最常读哪类，就推荐哪类里你还没读的。</p>
 * <ol>
 *   <li>兴趣权重：最近 30 天 links_history 按标签聚合阅读次数 → w(tag)；</li>
 *   <li>候选打分：未读链接 score = Σ w(所属标签)，无标签用域名权重兜底；</li>
 *   <li>过滤：links_history 里出现过的链接剔除；</li>
 *   <li>排序：score 降序，同分随机，避免每次一样。</li>
 * </ol>
 *
 * <p><b>刷新语义（用户 2026-11-01 拍板"都要"）</b>：推荐按 5 个一批滚动——
 * 每消费完一批（翻到下一页）就重算一次权重；重进阅读页也重算。
 * 所以这里不缓存大快照，{@link #loadPage} 以 5 个为一轮：每轮起点重新查库。</p>
 */
final class ForYouSource implements ReaderSource {

    private static final String TAG = "ForYouSource";
    /** 兴趣回看窗口（毫秒）：30 天。 */
    private static final long WINDOW_MS = 30L * 24 * 60 * 60 * 1000;
    /** 一批 = 5 个，消费完重算（用户拍板）。 */
    private static final int BATCH = 5;
    /** 单次候选查询上限：一批取 5,候选池放宽到 100 保证洗牌有空间。 */
    private static final int POOL_LIMIT = 100;

    private final LinkDao linkDao;

    ForYouSource(Context ctx) {
        this.linkDao = new LinkDao(DbConnection.get(ctx).writable());
    }

    @Override
    public String type() {
        return ReaderSourceSpec.TYPE_FOR_YOU;
    }

    @Override
    public List<Long> loadPage(int offset, int limit) {
        List<Long> out = new ArrayList<>();
        if (limit <= 0 || offset < 0) return out;
        // 每个新批次（含批内翻页偏移）都基于"此刻"的兴趣权重实时算
        try {
            collectByTagWeight(out, limit);
            if (out.size() < limit) collectByDomainWeight(out, limit);
        } catch (Exception e) {
            Log.e(TAG, "loadPage failed offset=" + offset + " limit=" + limit, e);
        }
        return out;
    }

    /** 第 1-4 步：按标签权重打分未读链接，取 limit 个。 */
    private void collectByTagWeight(List<Long> out, int limit) {
        long since = System.currentTimeMillis() - WINDOW_MS;
        Cursor c = linkDao.getForYouCandidates(since, POOL_LIMIT);
        if (c == null) return;
        try {
            List<Long> ids = new ArrayList<>();
            while (c.moveToNext()) ids.add(c.getLong(0));
            shuffleWithinScore(ids, c);
            for (long id : ids) {
                if (out.size() >= limit) break;
                out.add(id);
            }
        } finally {
            c.close();
        }
    }

    /**
     * 候选 SQL 已按 score 降序返回；把「同分区段内部」随机重排，跨区段顺序不动。
     * 整批均匀洗牌会破坏打分序，所以按 score 分组洗——SQL 里带出 score 列。
     */
    private void shuffleWithinScore(List<Long> ids, Cursor c) {
        Random rnd = new Random();
        int i = 0;
        while (i < ids.size()) {
            c.moveToPosition(i);
            double score = c.getDouble(1);
            int j = i;
            double lastScore = score;
            while (j < ids.size()) {
                c.moveToPosition(j);
                double s = c.getDouble(1);
                if (s != lastScore) break;
                lastScore = s;
                j++;
            }
            // [i, j) 是同分段
            for (int k = j - 1; k > i; k--) {
                int swap = i + rnd.nextInt(k - i + 1);
                long tmp = ids.get(i);
                ids.set(i, ids.get(swap));
                ids.set(swap, tmp);
            }
            i = j;
        }
    }

    /** 无标签兜底：按域名阅读次数排序的未读链接，凑满 limit。 */
    private void collectByDomainWeight(List<Long> out, int limit) {
        Cursor c = linkDao.getForYouDomainCandidates(WINDOW_MS, POOL_LIMIT);
        if (c == null) return;
        try {
            Set<Long> picked = new HashSet<>(out);
            while (c.moveToNext()) {
                if (out.size() >= limit) break;
                long id = c.getLong(0);
                if (picked.add(id)) out.add(id);
            }
        } finally {
            c.close();
        }
    }
}
