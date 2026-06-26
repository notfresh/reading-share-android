# 搜索历史下拉功能实现计划

> **For agentic workers:** REQUIRED SUB-KILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 HomeFragment 搜索框下添加搜索历史下拉（pin / 删 / LRU 淘汰 + maxCount 配置）。

**Architecture:** 拆三层 —— 纯逻辑 `SearchHistoryLogic`（JUnit 可测）+ 持久化 `SearchHistoryManager`（SharedPreferences + org.json）+ 视图层（PopupWindow + RecyclerView）。HomeFragment 维护内存 `historyCache`，每次写后 `refreshHistoryCache()` 重渲下拉。键入走 300ms 防抖，blur 走 200ms 延迟隐藏。

**Tech Stack:** Android PopupWindow, RecyclerView, SharedPreferences, org.json, JUnit 4

**参考 spec:** `docs/superpowers/specs/2026-06-08-search-history-dropdown-reference.md`

---

## 文件变更清单

| 文件 | 变更内容 |
|------|----------|
| `model/SearchHistoryItem.java` | **新建** —— 数据类，字段 text/pinned/lastUsed |
| `db/SearchHistoryLogic.java` | **新建** —— 纯逻辑：sortItems / upsertItem / evictLRU（无 Android 依赖） |
| `db/SearchHistoryManager.java` | **新建** —— 持久化层，包装 Logic + SharedPreferences，对外 5 个 API |
| `adapter/SearchHistoryAdapter.java` | **新建** —— 下拉行 RecyclerView 适配器 |
| `res/layout/item_search_history.xml` | **新建** —— 下拉行布局（文本 + 📌 + ✕） |
| `res/values/strings.xml` | **修改** —— 添加下拉相关字符串 |
| `res/layout/fragment_slideshow.xml` | **修改** —— 添加 maxCount 输入框 |
| `ui/home/HomeFragment.java` | **修改** —— 集成：防抖 / focus-blur / 下拉显示 / 事件分发 |
| `ui/settings/SettingFragment.java` | **修改** —— 加载并保存 maxCount |
| `test/db/SearchHistoryLogicTest.java` | **新建** —— Logic 单元测试 |

---

### Task 1: SearchHistoryItem 数据类

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/model/SearchHistoryItem.java`

**Steps:**

- [ ] **Step 1: 创建数据类**

新建文件，完整内容：

```java
package person.notfresh.readingshare.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * 搜索历史条目
 * 字段：text（trim 后存的搜索词）、pinned（是否固定）、lastUsed（毫秒时间戳）
 */
public class SearchHistoryItem {
    private String text;
    private boolean pinned;
    private long lastUsed;

    public SearchHistoryItem(String text, boolean pinned, long lastUsed) {
        this.text = text;
        this.pinned = pinned;
        this.lastUsed = lastUsed;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public long getLastUsed() { return lastUsed; }
    public void setLastUsed(long lastUsed) { this.lastUsed = lastUsed; }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("text", text);
        obj.put("pinned", pinned);
        obj.put("lastUsed", lastUsed);
        return obj;
    }

    public static SearchHistoryItem fromJson(JSONObject obj) throws JSONException {
        return new SearchHistoryItem(
            obj.getString("text"),
            obj.getBoolean("pinned"),
            obj.getLong("lastUsed")
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchHistoryItem)) return false;
        SearchHistoryItem that = (SearchHistoryItem) o;
        return pinned == that.pinned
            && lastUsed == that.lastUsed
            && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, pinned, lastUsed);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd "c:/projects/duxiang-pack/duxiang-android" && ./gradlew :app:compileDebugJavaWithJavac
