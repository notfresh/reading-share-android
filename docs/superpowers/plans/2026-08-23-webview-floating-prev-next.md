# WebView 悬浮上下页按钮 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `WebViewActivity` 中新增右侧悬浮、上下排列的"上一篇/下一篇"圆形按钮;在设置页新增"翻页按钮显示方式"三选一选项(底部横排 / 右侧悬浮 / 都显示,默认"都显示");逻辑完全复用现有的 `navigateToPrevious()` / `navigateToNext()` 与可见性管理。

**Architecture:** 在 `activity_webview.xml` 的 `webview_container` 内追加一个 `FrameLayout`,内含两个垂直排列的 `ImageButton`。`WebViewActivity` 把这两个按钮绑定到现有导航方法,并在所有现有的可见性切换点(`setupNavigationControls` / `initControlsIfNeeded` / `showControlsTemporarily` / `hideControlsRunnable`)做镜像同步,通过新增的两个私有方法 `shouldShowBottomNav()` / `shouldShowFloatingNav()` 按 `SharedPreferences("settings")` 中的 `nav_button_style` 过滤可见性。设置项 UI 沿用 `fragment_slideshow.xml` 中现有的 RadioGroup 三选项模式。

**Tech Stack:** Android Java AppCompat、`SharedPreferences`、AndroidX Fragment;沿用现有 `ic_expand_less` / `ic_expand_more` 矢量图与 NavigationControls 既有方法。

## Global Constraints

- 单 Activity + 单 Fragment 改动范围,不动 `WebShortcutActivity` / `MainActivity` / `AndroidManifest.xml` / build 文件。
- 保持与现有 `navigationControls` 完全独立:不重写既有逻辑,只追加镜像同步。
- 设置项默认值为 `"both"`(都显示)。
- `KEY_NAV_CONTROLS_HIDDEN` 仅控制底部横排,与悬浮无关。
- SharedPreferences name 沿用 `"settings"`(与其他偏好一致),key 为 `"nav_button_style"`。
- 不抽成自定义 ViewGroup(后续如需,可作为单独重构任务)。
- 中文文案遵循项目现有写法(全角括号 `（）`、顿号 `、`),与 `fragment_slideshow.xml` 既有区块保持一致。

---

## 文件改动一览

| 文件 | 类型 | 职责 |
|---|---|---|
| `app/src/main/res/drawable/floating_arrow_bg.xml` | 新增 | 圆形半透明背景 drawable |
| `app/src/main/res/layout/activity_webview.xml` | 改 | 在 `webview_container` 末尾新增 `@+id/floating_nav_controls` FrameLayout + 两个 ImageButton |
| `app/src/main/res/layout/fragment_slideshow.xml` | 改 | 在"阅读模式"区块后新增"翻页按钮显示方式" RadioGroup |
| `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java` | 改 | 字段、setup、可见性同步、点击绑定、两个工具方法 |
| `app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java` | 改 | 新增"翻页按钮显示方式"加载与监听段 |

---

### Task 1: 新增悬浮按钮圆形背景 drawable

**Files:**
- Create: `app/src/main/res/drawable/floating_arrow_bg.xml`

**Interfaces:**
- Consumes: 无
- Produces: `R.drawable.floating_arrow_bg`,圆形半透明背景(oval,`#80000000`)

- [ ] **Step 1: 创建 drawable 文件**

在 `app/src/main/res/drawable/floating_arrow_bg.xml` 写入:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#80000000" />
</shape>
```

- [ ] **Step 2: 校验文件存在且格式正确**

读取文件确认内容无误,且只有 `<shape>` 一层。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/drawable/floating_arrow_bg.xml
git commit -m "feat(webview): 新增悬浮按钮圆形半透明背景 drawable"
```

---

### Task 2: 在 activity_webview.xml 添加悬浮控件节点

**Files:**
- Modify: `app/src/main/res/layout/activity_webview.xml`(在 `</FrameLayout>` 闭合 `webview_container` 之前插入)

**Interfaces:**
- Consumes: `R.drawable.floating_arrow_bg`(Task 1)、`R.drawable.ic_expand_less`、`R.drawable.ic_expand_more`(项目既有)
- Produces: `R.id.floating_nav_controls`、`R.id.button_floating_previous`、`R.id.button_floating_next`

