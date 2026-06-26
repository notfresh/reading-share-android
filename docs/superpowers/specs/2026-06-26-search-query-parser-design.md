# 搜索语法解析器设计

## 背景

HomeFragment 的搜索框目前只支持单个关键字的 `contains` 匹配。需要扩展为支持 Lucene 风格的布尔搜索语法。

## 目标

支持以下搜索语法：
- `term1 term2` → 默认 AND，必须同时包含
- `term1 & term2` → 等效于 AND
- `term1 | term2` → OR，包含任一即可
- `!term` → NOT，排除
- `"exact phrase"` → 短语精确匹配
- `(term1 | term2) & term3` → 分组，优先级 NOT > AND > OR

## 设计

### 文件结构

```
app/src/main/java/person/notfresh/readingshare/util/
    └── SearchQueryParser.java     # 解析器核心
    └── SearchQueryParserTest.java # 测试类（AndroidInstrumentedTest 或纯 JUnit）
```

### 核心接口

```java
public class SearchQueryParser {

    // 节点类型
    public interface SearchNode {
        boolean matches(LinkItem item);
    }

    // 终结符：普通词条
    public record TermNode(String term) implements SearchNode

    // 终结符：精确短语
    public record PhraseNode(String phrase) implements SearchNode

    // 布尔 NOT
    public record NotNode(SearchNode child) implements SearchNode

    // 布尔 AND
    public record AndNode(SearchNode left, SearchNode right) implements SearchNode

    // 布尔 OR
    public record OrNode(SearchNode left, SearchNode right) implements SearchNode

    // 解析入口
    public static SearchNode parse(String query)

    // 匹配 LinkItem
    public static boolean matches(SearchNode node, LinkItem item)
}
```

### 两遍处理流程

**第1遍：Tokenize（空格分词 + 规范化）**

输入：`java & python | !android "hello world"`

处理：
1. 按空格 split
2. `&` → AND token
3. `|` → OR token
4. `!` 前缀 → NOT token
5. `"..."` → PHRASE token

输出：`[TERM("java"), AND, TERM("python"), OR, NOT, TERM("android"), PHRASE("hello world")]`

**第2遍：Parse（单趟递归下降，按优先级构建 AST）**

优先级：NOT > AND > OR

```
OR
├── AND
│   ├── TERM("java")
│   └── TERM("python")
└── NOT
    └── TERM("android")
```

### 匹配逻辑

对于 LinkItem，匹配检查 title、url、tags 三个字段是否包含：

```java
// TermNode 匹配
boolean matchesField = field.toLowerCase().contains(term);

// PhraseNode 匹配
boolean matchesField = field.toLowerCase().contains(phrase);

// NOTNode 匹配
boolean matchesChild = child.matches(item);
return !matchesChild;

// ANDNode 匹配
return left.matches(item) && right.matches(item);

// ORNode 匹配
return left.matches(item) || right.matches(item);
```

## 与现有代码集成

`LinksAdapter.filter()` 第945-992行改造：

```java
public void filter(String query) {
    query = query.trim();
    items.clear();

    if (query.isEmpty()) {
        items.addAll(originalItems);
        notifyDataSetChanged();
        return;
    }

    SearchNode node = SearchQueryParser.parse(query);
    Map<String, List<LinkItem>> filteredGroups = new TreeMap<>(Collections.reverseOrder());

    for (Object item : originalItems) {
        if (item instanceof String) continue;  // 日期标题
        LinkItem linkItem = (LinkItem) item;
        if (SearchQueryParser.matches(node, linkItem)) {
            String date = formatDate(linkItem.getTimestamp());
            filteredGroups.computeIfAbsent(date, k -> new ArrayList<>()).add(linkItem);
        }
    }

    for (Map.Entry<String, List<LinkItem>> entry : filteredGroups.entrySet()) {
        if (!entry.getValue().isEmpty()) {
            items.add(entry.getKey());
            items.addAll(entry.getValue());
        }
    }

    notifyDataSetChanged();
}
```

## 测试用例

| 输入 | 期望 AST |
|------|---------|
| `java` | `TermNode("java")` |
| `java python` | `AndNode(TermNode("java"), TermNode("python"))` |
| `java \| python` | `OrNode(TermNode("java"), TermNode("python"))` |
| `java & python` | `AndNode(TermNode("java"), TermNode("python"))` |
| `!java` | `NotNode(TermNode("java"))` |
| `"hello world"` | `PhraseNode("hello world")` |
| `(java \| python) & android` | `AndNode(OrNode(TermNode("java"), TermNode("python")), TermNode("android"))` |
| `java AND NOT python` | `AndNode(TermNode("java"), NotNode(TermNode("python")))` |

## 风险与限制

- 分组括号不支持嵌套超过2层（简单实现）
- 短语匹配不支持转义引号
- 操作符必须空格分隔（`&`/`|`可紧邻，前提是`&`不是独立token）
