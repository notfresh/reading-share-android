# WebView 折叠为悬浮 mini player 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 WebViewActivity 可以折叠为可拖动悬浮 mini（圆角正方形 56dp × 56dp），仅显示"展开"图标，点击任意位置 = 展开回 WebViewActivity + 卸载 mini。折叠/展开过程中音频继续。

**Architecture:** 折叠 = `WebViewManager.storeWebView(url, webView, /*pauseMedia=*/false)` 暂存 WebView + `Activity.finish()`；mini 由 `WebViewBackgroundService` 用 `WindowManager.addView` 全局悬浮。展开 = mini click → Service `startActivity(WebViewActivity)`，复用现有 `WebViewManager.getWebView` 恢复分支（`WebViewActivity.java:150-159`）。

**Tech Stack:** Android SDK (minSdk 29, compileSdk 34, Java 8), JUnit 4.13.2 (existing, no mock framework).

## Global Constraints

（这些约束来自项目 AGENTS.md / spec 第 6 节，所有 task 都必须遵守）

- **小范围修改**：每个 task 只动所需文件，禁止顺手重构无关代码。
- **不要执行编译命令**：验证步骤只用 `git status` / `git diff` / 文件存在性检查。**不**跑 `./gradlew build` / `./gradlew assembleDebug` / `./gradlew test`。
- **不引入新依赖**：Mockito / Robolectric 等 mock 框架不在本计划范围。没有可写的自动化测试，所有验证为手动 + 文件检查。
- **不销毁 WebView**：折叠期 WebView 始终存活，由 `WebViewManager` 持有。
- **mini 仅一个交互**：点击任意区域 = 展开 + 卸载，无图标/本体区分。
- **不显示音频状态**：mini 上不显示播放/暂停图标，固定显示 `ic_open_in_full`。
- **不涉及通知栏 / MediaSession**：本次不调用 `MediaCallback`，不动音频控制。

---

## Task 1: 权限与 WebViewManager 新重载

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`（在 `<uses-permission>` 区追加一行）
- Modify: `app/src/main/java/person/notfresh/readingshare/WebViewManager.java`（新增一个重载 + 委托原方法）

**Interfaces:**
- Consumes: 无
- Produces:
  - `WebViewManager.storeWebView(String url, WebView webView, boolean pauseMedia)` —— 当 `pauseMedia=true` 时调 `evaluateJavascript` pause 所有 audio/video；当 `pauseMedia=false` 时直接存入缓存。原 `storeWebView(url, webView)` 行为不变。

- [ ] **Step 1: 在 AndroidManifest.xml 追加 SYSTEM_ALERT_WINDOW 权限**

打开 `app/src/main/AndroidManifest.xml`，找到现有 `<uses-permission>` 列表最后一行之后（搜索 `</uses-permission>` 的最后一个），在它**后面**插入：

```xml
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
```

缩进与其它 `<uses-permission>` 一致（4 空格）。

- [ ] **Step 2: 修改 WebViewManager.java，新增重载并保留旧方法**

打开 `app/src/main/java/person/notfresh/readingshare/WebViewManager.java`，把当前 `storeWebView(String url, WebView webView)` 方法**重命名**为 `storeWebView(String url, WebView webView, boolean pauseMedia)`，并把方法体内的 `webView.evaluateJavascript(...)` 用 `if (pauseMedia)` 包起来：

```java
    /**
     * 存储WebView实例
     * @param pauseMedia true 时注入脚本暂停所有媒体（"存档返回"场景）；false 时保留音频（折叠场景）
     */
    public void storeWebView(String url, WebView webView, boolean pauseMedia) {
        // 限制最大缓存数量为5个，避免内存问题
        if (cachedWebViews.size() >= 5) {
            // 如果已经有5个缓存，移除最早加入的
            String firstKey = cachedWebViews.keySet().iterator().next();
            WebView oldWebView = cachedWebViews.remove(firstKey);
            if (oldWebView != null) {
                oldWebView.destroy();
            }
        }

        if (pauseMedia) {
            // 注入一个脚本，清理可能导致问题的媒体会话
            webView.evaluateJavascript(
                "try {" +
                "  var mediaElements = document.querySelectorAll('audio,video');" +
                "  for(var i=0; i<mediaElements.length; i++) {" +
                "    mediaElements[i].pause();" +
                "  }" +
                "} catch(e) { console.log(e); }", null);
        }

        cachedWebViews.put(url, webView);
    }