- [ ] **Step 1: 在 layout 文件中插入新节点**

打开 `app/src/main/res/layout/activity_webview.xml`,找到现有的 `<FrameLayout android:id="@+id/navigation_controls" ...>...</FrameLayout>` 块,**在它结束之后、`</FrameLayout>`(闭合 `webview_container`)之前**插入:

```xml
<FrameLayout
    android:id="@+id/floating_nav_controls"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:clickable="false"
    android:focusable="false"
    android:visibility="gone">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="right|center_vertical"
        android:orientation="vertical"
        android:layout_marginEnd="12dp"
        android:gravity="center">

        <ImageButton
            android:id="@+id/button_floating_previous"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:layout_marginBottom="12dp"
            android:background="@drawable/floating_arrow_bg"
            android:src="@drawable/ic_expand_less"
            android:contentDescription="上一篇"
            android:scaleType="centerInside" />

        <ImageButton
            android:id="@+id/button_floating_next"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="@drawable/floating_arrow_bg"
            android:src="@drawable/ic_expand_more"
            android:contentDescription="下一篇"
            android:scaleType="centerInside" />
    </LinearLayout>
</FrameLayout>
```

插入位置必须保证**仍在 `@+id/webview_container` 这个 FrameLayout 内部**,不要破坏外层 LinearLayout 结构。

- [ ] **Step 2: 校验结构**

读取修改后的文件,确认:
- 新节点位于 `@+id/navigation_controls` 之后、`</FrameLayout>`(闭合 `webview_container`)之前
- 三处 id 正确(`@+id/floating_nav_controls`、`@+id/button_floating_previous`、`@+id/button_floating_next`)
- 外层 `LinearLayout` 与 `Toolbar` 结构未受影响

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/activity_webview.xml
git commit -m "feat(webview): 新增悬浮上下页按钮节点"
```

---

### Task 3: WebViewActivity 新增字段、工具方法与初始化逻辑

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java`
  - 在字段声明区(line 80-86 附近)新增字段
  - 修改 `setupNavigationControls()`(line 989-1004),追加浮动控件初始化与按钮绑定
  - 新增 `shouldShowBottomNav()` 与 `shouldShowFloatingNav()` 两个方法

**Interfaces:**
- Consumes: Task 2 新增的 `R.id.floating_nav_controls`、`R.id.button_floating_previous`、`R.id.button_floating_next`;现有的 `navigateToPrevious()` / `navigateToNext()`
- Produces: 字段 `floatingNavControls`、`buttonFloatingPrevious`、`buttonFloatingNext`;方法 `shouldShowBottomNav()` 与 `shouldShowFloatingNav()`

- [ ] **Step 1: 新增字段**

在 WebViewActivity.java 第 80-86 行的字段声明区域(现有 `navigationControls`、`buttonPrevious`、`buttonNext` 等字段之后),新增:

```java
private FrameLayout floatingNavControls;
private ImageButton buttonFloatingPrevious;
private ImageButton buttonFloatingNext;
```

字段放置位置应紧邻现有 `navigationControls` 相关字段,保持阅读连贯。

并在文件顶部 import 区(已有 `import android.widget.FrameLayout;`、`import android.widget.Button;`)确认有 `import android.widget.ImageButton;`。如果没有,补上(按字母顺序)。

- [ ] **Step 2: 修改 setupNavigationControls()**

将现有方法(line 989-1004):

```java
private void setupNavigationControls() {
    try {
        navigationControls = findViewById(R.id.navigation_controls);
        if (navigationControls == null) return;
        buttonPrevious = navigationControls.findViewById(R.id.button_previous);
        buttonNext = navigationControls.findViewById(R.id.button_next);
        if (buttonPrevious != null) buttonPrevious.setOnClickListener(v -> navigateToPrevious());
        if (buttonNext != null) buttonNext.setOnClickListener(v -> navigateToNext());
        hideControlsRunnable = () -> runOnUiThread(() -> {
            if (navigationControls != null) navigationControls.setVisibility(View.GONE);
        });
        navigationControls.setVisibility(hasValidNavigationContext() ? View.VISIBLE : View.GONE);
    } catch (Exception e) {
        Log.w("WebViewActivity", "setupNavigationControls failed: " + e.getMessage());
    }
}
```

