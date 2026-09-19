# WebView 折叠为悬浮 mini player 设计

**日期**: 2026-09-18
**状态**: 设计稿，待用户评审

## 1. 背景与目标

项目已有完整的后台播放能力（`WebViewBackgroundService` + `BackgroundAudioWebView` + `MediaCallback` 静态桥），用户希望 WebView **不退出 Activity** 也能"折叠"：把 WebView 收起成屏幕上一颗可拖动的 mini 圆角正方形按钮，**音频继续播放**，点 mini 又能恢复 WebView 视图。

本次需求：**方案 B —— SYSTEM_ALERT_WINDOW 全局悬浮 mini**。

## 2. 设计范围

涉及文件：

| 文件 | 改动 |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | 新增 `SYSTEM_ALERT_WINDOW` 权限 |
| `app/src/main/res/layout/floating_mini_player.xml` | 新增（mini 视图布局） |
| `app/src/main/res/drawable/bg_floating_mini.xml` | 新增（mini 圆角正方形背景） |
| `app/src/main/java/person/notfresh/readingshare/FloatingMiniPlayerView.java` | 新增（mini 自定义 View） |
| `app/src/main/java/person/notfresh/readingshare/WebViewBackgroundService.java` | 新增 mini 生命周期管理 |
| `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java` | 新增 Toolbar 折叠菜单 + `collapseToMiniPlayer()` / `expandFromMini()` |
| `app/src/main/java/person/notfresh/readingshare/WebViewManager.java` | `storeWebView` 增加 `pauseMedia` 参数（默认 true 保持向后兼容） |

不涉及：`MainActivity`、`SubjectDetailActivity`、数据库、JS 注入逻辑、通知栏。

## 3. 关键设计决策

### 3.1 折叠后 WebView 不销毁 —— 复用 WebViewManager 缓存

**原因**：折叠 = 把 WebView 从 Activity 视图树摘下，**不能 finish Activity**，因为：
- Activity finish 会触发 `WebViewActivity.onDestroy` → 销毁 WebView（音频链路随之断裂）
- 折叠后 mini 仍要悬浮在所有 App 之上，Activity 不应退出

**做法**：复用现成的 `WebViewManager`（`app/src/main/java/person/notfresh/readingshare/WebViewManager.java`）。该项目本就用它跨 Activity 暂存 WebView（见 `WebViewActivity.java:150` 取缓存、`L365` 存缓存）。

`WebViewManager.storeWebView(url, webView)` 默认会注入脚本 pause 所有媒体（为"存档返回"场景设计）。折叠场景需要保留音频播放，因此本次新增重载：

```java
public void storeWebView(String url, WebView webView, boolean pauseMedia) {
    if (pauseMedia) {
        webView.evaluateJavascript(/* pause 所有 audio/video */, null);
    }
    cachedWebViews.put(url, webView);
}
```

折叠调用：`WebViewManager.getInstance().storeWebView(currentUrl, webView, /*pauseMedia=*/false);`

### 3.2 WebView 在折叠期处于"无 parent"中间态

`WebViewManager` 存的是裸 `WebView` 引用，不持有 parent ViewGroup。这点项目已经在用（`WebViewActivity.java:362-368` 现存代码就做"从 parent 摘下 → 存 manager"），所以沿用无 parent 方案。

WebView 无 parent 时仍可保持 JS 引擎、媒体管道运行（项目现有 "存档返回" 场景即如此）。`BackgroundAudioWebView` 拦截 `onWindowVisibilityChanged(GONE)` 也保证系统级 onPause 不会杀掉音频。

### 3.3 mini 形态

- 圆角正方形 56dp × 56dp（圆角半径约 12dp，**非圆形**）
- 半透明深色背景（`#CC222222`）+ 描边 1dp 浅灰；圆角矩形
- 中心固定显示一个"展开"图标（如 `ic_open_in_full`，白色 24dp），**不显示任何音频状态**
- 点击 mini 任何区域 = 展开回 WebViewActivity + 卸载 mini（**单一交互，不分图标区/本体区**）

## 4. 详细设计

### 4.1 折叠流程

```
WebViewActivity Toolbar 点击"折叠"菜单
└─ collapseToMiniPlayer()
   ├─ 1. Settings.canDrawOverlays() ?
   │     ├─ false → startActivity(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
   │     │            + Toast("请授予悬浮窗权限")
   │     │            + return  (WebView 不动)
   │     └─ true ↓
   ├─ 2. ((ViewGroup) webView.getParent()).removeView(webView)
   ├─ 3. WebViewManager.getInstance().storeWebView(currentUrl, webView, false)
   │        // 存入 manager,不 pause 媒体
   ├─ 4. webView = null   // 防止 Activity onDestroy 时误调 webView.destroy()
   ├─ 5. Intent startForegroundService(WebViewBackgroundService)
   │        .putExtra("action", "SHOW_MINI")
   │        .putExtra("url", currentUrl)
   ├─ 6. finish()         // Activity 退出,但 WebView 在 manager 中存活
```

> 关键点：第 6 步 finish Activity 是允许的 —— WebView 已交由 `WebViewManager` 持有，Activity 销毁时 `webView` 字段已被置 null（步骤 4），不会触发 destroy。

### 4.2 展开流程

```
用户点 mini
└─ FloatingMiniPlayerView.onClick
   └─ WebViewBackgroundService.handleMiniClick()
      ├─ WindowManager.removeView(miniView)            // 卸载 mini
      ├─ stopForeground(true)  +  stopSelf()          // 销毁 Service
      └─ Context.startActivity(Intent(WebViewActivity)
            .putExtra("url", savedUrl)
            .addFlags(FLAG_ACTIVITY_NEW_TASK))
```