```

然后在它**下方**插入保留原签名的便捷方法（保持向后兼容）：

```java
    /**
     * 存储WebView实例（默认暂停媒体，向后兼容旧调用方）
     */
    public void storeWebView(String url, WebView webView) {
        storeWebView(url, webView, true);
    }
```

- [ ] **Step 3: 验证 WebViewActivity 调用旧 storeWebView 仍兼容**

```bash
cd "C:/projects/duxiang-pack/duxiang-android"
grep -n "storeWebView" app/src/main/java/person/notfresh/readingshare/WebViewActivity.java
```

预期输出包含 `getInstance().storeWebView(currentUrl, webView);`（单参数旧调用）—— 因为我们新增了同名单参数方法作为委托，旧调用无需改动。

- [ ] **Step 4: 提交**

```bash
cd "C:/projects/duxiang-pack/duxiang-android"
git add app/src/main/AndroidManifest.xml app/src/main/java/person/notfresh/readingshare/WebViewManager.java
git commit -m "feat: 为折叠 mini 增加悬浮窗权限与 WebViewManager pauseMedia 重载"
```

---

## Task 2: mini 视觉资源

**Files:**
- Create: `app/src/main/res/layout/floating_mini_player.xml`
- Create: `app/src/main/res/drawable/bg_floating_mini.xml`

**Interfaces:**
- Consumes: 无
- Produces:
  - `R.layout.floating_mini_player` —— FrameLayout 56dp×56dp，中心一个 ImageView
  - `R.drawable.bg_floating_mini` —— 圆角正方形背景（圆角 12dp）

- [ ] **Step 1: 检查项目是否已有 `ic_open_in_full` 图标**

```bash
cd "C:/projects/duxiang-pack/duxiang-android"
ls app/src/main/res/drawable/ | grep -i "open_in_full\|fullscreen"
```

预期：无匹配输出（项目没现成图标）。需要复用 Material Icons 的 SVG。

- [ ] **Step 2: 创建 mini 背景 drawable**

新建文件 `app/src/main/res/drawable/bg_floating_mini.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#CC222222"/>
    <corners android:radius="12dp"/>
    <stroke
        android:width="1dp"
        android:color="#33FFFFFF"/>
</shape>
```

- [ ] **Step 3: 创建 mini 布局文件**

新建文件 `app/src/main/res/layout/floating_mini_player.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="56dp"
    android:layout_height="56dp"
    android:background="@drawable/bg_floating_mini">

    <ImageView
        android:id="@+id/mini_icon"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:layout_gravity="center"
        android:src="@android:drawable/ic_menu_upload"
        android:contentDescription="展开"
        app:tint="#FFFFFF"
        xmlns:app="http://schemas.android.com/apk/res-auto"/>
</FrameLayout>
```

> 说明：用系统内置 `@android:drawable/ic_menu_upload`（向上箭头）作为占位展开图标。如果项目后续要换成 Material `ic_open_in_full`，替换 src 即可，**不**要在本次范围引入 vector asset。

- [ ] **Step 4: 验证文件已创建**

```bash
cd "C:/projects/duxiang-pack/duxiang-android"
ls app/src/main/res/layout/floating_mini_player.xml app/src/main/res/drawable/bg_floating_mini.xml
```

预期：两条路径都打印成功，无 `No such file` 错误。

- [ ] **Step 5: 提交**

```bash
cd "C:/projects/duxiang-pack/duxiang-android"
git add app/src/main/res/layout/floating_mini_player.xml app/src/main/res/drawable/bg_floating_mini.xml
git commit -m "feat: 添加悬浮 mini 的布局与圆角正方形背景"
```

---

## Task 3: FloatingMiniPlayerView 自定义 View

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/FloatingMiniPlayerView.java`