替换为:

```java
private void setupNavigationControls() {
    try {
        navigationControls = findViewById(R.id.navigation_controls);
        if (navigationControls != null) {
            buttonPrevious = navigationControls.findViewById(R.id.button_previous);
            buttonNext = navigationControls.findViewById(R.id.button_next);
            if (buttonPrevious != null) buttonPrevious.setOnClickListener(v -> navigateToPrevious());
            if (buttonNext != null) buttonNext.setOnClickListener(v -> navigateToNext());
            navigationControls.setVisibility(hasValidNavigationContext() ? View.VISIBLE : View.GONE);
        }

        floatingNavControls = findViewById(R.id.floating_nav_controls);
        if (floatingNavControls != null) {
            buttonFloatingPrevious = floatingNavControls.findViewById(R.id.button_floating_previous);
            buttonFloatingNext = floatingNavControls.findViewById(R.id.button_floating_next);
            if (buttonFloatingPrevious != null) buttonFloatingPrevious.setOnClickListener(v -> navigateToPrevious());
            if (buttonFloatingNext != null) buttonFloatingNext.setOnClickListener(v -> navigateToNext());
            floatingNavControls.setVisibility(hasValidNavigationContext() ? View.VISIBLE : View.GONE);
        }

        hideControlsRunnable = () -> runOnUiThread(() -> {
            if (navigationControls != null) navigationControls.setVisibility(View.GONE);
            if (floatingNavControls != null) floatingNavControls.setVisibility(View.GONE);
        });
    } catch (Exception e) {
        Log.w("WebViewActivity", "setupNavigationControls failed: " + e.getMessage());
    }
}
```

注意三处变化:
1. `navigationControls` 与 `floatingNavControls` 各自的 `if (... != null)` 包裹各自的初始化,**不**用 `return` 提前退出(Task 3 仅做字段与初始化,可见性同步由 Task 4 处理)。
2. `hideControlsRunnable` 同时隐藏两控件。
3. 初始 `setVisibility` 各做各的(沿用原语义)。

- [ ] **Step 3: 新增两个工具方法**

在 `isSmoothReadingMode()` 方法(line 1059-1067)之后新增:

```java
private boolean shouldShowBottomNav() {
    String style = getSharedPreferences("settings", MODE_PRIVATE)
            .getString("nav_button_style", "both");
    return "bottom".equals(style) || "both".equals(style);
}

private boolean shouldShowFloatingNav() {
    String style = getSharedPreferences("settings", MODE_PRIVATE)
            .getString("nav_button_style", "both");
    return "floating".equals(style) || "both".equals(style);
}
```

- [ ] **Step 4: 校验编译可行性**

由于任务约定"不执行编译",改完后**只读不改**:
- 确认 `FrameLayout`、`ImageButton`、`View` 等类已被 import(若 `FrameLayout` 既有,`ImageButton` 也需补)。
- 确认大括号匹配(`navigationControls` 与 `floatingNavControls` 各自的 `if` 块均独立)。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/person/notfresh/readingshare/WebViewActivity.java
git commit -m "refactor(webview): 拆分 setupNavigationControls 并新增浮动字段与可见性过滤方法"
```

---

### Task 4: WebViewActivity 同步可见性切换点

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java`
  - 修改 `initControlsIfNeeded()`(line 1006-1033)
  - 修改 `showControlsTemporarily()`(line 1035-1042)
  - `hideControlsRunnable` 已在 Task 3 中同步(本 Task 不再改)

**Interfaces:**
- Consumes: Task 3 的 `floatingNavControls` 字段、`shouldShowBottomNav()`、`shouldShowFloatingNav()`;现有 `controlsHandler`、`CONTROLS_AUTO_HIDE_MS`、`navigationControlsManuallyHidden`
- Produces: 修改后的可见性同步逻辑,使两控件根据设置项与丝滑模式共同进退

- [ ] **Step 1: 修改 initControlsIfNeeded()**

将现有方法(line 1006-1033):

