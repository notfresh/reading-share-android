package person.notfresh.readingshare.links;

import person.notfresh.readingshare.eventlog.EventLogClient;
import person.notfresh.readingshare.eventlog.EventRecord;
import person.notfresh.readingshare.eventlog.EventLogException;
import person.notfresh.readingshare.model.LinkItem;

/**
 * 链接同步埋点的**唯一入口** —— 业务代码只调这里，不直接碰
 * {@link EventLogClient}。
 *
 * <p><b>为什么要有这一层</b>：同步事件的 identity 和 payload 格式是跨端
 * 契约，散落在 29 个埋点里必然走偏 —— 现在有 29 处
 * {@code String.valueOf(link.getId())}（自增 id，两端会碰撞）和 29 处
 * {@code LinkJson.toJsonString(link)}（含两端不同步的字段 + 字段名不统一）。
 * 收敛到一处之后：</p>
 *
 * <ul>
 *   <li>entity_id 统一 = URL</li>
 *   <li>payload 统一 = wire 格式（见 {@link LinkWire}）</li>
 *   <li>event_time 统一 = 链接创建时间（create）或变更时刻（update/delete）</li>
 * </ul>
 *
 * <p>业务代码从「手拼 entity_id + 手拼 payload」变成一行
 * {@code LinkSync.recordUpdate(link)} —— 契约变了只改这一处。</p>
 */
public final class LinkSync {

    private static final String TOPIC_LINKS = "links";

    private LinkSync() {
    }

    /**
     * 取链接的跨端身份 —— URL。
     *
     * @param item 链接
     * @return URL；为空时返回 null
     */
    public static String entityId(LinkItem item) {
        if (item == null) return null;
        String url = item.getUrl();
        if (url == null) return null;
        url = url.trim();
        return url.isEmpty() ? null : url;
    }

    /**
     * 记录一条 create 事件（新增链接）。
     *
     * @param item 新增的链接
     * @return 事件记录；失败返回 null
     */
    public static EventRecord recordCreate(LinkItem item) {
        String entityId = entityId(item);
        if (entityId == null) {
            return null;
        }
        try {
            return EventLogClient.get().create(
                    TOPIC_LINKS, entityId, item.getTimestamp(),
                    LinkWire.toWireJson(item));
        } catch (Exception e) {
            // 埋点失败绝不能影响业务操作
            return null;
        }
    }

    /**
     * 记录一条 update 事件（更新链接的共有字段）。
     *
     * @param item 更新后的链接
     * @return 事件记录；失败返回 null
     */
    public static EventRecord recordUpdate(LinkItem item) {
        String entityId = entityId(item);
        if (entityId == null) {
            return null;
        }
        try {
            return EventLogClient.get().update(
                    TOPIC_LINKS, entityId, System.currentTimeMillis(),
                    LinkWire.toWireJson(item));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 记录一条 delete 事件（删除链接）。
     *
     * <p>delete 是 tombstone —— data 为 null，接收端按 entity_id（URL）删除。</p>
     *
     * @param item 被删除的链接（需要它的 URL）
     * @return 事件记录；失败返回 null
     */
    public static EventRecord recordDelete(LinkItem item) {
        String entityId = entityId(item);
        if (entityId == null) {
            return null;
        }
        try {
            return EventLogClient.get().delete(
                    TOPIC_LINKS, entityId, System.currentTimeMillis());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按 URL 记录 delete 事件 —— 调用方手上只有 URL 时用。
     *
     * @param url 被删除链接的 URL
     * @return 事件记录；失败返回 null
     */
    public static EventRecord recordDeleteByUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        try {
            return EventLogClient.get().delete(
                    TOPIC_LINKS, url.trim(), System.currentTimeMillis());
        } catch (EventLogException e) {
            return null;
        }
    }
}
