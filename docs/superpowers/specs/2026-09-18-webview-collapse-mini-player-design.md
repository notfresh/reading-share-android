# WebView 折叠为悬浮 mini player 设计

**日期**: 2026-09-18
**状态**: 设计稿，待用户评审

## 1. 背景与目标

项目已有完整的后台播放能力（`WebViewBackgroundService` + `BackgroundAudioWebView` + `MediaCallback` 静态桥），但用户希望 WebView **不退出 Activity** 也能"折叠"：把 WebView 收起成屏幕上一颗可拖动的 mini 圆形按钮，**音频继续播放**，点 mini 又能恢复 WebView 视图。

本次需求：**方案 B —— SYSTEM_ALERT_WINDOW 全局悬浮 mini**。

## 2. 设计范围

涉及文件：

| 文件 | 改动 |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | 新增 `SYSTEM_ALERT_WINDOW` 权限 |
| `app/src/main/res/layout/floating_mini_player.xml` | 新增（mini 视图布局） |
| `app/src/main/res/drawable/bg_floating_mini.xml` | 新增（mini 圆形背景） |
| `app/src/main/java/person/notfresh/readingshare/FloatingMiniPlayerView.java` | 新增（mini 自定义 View） |
| `app/src/main/java/person/notfresh/readingshare/WebViewBackgroundService.java` | 新增 mini 生命周期管理 |
| `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java` | 新增 Toolbar 折叠菜单 + `collapseToMiniPlayer()` |

不涉及：`MainActivity`、`SubjectDetailActivity`、数据库、JS 注入逻辑、`WebViewManager`。

## 3. 关键设计决策

### 3.1 折叠后 WebView 不销毁

**原因**：现有 `MediaCallback`（`WebViewActivity.java:919-944`）的所有回调里都通过 `webView.evaluateJavascript(...)` 控制音频。若折叠 = `WebViewActivity.finish()` + `WebView.destroy()`，则 mini 上的播放/暂停按钮点击后将因 `webView==null` 而失效（探索文档 `webview-js-injection-and-media-playback.md` 已记录这条链路）。

**做法**：折叠 = **不销毁 Activity**，把 `BackgroundAudioWebView` 从 Activity 的视图树 **detach** 并 attach 到 Service 持有的一个不可见 `FrameLayout` 上。WebView 实例、JS 状态、媒体元素状态全部保留。

### 3.2 音频链路完全复用现有后台播放

- mini 播放/暂停 → 走 `WebViewBackgroundService.sMediaCallback.onPlayRequested()/onPauseRequested()`，与现有通知栏按钮同链路
- mini 不持有 WebView 引用 —— WebView 被 detach 但仍存活，由 Activity 销毁前通过 `WebViewManager` 缓存给 Service
- 通知栏"停止"按钮 → 销毁 mini + 销毁 Service 持有的 WebView（**行为变更**：当前"停止"只调用 `__stopMedia`，不销毁 WebView；本次仍不销毁 WebView，仅卸载 mini 视图）

### 3.3 mini 形态

- 正方形小圆 56dp × 56dp
- 圆形半透明黑背景 + 中心播放/暂停图标
- 点图标 = 播放/暂停切换
- 点 mini 本体（图标外区域）= 展开回 WebViewActivity

## 4. 详细设计

### 4.1 折叠流程

