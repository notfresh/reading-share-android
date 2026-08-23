# WebView 悬浮上下页按钮(设计稿)

**日期**: 2026-08-23
**范围**: `WebViewActivity` 单 Activity + 设置项 UI
**目的**: 在保留现有底部"上一篇/下一篇"横排的基础上,新增一组右侧悬浮、上下排列、半透明的圆形箭头按钮;并通过设置项让用户选择底部横排 / 悬浮 / 都显示。

---

## 一、背景

`WebViewActivity` 当前在 `webview_container` 内、贴近底部有一组半透明横排控件(`navigation_controls`,包含"上一篇""下一篇"两个 `Button`),仅在"丝滑阅读模式"(`reading_mode = "smooth"`)下显示,默认 3 秒后自动隐藏,触摸 WebView 重新唤出,菜单中的"显示/隐藏导航控件"项可手动隐藏并持久化。

用户希望再增加一组悬浮、上下排列、半透明的圆形箭头按钮,逻辑复用现有的 `navigateToPrevious()` / `navigateToNext()`,并由用户在设置中决定启用哪一种组合。

---

## 二、设计目标

1. **最小侵入**:不修改现有 `navigationControls` 的渲染与逻辑,只在它旁边加一组对称的悬浮控件。
2. **逻辑复用**:`navigateToPrevious()`、`navigateToNext()`、`hasValidNavigationContext()`、`isSmoothReadingMode()`、`showControlsTemporarily()`、`attachControlRevealTouchListener()` 等所有现有方法保持不变,只在新代码里**调用**它们。
3. **可配置**:设置项新增"翻页按钮显示方式",三选一(底部横排 / 右侧悬浮 / 都显示),默认"都显示"。
4. **可逆性**:不破坏现有用户的操作习惯;若设置项选为"底部横排",行为与当前完全一致。

---

## 三、UI 设计

### 3.1 悬浮按钮(`activity_webview.xml` 新增节点)

在 `webview_container` 这个 `FrameLayout` 内、**现有 `@+id/navigation_controls` 节点之后**新增一个同级 `FrameLayout`,id `@+id/floating_nav_controls`,结构如下:

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

设计要点:
- `layout_gravity="right|center_vertical"` — 屏幕右侧、垂直居中。
- `48dp` × `48dp` — 满足 Material Design 触控最小尺寸。
- `12dp` 间距 + 圆形背景 — 视觉上是一对独立按钮,与底部横排风格呼应但形状不同。
- `clickable="false"` + `focusable="false"` 在外层 FrameLayout 上 — 让 WebView 仍能接收触摸事件(沿用现有 `attachControlRevealTouchListener` 行为)。
- 复用现有矢量图标 `ic_expand_less`(上箭头)与 `ic_expand_more`(下箭头)。

### 3.2 圆形半透明背景(`drawable/floating_arrow_bg.xml`,新文件)

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#80000000" />
</shape>
```

与现有底部横排的 `#66000000` 略有差异(0x80 vs 0x66),悬浮按钮稍亮一些,因面积小需要更高对比度。

### 3.3 设置项 UI(`fragment_slideshow.xml` 新增节点)

在现有"阅读模式" RadioGroup 区块之后、"外部链接打开模式"区块之前插入:

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

---

## 四、行为设计

### 4.1 设置项(`SettingFragment.java` 改动)

- key:`"nav_button_style"`,值 `"bottom"` / `"floating"` / `"both"`,默认 `"both"`。
- 存储位置:`SharedPreferences("settings")`(与现有 `reading_mode`、`external_link_mode` 一致)。
- 加载/监听代码结构沿用现有"外部链接打开模式"区块(SettingFragment.java 第 246-270 行),只替换控件 id 与 key。
- 选中后**立即持久化**,但生效时机为**下次进入 WebViewActivity**(不重启当前页面、不广播事件)。

### 4.2 WebViewActivity 可见性规则

新增两个私有方法,集中表达"按 `nav_button_style` 过滤":

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

现有方法体的改动如下(均为**只增加可见性同步**,不改变原有判定逻辑):

