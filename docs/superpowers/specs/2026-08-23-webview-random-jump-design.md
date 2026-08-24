# WebView 悬浮随机跳转按钮(设计稿)

**日期**: 2026-08-23
**范围**: `WebViewActivity` 单 Activity + 1 个 drawable + 1 个 layout 节点
**目的**: 在 WebViewActivity 现有悬浮上下页按钮组之下,新增一个"随机跳转"圆形按钮,点击后从当前 `context_ids` 数组中随机选一个不等于当前 `currentIndex` 的条目跳转。逻辑复用现有 `loadByContextIndex`,可见性自动继承 `floatingNavControls` 容器。

---

## 一、背景

`WebViewActivity` 已有底部"上一篇/下一篇"横排 + 右侧悬浮上下箭头(2026-08-23 完成)。用户希望再增加一个**悬浮随机跳转按钮**,从当前上下文中随机选一条,方便在阅读时"换换口味"。

设计约束(用户答复):
1. 随机范围:**当前 `context_ids` 内随机,不包含当前条目**
2. 按钮位置:**悬浮上下组中加一个,放在下方,距离稍远,图标独立**
3. 可见性:**跟随悬浮上下箭头一起显示(丝滑模式 + 可设置)**
4. 防误触:**不加,随手一点就跳**

---

## 二、设计目标

1. **最小侵入**:不动现有 `initControlsIfNeeded` / `showControlsTemporarily` / `hideControlsRunnable` / `toggleNavigationControls` / `setupNavigationControls` 已有逻辑。
2. **逻辑复用**:随机跳转完全复用 `loadByContextIndex(int index)`,自带 `isNavigating` 守护、DAO 查询、Toast 错误处理。
3. **零新增设置项**:随机按钮属于悬浮组,沿用 `nav_button_style`(BOTTOM 时自动隐藏,FLOATING / BOTH 时跟随悬浮组)。
4. **零新增可见性同步**:随机按钮作为 `floatingNav_controls` 容器的子节点,容器的 VISIBLE / GONE 已经覆盖了它。

---

## 三、UI 设计

### 3.1 随机按钮图标(`drawable/ic_random.xml`,新文件)

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
 <path android:fillColor="@android:color/white"
      android:pathData="M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 20,9.5V4h-5.5zM14.83,13.41l-1.41,1.41 3.13,3.13L14.5,20H20v-5.5l-2.04,2.04 -3.13,-3.13z"/>
</vector>
```

- 24dp viewport,白色 path,与现有 `ic_expand_less` / `ic_expand_more` 风格一致(`fillColor="@android:color/white"`)
- pathData 是 Material Icons 的"shuffle"经典形状:两个交叉箭头,直观表达"随机/洗牌"

### 3.2 悬浮按钮节点(`activity_webview.xml` 新增节点)

在 `floating_nav_controls` FrameLayout 内、已有 `LinearLayout`(上下箭头组)结束之后,新增一个**独立**的 `ImageButton`,**不**放入现有 LinearLayout:

```xml
<ImageButton
    android:id="@+id/button_floating_random"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:layout_gravity="right|center_vertical"
    android:layout_marginTop="24dp"
    android:layout_marginEnd="12dp"
    android:background="@drawable/floating_arrow_bg"
    android:src="@drawable/ic_random"
    android:contentDescription="随机跳转"
    android:scaleType="centerInside" />