```
WebViewActivity Toolbar 点击"折叠"菜单
└─ collapseToMiniPlayer()
   ├─ 1. Settings.canDrawOverlays() 检查
   │     ├─ false → startActivity(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
   │     │            + Toast("请授予悬浮窗权限以使用折叠播放")
   │     │            + return  (WebView 不动)
   │     └─ true ↓
   ├─ 2. 缓存 WebView 给 Service:
   │        Intent(this, WebViewBackgroundService.class)
   │          .putExtra("action", "ATTACH_MINI")
   │          .putExtra("url", currentUrl)
   │          .putExtra("title", pageTitleCache)
   │        startForegroundService(intent)
   ├─ 3. Service.onStartCommand(ATTACH_MINI):
   │        ├─ startForeground(NOTIFICATION_ID, ...)  // 已有
   │        ├─ attachWebViewToService(webView)        // 接收 Activity 通过 bindActivity 注入的 WebView
   │        └─ windowManager.addView(miniView, params)
   └─ 4. WebViewActivity:
          - 从 R.id.webview_container removeView(webView)
          - 不 finish, 不调用 webView.destroy()
          - 设置 setContentView 一个空白 R.layout.activity_webview_collapsed (空 FrameLayout, status="WebView 已折叠")
          - 这样 Activity 仍在栈中, 返回键可回到上一 Activity
```

> 关键点：第 4 步把 WebView 从 Activity 视图树移除但 Activity 不 finish。Service 通过新的 `attachWebViewToService` API 接收 WebView 引用（**实现**：Activity 调 `Service.attachWebView(webView)`，Service 把 webView 加到自己持有的 `mServiceViewHost: FrameLayout`）。

### 4.2 展开流程

```
FloatingMiniPlayerView 本体 onClick (图标外区域)
└─ expandToWebView()
   ├─ Intent(this, WebViewActivity.class)
   │      .putExtra("url", savedUrl)
   │      .putExtra("title", savedTitle)
   │      .addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP)
   ├─ startActivity(intent)
   └─ 不主动卸载 mini —— 展开后 mini 仍在悬浮, 用户可继续拖动
```

回到 WebViewActivity 后：
- `onCreate` 检测 `savedUrl` 与当前 WebViewManager 中缓存匹配 → 复用同一 WebView（避免重新加载 + 音频状态丢失）
- 若 Service 还持有该 WebView，则 `onCreate` 从 Service 重新 attach 回 `R.id.webview_container`

**简化**：折叠/展开采用**同一个 Activity 实例**：折叠不 finish，展开 = `setContentView(R.layout.activity_webview)` 重新加载布局 + 重新 attach WebView。这样不需要 intent 传参与 Activity 重建。

### 4.3 折叠/展开的实现细节（同一 Activity）

```
折叠:
  setContentView(R.layout.activity_webview_collapsed)  // 空布局+提示文字
  findViewById(R.id.webview_container).removeView(webView)
  WebViewBackgroundService.attachWebView(this, webView)
  WebViewBackgroundService.showMiniPlayer(this, currentUrl)
  
展开:
  setContentView(R.layout.activity_webview)
  webView = findViewById(R.id.webview)
  WebViewBackgroundService.detachWebView(this)  // webView 从 Service 拿回来
  webview_container.addView(webView)
  WebViewBackgroundService.hideMiniPlayer(this)
```

### 4.4 FloatingMiniPlayerView

```java
public class FloatingMiniPlayerView extends FrameLayout {
    private final WindowManager.LayoutParams params;
    private final ImageView iconView;
    private float downRawX, downRawY;
    private int initialX, initialY;
    private boolean isPlaying = true;
    private OnActionListener listener;  // playPause, expand

    public void attachTo(WindowManager wm) { wm.addView(this, params); }
    public void detachFrom(WindowManager wm) { wm.removeView(this); }
    public void updatePlayingState(boolean playing) { isPlaying = playing; iconView.setImageResource(...); }
}
```

`LayoutParams`：
- `gravity = TOP | START`
- `width = height = (int)(56 * density + 0.5f)`
- `flags = FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS`
- `type = TYPE_APPLICATION_OVERLAY`（Android 8+ 必须）

### 4.5 拖动 + 吸边

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

### 4.6 WebViewBackgroundService 扩展

新增字段：
```java
private WebView mServiceWebView;
private FrameLayout mServiceViewHost;  // Service 内部 inflate 一个 1x1 的 FrameLayout 挂上 WebView
private FloatingMiniPlayerView mMiniView;
private String mCurrentUrl;
private String mCurrentTitle;
```