| 方法 | 改动 |
|---|---|
| `setupNavigationControls()` | 在现有初始化 `navigationControls` 之后,新增 `floatingNavControls = findViewById(R.id.floating_nav_controls)`,并把 `button_floating_previous` / `button_floating_next` 绑定到 `navigateToPrevious()` / `navigateToNext()`;两控件初始 `View.GONE` |
| `initControlsIfNeeded()` | 在每个 `navigationControls.setVisibility(...)` 处,镜像对 `floatingNavControls` 做相同操作,但用 `shouldShowBottomNav()` / `shouldShowFloatingNav()` 过滤;**不显示的那组永远 GONE** |
| `hideControlsRunnable` | 把 `navigationControls.setVisibility(GONE)` 同步成"两控件同时设 GONE"(仍按上述过滤) |
| `showControlsTemporarily()` | 把 `navigationControls.setVisibility(VISIBLE)` 同步成"两控件同时设 VISIBLE"(仍按上述过滤),共享同一个 `controlsHandler.postDelayed` |
| `toggleNavigationControls()` | **不变**(按用户决定,手动隐藏只控制底部横排) |
| `attachControlRevealTouchListener()` | **不变**(`showControlsTemporarily()` 已包含悬浮) |

### 4.3 点击与导航逻辑

`button_floating_previous` 与现有 `button_previous` **共用** `navigateToPrevious()`;`button_floating_next` 与 `button_next` 共用 `navigateToNext()`。无任何新增导航逻辑。

### 4.4 与现有"手动隐藏"互操作

- `KEY_NAV_CONTROLS_HIDDEN` 只控制底部横排,与悬浮无关。
- 设置项为 `BOTTOM` 时,悬浮按钮永久 `GONE`;菜单项 `action_toggle_navigation` 仍可隐藏底部横排。
- 设置项为 `FLOATING` 时,底部横排永久 `GONE`;菜单项 `action_toggle_navigation` 隐藏的对象此时不可见,该操作变成 no-op(无副作用)。
- 设置项为 `BOTH`(默认)时,行为与新增前类似,但额外多出一组悬浮按钮。

---

## 五、错误与边界处理

- `context_ids` 为空或单元素:`hasValidNavigationContext()` 返回 false,两组控件均为 `GONE`,沿用现有行为。
- 用户中途切换设置项:**不立即生效**;下次进入 `WebViewActivity` 生效。
- `SharedPreferences` 读取异常:回退到默认值 `"both"`,与现有 `reading_mode` 的回退方式一致(`try/catch` 包住,默认 `"normal"`)。

---

## 六、文件改动清单

| 文件 | 类型 | 说明 |
|---|---|---|
| `app/src/main/res/layout/activity_webview.xml` | 改 | 在 `webview_container` 内新增 `floating_nav_controls` 节点(30 行) |
| `app/src/main/res/drawable/floating_arrow_bg.xml` | 新增 | 圆形半透明背景 drawable(7 行) |
| `app/src/main/res/layout/fragment_slideshow.xml` | 改 | 在"阅读模式"区块后插入"翻页按钮显示方式" TextView + RadioGroup(22 行) |
| `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java` | 改 | 新增 2 个字段、2 个工具方法、若干可见性同步语句(约 50 行净增) |
| `app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java` | 改 | 在"阅读模式"代码块后新增"翻页按钮显示方式"加载/监听段(约 25 行) |

不修改:`navigationControls` 既有逻辑、`WebShortcutActivity`、`MainActivity`、`AndroidManifest.xml`、任何 build 文件。

---

## 七、测试要点

1. **默认行为(BOTH)**:冷启动带 `context_ids` 的 WebViewActivity → 看到底部横排与右侧悬浮两组;触摸 WebView → 两组同步出现,3 秒后同步消失;切到下一个链接 → 两组同步出现。
2. **设置项 BOTTOM**:设置后冷启动 → 只有底部横排,右侧不显示;触摸 WebView → 只底部横排唤出;菜单"显示/隐藏导航控件"仍能控制底部横排。
3. **设置项 FLOATING**:设置后冷启动 → 只有右侧悬浮;底部横排始终 GONE;菜单"显示/隐藏导航控件"操作无副作用(对象不可见)。
4. **手动隐藏**:菜单"显示/隐藏导航控件"开启 → 底部横排消失,右侧悬浮仍按原规则出现/消失(BOTH 模式下)。
5. **空上下文**:冷启动 `WebViewActivity` 时 `context_ids` 为空 → 两组都 GONE。
6. **横排按钮点击**:点击"上一篇" → 导航到上一条,悬浮上箭头仍能再次触发(无 isNavigating 误锁)。
7. **悬浮按钮点击**:点击悬浮上箭头 → 同样导航到上一条。

---

## 八、不做的事

- 不修改 `navigateToPrevious()` / `navigateToNext()` 的内部逻辑。
- 不改变 `isSmoothReadingMode()` 的语义。
- 不新增 Activity / Fragment / Service。
- 不修改 `KEY_NAV_CONTROLS_HIDDEN` 的语义。
- 不抽成独立可复用自定义 ViewGroup(后续如需,可作为单独的重构任务)。