```

预期：`BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/model/SearchHistoryItem.java
git commit -m "feat(search-history): add SearchHistoryItem data class"
```

---

### Task 2: SearchHistoryLogic 纯逻辑层（TDD）

**Files:**
- Create: `app/src/test/java/person/notfresh/readingshare/db/SearchHistoryLogicTest.java`
- Create: `app/src/main/java/person/notfresh/readingshare/db/SearchHistoryLogic.java`

**Steps:**

- [ ] **Step 1: 写失败的测试**

新建测试文件，完整内容：

```java
package person.notfresh.readingshare.db;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import person.notfresh.readingshare.model.SearchHistoryItem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SearchHistoryLogicTest {

    private static final long T1 = 1000L;
    private static final long T2 = 2000L;
    private static final long T3 = 3000L;

    // ===== sortItems =====

    @Test
    public void sortItems_pinnedBeforeUnpinned() {
        List<SearchHistoryItem> items = Arrays.asList(
            item("a", false, T2),
            item("b", true,  T1)
        );
        List<SearchHistoryItem> sorted = SearchHistoryLogic.sortItems(items);
        assertEquals("b", sorted.get(0).getText());
        assertEquals("a", sorted.get(1).getText());
    }

    @Test
    public void sortItems_eachGroupByLastUsedDesc() {
        List<SearchHistoryItem> items = Arrays.asList(
            item("p1", true,  T1),
            item("p2", true,  T3),
            item("u1", false, T2),
            item("u2", false, T3)
        );
        List<SearchHistoryItem> sorted = SearchHistoryLogic.sortItems(items);
        // pinned 组：p2 (T3) 在前, p1 (T1) 在后
        assertEquals("p2", sorted.get(0).getText());
        assertEquals("p1", sorted.get(1).getText());
        // unpinned 组：u2 (T3) 在前, u1 (T2) 在后
        assertEquals("u2", sorted.get(2).getText());
        assertEquals("u1", sorted.get(3).getText());
    }

    @Test
    public void sortItems_emptyList_returnsEmpty() {
        assertTrue(SearchHistoryLogic.sortItems(new ArrayList<>()).isEmpty());
    }

    // ===== upsertItem =====

    @Test
    public void upsertItem_newKeyword_appends() {
        List<SearchHistoryItem> items = new ArrayList<>();
        SearchHistoryLogic.upsertItem(items, "react", T1);
        assertEquals(1, items.size());
        assertEquals("react", items.get(0).getText());
        assertEquals(false, items.get(0).isPinned());
        assertEquals(T1, items.get(0).getLastUsed());
    }

    @Test
    public void upsertItem_existingUnpinned_updatesLastUsed() {
        List<SearchHistoryItem> items = new ArrayList<>(Arrays.asList(
            item("react", false, T1)
        ));
        SearchHistoryLogic.upsertItem(items, "react", T3);
        assertEquals(1, items.size());
        assertEquals(T3, items.get(0).getLastUsed());
    }

    @Test
    public void upsertItem_existingPinned_keepsPinnedAndUpdatesLastUsed() {
        List<SearchHistoryItem> items = new ArrayList<>(Arrays.asList(
            item("react", true, T1)
        ));
        SearchHistoryLogic.upsertItem(items, "react", T3);
        assertEquals(1, items.size());
        assertTrue(items.get(0).isPinned());
        assertEquals(T3, items.get(0).getLastUsed());
    }

    @Test
    public void upsertItem_trimsWhitespace() {
        List<SearchHistoryItem> items = new ArrayList<>();
        SearchHistoryLogic.upsertItem(items, "  react  ", T1);
        assertEquals("react", items.get(0).getText());
    }

    // ===== evictLRU =====

    @Test
    public void evictLRU_keepsAllPinned() {
        List<SearchHistoryItem> items = new ArrayList<>(Arrays.asList(
            item("p1", true,  T1),
            item("p2", true,  T2),
            item("u1", false, T1),
            item("u2", false, T2),
            item("u3", false, T3)
        ));
        // maxCount=2, 应保留 2 个最新 unpinned + 全部 pinned
        SearchHistoryLogic.evictLRU(items, 2);
        assertEquals(4, items.size());
        // 留下的 unpinned 应该是 T3 和 T2
        long uCount = items.stream().filter(it -> !it.isPinned()).count();
        long pCount = items.stream().filter(SearchHistoryItem::isPinned).count();
        assertEquals(2, uCount);
        assertEquals(2, pCount);
    }

    @Test
    public void evictLRU_dropsOldestUnpinnedWhenExceeds() {
        List<SearchHistoryItem> items = new ArrayList<>(Arrays.asList(
            item("u1", false, T1),
            item("u2", false, T2),
            item("u3", false, T3)
        ));
        // maxCount=2, u1 (T1 最旧) 应被淘汰
        SearchHistoryLogic.evictLRU(items, 2);
        assertEquals(2, items.size());
        assertEquals("u3", items.get(0).getText());
        assertEquals("u2", items.get(1).getText());
    }

    @Test
    public void evictLRU_doesNotEvictAtMaxCountBoundary() {
        // 关键不变量：严格 > maxCount 才淘汰
        List<SearchHistoryItem> items = new ArrayList<>(Arrays.asList(
            item("u1", false, T1),
            item("u2", false, T2)
        ));
        SearchHistoryLogic.evictLRU(items, 2);  // 等于 maxCount
        assertEquals("边界等于 maxCount 时不淘汰", 2, items.size());
    }

    @Test
    public void evictLRU_onlyCountsUnpinned() {
        // pinned 不挤掉 unpinned 配额
        List<SearchHistoryItem> items = new ArrayList<>(Arrays.asList(
            item("p1", true,  T1),
            item("p2", true,  T2),
            item("p3", true,  T3),
            item("u1", false, T1)
        ));
        // maxCount=1 (unpinned 上限), 当前 unpinned=1 不超, 不应淘汰
        SearchHistoryLogic.evictLRU(items, 1);
        assertEquals(4, items.size());
    }

    // ===== helper =====

    private static SearchHistoryItem item(String text, boolean pinned, long ts) {
        return new SearchHistoryItem(text, pinned, ts);
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd "c:/projects/duxiang-pack/duxiang-android" && ./gradlew :app:testDebugUnitTest --tests "person.notfresh.readingshare.db.SearchHistoryLogicTest"
```

预期：编译失败（`SearchHistoryLogic` 不存在）或 `NoSuchMethodError`。

- [ ] **Step 3: 实现 SearchHistoryLogic**

新建文件，完整内容：

```java
package person.notfresh.readingshare.db;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import person.notfresh.readingshare.model.SearchHistoryItem;

/**
 * 搜索历史的纯逻辑层（无 Android 依赖，便于单元测试）
 * 三个职责：排序、upsert、LRU 淘汰
 */
public final class SearchHistoryLogic {

    private SearchHistoryLogic() {}

    /**
     * 排序：pinned 在前，组内按 lastUsed 降序
     */
    public static List<SearchHistoryItem> sortItems(List<SearchHistoryItem> items) {
        List<SearchHistoryItem> pinned = new ArrayList<>();
        List<SearchHistoryItem> unpinned = new ArrayList<>();
        for (SearchHistoryItem it : items) {
            if (it.isPinned()) pinned.add(it); else unpinned.add(it);
        }
        Comparator<SearchHistoryItem> byLastUsedDesc = (a, b) -> Long.compare(b.getLastUsed(), a.getLastUsed());
        pinned.sort(byLastUsedDesc);
        unpinned.sort(byLastUsedDesc);
        List<SearchHistoryItem> result = new ArrayList<>(items.size());
        result.addAll(pinned);
        result.addAll(unpinned);
        return result;
    }

    /**
     * upsert：keyword trim 后写入；若已存在则更新 lastUsed（保持原有 pinned 状态）
     * 输入为空（含纯空白）时静默返回
     */
    public static void upsertItem(List<SearchHistoryItem> items, String keyword, long now) {
        if (keyword == null) return;
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) return;

        for (SearchHistoryItem it : items) {
            if (it.getText().equals(trimmed)) {
                it.setLastUsed(now);
                return;
            }
        }
        items.add(new SearchHistoryItem(trimmed, false, now));
    }

    /**
     * LRU 淘汰：仅在 unpinned 数量 > maxCount 时淘汰最旧的
     * pinned 条目不参与计数，永不淘汰
     * 边界：等于 maxCount 时不淘汰（严格 >）
     */
    public static void evictLRU(List<SearchHistoryItem> items, int maxCount) {
        List<SearchHistoryItem> unpinned = new ArrayList<>();
        for (SearchHistoryItem it : items) {
            if (!it.isPinned()) unpinned.add(it);
        }
        if (unpinned.size() <= maxCount) return;

        unpinned.sort((a, b) -> Long.compare(a.getLastUsed(), b.getLastUsed()));  // 升序：最旧在前
        int toRemove = unpinned.size() - maxCount;
        // 收集要淘汰的 text
        java.util.Set<String> removeTexts = new java.util.HashSet<>();
        for (int i = 0; i < toRemove; i++) {
            removeTexts.add(unpinned.get(i).getText());
        }
        // 从原列表移除
        Iterator<SearchHistoryItem> it = items.iterator();
        while (it.hasNext()) {
            SearchHistoryItem item = it.next();
            if (!item.isPinned() && removeTexts.contains(item.getText())) {
                it.remove();
            }
        }
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd "c:/projects/duxiang-pack/duxiang-android" && ./gradlew :app:testDebugUnitTest --tests "person.notfresh.readingshare.db.SearchHistoryLogicTest"
```

预期：`BUILD SUCCESSFUL`，11 个测试全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/test/java/person/notfresh/readingshare/db/SearchHistoryLogicTest.java app/src/main/java/person/notfresh/readingshare/db/SearchHistoryLogic.java
git commit -m "feat(search-history): add SearchHistoryLogic with TDD coverage for sort/upsert/evict"
```

---

### Task 3: SearchHistoryManager 持久化层

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/db/SearchHistoryManager.java`

**Steps:**

- [ ] **Step 1: 实现 Manager**

新建文件，完整内容：

```java
package person.notfresh.readingshare.db;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.model.SearchHistoryItem;

/**
 * 搜索历史持久化层
 * 存储后端：SharedPreferences（key="searchHistory"，value=JSON）
 * 数据结构：{ items: [{text, pinned, lastUsed}, ...], maxCount: int }
 */
public class SearchHistoryManager {

    private static final String PREFS_NAME = "search_history_prefs";
    private static final String KEY_DATA = "searchHistory";
    private static final int DEFAULT_MAX_COUNT = 10;
    private static final String FIELD_ITEMS = "items";
    private static final String FIELD_MAX_COUNT = "maxCount";

    private final SharedPreferences prefs;

    public SearchHistoryManager(Context context) {
        this.prefs = context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 读取并按 sortItems 排序后返回 */
    public List<SearchHistoryItem> loadHistory() {
        Data data = readData();
        return SearchHistoryLogic.sortItems(data.items);
    }

    /** 写入：trim → upsert → 淘汰 → save */
    public void addSearchKeyword(String keyword) {
        if (keyword == null) return;
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) return;

        Data data = readData();
        SearchHistoryLogic.upsertItem(data.items, trimmed, System.currentTimeMillis());
        SearchHistoryLogic.evictLRU(data.items, data.maxCount);
        writeData(data);
    }

    /** 翻转 pinned 状态后保存（若不存在则不操作） */
    public void togglePinKeyword(String keyword) {
        if (keyword == null) return;
        Data data = readData();
        boolean changed = false;
        for (SearchHistoryItem it : data.items) {
            if (it.getText().equals(keyword)) {
                it.setPinned(!it.isPinned());
                changed = true;
                break;
            }
        }
        if (changed) writeData(data);
    }

    /** 删除后保存 */
    public void deleteKeyword(String keyword) {
        if (keyword == null) return;
        Data data = readData();
        boolean changed = data.items.removeIf(it -> it.getText().equals(keyword));
        if (changed) writeData(data);
    }

    /** 设置上限，立即按 LRU 淘汰多余（仅 unpinned） */
    public void setMaxCount(int count) {
        if (count < 0) count = 0;
        Data data = readData();
        data.maxCount = count;
        SearchHistoryLogic.evictLRU(data.items, count);
        writeData(data);
    }

    public int getMaxCount() {
        return readData().maxCount;
    }

    // ===== 内部：序列化/反序列化 =====

    private static class Data {
        List<SearchHistoryItem> items = new ArrayList<>();
        int maxCount = DEFAULT_MAX_COUNT;
    }

    private Data readData() {
        Data data = new Data();
        String raw = prefs.getString(KEY_DATA, null);
        if (raw == null) return data;
        try {
            JSONObject root = new JSONObject(raw);
            data.maxCount = root.optInt(FIELD_MAX_COUNT, DEFAULT_MAX_COUNT);
            JSONArray arr = root.optJSONArray(FIELD_ITEMS);
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    data.items.add(SearchHistoryItem.fromJson(obj));
                }
            }
        } catch (JSONException e) {
            // 损坏的数据：返回空数据，不抛异常
        }
        return data;
    }

    private void writeData(Data data) {
        try {
            JSONObject root = new JSONObject();
            root.put(FIELD_MAX_COUNT, data.maxCount);
            JSONArray arr = new JSONArray();
            for (SearchHistoryItem it : data.items) {
                arr.put(it.toJson());
            }
            root.put(FIELD_ITEMS, arr);
            prefs.edit().putString(KEY_DATA, root.toString()).apply();
        } catch (JSONException e) {
            // 写入失败：静默，不影响 UI
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd "c:/projects/duxiang-pack/duxiang-android" && ./gradlew :app:compileDebugJavaWithJavac
```

预期：`BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/db/SearchHistoryManager.java
git commit -m "feat(search-history): add SearchHistoryManager with 5 storage APIs"
```

---

### Task 4: 字符串资源

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

**Steps:**

- [ ] **Step 1: 添加新字符串**

在 `</resources>` 之前追加：

```xml
    <!-- 搜索历史下拉 -->
    <string name="search_history_empty">暂无搜索历史</string>
    <string name="search_history_pin">固定</string>
    <string name="search_history_unpin">取消固定</string>
    <string name="search_history_delete">删除</string>
    <string name="search_history_max_count_label">搜索历史保留条数（非固定）</string>
    <string name="search_history_max_count_hint">默认 10</string>
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(search-history): add string resources for dropdown UI"
```

---

### Task 5: 下拉行布局

**Files:**
- Create: `app/src/main/res/layout/item_search_history.xml`
- Create: `app/src/main/res/drawable/search_history_popup_bg.xml`
- Create: `app/src/main/res/drawable/popup_shadow.xml`

**Steps:**

- [ ] **Step 1: 创建下拉行布局**

新建文件，完整内容：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="16dp"
    android:paddingEnd="8dp"
    android:paddingTop="10dp"
    android:paddingBottom="10dp"
    android:background="?android:attr/selectableItemBackground"
    android:clickable="true"
    android:focusable="true">

    <TextView
        android:id="@+id/search_history_text"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textSize="14sp"
        android:textColor="#212121"
        android:singleLine="true"
        android:ellipsize="end" />

    <ImageButton
        android:id="@+id/search_history_pin"
        android:layout_width="32dp"
        android:layout_height="32dp"
        android:background="?android:attr/selectableItemBackgroundBorderless"
        android:src="@drawable/ic_pin_outline"
        android:contentDescription="@string/search_history_pin"
        android:scaleType="centerInside" />

    <ImageButton
        android:id="@+id/search_history_delete"
        android:layout_width="32dp"
        android:layout_height="32dp"
        android:background="?android:attr/selectableItemBackgroundBorderless"
        android:src="@drawable/ic_close"
        android:contentDescription="@string/search_history_delete"
        android:scaleType="centerInside" />

</LinearLayout>
```

- [ ] **Step 2: 创建下拉背景 drawable（圆角白底 + 阴影）**

新建 `app/src/main/res/drawable/search_history_popup_bg.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFFFFF" />
    <corners android:radius="6dp" />
    <stroke android:width="1dp" android:color="#E0E0E0" />
</shape>
```

新建 `app/src/main/res/drawable/popup_shadow.xml`（可选，9-patch 阴影；如不需要可省略跳过本步）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#22000000" />
</shape>
```

- [ ] **Step 3: 创建 pin 图标 drawable**

新建 `app/src/main/res/drawable/ic_pin_outline.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#9E9E9E">
    <path
        android:fillColor="#000000"
        android:pathData="M16,12V4H17V2H7V4H8V12L6,14V16H11.2V22H12.8V16H18V14L16,12Z" />
</vector>
```

新建 `app/src/main/res/drawable/ic_pin_filled.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#1976D2">
    <path
        android:fillColor="#000000"
        android:pathData="M16,12V4H17V2H7V4H8V12L6,14V16H11.2V22H12.8V16H18V14L16,12Z" />
</vector>
```

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/layout/item_search_history.xml app/src/main/res/drawable/search_history_popup_bg.xml app/src/main/res/drawable/ic_pin_outline.xml app/src/main/res/drawable/ic_pin_filled.xml
git commit -m "feat(search-history): add dropdown row layout and pin icons"
```

---

### Task 6: SearchHistoryAdapter

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/adapter/SearchHistoryAdapter.java`

**Steps:**

- [ ] **Step 1: 实现 Adapter**

新建文件，完整内容：

```java
package person.notfresh.readingshare.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.model.SearchHistoryItem;

/**
 * 搜索历史下拉行适配器
 * - pinned 在前、unpinned 在后（由 SearchHistoryLogic.sortItems 保证）
 * - 空状态显示"暂无搜索历史"占位行（disable）
 */
public class SearchHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_EMPTY = 1;

    public interface OnItemClickListener {
        void onItemClick(SearchHistoryItem item);
        void onPinClick(SearchHistoryItem item);
        void onDeleteClick(SearchHistoryItem item);
    }

    private final List<SearchHistoryItem> items = new ArrayList<>();
    private OnItemClickListener listener;

    public void setItems(List<SearchHistoryItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    @Override
    public int getItemViewType(int position) {
        return items.isEmpty() ? TYPE_EMPTY : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_EMPTY) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_history_empty, parent, false);
            return new EmptyVH(v);
        }
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_search_history, parent, false);
        return new ItemVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof EmptyVH) return;
        SearchHistoryItem item = items.get(position);
        ItemVH vh = (ItemVH) holder;
        vh.text.setText(item.getText());
        vh.pin.setImageResource(item.isPinned() ? R.drawable.ic_pin_filled : R.drawable.ic_pin_outline);
        vh.pin.setContentDescription(vh.itemView.getContext()
            .getString(item.isPinned() ? R.string.search_history_unpin : R.string.search_history_pin));

        vh.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
        vh.pin.setOnClickListener(v -> {
            if (listener != null) listener.onPinClick(item);
        });
        vh.delete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClick(item);
        });
    }

    @Override
    public int getItemCount() {
        // 空时仍显示 1 个占位行
        return items.isEmpty() ? 1 : items.size();
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        final TextView text;
        final ImageButton pin;
        final ImageButton delete;
        ItemVH(View v) {
            super(v);
            text = v.findViewById(R.id.search_history_text);
            pin = v.findViewById(R.id.search_history_pin);
            delete = v.findViewById(R.id.search_history_delete);
        }
    }

    static class EmptyVH extends RecyclerView.ViewHolder {
        EmptyVH(View v) { super(v); }
    }
}
```

- [ ] **Step 2: 创建空状态布局**

新建 `app/src/main/res/layout/item_search_history_empty.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="16dp"
    android:gravity="center"
    android:textSize="14sp"
    android:textColor="#9E9E9E"
    android:text="@string/search_history_empty" />