新增方法：
- `attachWebView(Activity activity, WebView webView)`：从 activity 容器 detach，挂到 `mServiceViewHost`，保存引用
- `detachWebView()`：反向，把 WebView 从 `mServiceViewHost` 移除并返回给 Activity（注意：Service 不能返回 View 对象，需 Activity 自己持有引用，详见 4.7）
- `showMiniPlayer(Context ctx, String url, String title)`：inflate `FloatingMiniPlayerView`，addView 到 `WindowManager`
- `hideMiniPlayer()`：`removeView`
- `setPlayingState(boolean)`：mini 更新图标 + 同步通知栏

`onStartCommand` 新增分支：
- `ACTION_ATTACH_MINI` → `attachWebView + showMiniPlayer`
- `ACTION_DETACH_MINI` → `detachWebView + hideMiniPlayer`

### 4.7 WebView 引用归属

**简化方案**：折叠/展开不移动 WebView 引用本身，只移动 **parent**。
- 折叠：Activity 把 `webview_container.removeView(webView)`，调用 `WebViewBackgroundService.attachWebView(webView)` → Service 内部 `mServiceViewHost.addView(webView)`。Activity 不再持有 webView 引用（事实上持有 `webView` 字段，但 view parent 已变）。
- 展开：Service `mServiceViewHost.removeView(webView)`；Activity `webview_container.addView(webView)`。这里需要 Activity 自己调 `findViewById(R.id.webview)` —— 但 setContentView 之后那个 view 引用是新的 FrameLayout 引用，不是原 WebView。

**矛盾**：setContentView 会销毁旧 view 树的引用，Activity 的 `private WebView webView` 字段指向原 WebView 对象（Java 对象仍存活），但 findViewById 会拿到新的容器 view。**解决方法**：折叠前 `saveWebViewRef = webView`，展开后 `webView = saveWebViewRef; webview_container.addView(webView);` 即可。

### 4.8 错误处理

- `canDrawOverlays()` false：跳系统设置 + Toast + **不进入折叠状态**
- `addView` 抛 `BadTokenException`：catch + stopForeground + stopSelf + 重新 attach WebView 回 Activity 失败 → Activity 重建时需检测
- mini 已 attach 时再次折叠：先 `detachFrom` 再 `attachTo`
- mini 拖动到屏外：`FLAG_LAYOUT_NO_LIMITS` + 边界 clamp

## 5. 测试

项目当前没有测试框架。本次手动验证清单：

1. 加载含 `<audio>` 的网页，点击 Toolbar 折叠按钮 → 首次跳授权页 → 授权后 mini 出现 + 音频继续
2. 拖动 mini → 拖动过程中跟随手指 → 松开后自动吸到左/右
3. 点 mini 图标 → 播放/暂停切换 → 通知栏状态同步
4. 点 mini 本体 → 回到完整 WebView 视图 + 音频继续
5. 再次折叠 → mini 仍存在 → 音频状态保留
6. mini 在设置页 / 第三方 App 上方均可显示
7. 通知栏"停止" → mini 消失 + 音频停止
8. 折叠后点系统返回键 → 回到上一 Activity (SubjectFragment/MainActivity) → mini 仍在
9. 折叠后 App 杀进程重启 → SharedPreferences 读取上次的 url/title + mini 自动出现（**待定**：本次不实现进程被杀重启的恢复，spec 注明限制）

## 6. 已知限制 / 非目标

- 不支持进程被杀重启后 mini 自动恢复
- 不在 mini 上显示页面标题
- mini 仅含播放/暂停与展开两个交互点
- mini 的位置不持久化（每次重新出现为初始位置）

## 7. 与现有架构的兼容性

- `BackgroundAudioWebView` 仍按现状工作（exploration 探索已记录其拦截 `onWindowVisibilityChanged(GONE)`）
- `MediaCallback` 静态桥不变 —— mini 操作走同一回调，webView 引用不丢
- 通知栏 + mini 双入口共存，无冲突
- `WebViewManager` 缓存逻辑不变 —— 本次不通过它传递 WebView
