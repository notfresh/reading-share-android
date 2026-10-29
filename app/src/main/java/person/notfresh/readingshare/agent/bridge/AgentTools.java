package person.notfresh.readingshare.agent.bridge;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import person.notfresh.readingshare.core.model.Subject;
import person.notfresh.readingshare.core.model.SubjectItem;
import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.db.SubjectDao;
import person.notfresh.readingshare.eventlog.EventLogClient;
import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.model.LinkJson;
import person.notfresh.readingshare.util.LinkSearchScope;
import person.notfresh.readingshare.util.LinkSearchSpec;

/**
 * Agent 工具的具体实现。
 *
 * <p>**写操作走和用户手动完全同一条路**:同一个 DAO + 同一个
 * {@link EventLogClient} 埋点 + 同一个 {@link LinkJson} 序列化。
 * 这样跨端同步、审计、回放三样白送 —— agent 干了什么 = 事件流,
 * 不需要另开一套日志。</p>
 *
 * <p>返回给 LLM 的结果一律是 JSON 字符串。异常不外抛,转成
 * {@code {"error": "..."}} —— 错误也是 Observation,让 agent 自己纠正。</p>
 */
public final class AgentTools {

    private static final String TAG = "AgentTools";

    /** 搜索结果条数上限 —— 防止 LLM 一次拉爆上下文。 */
    private static final int SEARCH_LIMIT_MAX = 50;
    private static final int SEARCH_LIMIT_DEFAULT = 20;

    private final Context context;
    private final LinkDao linkDao;
    private final SubjectDao subjectDao;

    public AgentTools(Context context) {
        this.context = context.getApplicationContext();
        this.linkDao = new LinkDao(DbConnection.get(this.context).writable());
        this.subjectDao = new SubjectDao(DbConnection.get(this.context).writable());
    }

    // ── 派发 ──────────────────────────────────────────────────────────