```

- [ ] **Step 3: 编译验证**

```bash
cd "c:/projects/duxiang-pack/duxiang-android" && ./gradlew :app:compileDebugJavaWithJavac
```

预期：`BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/adapter/SearchHistoryAdapter.java app/src/main/res/layout/item_search_history_empty.xml
git commit -m "feat(search-history): add SearchHistoryAdapter with item/empty view types"
```

---

### Task 7: HomeFragment 集成

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/home/HomeFragment.java`

**Steps:**

- [ ] **Step 1: 添加新字段和常量**

在 HomeFragment 类的字段区（约第 104 行 `private EditText searchEditText;` 之后）添加：

```java
    // ========== 搜索历史下拉 ==========
    private static final long SEARCH_DEBOUNCE_MS = 300L;
    private static final long SEARCH_BLUR_HIDE_DELAY_MS = 200L;
    private static final int SEARCH_HISTORY_POPUP_MAX_DP = 300;

    private SearchHistoryManager searchHistoryManager;
    private List<SearchHistoryItem> historyCache = new ArrayList<>();
    private PopupWindow historyPopup;
    private SearchHistoryAdapter historyAdapter;
    private View historyPopupContentView;
    private final Runnable debounceSearchRunnable = new Runnable() {
        @Override public void run() { performSearch(searchEditText.getText().toString()); }
    };
    private final Runnable hidePopupRunnable = new Runnable() {
        @Override public void run() { dismissHistoryPopup(); }
    };
```