**Interfaces:**
- Consumes: `R.layout.floating_mini_player`（Task 2 产出）
- Produces:
  - `class FloatingMiniPlayerView extends FrameLayout` —— 提供：
    - 构造函数 `(Context ctx, OnClickListener onClick)`
    - 字段 `WindowManager.LayoutParams params`
    - 字段 `int initialX, initialY; float downRawX, downRawY; boolean isDragging;`
    - `attachTo(WindowManager wm)` / `detachFrom(WindowManager wm)`

- [ ] **Step 1: 创建 FloatingMiniPlayerView.java**

新建文件 `app/src/main/java/person/notfresh/readingshare/FloatingMiniPlayerView.java`：

```java
package person.notfresh.readingshare;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 悬浮 mini player 视图。
 * 点击 = 触发外部传入的 OnClickListener。
 * 拖动 = 移动 LayoutParams 坐标，松手自动吸左/右贴边。
 */
public class FloatingMiniPlayerView extends FrameLayout {

    private final WindowManager.LayoutParams params;
    private final int touchSlop;
    private final float density;

    private float downRawX, downRawY;
    private int initialX, initialY;
    private boolean isDragging;

    public FloatingMiniPlayerView(@NonNull Context context, @Nullable OnClickListener onClickListener) {
        super(context);
        this.density = context.getResources().getDisplayMetrics().density;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.params = buildLayoutParams();

        LayoutInflater.from(context).inflate(R.layout.floating_mini_player, this, true);
        setOnClickListener(onClickListener);

        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleTouch(event);
            }
        });
    }

    private WindowManager.LayoutParams buildLayoutParams() {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        int margin = (int) (16 * density + 0.5f);
        p.x = margin;
        p.y = margin;
        // 56dp 转 px
        p.width = (int) (56 * density + 0.5f);
        p.height = (int) (56 * density + 0.5f);
        return p;
    }

    private boolean handleTouch(MotionEvent event) {
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                initialX = params.x;
                initialY = params.y;
                isDragging = false;
                return false; // 让 click 事件也有机会触发
            case MotionEvent.ACTION_MOVE: {
                int dx = (int) (event.getRawX() - downRawX);
                int dy = (int) (event.getRawY() - downRawY);
                if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    isDragging = true;
                }
                if (isDragging) {
                    params.x = initialX + dx;
                    params.y = initialY + dy;
                    if (wm != null) {
                        try {
                            wm.updateViewLayout(this, params);
                        } catch (IllegalArgumentException ignored) {
                            // view 已被外部 remove
                        }
                    }
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    snapToEdge(wm);
                    isDragging = false;
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private void snapToEdge(WindowManager wm) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int viewWidth = params.width > 0 ? params.width : (int) (56 * density + 0.5f);
        int centerX = params.x + viewWidth / 2;
        params.x = centerX < screenWidth / 2 ? 0 : screenWidth - viewWidth;
        if (wm != null) {
            try {
                wm.updateViewLayout(this, params);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void attachTo(WindowManager wm) {
        try {
            wm.addView(this, params);
        } catch (IllegalStateException ignored) {
            // 已 add 过
        }
    }

    public void detachFrom(WindowManager wm) {
        if (getParent() != null) {
            try {
                wm.removeView(this);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
```

- [ ] **Step 2: 验证文件已创建**

```bash
cd "C:/projects/duxiang-pack/duxiang-android"
ls app/src/main/java/person/notfresh/readingshare/FloatingMiniPlayerView.java
```

预期：路径打印成功。

- [ ] **Step 3: 提交**

```bash
cd "C:/projects/duxiang-pack/duxiang-android"
git add app/src/main/java/person/notfresh/readingshare/FloatingMiniPlayerView.java
git commit -m "feat: 添加 FloatingMiniPlayerView(拖动+吸边+点击)"
```

---

