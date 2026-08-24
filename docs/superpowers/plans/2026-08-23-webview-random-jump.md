# WebView 悬浮随机跳转按钮 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 WebViewActivity 现有悬浮上下页按钮组之下,新增一个独立的"随机跳转"圆形按钮,点击后从当前 `context_ids` 中随机选一个不等于 `currentIndex` 的条目跳转。

**Architecture:** 新增一个矢量 drawable `ic_random.xml`(shuffle 风格);在 `activity_webview.xml` 的 `floating_nav_controls` FrameLayout 内、已有 LinearLayout 结束之后,新增一个独立的 `ImageButton`(marginTop=24dp 形成视觉留白);WebViewActivity 新增字段 `buttonFloatingRandom` 与 `random`、在 `setupNavigationControls()` 中绑定点击、新增 `navigateToRandom()` 方法,内部用 `do-while` 重抽随机 index 并复用 `loadByContextIndex(int)`。**完全不动现有可见性切换逻辑**——随机按钮是 `floating_nav_controls` 容器的子节点,容器的 VISIBLE/GONE 自动覆盖。

**Tech Stack:** Android Java AppCompat、`java.util.Random`;沿用现有 `loadByContextIndex` / `isNavigating` 守护 / `Toast` 错误处理。

## Global Constraints

- 仅动 3 个文件:`drawable/ic_random.xml`(新)、`layout/activity_webview.xml`、`WebViewActivity.java`。
- **不动**:`initControlsIfNeeded` / `showControlsTemporarily` / `hideControlsRunnable` / `toggleNavigationControls` / `navigateToPrevious` / `navigateToNext` / `loadByContextIndex` / `isSmoothReadingMode` / `attachControlRevealTouchListener` / `setupNavigationControls` 既有初始化流程。
- 不新增设置项 UI / SharedPreferences key(随机按钮属于悬浮组,沿用 `nav_button_style`)。
- 不抽成独立 ViewGroup / Fragment。
- 项目 AGENTS.md 明确禁止"随便执行编译",本计划所有 Task 均不执行 gradle 编译。
- 中文文案遵循项目风格(全角括号 `（）`),与现有 Toast 文案("无法加载下一页")对齐。
- 命名:`buttonFloatingRandom`(camelCase)、`R.id.button_floating_random`(snake_case)、`@drawable/ic_random`。

---

## 文件改动一览

| 文件 | 类型 | 职责 |
|---|---|---|
| `app/src/main/res/drawable/ic_random.xml` | 新增 | shuffle 矢量图标(white,24dp viewport) |
| `app/src/main/res/layout/activity_webview.xml` | 改 | 在 `floating_nav_controls` 内、已有 LinearLayout 之后,新增独立 ImageButton |
| `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java` | 改 | 字段、setup 绑定、新方法 `navigateToRandom()` |

---

### Task 1: 新增随机按钮矢量图标

**Files:**
- Create: `app/src/main/res/drawable/ic_random.xml`

**Interfaces:**
- Consumes: 无
- Produces: `R.drawable.ic_random`,24dp viewport 白色 shuffle 图标

- [ ] **Step 1: 创建 drawable 文件**

在 `app/src/main/res/drawable/ic_random.xml` 写入:

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

- [ ] **Step 2: 校验文件**

读取文件确认内容无误,只有 `<vector>` + 一个 `<path>` 节点。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/drawable/ic_random.xml
git commit -m "feat(webview): 新增随机按钮 shuffle 矢量图标"
```

---

### Task 2: 在 activity_webview.xml 添加随机按钮节点

**Files:**
- Modify: `app/src/main/res/layout/activity_webview.xml`(在 `floating_nav_controls` FrameLayout 内、已有上下箭头 LinearLayout 结束之后插入)

**Interfaces:**
- Consumes: Task 1 新增的 `R.drawable.ic_random`、既有 `R.drawable.floating_arrow_bg`
- Produces: `R.id.button_floating_random`

- [ ] **Step 1: 定位插入点**

打开 `app/src/main/res/layout/activity_webview.xml`,找到 `floating_nav_controls` FrameLayout 内的 LinearLayout(上下箭头组,line 91-117 附近),确认其 `</LinearLayout>` 闭合位置。

- [ ] **Step 2: 在 LinearLayout 之后插入新节点**

在 `</LinearLayout>` 结束之后、`</FrameLayout>`(闭合 `floating_nav_controls`)之前,插入:

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

新节点必须**仍在 `floating_nav_controls` 这个 FrameLayout 内部**,不要破坏外层结构。

- [ ] **Step 3: 校验结构**

读取修改后的文件,确认:
- 新节点位于 `</LinearLayout>`(上下箭头)之后、`</FrameLayout>`(闭合 `floating_nav_controls`)之前
- id `@+id/button_floating_random` 正确
- 引用 `@drawable/floating_arrow_bg` 与 `@drawable/ic_random` 都存在
- 外层 LinearLayout 与 Toolbar 结构未受影响

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/activity_webview.xml
git commit -m "feat(webview): 新增悬浮随机跳转按钮节点"
```