```java
private void initControlsIfNeeded() {
    if (navigationControls == null) setupNavigationControls();
    if (!hasValidNavigationContext()) {
        if (navigationControls != null) {
            navigationControls.setVisibility(View.GONE);
        }
        return;
    }
    // 读取用户手动隐藏的状态
    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    navigationControlsManuallyHidden = prefs.getBoolean(KEY_NAV_CONTROLS_HIDDEN, false);

    boolean smoothMode = isSmoothReadingMode();
    if (navigationControls != null) {
        if (!smoothMode) {
            // 普通模式：完全不显示导航控件
            navigationControls.setVisibility(View.GONE);
        } else if (navigationControlsManuallyHidden) {
            // 丝滑模式但用户手动隐藏了，保持隐藏
            navigationControls.setVisibility(View.GONE);
        } else {
            // 丝滑模式：默认显示，3秒后自动隐藏
            navigationControls.setVisibility(View.VISIBLE);
            controlsHandler.removeCallbacks(hideControlsRunnable);
            controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS);
        }
    }
}
```

替换为:

```java
private void initControlsIfNeeded() {
    if (navigationControls == null) setupNavigationControls();
    if (!hasValidNavigationContext()) {
        if (navigationControls != null) navigationControls.setVisibility(View.GONE);
        if (floatingNavControls != null) floatingNavControls.setVisibility(View.GONE);
        return;
    }
    // 读取用户手动隐藏的状态
    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    navigationControlsManuallyHidden = prefs.getBoolean(KEY_NAV_CONTROLS_HIDDEN, false);

    boolean smoothMode = isSmoothReadingMode();
    boolean showBottom = shouldShowBottomNav();
    boolean showFloating = shouldShowFloatingNav();

    if (navigationControls != null && !showBottom) {
        navigationControls.setVisibility(View.GONE);
    }
    if (floatingNavControls != null && !showFloating) {
        floatingNavControls.setVisibility(View.GONE);
    }
    if (!smoothMode) {
        // 普通模式：完全不显示导航控件
        if (navigationControls != null) navigationControls.setVisibility(View.GONE);
        if (floatingNavControls != null) floatingNavControls.setVisibility(View.GONE);
        return;
    }
    if (navigationControlsManuallyHidden) {
        // 丝滑模式但用户手动隐藏了底部横排，悬浮按原规则显示
        if (navigationControls != null) navigationControls.setVisibility(View.GONE);
        if (showFloating && floatingNavControls != null) {
            floatingNavControls.setVisibility(View.VISIBLE);
            controlsHandler.removeCallbacks(hideControlsRunnable);
            controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS);
        }
        return;
    }
    // 丝滑模式且未手动隐藏：底部横排与悬浮按设置项显示，3秒后自动隐藏
    if (showBottom && navigationControls != null) {
        navigationControls.setVisibility(View.VISIBLE);
    }
    if (showFloating && floatingNavControls != null) {
        floatingNavControls.setVisibility(View.VISIBLE);
    }
    controlsHandler.removeCallbacks(hideControlsRunnable);
    controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS);
}
```

关键改动:
- 任何分支下,被设置项排除的那组(`!showBottom` 或 `!showFloating`)始终 `GONE`,**永不被自动隐藏 Runnable 影响**。
- `navigationControlsManuallyHidden` 只影响底部横排;悬浮仍按规则进退。
- 自动隐藏 Handler 只在至少有**一组**将要显示时才 `postDelayed`(满足条件的分支末尾统一 post 一次)。

- [ ] **Step 2: 修改 showControlsTemporarily()**

将现有方法(line 1035-1042):

```java
private void showControlsTemporarily() {
    if (navigationControls == null || !hasValidNavigationContext()) return;
    // 如果用户手动隐藏了，不再自动显示
    if (navigationControlsManuallyHidden) return;
    navigationControls.setVisibility(View.VISIBLE);
    controlsHandler.removeCallbacks(hideControlsRunnable);
    controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS);
}
```

替换为:

```java
private void showControlsTemporarily() {
    if (!hasValidNavigationContext()) return;
    if (navigationControlsManuallyHidden) return;
    boolean anyShown = false;
    if (shouldShowBottomNav() && navigationControls != null) {
        navigationControls.setVisibility(View.VISIBLE);
        anyShown = true;
    }
    if (shouldShowFloatingNav() && floatingNavControls != null) {
        floatingNavControls.setVisibility(View.VISIBLE);
        anyShown = true;
    }
    if (anyShown) {
        controlsHandler.removeCallbacks(hideControlsRunnable);
        controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS);
    }
}
```

