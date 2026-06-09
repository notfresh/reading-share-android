# 搜索框输入态完善 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给搜索框补齐三个交互细节——空时弹下拉、无历史不弹、有文字时显示 × 清除按钮。

**Architecture:** 单一真源方法 `updateInputChrome()` 统一处理 × 显隐和下拉显隐；5 个调用点（focus / text changed / × click / adapter 三个 callback）都走它。布局用 `FrameLayout` 叠放 `ImageButton`，保持现有 `search_background.xml` 圆角风格。

**Tech Stack:** Android Java（项目无新增依赖）、FrameLayout + ImageButton、已有 `SearchHistoryManager`。

---

### Task 1: 资源层（drawable + string + layout）

**Files:**
- Create: `app/src/main/res/drawable/ic_clear.xml`
- Modify: `app/src/main/res/values/strings.xml:71-78`（在"搜索历史下拉"section 末尾追加一行）
- Modify: `app/src/main/res/layout/fragment_home.xml:8-20`（替换 EditText 节点为 FrameLayout 包裹）

- [ ] **Step 1: 新建 ic_clear.xml 清除图标**

文件路径：`app/src/main/res/drawable/ic_clear.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#757575"
        android:pathData="M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"/>
</vector>
```

- [ ] **Step 2: strings.xml 追加 search_clear**

在 `app/src/main/res/values/strings.xml` 末尾的 `<!-- 搜索历史下拉 -->` section 之后追加：

```xml
    <string name="search_clear">清除搜索</string>
```

完整尾部变为：

```xml
    <!-- 搜索历史下拉 -->
    <string name="search_history_empty">暂无搜索历史</string>
    <string name="search_history_pin">固定</string>
    <string name="search_history_unpin">取消固定</string>
    <string name="search_history_delete">删除</string>
    <string name="search_history_max_count_label">搜索历史保留条数（非固定）</string>
    <string name="search_history_max_count_hint">默认 10</string>
    <string name="search_clear">清除搜索</string>
</resources>
```

- [ ] **Step 3: fragment_home.xml 替换 EditText 节点**

在 `app/src/main/res/layout/fragment_home.xml` 中，找到现有第 8-20 行的 EditText 块（注释 `<!-- 搜索框 -->` 后），整体替换为下面的 FrameLayout：

```xml
    <!-- 搜索框 -->
    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_margin="8dp">

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

        <ImageButton
            android:id="@+id/search_clear_button"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:layout_gravity="end|center_vertical"
            android:layout_marginEnd="4dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@drawable/ic_clear"
            android:contentDescription="@string/search_clear"
            android:visibility="gone" />
    </FrameLayout>
```

- [ ] **Step 4: 编译验证资源层无错**

Run: `./gradlew :app:assembleDebug --offline`（带 `--offline` 避免下载新依赖，依赖未变）
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 提交资源层**

> 注意：当前工作树有未提交的 `.claude/settings.local.json`、`SubjectFragment.java`、`done-sync → done-sink` 重命名，必须用具体文件名 add，不能用 `git add -A`。

```bash
git add app/src/main/res/drawable/ic_clear.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/res/layout/fragment_home.xml
git commit -m "feat(search-history): add × clear button to search input

- Wrap search EditText in FrameLayout
- Add ic_clear.xml (24dp × icon, #757575)
- Add search_clear string
- ImageButton end-aligned, 36dp end padding gives space"
```

---

### Task 2: HomeFragment.java 接入 updateInputChrome

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/home/HomeFragment.java`
  - 字段区（line ~110-124）
  - `setOnFocusChangeListener`（line ~367-377）
  - `TextWatcher.afterTextChanged`（line ~358-364）
  - `performSearch` 末尾（line ~424-433）
  - `showHistoryPopup`（line ~444-454）
  - adapter onItemClick / onPinClick / onDeleteClick（line ~466-485）
  - 新增 `updateInputChrome` 方法
  - `onDestroyView`（line ~750-766）— null out new field
  - import: `ImageButton`

- [ ] **Step 1: 加 import 和字段**

在 HomeFragment.java 顶部 import 区，添加 `ImageButton`（如已存在则跳过）：

```java
import android.widget.ImageButton;
```

> 注意：文件已 import 了 `ImageView`（line 55），但不是 `ImageButton`，需要新加。

在字段区（line 110-124 之间，"`// ========== 搜索历史下拉 ==========`" 注释下），在 `private final Runnable hidePopupRunnable = ...` 之后追加：

