# 搜索框细节打磨 (2026-06-09)

> 在已实现的搜索框输入态（× 按钮 + 空弹下拉）基础上修两处问题：
> 1. 搜索框高度被压扁（垂直 padding 丢失）→ 恢复 + toggle paddingEnd
> 2. 下拉列表的删除按钮用白色不够明显 → 换为深灰 #757575

## 一、问题诊断

上一轮（commit 3b81e88）把 EditText 从 `android:padding="8dp"`（四边 8dp）拆成了 `paddingStart="8dp"` + `paddingEnd="36dp"`，**漏掉了 paddingTop / paddingBottom**，二者默认 0dp。

后果：搜索框垂直方向少了 16dp，文本直接贴边，看起来"被压扁"。

## 二、行为规则

| 输入框文本 | paddingStart | paddingTop | paddingEnd | paddingBottom | × 可见 |
|---|---|---|---|---|---|
| 空 | 8dp | 8dp | **8dp** | 8dp | GONE |
| 有文字 | 8dp | 8dp | **36dp** | 8dp | VISIBLE |

paddingStart / paddingTop / paddingBottom **始终是 8dp**（不 toggle），只 toggle paddingEnd（让位给 × 按钮）。

## 三、UI 改动

### fragment_home.xml 补回垂直 padding

当前 EditText 节点（约 12-22 行）：

```xml
<EditText
    android:id="@+id/search_edit_text"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingStart="8dp"
    android:paddingEnd="36dp"
    android:background="@drawable/search_background"
    android:hint="搜索标题或标签"
    android:maxLines="1"
    android:singleLine="true"
    android:imeOptions="actionSearch"
    android:textColor="@android:color/darker_gray"/>
```

改为（加 paddingTop + paddingBottom）：

```xml
<EditText
    android:id="@+id/search_edit_text"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingStart="8dp"
    android:paddingTop="8dp"
    android:paddingEnd="36dp"
    android:paddingBottom="8dp"
    android:background="@drawable/search_background"
    android:hint="搜索标题或标签"
    android:maxLines="1"
    android:singleLine="true"
    android:imeOptions="actionSearch"
    android:textColor="@android:color/darker_gray"/>
```

### item_search_history.xml 删除按钮换图标

第 39 行：

```diff
-        android:src="@drawable/ic_close"
+        android:src="@drawable/ic_clear"
```

`ic_clear.xml` 已存在（深灰 24dp × 形，#757575），上一轮为搜索框 × 按钮新建。现在复用为下拉删除按钮的图标。

## 四、Java 改动（HomeFragment.java）

### 字段区加常量

在 `SEARCH_HISTORY_POPUP_MAX_DP` 附近添加：

```java
private static final int SEARCH_DEFAULT_END_PADDING_DP = 8;
private static final int SEARCH_CLEAR_END_PADDING_DP = 36;
```

### 辅助方法

```java
private int dpToPx(int dp) {
    return (int) (dp * getResources().getDisplayMetrics().density);
}
```

### updateInputChrome 改造

把 × 可见性 toggle 和 paddingEnd toggle 合并到一处：

```java
private void updateInputChrome() {
    if (searchEditText == null) return;
    String text = searchEditText.getText().toString();
    boolean hasText = !text.isEmpty();

    if (searchClearButton != null) {
        searchClearButton.setVisibility(hasText ? View.VISIBLE : View.GONE);
    }
    // 动态 paddingEnd：× 在时 36dp 让位，× 不在时 8dp 恢复
    // paddingStart / Top / Bottom 保持 8dp 写死在 XML
    int endPaddingPx = dpToPx(hasText ? SEARCH_CLEAR_END_PADDING_DP : SEARCH_DEFAULT_END_PADDING_DP);
    searchEditText.setPadding(
        searchEditText.getPaddingLeft(),
        searchEditText.getPaddingTop(),
        endPaddingPx,
        searchEditText.getPaddingBottom()
    );

    if (!hasText && searchEditText.hasFocus() && !historyCache.isEmpty()) {
        showHistoryPopup();
    } else {
        dismissHistoryPopup();
    }
}
```

## 五、影响范围

| 文件 | 变更 | 行数估计 |
|---|---|---|
| `app/src/main/res/layout/fragment_home.xml` | 修改 | +2 |
| `app/src/main/res/layout/item_search_history.xml` | 修改 | 1 |
| `app/src/main/java/person/notfresh/readingshare/ui/home/HomeFragment.java` | 修改 | +15 / 改 5 |

## 六、保留 / 不动

- `ic_close.xml`（白 ×，菜单仍用）
- `ic_clear.xml`（深灰 ×，现在服务两个场景：搜索清除 + 历史删除）
- `fragment_home.xml` 中 paddingEnd=36dp / paddingStart=8dp / paddingTop=8dp / paddingBottom=8dp **写死**作为 XML 初始值（与 Java toggle 的初始态一致：× 默认 GONE 时 paddingEnd=8dp，XML 写 36dp 只是兜底，updateInputChrome 启动后会立即纠正）
- `SearchHistoryAdapter` / `SearchHistoryManager` / `SearchHistoryLogic`：全部不动
- `updateInputChrome()` 的其他逻辑（下拉显隐判断）：完全不变

## 七、测试

- 不加 JUnit（dp→px 转换 + if 逻辑简单到不需要专门测试）
- Manual smoke（3 项）：
  1. 空文本时输入框高度 = 原始（恢复），右侧 padding = 8dp（视觉上"右边到边"）
  2. 输入文字后 × 出现，文本不被 × 遮住
  3. 下拉里删除按钮变成深灰 ×，比之前的白色明显