并把现有 `TextWatcher` 改成最小存根（先不动内部逻辑，下一步替换）。在 import 区添加：

```java
import android.widget.PopupWindow;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.util.ArrayList;
import person.notfresh.readingshare.adapter.SearchHistoryAdapter;
import person.notfresh.readingshare.db.SearchHistoryManager;
import person.notfresh.readingshare.model.SearchHistoryItem;
```

- [ ] **Step 2: 把 afterTextChanged 改为 debounce 触发**

把现有 `afterTextChanged` 改为：

```java
                    @Override
                    public void afterTextChanged(Editable s) {
                        String text = s.toString();
                        // 取消上一次的 debounce，重新计时
                        searchEditText.removeCallbacks(debounceSearchRunnable);
                        searchEditText.postDelayed(debounceSearchRunnable, SEARCH_DEBOUNCE_MS);
                    }
```

- [ ] **Step 3: 抽取 performSearch 方法**

在 `HomeFragment` 类中新增私有方法（紧跟 onCreateView 之后）：

```java
    /**
     * 实际执行搜索 + 入历史 + 刷新下拉
     * 由 debounce 和点选历史项两种路径调用
     */
    private void performSearch(String rawText) {
        if (adapter == null) return;
        String text = rawText == null ? "" : rawText.trim();
        adapter.filter(text);
        // 入历史（非空才记）
        if (!text.isEmpty() && searchHistoryManager != null) {
            searchHistoryManager.addSearchKeyword(text);
            refreshHistoryCache();
        }
    }
```