> mini 的 click 在 Service 进程触发，startActivity 需要 `FLAG_ACTIVITY_NEW_TASK`（Service Context 非 Activity）。

回到 WebViewActivity：
- `onCreate` 走现有缓存恢复分支（`WebViewActivity.java:150`）：
  ```java
  WebView cached = WebViewManager.getInstance().getWebView(currentUrl);
  if (cached != null) {
      webView = cached;
      if (webView.getParent() != null) ((ViewGroup) webView.getParent()).removeView(webView);
      webViewContainer.addView(webView, 0);
  }
  ```
- 音频链路、JS 监听、`MediaCallback` 全部继续工作 —— Activity 重建会重新注册 `MediaCallback`，但 WebView 实例是同一个，JS 端 `window.__mediaListenerAttached` 幂等保护防止重复挂监听（见探索文档 `webview-js-injection-and-media-playback.md` 第 114 行）。

### 4.3 FloatingMiniPlayerView

```java
public class FloatingMiniPlayerView extends FrameLayout {
    private final WindowManager.LayoutParams params;
    private final ImageView iconView;  // 固定显示 ic_open_in_full
    private float downRawX, downRawY;
    private int initialX, initialY;
    private OnClickListener onClickListener;

    public void attachTo(WindowManager wm) { wm.addView(this, params); }
    public void detachFrom(WindowManager wm) { wm.removeView(this); }
}
```

`LayoutParams`：
- `gravity = TOP | START`
- `width = height = (int)(56 * density + 0.5f)`
- `flags = FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS`
- `type = TYPE_APPLICATION_OVERLAY`（Android 8+ 必须）

### 4.4 拖动 + 吸边

`onTouch`：
- `ACTION_DOWN`：`downRawX = event.getRawX(); downRawY = event.getRawY(); initialX = params.x; initialY = params.y;`
- `ACTION_MOVE`：`params.x = initialX + (int)(event.getRawX() - downRawX); params.y = initialY + (int)(event.getRawY() - downRawY); windowManager.updateViewLayout(this, params);`
- `ACTION_UP`：
  - 中点 x = `params.x + viewWidth/2`
  - `screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels`
  - 中点 x < screenWidth/2 → `params.x = 0`
  - 否则 → `params.x = screenWidth - viewWidth`
  - `windowManager.updateViewLayout(this, params);`

阈值：x 方向偏移 > `ViewConfiguration.get(ctx).getScaledTouchSlop()` 才视为拖动；否则走 click 事件。

### 4.5 WebViewBackgroundService 扩展

新增字段：
```java
private FloatingMiniPlayerView mMiniView;
private String mCurrentUrl;
```

新增方法：
- `showMiniPlayer(String url)`：inflate `FloatingMiniPlayerView`，设 onClick = `handleMiniClick`，addView 到 `WindowManager`；保存 `mCurrentUrl`
- `handleMiniClick()`：见 4.2 节展开流程

`onStartCommand` 新增分支：
- `ACTION_SHOW_MINI` → 调 `showMiniPlayer(url)`

`onDestroy`：
- `if (mMiniView != null && mMiniView.getParent() != null) windowManager.removeView(mMiniView);`

mini **不监听** JS 的 `setMediaPlaying` 回调，不更新图标。折叠/展开是纯 UI 切换，音频状态由项目原有链路自然维持（不在本次需求范围内）。

### 4.6 错误处理

- `canDrawOverlays()` false：跳系统设置 + Toast + **不进入折叠状态**
- `addView` 抛 `BadTokenException`：catch + stopForeground + stopSelf
- mini 已 attach 时再次折叠：先 `detachFrom` 再 `attachTo`
- mini 拖动到屏外：`FLAG_LAYOUT_NO_LIMITS` + 边界 clamp
- 折叠后用户立即点返回：Activity 已 finish，正常回到上一 Activity；WebView 在 manager 中待命

## 5. 测试

项目当前没有测试框架。本次手动验证清单：

1. 加载含 `<audio>` 的网页，点击 Toolbar 折叠按钮 → 首次跳授权页 → 授权后 mini 出现 + 音频继续
2. 拖动 mini → 拖动过程中跟随手指 → 松开后自动吸到左/右
3. 点 mini 任何区域 → 回到完整 WebView 视图 + 音频继续 + mini 消失
4. 再次折叠 → mini 重新出现 → 音频状态保留
5. mini 在设置页 / 第三方 App 上方均可显示
6. 折叠后点系统返回键 → 回到上一 Activity (SubjectFragment/MainActivity) → mini 仍在
7. 折叠后 App 杀进程重启 → 本次不实现进程被杀重启的恢复（见第 6 节限制）

## 6. 已知限制 / 非目标

- 不支持进程被杀重启后 mini 自动恢复
- 不在 mini 上显示页面标题
- mini 仅一个交互：点击 = 展开 + 卸载（**不显示任何音频状态**）
- mini 的位置不持久化（每次重新出现为初始位置）
- 本次需求不涉及音频控制、MediaSession、通知栏的改动

## 7. 与现有架构的兼容性

- `BackgroundAudioWebView` 仍按现状工作（exploration 探索已记录其拦截 `onWindowVisibilityChanged(GONE)`）
- `MediaCallback` 静态桥本次不动 —— mini **不**触发音频控制（只展开/卸载）
- 折叠 = Activity finish + WebView 暂存 `WebViewManager`；展开 = 重启 Activity + 从 manager 取 WebView。复用 `WebViewActivity.java:150-159` 的缓存恢复逻辑
- `WebViewManager.storeWebView` 新增 `boolean pauseMedia` 重载，原 `storeWebView(url, webView)` 保持向后兼容（默认行为不变）
- `WebViewManager` 的 5 个缓存上限、LinkedHashMap FIFO 问题（探索文档第 197 行）**不在本次范围**