注意:`navigationControls == null` 这一早期 return 条件被移除,改为对每组分别判空,确保 `floatingNavControls` 不会被漏掉。

- [ ] **Step 3: 校验**

读取修改后的两个方法,确认:
- 所有 `setVisibility` 调用均包在非空判断中。
- `controlsHandler.postDelayed` 仅在 `anyShown == true` 时调用,避免空 Runnable 残留。
- `toggleNavigationControls()` 与 `attachControlRevealTouchListener()` 未被改动。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/person/notfresh/readingshare/WebViewActivity.java
git commit -m "feat(webview): 同步悬浮控件与底部横排的可见性切换"
```

---

### Task 5: 设置页 layout 新增"翻页按钮显示方式"区块

**Files:**
- Modify: `app/src/main/res/layout/fragment_slideshow.xml`(在"阅读模式" RadioGroup 结束之后、"外部链接打开模式" TextView 之前插入)

**Interfaces:**
- Consumes: 无
- Produces: `R.id.nav_button_style_group`、`R.id.nav_button_style_bottom`、`R.id.nav_button_style_floating`、`R.id.nav_button_style_both`

- [ ] **Step 1: 定位插入点**

打开 `app/src/main/res/layout/fragment_slideshow.xml`,找到 `</RadioGroup>` 闭合"阅读模式"区块的那一行(line 143 附近),以及"外部链接打开模式"区块开头的 `<TextView>` 那一行(line 145-151)。

- [ ] **Step 2: 在两者之间插入新节点**

在 `</RadioGroup>`(阅读模式)之后、`<TextView android:text="外部链接打开模式" .../>` 之前,插入:

```xml
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="翻页按钮显示方式"
    android:textSize="18sp"
    android:layout_marginTop="24dp"
    android:layout_marginBottom="8dp" />

<RadioGroup
    android:id="@+id/nav_button_style_group"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:layout_marginBottom="8dp">

    <RadioButton
        android:id="@+id/nav_button_style_bottom"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="底部横排（仅上一篇/下一篇）" />

    <RadioButton
        android:id="@+id/nav_button_style_floating"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="右侧悬浮（仅上下箭头）" />

    <RadioButton
        android:id="@+id/nav_button_style_both"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="都显示（默认）" />

</RadioGroup>
```

注意三处属性顺序一致:每个 RadioButton 均为 `id` → `layout_width` → `layout_height` → `text`(与同文件中 `nav_button_style_bottom` 等其他 RadioButton 风格对齐)。

- [ ] **Step 3: 校验结构**

读取修改后的文件,确认:
- 新区块紧邻"阅读模式"区块之后,没有被插入到不相关的位置。
- 所有 RadioButton 的 `id` 唯一。
- 没有破坏外层 `ScrollView` 或 `LinearLayout` 结构。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/fragment_slideshow.xml
git commit -m "feat(settings): 新增翻页按钮显示方式设置项"
```

---

### Task 6: SettingFragment 加载与监听"翻页按钮显示方式"

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java`
  - 在"阅读模式"代码块(line 223-243)结束后、"外部链接打开模式"代码块开始(line 245-270)之前新增代码段

**Interfaces:**
- Consumes: Task 5 新增的 `R.id.nav_button_style_group`、`R.id.nav_button_style_bottom`、`R.id.nav_button_style_floating`、`R.id.nav_button_style_both`;现有 `globalPrefs`(命名 SharedPreferences)
- Produces: SharedPreferences 中持久化的 `nav_button_style` 值,被 `WebViewActivity.shouldShowBottomNav()` / `shouldShowFloatingNav()` 读取

- [ ] **Step 1: 定位插入点**

打开 `app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java`,找到现有"阅读模式"代码块结尾(约 line 243 的 `});` 之后)与"外部链接打开模式"代码块开头(约 line 246 的 `RadioGroup externalLinkModeGroup = ...` 之前)。

- [ ] **Step 2: 插入新代码段**

在两者之间插入:

```java
// 翻页按钮显示方式设置（bottom / floating / both）
RadioGroup navButtonStyleGroup = root.findViewById(R.id.nav_button_style_group);
RadioButton bottomRb = root.findViewById(R.id.nav_button_style_bottom);
RadioButton floatingRb = root.findViewById(R.id.nav_button_style_floating);
RadioButton bothRb = root.findViewById(R.id.nav_button_style_both);