## Task 4: WebViewBackgroundService 扩展 mini 生命周期

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/WebViewBackgroundService.java`

**Interfaces:**
- Consumes:
  - `FloatingMiniPlayerView(Context, OnClickListener)`（Task 3 产出）
  - `R.layout.floating_mini_player`（Task 2 产出）
- Produces:
  - 静态常量 `String ACTION_SHOW_MINI = "person.notfresh.readingshare.SHOW_MINI"`
  - Service 字段 `private FloatingMiniPlayerView mMiniView; private String mCurrentUrl; private WindowManager mWindowManager;`
  - Service 方法 `private void showMiniPlayer(String url)`
  - Service 方法 `private void handleMiniClick()`
  - `onStartCommand` 处理 `ACTION_SHOW_MINI` 分支
  - `onDestroy` 卸载 mini（已有 WakeLock release 之后追加）

- [ ] **Step 1: 追加 import 和常量**

打开 `app/src/main/java/person/notfresh/readingshare/WebViewBackgroundService.java`，**文件顶部 import 区**追加：

```java
import android.content.Intent;
import android.view.WindowManager;
```

（`Intent` 可能已存在，看一下是否有；没有就加。）

在 `public static final String ACTION_STOP` 那行**之后**追加：

```java
public static final String ACTION_SHOW_MINI = "person.notfresh.readingshare.SHOW_MINI";
```

- [ ] **Step 2: 声明新字段**

在 `private boolean isPaused = false;` 这行**之后**追加：

```java
private FloatingMiniPlayerView mMiniView;
private String mCurrentUrl;
```

- [ ] **Step 3: 在 onStartCommand 中处理 ACTION_SHOW_MINI**

找到现有的 `if (intent != null) { ... }` 处理 ACTION_PLAY_PAUSE / ACTION_STOP 的代码块，**在该 if 块的最前面**（或按你阅读顺序合理的位置）插入新分支。注意现有代码先用 `if (ACTION_PLAY_PAUSE.equals(action))` 然后 `else if (ACTION_STOP.equals(action))`，新分支加在它们**之前**或之后都可。

在 `else if (ACTION_STOP.equals(action)) { ... }` 块**结束**之后、`// 正常启动` 注释之前，插入：

```java
            else if (ACTION_SHOW_MINI.equals(action)) {
                Log.d(TAG, "SHOW_MINI action, url=" + intent.getStringExtra("url"));
                String url = intent.getStringExtra("url");
                showMiniPlayer(url);
                // 不 return，继续走到 startForeground 让通知显示
            }
```

- [ ] **Step 4: 添加 showMiniPlayer 与 handleMiniClick 方法**

在 `private void createNotificationChannel()` 方法**之前**，插入两个新方法：

```java
    private void showMiniPlayer(String url) {
        mCurrentUrl = url;
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            Log.e(TAG, "WindowManager 为空,无法显示 mini");
            return;
        }
        if (mMiniView != null && mMiniView.getParent() != null) {
            mMiniView.detachFrom(wm);
        }
        mMiniView = new FloatingMiniPlayerView(this, v -> handleMiniClick());
        mMiniView.attachTo(wm);
    }

    private void handleMiniClick() {
        Log.d(TAG, "mini 被点击,展开 WebView");
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (mMiniView != null) {
            mMiniView.detachFrom(wm);
            mMiniView = null;
        }
        if (mCurrentUrl != null && !mCurrentUrl.isEmpty()) {
            Intent expandIntent = new Intent(this, WebViewActivity.class);
            expandIntent.putExtra("url", mCurrentUrl);
            expandIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(expandIntent);
            } catch (Exception e) {
                Log.e(TAG, "展开 WebViewActivity 失败", e);
            }
        }
        stopForeground(true);
        stopSelf();
    }
```

- [ ] **Step 5: 在 onDestroy 中清理 mini**

找到 `onDestroy` 方法（已存在 WakeLock release 逻辑），在 `super.onDestroy();` 之前追加：

```java
        if (mMiniView != null) {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            mMiniView.detachFrom(wm);
            mMiniView = null;
        }
```

- [ ] **Step 6: 验证改动**

```bash
cd "C:/projects/duxiang-pack/duxiang-android"
grep -n "ACTION_SHOW_MINI\|showMiniPlayer\|handleMiniClick\|mMiniView" app/src/main/java/person/notfresh/readingshare/WebViewBackgroundService.java
```