```java
    private ImageButton searchClearButton;
```

- [ ] **Step 2: 新增 updateInputChrome 方法**

在 `refreshHistoryCache()` 方法（line 435-442 之后）和 `showHistoryPopup()` 方法（line 444-454 之前）之间，插入新方法：

```java
    /**
     * 统一处理输入框周围的 × 按钮和下拉的显隐
     * - 有文字：显示 ×，不显示下拉
     * - 空 + 焦点 + 有历史：显示下拉
     * - 空 + 无历史：什么都不显示
     */
    private void updateInputChrome() {
        if (searchEditText == null) return;
        String text = searchEditText.getText().toString();
        boolean hasText = !text.isEmpty();

        if (searchClearButton != null) {
            searchClearButton.setVisibility(hasText ? View.VISIBLE : View.GONE);
        }
        if (!hasText && searchEditText.hasFocus() && !historyCache.isEmpty()) {
            showHistoryPopup();
        } else {
            dismissHistoryPopup();
        }
    }
```

- [ ] **Step 3: showHistoryPopup 加守门 + 重读**

替换现有 `showHistoryPopup()` 方法（line 444-454）为：

```java
    private void showHistoryPopup() {
        if (searchEditText == null || searchEditText.getWindowToken() == null) return;
        if (historyCache.isEmpty()) return;   // 新增：空历史不弹
        refreshHistoryCache();               // 重读：保证下拉里删除最后一条时立即反映
        if (historyPopup == null) {
            buildHistoryPopup();
        }
        if (historyPopup != null && !historyPopup.isShowing()) {
            historyPopup.showAsDropDown(searchEditText, 0, 0);
        }
    }
```

- [ ] **Step 4: 替换 setOnFocusChangeListener**

在 `onCreateView` 中（line 367-377），找到现有 `searchEditText.setOnFocusChangeListener((v, hasFocus) -> { ... })` lambda，整体替换为：

```java
                // focus: 统一走 updateInputChrome 让它决定是否弹下拉
                searchEditText.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        updateInputChrome();
                    } else {
                        // 200ms 延迟，给点击下拉留时间窗
                        searchEditText.removeCallbacks(hidePopupRunnable);
                        searchEditText.postDelayed(hidePopupRunnable, SEARCH_BLUR_HIDE_DELAY_MS);
                    }
                });
```

- [ ] **Step 5: TextWatcher.afterTextChanged 加 updateInputChrome**

在 `onCreateView` 中（line 358-364），找到现有 `afterTextChanged` 实现，整体替换为：

```java
                    @Override
                    public void afterTextChanged(Editable s) {
                        // 立即同步 × 和下拉的显隐（不等 300ms 防抖）
                        updateInputChrome();
                        // 防抖触发实际搜索 + 入历史
                        searchEditText.removeCallbacks(debounceSearchRunnable);
                        searchEditText.postDelayed(debounceSearchRunnable, SEARCH_DEBOUNCE_MS);
                    }
```

- [ ] **Step 6: × 按钮绑定 click 监听**

在 `onCreateView` 中 `setOnFocusChangeListener` 之后，`else { Log.w(...); }` 之前，添加：

```java
                // × 清除按钮：清空文本 + 保持焦点 + 由 updateInputChrome 决定是否弹下拉
                searchClearButton = binding.searchClearButton;
                if (searchClearButton != null) {
                    searchClearButton.setOnClickListener(v -> {
                        searchEditText.setText("");
                        // 手动同步：setText 不触发 TextWatcher
                        updateInputChrome();
                    });
                }
```

- [ ] **Step 7: performSearch 末尾加 updateInputChrome**

在 `performSearch` 方法（line 424-433）末尾的 `refreshHistoryCache();` 之后追加一行：

```java
        // 入历史（非空才记）
        if (!text.isEmpty() && searchHistoryManager != null) {
            searchHistoryManager.addSearchKeyword(text);
            refreshHistoryCache();
            updateInputChrome();
        }
```