---

### Task 3: WebViewActivity 字段、setup 绑定、navigateToRandom 方法

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java`
  - 字段声明区(line 84-86 附近)新增 2 个字段
  - `setupNavigationControls()` 内(line 1004-1015 浮动初始化块)追加随机按钮绑定
  - 在 `navigateToPrevious()` 与 `navigateToNext()` 之间新增 `navigateToRandom()` 方法

**Interfaces:**
- Consumes: Task 2 新增的 `R.id.button_floating_random`;现有 `contextIds` / `currentIndex` / `isNavigating` / `hasValidNavigationContext` / `loadByContextIndex(int)`
- Produces: 字段 `buttonFloatingRandom`、`random`(java.util.Random);方法 `navigateToRandom()`(private void,无参数)

- [ ] **Step 1: 新增字段**

在 WebViewActivity.java 第 84-86 行的字段声明区域(现有 `buttonFloatingNext` 之后),新增:

```java
private ImageButton buttonFloatingRandom;
private java.util.Random random = new java.util.Random();
```

字段放置紧邻现有 `buttonFloatingNext`,保持阅读连贯。

- [ ] **Step 2: setupNavigationControls 追加绑定**

将现有浮动初始化块:

```java
floatingNavControls = findViewById(R.id.floating_nav_controls);
if (floatingNavControls != null) {
    buttonFloatingPrevious = floatingNavControls.findViewById(R.id.button_floating_previous);
    buttonFloatingNext = floatingNavControls.findViewById(R.id.button_floating_next);
    if (buttonFloatingPrevious != null) buttonFloatingPrevious.setOnClickListener(v -> navigateToPrevious());
    if (buttonFloatingNext != null) buttonFloatingNext.setOnClickListener(v -> navigateToNext());
    floatingNavControls.setVisibility(hasValidNavigationContext() ? View.VISIBLE : View.GONE);
}
```

替换为:

```java
floatingNavControls = findViewById(R.id.floating_nav_controls);
if (floatingNavControls != null) {
    buttonFloatingPrevious = floatingNavControls.findViewById(R.id.button_floating_previous);
    buttonFloatingNext = floatingNavControls.findViewById(R.id.button_floating_next);
    buttonFloatingRandom = floatingNavControls.findViewById(R.id.button_floating_random);
    if (buttonFloatingPrevious != null) buttonFloatingPrevious.setOnClickListener(v -> navigateToPrevious());
    if (buttonFloatingNext != null) buttonFloatingNext.setOnClickListener(v -> navigateToNext());
    if (buttonFloatingRandom != null) buttonFloatingRandom.setOnClickListener(v -> navigateToRandom());
    floatingNavControls.setVisibility(hasValidNavigationContext() ? View.VISIBLE : View.GONE);
}
```

**关键**:随机按钮的可见性**完全继承** `floatingNavControls.setVisibility(...)`,本 Task 不引入新的 setVisibility 调用。

- [ ] **Step 3: 新增 navigateToRandom 方法**

在 `navigateToPrevious()`(line 1100 附近)与 `navigateToNext()` 之间(line 1102-1106 附近)新增:

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

**逻辑要点**:
- `length <= 1` 时给轻量 Toast,直接 return(不响应点击)
- `do-while` 循环重抽,长度 ≥ 2 时必然找到不等于 `currentIndex` 的 index
- 完全复用 `loadByContextIndex(int)`,自带 `isNavigating` 守护

- [ ] **Step 4: 校验编译可行性(静态)**

由于任务约定不执行编译,改完后**只读不改**:
- 确认 `java.util.Random` 通过完全限定名引用,无需 import(也可在文件顶部加 `import java.util.Random;`,但完全限定名更稳)
- 确认 `Toast` 已 import(现有 `import android.widget.Toast;` 应已存在)
- 确认大括号匹配:新增方法体闭合,setupNavigationControls 的 if 块闭合

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/person/notfresh/readingshare/WebViewActivity.java
git commit -m "feat(webview): 新增悬浮随机跳转按钮逻辑与 navigateToRandom 方法"
```

---

### Task 4: 手工回归检查

**Files:**
- 无