预期：6+ 行匹配，包含新增的方法名和常量名。

- [ ] **Step 7: 提交**

```bash
cd "C:/projects/duxiang-pack/duxiang-android"
git add app/src/main/java/person/notfresh/readingshare/WebViewBackgroundService.java
git commit -m "feat: WebViewBackgroundService 增加 mini 生命周期管理"
```

---

## Task 5: WebViewActivity 接入折叠菜单

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java`

**Interfaces:**
- Consumes:
  - `WebViewManager.storeWebView(url, webView, false)`（Task 1 产出）
  - `WebViewBackgroundService.ACTION_SHOW_MINI`（Task 4 产出）
- Produces:
  - 菜单资源 id: `R.id.action_collapse_to_mini`（在 `onCreateOptionsMenu` 里 inflate 一个 menu xml，或者用 `add(Menu.NONE, R.id...., ...)` 方式）
  - `WebViewActivity.collapseToMiniPlayer()` 私有方法
  - `WebViewActivity.onOptionsItemSelected` 处理新菜单项

- [ ] **Step 1: 检查项目菜单资源现状**

```bash
cd "C:/projects/duxiang-pack/duxiang-android"
ls app/src/main/res/menu/ 2>/dev/null
grep -n "onCreateOptionsMenu\|onOptionsItemSelected" app/src/main/java/person/notfresh/readingshare/WebViewActivity.java | head -10
```

预期：能看到现有 menu xml（如果有）和 `onCreateOptionsMenu` / `onOptionsItemSelected` 的方法体位置。

- [ ] **Step 2: 在现有 menu xml 中新增一项（或新建）**

如果 `app/src/main/res/menu/` 已存在且有 webview 相关 xml（如 `webview.xml`），打开它，在 `</menu>` 之前插入：

```xml
    <item
        android:id="@+id/action_collapse_to_mini"
        android:title="折叠"
        android:icon="@android:drawable/ic_menu_revert"
        app:showAsAction="ifRoom"/>
```

（如果项目 menu xml 没有 `xmlns:app` 命名空间，需要在根 `<menu>` 加 `xmlns:app="http://schemas.android.com/apk/res-auto"`。）

如果 `app/src/main/res/menu/` 目录不存在或没相关 xml，新建 `app/src/main/res/menu/webview.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <item
        android:id="@+id/action_collapse_to_mini"
        android:title="折叠"
        android:icon="@android:drawable/ic_menu_revert"
        app:showAsAction="ifRoom"/>
</menu>
```

**注意**：如果 WebViewActivity 现有 menu 还有其它 item，需要把那些 item 也复制到新文件（或用 append 方式修改现有文件）。先读现有文件，再决定动作。

- [ ] **Step 3: 在 WebViewActivity 中 inflate 新 menu**

打开 `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java`，找到现有的 `onCreateOptionsMenu(Menu menu)` 方法体。如果它已经 inflate 了一个 menu xml，把 `R.menu.xxx` 替换为新建的 `R.menu.webview`（或对应的）。如果方法体是空的，新增：

```java
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.webview, menu);
        return super.onCreateOptionsMenu(menu);
    }
```

- [ ] **Step 4: 在 onOptionsItemSelected 添加处理**

找到现有的 `onOptionsItemSelected(MenuItem item)` 方法，在 `return super.onOptionsItemSelected(item);` 之前新增：

```java
        if (item.getItemId() == R.id.action_collapse_to_mini) {
            collapseToMiniPlayer();
            return true;
        }
