# 搜索框细节打磨 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 × 清除按钮改动后引入的两处细节：搜索框垂直 padding 丢失（高度被压扁）、下拉删除按钮颜色太浅（白 × 不够明显）。

**Architecture:** XML 资源层做静态修复（补 padding / 换 src），Java 逻辑层加 `dpToPx` 辅助方法并在 `updateInputChrome` 中 toggle `paddingEnd`（8dp ↔ 36dp 跟随 × 显隐）。`updateInputChrome` 仍是单点显隐控制。

**Tech Stack:** Android Java（项目无新增依赖），`Resources.getDisplayMetrics().density` 做 dp→px 转换，`View.setPadding` 写回。

---

### Task 1: XML 资源层修复

**Files:**
- Modify: `app/src/main/res/layout/fragment_home.xml:8-37`（在 EditText 节点加 paddingTop + paddingBottom）
- Modify: `app/src/main/res/layout/item_search_history.xml:39`（把 ic_close 换为 ic_clear）

- [ ] **Step 1: fragment_home.xml 加 paddingTop + paddingBottom**

在 `app/src/main/res/layout/fragment_home.xml` 的 EditText 节点中，找到现有的：

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

改为（`paddingEnd` 后插入 `paddingTop` + `paddingBottom`）：

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

- [ ] **Step 2: item_search_history.xml 改删除按钮 src**

在 `app/src/main/res/layout/item_search_history.xml` 第 39 行，把：

```xml
            android:src="@drawable/ic_close"
```

改为：

```xml
            android:src="@drawable/ic_clear"
```

只动这一行，其他属性不动。

- [ ] **Step 3: 编译验证 XML 无错**

Run: `./gradlew :app:assembleDebug --offline`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 提交 XML 改动**

> 注意：当前工作树有未提交的 pre-existing changes（`.claude/settings.local.json`、`SubjectFragment.java`、done-sync → done-sink 重命名），必须用具体文件名 add，不能用 `git add -A`。

```bash
git add app/src/main/res/layout/fragment_home.xml \
        app/src/main/res/layout/item_search_history.xml
git commit -m "fix(search-history): restore input vertical padding + darker delete icon

- EditText was missing paddingTop/paddingBottom (defaulted 0dp), making
  the search box look squashed; restore 8dp vertical padding
- Reuse ic_clear.xml (dark gray #757575) for the dropdown's delete
  button; the previous white ic_close was too subtle"
```

---

### Task 2: HomeFragment.java toggle paddingEnd

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/home/HomeFragment.java`
  - 字段区（在 `SEARCH_HISTORY_POPUP_MAX_DP = 300` 附近加两个新常量）
  - 新增 `dpToPx` 辅助方法
  - `updateInputChrome` 方法（× 显隐 toggle 后加 setPadding 调用）

- [ ] **Step 1: 加常量**

在 HomeFragment.java 字段区，找到：

```java
    private static final int SEARCH_HISTORY_POPUP_MAX_DP = 300;
```

在它后面追加：

```java
    private static final int SEARCH_DEFAULT_END_PADDING_DP = 8;
    private static final int SEARCH_CLEAR_END_PADDING_DP = 36;
```

- [ ] **Step 2: 加 dpToPx 辅助方法**

在 `updateInputChrome()` 方法之前（约 line 463 之前），插入新方法。建议放在 `refreshHistoryCache()` 和 `updateInputChrome()` 之间：

```java
    /** dp → px 转换（用于 setPadding） */
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
```

- [ ] **Step 3: 改造 updateInputChrome**

把现有 `updateInputChrome()` 方法（大约在第 463-476 行）替换为：

```java
    private void updateInputChrome() {
        if (searchEditText == null) return;
        String text = searchEditText.getText().toString();
        boolean hasText = !text.isEmpty();

        if (searchClearButton != null) {
            searchClearButton.setVisibility(hasText ? View.VISIBLE : View.GONE);
        }
        // 动态 paddingEnd：× 在时 36dp 让位，× 不在时 8dp 恢复原始
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

- [ ] **Step 4: 编译验证**

Run: `./gradlew :app:assembleDebug --offline`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 提交 Java 改动**

> 注意：用具体文件名 add，不要 `git add -A`。

```bash
git add app/src/main/java/person/notfresh/readingshare/ui/home/HomeFragment.java
git commit -m "feat(search-history): toggle EditText paddingEnd with × visibility

- Add SEARCH_DEFAULT_END_PADDING_DP=8 and SEARCH_CLEAR_END_PADDING_DP=36
- Add dpToPx(int) helper using Resources.getDisplayMetrics().density
- updateInputChrome now also calls setPadding(..., 8dp|36dp, ...)
  so the search box's right padding shrinks back to 8dp when × is
  gone, restoring the original visible text area"
```

---

### Task 3: 手动 smoke 验证（设备/模拟器）

> 这一步需要真实运行环境，本环境无设备/模拟器，**用户在真实设备上执行**。

**Files:** 无

- [ ] **Step 1: 启动 App 到首页**

- [ ] **Step 2: 验证 3 个 case**（spec 第七节）

| 场景 | 期望 |
|---|---|
| 初始空文本 | 输入框高度 = 原始，paddingEnd = 8dp（视觉上"右边到边"）|
| 输入文字 | × 显示，paddingEnd = 36dp（× 不遮文字）|
| 下拉里删除按钮 | 深灰 ×（#757575），比之前的白色明显 |
