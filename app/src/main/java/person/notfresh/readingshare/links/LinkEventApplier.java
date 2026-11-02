package person.notfresh.readingshare.links;

import org.json.JSONObject;

import java.util.Set;

import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.eventlog.EventLogException;
import person.notfresh.readingshare.eventlog.EventRecord;
import person.notfresh.readingshare.eventlog.LinkApplier;
import person.notfresh.readingshare.model.LinkItem;

/**
 * 把 {@code links} topic 的事件折叠到 {@link LinkDao}。
 *
 * <p><b>entity_id = URL</b>（不是本地自增 _id）。自增 id 在两台设备上必然
 * 碰撞 —— 各自从 1 开始。用 URL 当跨端身份，两端才能对上同一条链接。
 * 标题只是 payload 字段，不参与身份判定（改标题 ≠ 换身份）。</p>
 *
 * <p><b>字段级 LWW</b>（PROTOCOL §5.4）：用事件的 {@code old}（变更前快照）
 * 算 diff，只覆盖这条事件真正改动过的字段。这样两端同时改同一链接的不同
 * 字段时（手机改标题、插件改置顶），两边的改动都能保留；同字段才后写覆盖。</p>
 *
 * <p><b>去重</b>：Android 端 links.url 没有 UNIQUE 约束，历史数据可能留下
 * 重复行。折叠时保留 timestamp 最新的那条，其余删掉 —— 保证「一条 URL 一条
 * 记录」，两端才能收敛。</p>
 *
 * <p><b>不产生反向事件</b>：折叠远端事件时不能往本地事件日志再记一条 ——
 * 否则会「拉取 → 落库 → 记事件 → 推回服务端 → 对方再拉」的无限回环。
 * 这一点由调用方保证：折叠只在 pull 路径上发生，push 路径不碰这里。</p>
 *
 * <p>构造一次，整个 app 生命周期复用（App.onCreate 里 new，跟 {@link LinkDao}
 * 单例一致）— SQLite 连接由 {@code DbConnection} 全局持有，不重复打开。</p>
 */
public final class LinkEventApplier implements LinkApplier {

    private static final String TOPIC_LINKS = "links";

    private final LinkDao linkDao;

    public LinkEventApplier(LinkDao linkDao) {
        if (linkDao == null) {
            throw new EventLogException("linkDao is null");
        }
        this.linkDao = linkDao;
    }

    @Override
    public void apply(EventRecord event) {
        if (event == null || !TOPIC_LINKS.equals(event.getTopic())) {
            return; // 非 link topic 不归本 applier 管
        }
        // entity_id 现在是 URL —— 空值直接跳过
        String url = event.getEntityId();
        if (url == null || url.isEmpty()) {
            return;
        }
        switch (event.getAction()) {
            case CREATE:
            case UPDATE:
                handleUpsert(event, url);
                break;
            case DELETE:
                handleDelete(url);
                break;
        }
    }

    /**
     * create / update 都走同一套逻辑 —— 两者的差别只在 old 是否为空，
     * 而 diff 逻辑本身能处理 old 缺失（退化为实体级覆盖）。
     */
    private void handleUpsert(EventRecord event, String url) {
        JSONObject remote = LinkWire.wireFields(event.getData());
        if (remote == null) {
            return;
        }

        LinkItem local = linkDao.getLinkByUrl(url);
        JSONObject localWire = LinkWire.wireFields(
                local == null ? null : LinkWire.toWireJson(local));

        // 字段级 LWW：只覆盖这条事件改动过的字段。
        // old 和 data 都是 wire 格式 —— 字段名、类型一致，diff 才有意义。
        Set<String> changed = LinkWire.diffFields(event.getOldJson(), event.getData());
        JSONObject mergedWire = LinkWire.mergeByFields(localWire, remote, changed);

        // mergedWire → 本地 LinkItem
        LinkItem merged = LinkWire.fromWireJson(mergedWire.toString());
        if (merged == null) {
            return;
        }
        // 保留 Android 本地独有字段（remark / summary / click_count 等）——
        // 它们不参与同步，折叠时不能被远端事件的空值冲掉。
        if (local != null) {
            merged.setRemark(local.getRemark());
            merged.setSummary(local.getSummary());
            merged.setClickCount(local.getClickCount());
        }

        linkDao.upsertByUrl(merged);
        // 收敛本地同 URL 的重复行：保留 timestamp 最新的那条
        linkDao.dedupeByUrlKeepingNewest(url);
    }

    private void handleDelete(String url) {
        // delete = tombstone：按 URL 删掉本地所有匹配行
        linkDao.deleteLinksByUrl(url);
    }
}