- [ ] **Step 4: 添加下拉显示 / 隐藏 / refresh 方法**

在 HomeFragment 类中新增以下私有方法：

```java
    /** 重新读取历史到内存缓存，并按需重渲下拉 */
    private void refreshHistoryCache() {
        if (searchHistoryManager == null) return;
        historyCache = searchHistoryManager.loadHistory();
        if (historyAdapter != null) {
            historyAdapter.setItems(historyCache);
        }
    }

    /** 显示下拉：仅在 EditText 获得焦点且输入为空时调用 */
    private void showHistoryPopup() {
        if (searchEditText == null || searchEditText.getWindowToken() == null) return;
        refreshHistoryCache();
        if (historyPopup == null) {
            buildHistoryPopup();
        }
        if (historyPopup != null && !historyPopup.isShowing()) {
            historyPopup.showAsDropDown(searchEditText, 0, 0);
        }
    }

    private void dismissHistoryPopup() {
        if (historyPopup != null && historyPopup.isShowing()) {
            historyPopup.dismiss();
        }
    }

    /** 构造 PopupWindow（只构造一次） */
    private void buildHistoryPopup() {
        Context ctx = requireContext();
        historyAdapter = new SearchHistoryAdapter();
        historyAdapter.setOnItemClickListener(new SearchHistoryAdapter.OnItemClickListener() {
            @Override public void onItemClick(SearchHistoryItem item) {
                String kw = item.getText();
                searchEditText.removeCallbacks(hidePopupRunnable);
                searchEditText.setText(kw);
                searchEditText.setSelection(kw.length());
                searchEditText.removeCallbacks(debounceSearchRunnable);
                dismissHistoryPopup();
                // 手动同步副作用：setText 不触发 TextWatcher
                performSearch(kw);
            }
            @Override public void onPinClick(SearchHistoryItem item) {
                searchHistoryManager.togglePinKeyword(item.getText());
                refreshHistoryCache();
            }
            @Override public void onDeleteClick(SearchHistoryItem item) {
                searchHistoryManager.deleteKeyword(item.getText());
                refreshHistoryCache();
            }
        });
        historyAdapter.setItems(historyCache);

        RecyclerView rv = new RecyclerView(ctx);
        rv.setLayoutManager(new LinearLayoutManager(ctx));
        rv.setAdapter(historyAdapter);
        rv.setBackgroundResource(R.drawable.search_history_popup_bg);
        rv.setElevation(8f);

        int width = searchEditText.getWidth();
        int maxHeight = (int) (SEARCH_HISTORY_POPUP_MAX_DP * ctx.getResources().getDisplayMetrics().density);
        historyPopupContentView = rv;
        historyPopup = new PopupWindow(rv, width, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        historyPopup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        historyPopup.setOutsideTouchable(false);
        historyPopup.setFocusable(false);  // 关键：不抢焦点，避免 EditText 失焦
        historyPopup.setHeight(maxHeight);

        // 在下拉区域内触摸时取消 pending hide（spec 6.6 等价物）
        rv.setOnTouchListener((v, ev) -> {
            searchEditText.removeCallbacks(hidePopupRunnable);
            return false;
        });
    }
```

