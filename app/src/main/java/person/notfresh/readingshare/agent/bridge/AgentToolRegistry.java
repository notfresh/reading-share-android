package person.notfresh.readingshare.agent.bridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具注册表 —— **工具 schema 的单一来源**。
 *
 * <p>为什么 schema 放 Java 侧:工具直接对应 reading-share 的 DAO 能力,
 * schema 和实现同源 = 改 DAO 加字段时不会漏改 schema(reading-share §26
 * "reader 漏一列"同款风险)。Python 侧启动时通过
 * {@link #schemasJson()} 拉取,不再手抄第二份。</p>
 *
 * <p>每个工具有三类属性:</p>
 * <ul>
 *   <li>{@code name} / {@code description} —— 给 LLM 看</li>
 *   <li>{@code parameters} —— JSON Schema 的 parameters 段</li>
 *   <li>{@code requiresConfirm} —— 是否写操作(写操作必须经用户确认)</li>
 * </ul>
 *
 * <p>实现体在 {@link AgentTools}。本类只负责"声明 + 派发"。</p>
 */
public final class AgentToolRegistry {

    /** 一个参数声明。 */
    private static final class Param {
        final String name;
        final String type;
        final String description;
        final boolean required;

        Param(String name, String type, String description, boolean required) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.required = required;
        }
    }

    /** 一个工具的声明。 */
    public static final class ToolSpec {
        final String name;
        final String description;
        final JSONObject parameters;
        final boolean requiresConfirm;

        ToolSpec(String name, String description, JSONObject parameters, boolean requiresConfirm) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
            this.requiresConfirm = requiresConfirm;
        }
    }

    private final Map<String, ToolSpec> specs = new LinkedHashMap<>();

    public AgentToolRegistry() {
        registerReadTools();
        registerWriteTools();
    }

    // ── 声明 ──────────────────────────────────────────────────────────

    private void add(String name, String description, boolean requiresConfirm, Param... params) {
        try {
            specs.put(name, new ToolSpec(name, description, buildParameters(params), requiresConfirm));
        } catch (JSONException e) {
            // 声明是硬编码常量,构造失败说明代码写错了 —— 直接崩,别静默降级
            throw new IllegalStateException("构造工具 schema 失败: " + name, e);
        }
    }

    /** 构造 JSON Schema 的 parameters 段。 */
    private static JSONObject buildParameters(Param[] params) throws JSONException {
        JSONObject properties = new JSONObject();
        JSONArray required = new JSONArray();
        for (Param p : params) {
            JSONObject prop = new JSONObject();
            prop.put("type", p.type);
            prop.put("description", p.description);
            properties.put(p.name, prop);
            if (p.required) {
                required.put(p.name);
            }
        }
        JSONObject result = new JSONObject();
        result.put("type", "object");
        result.put("properties", properties);
        result.put("required", required);
        return result;
    }

    private static Param req(String name, String type, String description) {
        return new Param(name, type, description, true);
    }

    private static Param opt(String name, String type, String description) {
        return new Param(name, type, description, false);
    }

    private void registerReadTools() {
        add("search_links",
                "搜索阅读库里的链接。按关键词搜标题/URL/标签,返回匹配的链接列表。",
                false,
                req("query", "string", "搜索关键词"),
                opt("scope", "string", "搜索范围:all / title / url / tag,默认 all"),
                opt("limit", "number", "最多返回多少条,默认 20,上限 50"));

        add("get_link",
                "按链接 id 读取单条链接的详情,含标题、URL、备注、标签、置顶状态。",
                false,
                req("link_id", "number", "链接 id(用 search_links 拿到)"));

        add("list_tags",
                "列出所有标签及其链接数量。",
                false);

        add("list_links_by_tag",
                "列出某个标签下的所有链接。",
                false,
                req("tag", "string", "标签名"));

        add("list_pinned_links",
                "列出所有置顶的链接。",
                false);

        add("list_subjects",
                "列出所有主题(Subject)。",
                false);

        add("list_subject_items",
                "列出某个主题下的所有收录链接。",
                false,
                req("subject_id", "number", "主题 id"));

        add("list_links_by_date",
                "按日期查**当天创建**的链接(默认今天)。date 用 yyyy-MM-dd;"
                        + "返回该自然日 00:00~24:00 创建的链接,按时间倒序。",
                false,
                opt("date", "string", "日期 yyyy-MM-dd,不传 = 今天"),
                opt("limit", "number", "最多返回多少条,默认 20,上限 50"));

        add("open_link",
                "用 App 内置 WebView 打开一条链接。**直接打开,不需要用户确认。**"
                        + "链接 id 或 url 传一个即可(优先 link_id)。",
                false,
                opt("link_id", "number", "链接 id(优先,和 url 二选一)"),
                opt("url", "string", "链接地址(没有 id 时用)"));
    }

    private void registerWriteTools() {
        add("add_tag_to_link",
                "给指定链接加一个标签。标签不存在会自动创建。",
                true,
                req("link_id", "number", "链接 id"),
                req("tag", "string", "标签名"));

        add("remove_tag_from_link",
                "从指定链接移除一个标签。",
                true,
                req("link_id", "number", "链接 id"),
                req("tag", "string", "要移除的标签名"));

        add("set_link_remark",
                "设置指定链接的备注(覆盖原有备注)。",
                true,
                req("link_id", "number", "链接 id"),
                req("remark", "string", "备注内容"));

        add("set_link_pinned",
                "设置指定链接的置顶状态。",
                true,
                req("link_id", "number", "链接 id"),
                req("pinned", "boolean", "true = 置顶,false = 取消置顶"));
    }

    // ── 对外接口 ──────────────────────────────────────────────────────

    /** 全部工具名(顺序即注册顺序)。 */
    public List<String> names() {
        return new ArrayList<>(specs.keySet());
    }

    /** 某工具是否需要用户确认(写操作 = true)。 */
    public boolean requiresConfirm(String name) {
        ToolSpec spec = specs.get(name);
        return spec != null && spec.requiresConfirm;
    }

    /**
     * 工具 schema 列表,JSON 数组字符串。
     *
     * <p>格式:[{"name":..., "description":..., "parameters":{...}}, ...]</p>
     */
    public String schemasJson() {
        JSONArray arr = new JSONArray();
        for (ToolSpec spec : specs.values()) {
            try {
                JSONObject o = new JSONObject();
                o.put("name", spec.name);
                o.put("description", spec.description);
                o.put("parameters", spec.parameters);
                arr.put(o);
            } catch (JSONException e) {
                // 单个工具 schema 构造失败不应拖垮整体
                android.util.Log.e("AgentToolRegistry",
                        "构造 schema 失败: " + spec.name, e);
            }
        }
        return arr.toString();
    }
}