完整方法变为：

```java
    private void performSearch(String rawText) {
        if (adapter == null) return;
        String text = rawText == null ? "" : rawText.trim();
        adapter.filter(text);
        // 入历史（非空才记）
        if (!text.isEmpty() && searchHistoryManager != null) {
            searchHistoryManager.addSearchKeyword(text);
            refreshHistoryCache();
            updateInputChrome();
        }
    }
```

- [ ] **Step 8: adapter 三个 callback 末尾加 updateInputChrome**

在 `buildHistoryPopup` 中（line 466-485），找到三个 callback 实现，在 `refreshHistoryCache();` 调用之后各追加一行 `updateInputChrome();`：

- onItemClick：在 `searchEditText.removeCallbacks(hidePopupRunnable);` 块之后的 `dismissHistoryPopup();` 之后追加（注意：onItemClick 里的 refreshHistoryCache 不存在，只调用了 dismissHistoryPopup）——

实际上 onItemClick 当前代码是：

```java
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
```

在 `performSearch(kw);` 之后追加一行 `updateInputChrome();`（performSearch 内部已调，但显式调一次让 setText 之后的 × 显隐立刻反映，不依赖 performSearch 走 addSearchKeyword 分支）：

```java
                performSearch(kw);
                updateInputChrome();
```

- onPinClick：当前代码：

```java
            @Override public void onPinClick(SearchHistoryItem item) {
                searchHistoryManager.togglePinKeyword(item.getText());
                refreshHistoryCache();
            }
```

替换为：

```java
            @Override public void onPinClick(SearchHistoryItem item) {
                searchHistoryManager.togglePinKeyword(item.getText());
                refreshHistoryCache();
                updateInputChrome();
            }
```

- onDeleteClick：当前代码：

```java
            @Override public void onDeleteClick(SearchHistoryItem item) {
                searchHistoryManager.deleteKeyword(item.getText());
                refreshHistoryCache();
            }
```

替换为：

```java
            @Override public void onDeleteClick(SearchHistoryItem item) {
                searchHistoryManager.deleteKeyword(item.getText());
                refreshHistoryCache();
                updateInputChrome();
            }
```

- [ ] **Step 9: onDestroyView 清理**

在 `onDestroyView` 方法（line 750-766）中，搜索 `historyPopup = null;` 之后，添加 `searchClearButton = null;`：

```java
        dismissHistoryPopup();
        historyPopup = null;
        historyAdapter = null;
        searchClearButton = null;   // 新增
        binding = null;
```

- [ ] **Step 10: 编译验证**

Run: `./gradlew :app:assembleDebug --offline`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 11: 提交 Java 改动**

> 注意：用具体文件名 add，不要 `git add -A`，避免带进未提交的 pre-existing changes。

```bash
git add app/src/main/java/person/notfresh/readingshare/ui/home/HomeFragment.java
git commit -m "feat(search-history): wire × clear button + empty-state guard

- New updateInputChrome() centralizes × button + popup visibility
- showHistoryPopup: guard on empty history, re-read cache
- × click: clear text + keep focus + (re)show popup if has history
- Adapter callbacks: refresh + updateInputChrome to handle last-item-delete
- performSearch + focus listener + text watcher all funnel through it
- onDestroyView nulls the new ImageButton reference"
```

---

### Task 3: 手动 smoke 验证（设备/模拟器）

> 这一步需要真实运行环境，本环境无设备/模拟器，**用户在真实设备上执行**。

**Files:** 无

- [ ] **Step 1: 启动 App 到首页**

- [ ] **Step 2: 验证 6 个 case**（spec 第七节）

| 场景 | 期望 |
|---|---|
| 输入框初始空 + 有历史 → focus | 下拉弹出 |
| 输入框空 + 无历史（先删除所有历史）→ focus | 不弹 |
| 输入文字 | × 出现，下拉消失 |
| 点 × | 文本清空，焦点保持，有历史时下拉弹出 |
| 弹出的下拉里点删除最后一历史 | 下拉立即关闭 |
| 输入文字后 backspace 全清 | 若有历史，下拉弹出 |