- [ ] **Step 5: 添加 focus 监听器（替换 setOnFocusChangeListener / 增补）**

紧跟在 `searchEditText` 初始化之后（约第 329 行 `searchEditText = binding.searchEditText;`），把现有 if 块扩成：

```java
            searchEditText = binding.searchEditText;
            if (searchEditText != null) {
                // 搜索历史管理器
                searchHistoryManager = new SearchHistoryManager(requireContext());
                refreshHistoryCache();

                searchEditText.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override
                    public void afterTextChanged(Editable s) {
                        String text = s.toString();
                        searchEditText.removeCallbacks(debounceSearchRunnable);
                        searchEditText.postDelayed(debounceSearchRunnable, SEARCH_DEBOUNCE_MS);
                    }
                });

                // focus: 空时弹下拉
                searchEditText.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        if (searchEditText.getText().toString().trim().isEmpty()) {
                            showHistoryPopup();
                        }
                    } else {
                        // 200ms 延迟，给点击下拉留时间窗
                        searchEditText.removeCallbacks(hidePopupRunnable);
                        searchEditText.postDelayed(hidePopupRunnable, SEARCH_BLUR_HIDE_DELAY_MS);
                    }
                });
            } else {
                Log.w(TAG, "onCreateView: searchEditText is null");
            }
```

