package person.notfresh.readingshare.links;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.eventlog.EventAction;
import person.notfresh.readingshare.eventlog.EventLogException;
import person.notfresh.readingshare.eventlog.EventRecord;
import person.notfresh.readingshare.eventlog.LinkApplier;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.model.LinkJson;

/**
 * 把 {@code links} topic 的事件折叠到 {@link LinkDao}。
 *
 * <p>折叠规则（PROTOCOL §5.4 LWW）：同 {@code entity_id} 的多条事件，按
 * {@code process_time} 升序依次处理；后到的覆盖先到的。</p>
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
        if (!TOPIC_LINKS.equals(event.getTopic())) {
            return; // 非 link topic 不归本 applier 管
        }
        switch (event.getAction()) {
            case CREATE:
                handleCreate(event);
                break;
            case UPDATE:
                handleUpdate(event);
                break;
            case DELETE:
                handleDelete(event);
                break;
        }
    }

    private void handleCreate(EventRecord event) {
        LinkItem item = decode(event);
        if (item == null) return;
        // create: 拉服务端版本作为权威 — 直接 replace（已存在的则覆盖）
        // 幂等：服务端可能重发同 id 的 create，CONFLICT_REPLACE 安全
        linkDao.replaceById(item);
    }

    private void handleUpdate(EventRecord event) {
        LinkItem item = decode(event);
        if (item == null) return;
        // update: 服务端版本为权威；CONFLICT_REPLACE 保证本地之前没有也能落地
        linkDao.replaceById(item);
    }

    private void handleDelete(EventRecord event) {
        // entity_id 是 String（来自服务端），转 long 调 LinkDao
        long entityId = parseEntityId(event.getEntityId());
        if (entityId <= 0) return;
        linkDao.deleteLink(entityId);
    }

    private static LinkItem decode(EventRecord event) {
        if (event.getData() == null) return null;
        LinkItem item = LinkJson.fromJsonString(event.getData());
        if (item == null) return null;
        // 用服务端 entity_id 兜底 — eventLogClient 推上来时存的是 link.id 转字符串
        if (event.getEntityId() != null && !event.getEntityId().isEmpty()) {
            try {
                long serverEntityId = Long.parseLong(event.getEntityId());
                if (serverEntityId > 0) {
                    item.setId(serverEntityId);
                }
            } catch (NumberFormatException ignored) {
                // 不是数字 id，保留 LinkJson 解出的 id
            }
        }
        return item;
    }

    private static long parseEntityId(String s) {
        if (s == null) return -1L;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * Local hook — 让 LinkDao 暴露 upsert/replaceIfExists/deleteLink（已有 deleteLink，
     * upsert 和 replaceIfExists 需要补）。SEfirst 单向门警告：补这两个方法必须
     * 真有调用方（本 applier）；不补的话 applier 就降级为 delete-only。
     */
}
