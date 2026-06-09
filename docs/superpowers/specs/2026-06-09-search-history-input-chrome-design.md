# 搜索框输入态完善设计 (2026-06-09)

> 在已实现的搜索历史下拉功能上，补齐三处交互细节：
> 1. 输入框为空时，下拉一定弹出
> 2. 无历史时，下拉不弹出
> 3. 输入框有内容时，右边出现清除按钮

## 一、行为规则

| 输入框文本 | × 按钮 | 下拉 |
|---|---|---|
| 空 + 有历史 | 不显示 | **显示** |
| 空 + 无历史 | 不显示 | 不显示 |
| 有文字 | 显示 | 不显示 |

附加规则：
- 点 × → 清空文本 + 保持焦点 + 若有历史立即弹下拉
- 在弹出的下拉里删完最后一条历史 → 立即关闭下拉
- 文本从"有"变"空"（如 backspace）→ 同焦点场景：有历史就弹

## 二、UI 改动（fragment_home.xml）

把现有 `<EditText android:id="@+id/search_edit_text" />` 包进 `<FrameLayout>`，加一个 `ImageButton` 叠放在右端。

```xml
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

- EditText 的 `padding` 从 `8dp` 改为 `paddingStart=8dp` + `paddingEnd=36dp`，给 × 让位
- ImageButton 初始 `visibility="gone"`，由 HomeFragment 根据文本状态切换
- `selectableItemBackgroundBorderless` 给点击加圆形涟漪反馈

## 三、新增资源

### drawable/ic_clear.xml

深灰 24dp × 形（区别于现有白色 `ic_close.xml`）：

```xml
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

### strings.xml 新增

```xml
<string name="search_clear">清除搜索</string>
```

## 四、Java 改动（HomeFragment.java）

### 新增字段

```java
private ImageButton searchClearButton;
```

### 新增核心方法

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

### 修改 showHistoryPopup 加守门

```java
private void showHistoryPopup() {
    if (searchEditText == null || searchEditText.getWindowToken() == null) return;
    if (historyCache.isEmpty()) return;   // 新增：空历史不弹
    refreshHistoryCache();               // 重新读一次，确保 historyCache 反映最新数据
    if (historyPopup == null) {
        buildHistoryPopup();
    }
    if (historyPopup != null && !historyPopup.isShowing()) {
        historyPopup.showAsDropDown(searchEditText, 0, 0);
    }
}
```

> 关键修复：即使外部调用前 `historyCache` 不为空，函数内也重读一次，下拉里删除最后一条时能立刻反映。

### 替换四处调用点

#### 1) setOnFocusChangeListener

```java
searchEditText.setOnFocusChangeListener((v, hasFocus) -> {
    if (hasFocus) {
        updateInputChrome();
    } else {
        searchEditText.removeCallbacks(hidePopupRunnable);
        searchEditText.postDelayed(hidePopupRunnable, SEARCH_BLUR_HIDE_DELAY_MS);
    }
});
```

#### 2) TextWatcher.afterTextChanged

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

#### 3) × 按钮 OnClickListener（新增）

```java
searchClearButton.setOnClickListener(v -> {
    searchEditText.setText("");
    // 手动同步：setText 不触发 TextWatcher
    updateInputChrome();
});
```

#### 4) adapter 三个 callback 末尾

onItemClick / onPinClick / onDeleteClick 里 `refreshHistoryCache()` 之后，追加 `updateInputChrome()`：
- onItemClick：文本从"空"变 kw，updateInputChrome 会把 × 显示出来 + dismiss 下拉
- onPinClick / onDeleteClick：historyCache 内容变化；如果删完最后一条，updateInputChrome 会自动关下拉

### performSearch 末尾

`addSearchKeyword` + `refreshHistoryCache()` 之后，追加 `updateInputChrome()`，让缓存与视图同步。

## 五、保留 / 删除

- 保留 `SearchHistoryAdapter` 的 `TYPE_EMPTY` 分支与 `item_search_history_empty.xml`：
  HomeFragment 不会触发它，但 adapter 自身保持健壮
  （未来其他调用方可能传空 list）
- 保留 `showHistoryPopup` 内的 `refreshHistoryCache()`：
  是单一真源，即使外面刚 refresh 过也再读一次

## 六、影响范围

| 文件 | 变更类型 | 行数估计 |
|---|---|---|
| `app/src/main/res/layout/fragment_home.xml` | 修改 | +15 / 改 2 |
| `app/src/main/java/person/notfresh/readingshare/ui/home/HomeFragment.java` | 修改 | +30 / 改 15 |
| `app/src/main/res/values/strings.xml` | 修改 | +1 |
| `app/src/main/res/drawable/ic_clear.xml` | 新建 | ~8 |

## 七、测试

- `SearchHistoryLogicTest`：不动（纯逻辑未变）
- 单元测试：不加新 JUnit（`isEmpty()` 守门和 `updateInputChrome()` 行为是 1-2 行的 if，可读性已足够）
- Manual smoke（设备/模拟器，附加于既有 11 项清单）：
  - 输入框初始空 + 有历史 → focus 弹下拉
  - 输入框空 + 无历史（先删除所有历史）→ focus 不弹
  - 输入文字 → × 出现，下拉消失
  - 点 × → 文本清空，焦点保持，有历史时下拉弹出
  - 弹出的下拉里点删除最后一历史 → 下拉立即关闭
  - 输入文字后 backspace 全清 → 若有历史，下拉弹出