- [ ] **Step 6: 在 onDestroyView 中清理**

把 `onDestroyView` 改为：

```java
    @Override
    public void onDestroyView() {
        Log.d(TAG, "onDestroyView: start");
        super.onDestroyView();
        if (searchEditText != null) {
            searchEditText.removeCallbacks(debounceSearchRunnable);
            searchEditText.removeCallbacks(hidePopupRunnable);
        }
        dismissHistoryPopup();
        historyPopup = null;
        historyAdapter = null;
        binding = null;
        if (linkDao != null) {
            linkDao.close();
            Log.d(TAG, "onDestroyView: LinkDao closed");
        }
        Log.d(TAG, "onDestroyView: completed");
    }
```

- [ ] **Step 7: 编译验证**

```bash
cd "c:/projects/duxiang-pack/duxiang-android" && ./gradlew :app:compileDebugJavaWithJavac
```

预期：`BUILD SUCCESSFUL`。

- [ ] **Step 8: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/ui/home/HomeFragment.java
git commit -m "feat(search-history): integrate dropdown with debounce, focus/blur, and click handlers"
```

---

### Task 8: SettingFragment 添加 maxCount 配置

**Files:**
- Modify: `app/src/main/res/layout/fragment_slideshow.xml`
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java`

**Steps:**

- [ ] **Step 1: 在 fragment_slideshow.xml 末尾添加 maxCount 输入区**

在 `</LinearLayout>` 之前（即 `recent_tags_window_spinner` 的 `</Spinner>` 之后）添加：

```xml
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/search_history_max_count_label"
            android:textSize="18sp"
            android:layout_marginTop="24dp"
            android:layout_marginBottom="8dp"/>

        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:hint="@string/search_history_max_count_hint">

            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/search_history_max_count_input"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:inputType="number"
                android:maxLines="1" />

        </com.google.android.material.textfield.TextInputLayout>
```