String navButtonStyle = globalPrefs.getString("nav_button_style", "both");
if ("bottom".equals(navButtonStyle)) {
    bottomRb.setChecked(true);
} else if ("floating".equals(navButtonStyle)) {
    floatingRb.setChecked(true);
} else {
    bothRb.setChecked(true);
}

navButtonStyleGroup.setOnCheckedChangeListener((group, checkedId) -> {
    SharedPreferences.Editor editor = globalPrefs.edit();
    if (checkedId == R.id.nav_button_style_bottom) {
        editor.putString("nav_button_style", "bottom");
    } else if (checkedId == R.id.nav_button_style_floating) {
        editor.putString("nav_button_style", "floating");
    } else {
        editor.putString("nav_button_style", "both");
    }
    editor.apply();
});
```

风格完全沿用同文件中现有"阅读模式"与"外部链接打开模式"两段写法(`findViewById` → `setChecked` → `setOnCheckedChangeListener` → `editor.putXxx.apply()`)。

- [ ] **Step 3: 校验**

读取修改后的文件,确认:
- `globalPrefs` 已在该作用域内可用(现有 line 170 已声明)。
- 新代码段不依赖任何新 import;现有 `RadioGroup` / `RadioButton` / `SharedPreferences` 均已 import。
- 没有破坏现有 `onCreateView` 的返回路径。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java
git commit -m "feat(settings): 实现翻页按钮显示方式加载与监听"
```

---

### Task 7: 手工回归检查

**Files:**
- 无

**Interfaces:**
- Consumes: 所有前置任务产物
- Produces: 验证报告

- [ ] **Step 1: 检查最终 WebViewActivity 字段与 import**

读取 `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java`,确认:
- 字段 `floatingNavControls` / `buttonFloatingPrevious` / `buttonFloatingNext` 已声明。
- import 包含 `android.widget.ImageButton`(若原本没有)。
- import 包含 `android.widget.FrameLayout`(已存在)。

- [ ] **Step 2: 检查设置项 key 拼写一致**

分别 grep 三个文件,确认 `"nav_button_style"` 字面量完全一致:

```bash
grep -rn "nav_button_style" app/src/main/java app/src/main/res
```

预期输出包含 `WebViewActivity.java`、`SettingFragment.java`、3 个 `R.id.nav_button_style_*` 引用,**仅 5 处**,无拼写变体。

- [ ] **Step 3: 检查现有手动隐藏行为未变**

读取 `WebViewActivity.toggleNavigationControls()`(line 1044 附近),确认:
- 该方法体内不引用 `floatingNavControls`。
- 仅修改 `navigationControls` 与 `navigationControlsManuallyHidden`。

- [ ] **Step 4: 检查现有"丝滑模式"语义未变**

读取 `WebViewActivity.isSmoothReadingMode()`(line 1059-1067),确认:
- 返回值仍是 `reading_mode == "smooth"` 的 boolean。
- 没有被 Task 3/4 误改。

- [ ] **Step 5: 标记完成**

```bash
git status
```

预期:`working tree clean`(无未提交修改);若仍残留改动,说明前面某 Task 漏提交,需回头补上。

---

### Task 8: 文档同步检查

**Files:**
- 无(仅核对既有文档是否需要更新)

**Interfaces:**
- Consumes: 已提交的代码改动
- Produces: 决定是否需要更新既有文档

- [ ] **Step 1: 检查现有 WebViewActivity 文档**

读取 `dev-docs/WebViewActivity返回按钮处理技术总结.md`,判断是否需要更新:
- 该文档主要描述"返回按钮处理",与本任务的"翻页按钮"无关。
- 文档无需更新,跳过。

- [ ] **Step 2: 检查 AGENTS.md / README 是否需要更新**

```bash
ls AGENTS.md README.md 2>/dev/null
```

若存在,快速 grep 是否提及"翻页""上一篇""下一篇"。本任务未触及项目级约定,**预期无需更新**。如有则更新对应描述。

- [ ] **Step 3: 完成**

本 Task 不产生代码 commit,仅记录"已检查且无需更新"。