**Interfaces:**
- Consumes: 所有前置任务产物
- Produces: 验证报告

- [ ] **Step 1: 检查 id 与字段命名一致**

分别 grep 三个文件,确认 `button_floating_random` / `buttonFloatingRandom` / `ic_random` 字面量一致:

```bash
grep -rn "button_floating_random\|buttonFloatingRandom\|ic_random\|navigateToRandom" app/src/main/java app/src/main/res
```

预期:`activity_webview.xml`(1 处 `@+id`)、`WebViewActivity.java`(字段、findViewById、setOnClickListener、方法定义,共 5 处)、`drawable/ic_random.xml`(文件名,grep 会输出文件名匹配 1 处),无拼写变体。

- [ ] **Step 2: 确认可见性逻辑未动**

读取 WebViewActivity.java,确认以下方法体**未**新增 `buttonFloatingRandom` 相关的 `setVisibility` 调用:
- `initControlsIfNeeded`(line 1022-1068)
- `showControlsTemporarily`(line 1070-1086)
- `hideControlsRunnable`(在 `setupNavigationControls` 中,line 1013 附近)
- `toggleNavigationControls`(line 1088 附近,带 FLOATING 守卫)

- [ ] **Step 3: 确认现有 Toast 文案未被改动**

读取 `loadByContextIndex`(line 1150-1190 附近),确认其失败时的 Toast "无法加载下一页" 未被改动。

- [ ] **Step 4: 标记完成**

```bash
git status
```

预期:working tree clean。若仍残留改动,说明前面某 Task 漏提交,需回头补上。

---

### Task 5: 文档同步检查

**Files:**
- 无

**Interfaces:**
- Consumes: 已提交的代码改动
- Produces: 决定是否需要更新既有文档

- [ ] **Step 1: 检查现有 WebViewActivity 文档**

读取 `dev-docs/WebViewActivity返回按钮处理技术总结.md`,判断是否需要更新:
- 该文档主要描述"返回按钮处理",与本任务的"随机跳转"无关。
- 文档无需更新,跳过。

- [ ] **Step 2: 检查现有翻页文档**

读取 `dev-docs/实现方案-WebViewActivity上下文翻页(首页).md`,判断是否需要在 2026-08-23 追加的"右侧悬浮上下箭头变体"章节里加一节描述随机跳转:
- 章节结构:在"右侧悬浮上下箭头变体(2026-08-23)"之后追加"悬浮随机跳转变体(2026-08-23)"章节。

如果决定追加,插入位置在 `dev-docs/实现方案-WebViewActivity上下文翻页(首页).md` 第 183 行(章节末尾)之后,内容草稿:

```markdown
---

## 补充：悬浮随机跳转变体（2026-08-23）

在 2026-08-23 完成的悬浮上下箭头变体之上，再新增一个独立的"随机跳转"圆形按钮，点击后从当前 `context_ids` 中随机选一个不等于 `currentIndex` 的条目跳转。

- **复用面**：随机算法产生 target index 后，直接调用现有 `loadByContextIndex(int)`，自带 `isNavigating` 守护与 Toast 错误处理。随机按钮是 `floating_nav_controls` 容器的子节点，可见性自动继承容器（无需新增任何 setVisibility 调用）。
- **新增面**：
  - `drawable/ic_random.xml`（shuffle 矢量图标，white，24dp viewport）。
  - `activity_webview.xml` 内新增 `button_floating_random` ImageButton（`marginTop="24dp"` 形成与上下组 24dp 视觉留白，凸显独立）。
  - WebViewActivity 字段 `buttonFloatingRandom` / `random(java.util.Random)`，新方法 `navigateToRandom()`。
- **设计边界**：
  - `contextIds.length <= 1` 时 Toast "无可随机条目"，不响应。
  - 快速连点由 `loadByContextIndex` 内部的 `isNavigating` 守卫处理。
  - 没有新增设置项；随机按钮属于悬浮组，沿用 `nav_button_style`。
- **设计文档**：`docs/superpowers/specs/2026-08-23-webview-random-jump-design.md`
- **实施计划**：`docs/superpowers/plans/2026-08-23-webview-random-jump.md`
```

**判定**：本次特性独立成章，且逻辑复用面与上下箭头变体互补，应当追加该章节。

- [ ] **Step 3: 追加章节后 Commit**

```bash
git add dev-docs/实现方案-WebViewActivity上下文翻页（首页）.md
git commit -m "docs(webview): 补充悬浮随机跳转变体的实现说明"
```

- [ ] **Step 4: 完成**

本 Task 结束后 working tree 应再次 clean。若无新 commit 产生(如已合并到先前 commit),无需操作。