```

- [ ] **Step 5: 添加 collapseToMiniPlayer 私有方法**

在 WebViewActivity 类内找一个合理位置（如 `collapseToMiniPlayer()` 字符串搜不到，所以放在 onOptionsItemSelected 附近或类末尾），新增：

```java
    private void collapseToMiniPlayer() {
        if (currentUrl == null || webView == null) {
            Toast.makeText(this, "无内容可折叠", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请授予悬浮窗权限以使用折叠播放", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception e) {
                Log.e("WVCollapse", "跳悬浮窗权限失败", e);
            }
            return;
        }
        // 1. 从容器摘下 WebView
        if (webView.getParent() != null) {
            ((android.view.ViewGroup) webView.getParent()).removeView(webView);
        }
        // 2. 存入 WebViewManager,不暂停媒体
        WebViewManager.getInstance().storeWebView(currentUrl, webView, false);
        // 3. 防止 onDestroy 误调 destroy
        webView = null;
        // 4. 启动 Service 显示 mini
        Intent serviceIntent = new Intent(this, WebViewBackgroundService.class);
        serviceIntent.setAction(WebViewBackgroundService.ACTION_SHOW_MINI);
        serviceIntent.putExtra("url", currentUrl);
        startForegroundService(serviceIntent);
        // 5. 关闭当前 Activity
        finish();
    }
```

- [ ] **Step 6: 验证改动**

```bash
cd "C:/projects/duxiang-pack/duxiang-android"
grep -n "collapseToMiniPlayer\|action_collapse_to_mini\|ACTION_SHOW_MINI\|storeWebView.*false" app/src/main/java/person/notfresh/readingshare/WebViewActivity.java
```

预期：至少 4 行匹配，包含新增的方法名、菜单 id、service 常量、storeWebView 的 false 调用。

- [ ] **Step 7: 提交**

```bash
cd "C:/projects/duxiang-pack/duxiang-android"
git add app/src/main/java/person/notfresh/readingshare/WebViewActivity.java app/src/main/res/menu/
git commit -m "feat: WebViewActivity 增加折叠菜单与 collapseToMiniPlayer"
```

---

## Task 6: 全局手动验证

**Files:**
- 无（纯验证 task）

- [ ] **Step 1: 检查所有改动文件已存在**

```bash
cd "C:/projects/duxiang-pack/duxiang-android"
git diff --name-only HEAD~5 HEAD
```

预期看到 5+ 个文件：
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/person/notfresh/readingshare/WebViewManager.java`
- `app/src/main/res/layout/floating_mini_player.xml`
- `app/src/main/res/drawable/bg_floating_mini.xml`
- `app/src/main/java/person/notfresh/readingshare/FloatingMiniPlayerView.java`
- `app/src/main/java/person/notfresh/readingshare/WebViewBackgroundService.java`
- `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java`
- `app/src/main/res/menu/webview.xml`（或被修改的现有 menu 文件）

- [ ] **Step 2: 执行 spec 第 5 节的手动验证清单**

按照 spec 第 5 节的 7 条逐项验证（在真机或模拟器上）：
1. 加载含 `<audio>` 的网页 → Toolbar 折叠按钮 → 授权 → mini 出现 + 音频继续
2. 拖动 mini → 跟随手指 → 松开吸到左/右
3. 点 mini 任意区域 → 回到完整 WebView + 音频继续 + mini 消失
4. 再次折叠 → mini 重新出现 → 音频状态保留
5. mini 在第三方 App 上方仍可显示
6. 折叠后系统返回键 → 回到上一 Activity → mini 仍在
7. 折叠后杀进程重启 → mini 不自动出现（已知限制）

- [ ] **Step 3: 在每条验证通过后打勾**

逐项执行，发现问题先修复对应 task 的代码，再回来勾选。

- [ ] **Step 4: 最终提交（如果验证过程有微调）**

如果有手动验证过程中产生的代码微调，按原子提交原则分别 commit。如果无微调，跳过此步。

---

## 范围外（明确不做）

按 spec 第 6 节明确列出：
- 进程被杀重启后 mini 自动恢复
- mini 上显示页面标题
- mini 显示播放/暂停状态
- mini 位置持久化
- 通知栏 / MediaSession / `MediaCallback` 任何改动
- `WebViewManager` 5 个缓存上限与 LinkedHashMap FIFO 优化（探索文档第 197 行）
- 引入 Mockito / Robolectric 等 mock 框架（新增依赖，违反 AGENTS.md 小范围修改）
- 自动化单元测试（无 mock 框架，WebView/Activity/Service/WindowManager 均不可在 JUnit 中实例化）