    /**
     * 执行一个工具。
     *
     * @param name     工具名
     * @param argsJson 参数 JSON 字符串
     * @return 结果 JSON 字符串(永不为 null)
     */
    public String invoke(String name, String argsJson) {
        JSONObject args;
        try {
            args = new JSONObject(argsJson == null ? "{}" : argsJson);
        } catch (JSONException e) {
            return error("参数不是合法 JSON: " + e.getMessage());
        }
        try {
            switch (name) {
                case "search_links":        return searchLinks(args);
                case "get_link":            return getLink(args);
                case "list_tags":           return listTags();
                case "list_links_by_tag":   return listLinksByTag(args);
                case "list_pinned_links":   return listPinnedLinks();
                case "list_subjects":       return listSubjects();
                case "list_subject_items":  return listSubjectItems(args);
                case "list_links_by_date":  return listLinksByDate(args);
                case "open_link":           return openLink(args);
                case "add_tag_to_link":     return addTagToLink(args);
                case "remove_tag_from_link":return removeTagFromLink(args);
                case "set_link_remark":     return setLinkRemark(args);
                case "set_link_pinned":     return setLinkPinned(args);
                default:
                    return error("未知工具: " + name);
            }
        } catch (Exception e) {
            Log.e(TAG, "工具执行失败: " + name, e);
            return error(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ── 读工具 ────────────────────────────────────────────────────────

    private String searchLinks(JSONObject args) throws JSONException {
        String query = args.optString("query", "").trim();
        if (query.isEmpty()) {
            return error("query 不能为空");
        }
        int limit = args.optInt("limit", SEARCH_LIMIT_DEFAULT);
        limit = Math.max(1, Math.min(limit, SEARCH_LIMIT_MAX));

        String scopeRaw = args.optString("scope", "all").trim().toLowerCase();
        LinkSearchScope scope = parseScope(scopeRaw);

        // LinkSearchSpec.parse 支持 "title:foo" 前缀语法;scope 参数走显式构造
        LinkSearchSpec spec = new LinkSearchSpec(query, scope, limit);
        List<LinkItem> hits = linkDao.searchLinks(spec);

        JSONArray arr = new JSONArray();
        for (LinkItem item : hits) {
            arr.put(linkToJson(item));
        }
        JSONObject out = new JSONObject();
        out.put("count", arr.length());
        out.put("links", arr);
        return out.toString();
    }

    private static LinkSearchScope parseScope(String raw) {
        switch (raw) {
            case "title": return LinkSearchScope.TITLE;
            case "url":   return LinkSearchScope.URL;
            case "tag":   return LinkSearchScope.TAG;
            default:      return LinkSearchScope.ALL;
        }
    }

    private String getLink(JSONObject args) throws JSONException {
        long id = args.optLong("link_id", -1);
        if (id <= 0) {
            return error("link_id 无效");
        }
        LinkItem item = linkDao.getLinkById(id);
        if (item == null) {
            return error("找不到 id=" + id + " 的链接");
        }
        return linkToJson(item).toString();
    }

    /**
     * 按日期查当天创建的链接。
     *
     * <p>只读工具(不需要确认)。时间口径 = **设备本地时区的自然日**
     * [00:00:00.000, 次日 00:00:00.000)。</p>
     */
    private String listLinksByDate(JSONObject args) throws JSONException {
        String dateText = args.optString("date", "").trim();
        int limit = Math.min(Math.max(args.optInt("limit", SEARCH_LIMIT_DEFAULT), 1), SEARCH_LIMIT_MAX);

        java.util.Calendar cal = java.util.Calendar.getInstance();
        if (!dateText.isEmpty()) {
            java.text.SimpleDateFormat fmt =
                    new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            fmt.setLenient(false);
            try {
                java.util.Date parsed = fmt.parse(dateText);
                if (parsed == null) {
                    return error("日期格式应为 yyyy-MM-dd");
                }
                cal.setTime(parsed);
            } catch (java.text.ParseException e) {
                return error("日期格式应为 yyyy-MM-dd,收到: " + dateText);
            }
        }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long startMs = cal.getTimeInMillis();
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
        long endMs = cal.getTimeInMillis();

        String dayText = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date(startMs));
        List<LinkItem> links = linkDao.getLinksByTimeRange(startMs, endMs, limit);

        JSONArray arr = new JSONArray();
        for (LinkItem item : links) {
            JSONObject row = new JSONObject();
            row.put("id", item.getId());
            row.put("title", item.getTitle());
            row.put("url", item.getUrl());
            row.put("time", new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(new java.util.Date(item.getTimestamp())));
            arr.put(row);
        }
        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("date", dayText);
        out.put("count", links.size());
        out.put("links", arr);
        return out.toString();
    }

    /**
     * 用 App 内置 WebView 打开链接 —— **直接打开,不需要用户确认**(用户拍板)。
     *
     * <p>工具跑在后台线程,所以启动 Activity 必须带 {@code FLAG_ACTIVITY_NEW_TASK}。</p>
     */
    private String openLink(JSONObject args) throws JSONException {
        long linkId = args.optLong("link_id", 0);
        String url = args.optString("url", "").trim();
        String title = "";

        if (linkId > 0) {
            LinkItem item = linkDao.getLinkById(linkId);
            if (item == null) {
                return error("找不到 id=" + linkId + " 的链接");
            }
            url = item.getUrl();
            title = item.getTitle();
        }
        if (url.isEmpty()) {
            return error("需要 link_id 或 url 之一");
        }

        android.content.Intent intent =
                new android.content.Intent(context, person.notfresh.readingshare.WebViewActivity.class);
        intent.putExtra("url", url);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("opened", true);
        out.put("url", url);
        out.put("title", title);
        return out.toString();
    }

    private String listTags() throws JSONException {
        Map<String, Integer> counts = linkDao.getTagsWithCount();
        JSONArray arr = new JSONArray();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            JSONObject o = new JSONObject();
            o.put("tag", e.getKey());
            o.put("count", e.getValue());
            arr.put(o);
        }
        JSONObject out = new JSONObject();
        out.put("count", arr.length());
        out.put("tags", arr);
        return out.toString();
    }

    private String listLinksByTag(JSONObject args) throws JSONException {
        String tag = args.optString("tag", "").trim();
        if (tag.isEmpty()) {
            return error("tag 不能为空");
        }
        List<LinkItem> items = linkDao.getLinksByTag(tag);
        JSONArray arr = new JSONArray();
        for (LinkItem item : items) {
            arr.put(linkToJson(item));
        }
        JSONObject out = new JSONObject();
        out.put("tag", tag);
        out.put("count", arr.length());
        out.put("links", arr);
        return out.toString();
    }

    private String listPinnedLinks() throws JSONException {
        List<LinkItem> items = linkDao.getPinnedLinks();
        JSONArray arr = new JSONArray();
        for (LinkItem item : items) {
            arr.put(linkToJson(item));
        }
        JSONObject out = new JSONObject();
        out.put("count", arr.length());
        out.put("links", arr);
        return out.toString();
    }

    private String listSubjects() throws JSONException {
        List<Subject> subjects = subjectDao.getAllSubjects();
        JSONArray arr = new JSONArray();
        for (Subject s : subjects) {
            JSONObject o = new JSONObject();
            o.put("id", s.getId());
            o.put("title", s.getTitle());
            o.put("describe", s.getDescribe());
            arr.put(o);
        }
        JSONObject out = new JSONObject();
        out.put("count", arr.length());
        out.put("subjects", arr);
        return out.toString();
    }

    private String listSubjectItems(JSONObject args) throws JSONException {
        long subjectId = args.optLong("subject_id", -1);
        if (subjectId <= 0) {
            return error("subject_id 无效");
        }
        List<SubjectItem> items = subjectDao.getSubjectItemsBySubjectId(subjectId);
        JSONArray arr = new JSONArray();
        for (SubjectItem item : items) {
            JSONObject o = new JSONObject();
            o.put("id", item.getId());
            o.put("subject_id", item.getSubjectId());
            o.put("link_id", item.getLinkId());
            arr.put(o);
        }
        JSONObject out = new JSONObject();
        out.put("subject_id", subjectId);
        out.put("count", arr.length());
        out.put("items", arr);
        return out.toString();
    }

    // ── 写工具 ────────────────────────────────────────────────────────

    private String addTagToLink(JSONObject args) throws JSONException {
        long id = args.optLong("link_id", -1);
        String tag = args.optString("tag", "").trim();
        if (id <= 0) {
            return error("link_id 无效");
        }
        if (tag.isEmpty()) {
            return error("tag 不能为空");
        }
        LinkItem item = linkDao.getLinkById(id);
        if (item == null) {
            return error("找不到 id=" + id + " 的链接");
        }
        linkDao.addTagToLink(id, tag);
        // 重新读一次,让 payload 带上新标签(跟手动路径的 LinksAdapter.addTagToLink 语义一致)
        LinkItem updated = linkDao.getLinkById(id);
        recordUpdate(updated != null ? updated : item);
        return ok("已给链接 " + id + " 添加标签「" + tag + "」");
    }

    private String removeTagFromLink(JSONObject args) throws JSONException {
        long id = args.optLong("link_id", -1);
        String tag = args.optString("tag", "").trim();
        if (id <= 0) {
            return error("link_id 无效");
        }
        if (tag.isEmpty()) {
            return error("tag 不能为空");
        }
        LinkItem item = linkDao.getLinkById(id);
        if (item == null) {
            return error("找不到 id=" + id + " 的链接");
        }
        List<String> tags = new ArrayList<>(item.getTags());
        if (!tags.remove(tag)) {
            return error("链接 " + id + " 上没有标签「" + tag + "」");
        }
        item.setTags(tags);
        linkDao.updateLinkTags(item);
        recordUpdate(item);
        return ok("已从链接 " + id + " 移除标签「" + tag + "」");
    }

    private String setLinkRemark(JSONObject args) throws JSONException {
        long id = args.optLong("link_id", -1);
        String remark = args.optString("remark", "");
        if (id <= 0) {
            return error("link_id 无效");
        }
        LinkItem item = linkDao.getLinkById(id);
        if (item == null) {
            return error("找不到 id=" + id + " 的链接");
        }
        linkDao.updateLinkRemark(id, remark);
        item.setRemark(remark);
        recordUpdate(item);
        return ok("已更新链接 " + id + " 的备注");
    }

    private String setLinkPinned(JSONObject args) throws JSONException {
        long id = args.optLong("link_id", -1);
        if (id <= 0) {
            return error("link_id 无效");
        }
        boolean wantPinned = args.optBoolean("pinned", false);
        LinkItem item = linkDao.getLinkById(id);
        if (item == null) {
            return error("找不到 id=" + id + " 的链接");
        }
        if (item.isPinned() != wantPinned) {
            // togglePinStatus 是 toggle,先读当前值再决定要不要动
            linkDao.togglePinStatus(id);
        }
        LinkItem updated = linkDao.getLinkById(id);
        recordUpdate(updated != null ? updated : item);
        boolean nowPinned = updated != null ? updated.isPinned() : wantPinned;
        return ok(nowPinned ? "已置顶链接 " + id : "已取消链接 " + id + " 的置顶");
    }

    // ── 埋点 ──────────────────────────────────────────────────────────

    /**
     * 记录一次 update 事件 —— 和用户手动操作走同一个 EventLogClient。
     *
     * <p>topic 字符串 "links" 与项目既有 40 处埋点一致(见 reading-share
     * §"调研-标签实体纳入事件日志同步-v1.0.md" 的收敛建议,
     * 抽 EventTopics 常量是后续单独一刀,本次不动存量)。</p>
     */
    private void recordUpdate(LinkItem item) {
        if (item == null) {
            return;
        }
        try {
            EventLogClient.get().update("links",
                    String.valueOf(item.getId()),
                    item.getTimestamp(),
                    LinkJson.toJsonString(item));
        } catch (Exception e) {
            // 埋点失败不该让整个工具失败 —— 数据已经写进库了
            Log.e(TAG, "记录事件日志失败: " + item.getId(), e);
        }
    }

    // ── 序列化 ────────────────────────────────────────────────────────

    /** 把 LinkItem 转成给 LLM 看的精简 JSON(不塞原文摘要,省 token)。 */
    private static JSONObject linkToJson(LinkItem item) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", item.getId());
        o.put("title", item.getTitle());
        o.put("url", item.getUrl());
        o.put("remark", item.getRemark());
        o.put("pinned", item.isPinned());
        o.put("timestamp", item.getTimestamp());
        JSONArray tags = new JSONArray();
        if (item.getTags() != null) {
            for (String t : item.getTags()) {
                tags.put(t);
            }
        }
        o.put("tags", tags);
        return o;
    }

    // ── 结果小工具 ────────────────────────────────────────────────────

    private static String ok(String message) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("message", message);
            return o.toString();
        } catch (JSONException e) {
            return "{\"ok\":true}";
        }
    }

    private static String error(String message) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", false);
            o.put("error", message);
            return o.toString();
        } catch (JSONException e) {
            return "{\"ok\":false,\"error\":\"unknown\"}";
        }
    }
}