- [ ] **Step 2: 在 SettingFragment 加载并保存 maxCount**

在 `SettingFragment.onCreateView` 末尾（`return root;` 之前）追加：

```java
        // 搜索历史 maxCount
        TextInputEditText maxCountInput = root.findViewById(R.id.search_history_max_count_input);
        SearchHistoryManager historyManager = new SearchHistoryManager(requireContext());
        maxCountInput.setText(String.valueOf(historyManager.getMaxCount()));
        maxCountInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String raw = maxCountInput.getText().toString().trim();
                int count;
                try {
                    count = Integer.parseInt(raw);
                } catch (NumberFormatException e) {
                    count = 10;
                }
                if (count < 1) count = 1;
                if (count > 100) count = 100;
                historyManager.setMaxCount(count);
                maxCountInput.setText(String.valueOf(count));
                Snackbar.make(requireView(), "已保存：保留 " + count + " 条历史", Snackbar.LENGTH_SHORT).show();
            }
        });
```

并添加 import：

```java
import person.notfresh.readingshare.db.SearchHistoryManager;
```

- [ ] **Step 3: 编译验证**

```bash
cd "c:/projects/duxiang-pack/duxiang-android" && ./gradlew :app:compileDebugJavaWithJavac
```

预期：`BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/layout/fragment_slideshow.xml app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java
git commit -m "feat(search-history): expose maxCount setting in Settings page"
```

---

### Task 9: 构建 + 单元测试 + 手动验证

**Files:** 无（验证步骤）

**Steps:**

- [ ] **Step 1: 跑全部单元测试**

```bash
cd "c:/projects/duxiang-pack/duxiang-android" && ./gradlew :app:testDebugUnitTest
```

预期：`BUILD SUCCESSFUL`，11 个 SearchHistoryLogicTest 全 PASS。

- [ ] **Step 2: 编译 Debug APK**

```bash
cd "c:/projects/duxiang-pack/duxiang-android" && ./gradlew :app:assembleDebug
```

预期：`BUILD SUCCESSFUL`。

- [ ] **Step 3: 手动 Smoke Test（建议在真机或模拟器跑一遍）**

按以下清单逐项验证，每项需 PASS：

1. 启动 app → HomeFragment 显示搜索框
2. 点击搜索框（不输入）→ 弹出"暂无搜索历史"下拉
3. 输入 "react" → 等待 300ms → 列表过滤到含 react 的链接
4. 清空输入 → 重新 focus → 列表恢复全部
5. 输入 "react" 后等 300ms → 清空 → focus → 下拉里看到 "react"（最近使用在最上）
6. 点击 "react" → 填入输入框 + 立即过滤 + 列表重渲
7. 长下拉里某项点 📌 → 该项移到 pinned 区
8. 点 ✕ → 该项消失
9. 设置页 → 修改 maxCount 为 3 → 旧历史按 LRU 淘汰到 3 条
10. force-stop app → 重启 → 历史持久化仍在
11. 旋转屏幕 → focus 恢复 → 下拉正确显示

- [ ] **Step 4: 提交（如果手动测试发现 fix）**

```bash
git add -A
git commit -m "fix(search-history): manual smoke test fixes"
```

---

## 关键设计点回顾

| spec 要求 | 实现位置 |
|-----------|---------|
| 存储 `{items, maxCount}` | `SearchHistoryManager`（SharedPreferences + org.json） |
| 5 个 API | `SearchHistoryManager` 5 个 public 方法 |
| 排序 | `SearchHistoryLogic.sortItems`（TDD 覆盖） |
| LRU 淘汰 | `SearchHistoryLogic.evictLRU`（TDD 覆盖，边界用严格 `>`） |
| 程序化赋值不触发"输入变化" | 点选历史项后手动调 `performSearch(text)` |
| mousedown preventDefault | PopupWindow `setFocusable(false)` + RecyclerView touchListener cancel hide |
| blur 200ms 延迟 | `postDelayed(hideRunnable, 200)`，下拉触摸时 `removeCallbacks` |
| 每次写后 refresh | 封装在 `refreshHistoryCache()`，5 个 API 之后都跟着它 |
| 设置页 maxCount | `SettingFragment` 失焦时 `historyManager.setMaxCount(n)` |

---

## 后续可迭代（不在本次范围）

- 键盘 ↑↓/Enter 选词（spec 7 节明确说可后续做）
- 自定义分隔线（pinned/unpinned 之间）—— 当前直接用第一个非 pinned 行的视觉边界即可，效果够用
- i18n 国际化 —— strings.xml 已抽常量，后续做翻译时不用动代码
- 持久化改 Room 数据库 —— 当前 SharedPreferences 对小数据量完全够用
