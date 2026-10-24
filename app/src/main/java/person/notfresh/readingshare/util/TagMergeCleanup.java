package person.notfresh.readingshare.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;

/**
 * 标签合并后的本地配置同步(单一来源)。
 *
 * <p>标签合并不只是改 {@code tags} / {@code link_tags} 两张表 —— 项目里还有 5 处
 * 「按标签名索引」的本地状态,漏掉任何一处都会留下脏数据(例如置顶区还挂着已删除的
 * 标签名,筛选条件指向不存在的标签导致列表空)。</p>
 *
 * <p>本类把这 5 处的处理集中到一个入口:</p>
 * <ol>
 *   <li>置顶标签 — {@link PinnedTagsManager}(读写入口唯一,直接用它的 API)</li>
 *   <li>高亮标签 — SP {@code TagsPreferences} / key {@code highlighted_tags}</li>
 *   <li>选中标签 — SP {@code TagsPreferences} / key {@code selectedTags}</li>
 *   <li>最近标签 — {@link RecentTagsManager}</li>
 *   <li>标签排序 — config 表 key {@code tag_order}(存的是 tag_id)</li>
 * </ol>
 *
 * <p>置顶上限(3 个)由 {@link PinnedTagsManager#MAX_PINNED} 守住:source 置顶时
 * 若 target 已置顶,合并后置顶数量不变;若 target 未置顶,数量 +1 —— 超过上限时
 * 丢弃 source 的置顶状态(不挤掉别的置顶标签)。</p>
 */
public final class TagMergeCleanup {

    /** SP 名:HomeFragment / TagsFragment 共用的标签偏好。 */
    private static final String PREF_TAGS = "TagsPreferences";
    private static final String KEY_HIGHLIGHTED_TAGS = "highlighted_tags";
    private static final String KEY_SELECTED_TAGS = "selectedTags";

    private TagMergeCleanup() {}

    /**
     * 合并完成后同步所有「按标签名索引」的本地配置。
     *
     * @param ctx        上下文
     * @param sourceTag  被合并掉的标签名
     * @param targetTag  目标标签名
     * @param sourceId   source 标签在合并「之前」的 tag_id(合并后它已被删除,
     *                   事后再查会拿到 -1,排序就修不了了 —— 所以必须提前取)
     */
    public static void apply(Context ctx, String sourceTag, String targetTag, long sourceId) {
        if (ctx == null || sourceTag == null || targetTag == null
                || sourceTag.isEmpty() || targetTag.isEmpty()
                || sourceTag.equals(targetTag)) {
            return;
        }
        Context app = ctx.getApplicationContext();
        applyPinned(app, sourceTag, targetTag);
        applyHighlighted(app, sourceTag, targetTag);
        applySelected(app, sourceTag, targetTag);
        RecentTagsManager.replaceTag(app, sourceTag, targetTag);
        applyTagOrder(app, targetTag, sourceId);
    }

    /** 置顶:source 置顶 → 换成 target;target 已置顶则去重;不挤占其它置顶位。 */
    private static void applyPinned(Context ctx, String sourceTag, String targetTag) {
        PinnedTagsManager manager = new PinnedTagsManager(ctx);
        Set<String> pinned = manager.getPinnedTags();
        if (!pinned.contains(sourceTag)) {
            return;
        }
        LinkedHashSet<String> next = new LinkedHashSet<>(pinned);
        next.remove(sourceTag);
        if (next.size() < PinnedTagsManager.MAX_PINNED) {
            next.add(targetTag);
        }
        manager.setPinnedTags(next);
    }

    /** 高亮:source 高亮 → 传给 target。 */
    private static void applyHighlighted(Context ctx, String sourceTag, String targetTag) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_TAGS, Context.MODE_PRIVATE);
        Set<String> highlighted = prefs.getStringSet(KEY_HIGHLIGHTED_TAGS, new LinkedHashSet<>());
        if (highlighted == null || !highlighted.contains(sourceTag)) {
            return;
        }
        LinkedHashSet<String> next = new LinkedHashSet<>(highlighted);
        next.remove(sourceTag);
        next.add(targetTag);
        prefs.edit().putStringSet(KEY_HIGHLIGHTED_TAGS, next).apply();
    }

    /** 选中:source 正被筛选 → 改成 target(否则筛选命中空标签 → 列表空)。 */
    private static void applySelected(Context ctx, String sourceTag, String targetTag) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_TAGS, Context.MODE_PRIVATE);
        Set<String> selected = prefs.getStringSet(KEY_SELECTED_TAGS, new LinkedHashSet<>());
        if (selected == null || !selected.contains(sourceTag)) {
            return;
        }
        LinkedHashSet<String> next = new LinkedHashSet<>(selected);
        next.remove(sourceTag);
        next.add(targetTag);
        prefs.edit().putStringSet(KEY_SELECTED_TAGS, next).apply();
    }

    /**
     * 排序:config 表 {@code tag_order} 存的是 tag_id 数组。
     * 删掉 source 的 id;若 target 不在数组里,插到 source 原来的下标
     * (不插的话 target 会跑到列表末尾,顺序乱)。
     *
     * @param targetTag 目标标签名
     * @param sourceId  source 的 tag_id(合并前取得)
     */
    private static void applyTagOrder(Context ctx, String targetTag, long sourceId) {
        try {
            LinkDao dao = new LinkDao(DbConnection.get(ctx).writable());
            List<Long> order = dao.getTagOrder();
            if (order == null || order.isEmpty() || sourceId == -1L) {
                return;
            }
            long targetId = dao.getTagIdByName(targetTag);
            int sourceIndex = order.indexOf(sourceId);
            if (sourceIndex < 0) {
                return; // 顺序表里本来就没有 source
            }
            List<Long> next = new ArrayList<>(order);
            next.remove(sourceId);
            if (targetId != -1L && !next.contains(targetId)) {
                next.add(Math.min(sourceIndex, next.size()), targetId);
            }
            dao.saveTagOrder(next);
        } catch (Exception ignored) {
            // 排序同步失败不该让整个合并回滚 —— DB 已提交,这里只放弃顺序微调
        }
    }
}