```

设计要点:
- `layout_gravity="right|center_vertical"`:与上下箭头同列(屏幕右侧中部)
- `layout_marginEnd="12dp"`:与上下箭头对齐
- `layout_marginTop="24dp"`:与上下组间隔 24dp(比上下组内 12dp 大一倍,凸显独立)
- 复用现有 `floating_arrow_bg` 背景与新 `ic_random` 图标
- 节点位于 `floating_nav_controls` FrameLayout 内,可见性自动继承容器

---

## 四、行为设计

### 4.1 字段与初始化

**WebViewActivity.java 字段声明**(line 84-86 附近,紧邻现有 `buttonFloatingNext` 之后):

```java
private ImageButton buttonFloatingRandom;
private java.util.Random random = new java.util.Random();
```

### 4.2 按钮绑定(`setupNavigationControls()` 改动)

在现有 `if (floatingNavControls != null) { ... }` 块尾部、`buttonFloatingNext.setOnClickListener(...)` 之后追加:

```java
buttonFloatingRandom = floatingNavControls.findViewById(R.id.button_floating_random);
if (buttonFloatingRandom != null) buttonFloatingRandom.setOnClickListener(v -> navigateToRandom());
```

### 4.3 新方法 `navigateToRandom()`

在 `navigateToPrevious()` 与 `navigateToNext()` 之间新增(line 1100 附近):

```java
private void navigateToRandom() {
    if (!hasValidNavigationContext() || isNavigating) return;
    int length = contextIds.length;
    if (length <= 1) {
        // 只有一条或没有,无法随机
        Toast.makeText(this, "无可随机条目", Toast.LENGTH_SHORT).show();
        return;
    }
    int targetIndex;
    do {
        targetIndex = random.nextInt(length);
    } while (targetIndex == currentIndex);
    loadByContextIndex(targetIndex);
}
```

要点:
- `length <= 1` 时给一个轻量 Toast("无可随机条目"),不响应点击——比静默失败更友好
- 用 `do-while` 循环重抽,长度 ≥ 2 时**必然**找到不等于 `currentIndex` 的 index,不会死循环
- 完全复用 `loadByContextIndex(int)`,自带 `isNavigating` 守护与 Toast 错误处理
- `isNavigating` 守卫避免快速连点触发并发跳转

### 4.4 可见性(零新增同步逻辑)

由于随机按钮是 `floating_nav_controls` FrameLayout 的子节点,**完全继承**容器可见性,无需新增任何镜像同步代码:

| 场景 | 容器状态 | 随机按钮状态 |
|---|---|---|
| `nav_button_style = "bottom"` | GONE | 隐藏(自动) |
| `nav_button_style = "floating"` 或 `"both"` + 普通模式 | GONE | 隐藏(自动) |
| `nav_button_style = "floating"` 或 `"both"` + 丝滑模式 | VISIBLE | 可见(自动) |
| 触摸 WebView 唤出 | VISIBLE | 可见(自动) |
| 3 秒自动隐藏 | GONE | 隐藏(自动) |
| 菜单"显示/隐藏导航控件"(FLOATING 守卫) | 不动 | 不动 |
| 没有 `context_ids` | GONE | 隐藏(自动) |

**关键简化**:`initControlsIfNeeded()` / `showControlsTemporarily()` / `hideControlsRunnable` / `toggleNavigationControls()` 一行不改。

---

## 五、错误与边界处理

- **同一条重复点**:不会发生(`do-while` 排除 `currentIndex`)
- **快速连点**:`isNavigating` 守卫已存在,第二次点击会被 `loadByContextIndex` 内部的 `if (isNavigating) return` 跳过
- **`contextIds.length == 1`**:Toast "无可随机条目",不跳
- **`contextIds == null`**:被 `hasValidNavigationContext()` 拦下,不响应
- **DAO 查询失败**:复用 `loadByContextIndex` 现有 Toast "无法加载下一页"
- **资源未加载完就点击**:`navigateToRandom` 不依赖资源状态,任何时刻点击都按上述规则处理

---

## 六、文件改动清单

| 文件 | 类型 | 说明 |
|---|---|---|
| `app/src/main/res/drawable/ic_random.xml` | 新增 | shuffle 矢量图标(white) |
| `app/src/main/res/layout/activity_webview.xml` | 改 | 在 `floating_nav_controls` 内新增独立 ImageButton(8 行) |
| `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java` | 改 | 2 字段 + setup 绑定 2 行 + 新方法 14 行(净增 ~18 行) |

不修改:`navigationControls` 既有逻辑、`initControlsIfNeeded` / `showControlsTemporarily` / `hideControlsRunnable` / `toggleNavigationControls`、`setupNavigationControls` 既有流程、`fragment_slideshow.xml`、`SettingFragment.java`、任何 build / manifest 文件。

---

## 七、测试要点

1. **默认 BOTH 模式**:从首页列表点一条上下文 ≥ 2 条的链接 → 看到悬浮上下箭头 + 随机按钮;触摸 WebView 三者同步出现;点随机 → 跳到另一条(不是当前);3 秒后三者同步消失
2. **FLOATING 模式**:设置后冷启动 → 看到悬浮组(含随机);底部横排 GONE;点随机仍生效
3. **BOTTOM 模式**:设置后冷启动 → 底部横排可见,悬浮组(含随机)GONE
4. **上下文只有 1 条**:点随机 → Toast "无可随机条目",不跳
5. **没有上下文**(从桌面快捷方式 / 分享进入):悬浮组整体 GONE,随机按钮不可见
6. **快速连点**:在跳转未完成时再点随机 → 被 `isNavigating` 守卫拦下,不并发跳转
7. **DAO 查询失败**:复用 `loadByContextIndex` 现有 Toast "无法加载下一页"

---

## 八、不做的事

- 不修改 `navigateToPrevious()` / `navigateToNext()` / `loadByContextIndex()` 内部逻辑
- 不新增设置项 UI / SharedPreferences key
- 不修改 `initControlsIfNeeded` / `showControlsTemporarily` / `hideControlsRunnable` / `toggleNavigationControls`
- 不抽成独立可复用 ViewGroup
- 不引入新依赖
- 不改 `versionCode` / `versionName`
- 不改 `WebShortcutActivity` / `MainActivity` / `AndroidManifest.xml`
- 不在 RANDOM 算法上做花活(权重随机 / 避开最近 N 条 / 收藏夹优先等)— 当前需求是"随机一个"