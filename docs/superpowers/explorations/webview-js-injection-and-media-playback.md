---
name: webview-js-injection-and-media-playback
description: WebView 的 JS 注入、JS Interface 与媒体播放控制的整体架构 (WebViewActivity / WebViewManager / WebViewBackgroundService)
metadata:
  type: exploration
---

# WebView JS 注入与媒体播放控制

## 范围
- [WebViewActivity.java](../../app/src/main/java/person/notfresh/readingshare/WebViewActivity.java)
- [WebViewManager.java](../../app/src/main/java/person/notfresh/readingshare/WebViewManager.java)
- [WebViewBackgroundService.java](../../app/src/main/java/person/notfresh/readingshare/WebViewBackgroundService.java)

## 一、整体思路

App 需要在 WebView 内打开网页，并支持:
1. **后台继续播放音频** —— 即便 Activity 进入后台，网页里的 `<audio>` / `<video>` 也不能停
2. **系统级控制** —— 锁屏 / 通知栏 / 蓝牙耳机按钮可以播放/暂停/停止
3. **状态可见** —— Java 层要知道 "现在是不是有声音在响"，从而决定是否启动前台服务保活

实现路径：JS ↔ Java 双通道 + 前台服务保活

```
JS (网页)                                              Java (App)
────────────                                           ────────────
audio/video.play/pause/ended
   └─→ AndroidMediaInterface.setMediaPlaying(true/false)  ─→  onMediaPlayingChanged()
                                                                 ├─ MediaSessionCompat.setPlaybackState(...)
                                                                 └─ 启动/停止 WebViewBackgroundService

用户点通知栏播放/暂停
   └─ (Service 静态回调) MediaCallback.onPlayRequested()  ─→  webView.evaluateJavascript("__playMedia()")
                                                                    └─→ window.__activeMedia.forEach(play)

AudioFocus 变化
   └─ AudioFocusRequest.OnAudioFocusChangeListener        ─→  webView.evaluateJavascript("暂停/恢复所有 audio/video")
```

## 二、JS 注入清单

### 1. JS 接口 (addJavascriptInterface)
[WebViewActivity.java:459](app/src/main/java/person/notfresh/readingshare/WebViewActivity.java#L459)

```java
webView.addJavascriptInterface(new MediaInterfaceObject(), "AndroidMediaInterface");
```

`MediaInterfaceObject` ([L1145-1150](app/src/main/java/person/notfresh/readingshare/WebViewActivity.java#L1145-L1150)) 仅暴露一个方法:

| JS 调用 | Java 侧动作 |
| --- | --- |
| `AndroidMediaInterface.setMediaPlaying(boolean)` | `runOnUiThread(() -> onMediaPlayingChanged(isPlaying))` |

> 注: 之前版本里还有 `isMediaSessionActive()` / `isTongyiSite()` 等接口（见 [WebViewDesc.md](app/src/main/java/person/notfresh/readingshare/WebViewDesc.md) 的旧版本描述），当前代码里**只剩 `setMediaPlaying`**。`WebViewDesc.md` 与实现已不同步。

### 2. 标题提取注入 (onPageFinished)
[WebViewActivity.java:480-532](app/src/main/java/person/notfresh/readingshare/WebViewActivity.java#L480-L532)

页面加载完成后注入三段脚本:
1. `document.title` —— 拿原始 `<title>`
2. 微信公众号专用 `wechatTitleScript` —— 优先取 `#activity-name` / `rich_media_title`，否则 `og:title`，最后回退 `document.title`
3. 延迟 500ms 再跑一次 —— 应对晚加载的标题（SPAs）

返回结果是 JSON-encoded 字符串，Java 端剥掉首尾 `"` 后写入 `pageTitleCache`。

### 3. 媒体监听器注入 (onPageFinished) ★核心★
[WebViewActivity.java:537-572](app/src/main/java/person/notfresh/readingshare/WebViewActivity.java#L537-L572)

`onPageFinished` 末尾注入的 IIFE 是整个媒体控制的中枢。脚本干这些事:

#### 3.1 暴露全局控制函数

```js
window.__pauseMedia = function() {
  window.__activeMedia.forEach(e => e.pause());
};
window.__playMedia = function() {
  window.__activeMedia.forEach(e => e.play().catch(()=>{}));
};
window.__stopMedia = function() {
  window.__activeMedia.forEach(e => { e.pause(); e.currentTime = 0; });
  window.__activeMedia.clear();
};
```

#### 3.2 维护活跃媒体集合

- 用 `Set` 保存所有当前正在播放的 `<audio>` / `<video>` 元素引用
- 给每个元素挂 `play` / `pause` / `ended` 监听:
  - `play` → 加入集合 + `notify(true)`
  - `pause` / `ended` → 从集合移除 + 集合空时 `notify(false)`
- `__listenersAttached` 标志避免重复挂监听

#### 3.3 处理"监听前已在播放"

页面加载完成时可能已有媒体在播（JS 监听器还没挂上就触发了）。脚本会扫描一遍现有 `audio`/`video`，把"未暂停且未结束"的直接加入集合，再调一次 `notify(true)`。

#### 3.4 MutationObserver 跟踪动态元素

```js
var obs = new MutationObserver(ms => ms.forEach(m =>
  m.addedNodes.forEach(n => {
    if (n.tagName === 'AUDIO' || n.tagName === 'VIDEO') attach(n);
    else if (n.querySelectorAll) [...n.querySelectorAll('audio,video')].forEach(attach);
  })));
obs.observe(document.body || document.documentElement, {childList:true, subtree:true});
```

解决 SPA / 异步插入的媒体元素监听不到的问题。

#### 3.5 幂等性

外层 `if(window.__mediaListenerAttached)return;` 保证多次注入（页面跳转、刷新）只挂一次。

### 4. WebViewManager 的兜底注入
[WebViewManager.java:39-45](app/src/main/java/person/notfresh/readingshare/WebViewManager.java#L39-L45)

"存档返回"时把 WebView 缓存到 `WebViewManager`，先注入一段脚本暂停所有媒体元素，避免被缓存的页面继续在后台空播。

## 三、Java 侧媒体控制

### 1. AudioFocus 处理
[WebViewActivity.java:855-876](app/src/main/java/person/notfresh/readingshare/WebViewActivity.java#L855-L876)

- `AUDIOFOCUS_LOSS` / `LOSS_TRANSIENT` → `evaluateJavascript` 暂停所有 audio/video
- `AUDIOFOCUS_GAIN` 且 Java 侧记录 `audioPlaying=true` → 恢复播放
- 注意这里**不调用 `requestAudioFocus`**，注释明确写了"WebView 自己管理焦点"

### 2. MediaSessionCompat
[WebViewActivity.java:881-910](app/src/main/java/person/notfresh/readingshare/WebViewActivity.java#L881-L910)

- 标志 `FLAG_HANDLES_MEDIA_BUTTONS | FLAG_HANDLES_TRANSPORT_CONTROLS`
- Callback:
  - `onPlay()` → `__playMedia()`
  - `onPause()` → `__pauseMedia()`
  - `onStop()` → `stopAudioPlayback()`（→`__stopMedia()`）
- 初始 state = `STATE_NONE`，actions 含 `PLAY/PAUSE/PLAY_PAUSE/STOP`

### 3. 状态联动 `onMediaPlayingChanged(boolean)`
[WebViewActivity.java:941-963](app/src/main/java/person/notfresh/readingshare/WebViewActivity.java#L941-L963)

JS 回调 `setMediaPlaying(true/false)` 后的 Java 动作:

1. 更新 `audioPlaying` 标志
2. 同步 `MediaSessionCompat.setPlaybackState` 为 `STATE_PLAYING` / `STATE_PAUSED`
3. 仅当前台音频停止 + App 在前台时，主动停掉保活服务和 WakeLock
   - 后台时不立即停服务，避免瞬时波动把服务干掉
   - 服务的生命周期其实主要由 `onPause` / `onResume` / `onDestroy` 控制

### 4. 后台保活 (WebViewBackgroundService)
[WebViewBackgroundService.java](app/src/main/java/person/notfresh/readingshare/WebViewBackgroundService.java)

- `onCreate` / `onStartCommand` 各 `acquire` 一个 30 分钟的 `PARTIAL_WAKE_LOCK`
- 用 `NotificationCompat.MediaStyle` 显示"播放中"通知，带播放/暂停 + 停止两个 action
- Service 不直接调 `evaluateJavascript`，而是通过**静态回调 `MediaCallback`** 把事件转给 Activity（[L913-938](app/src/main/java/person/notfresh/readingshare/WebViewActivity.java#L913-L938)），同进程内不绕广播/IPC
- Activity 收到回调后 `runOnUiThread` → `evaluateJavascript("__playMedia / __pauseMedia / __stopMedia")`

`onPause` 无条件启动前台服务（[L839-844](app/src/main/java/person/notfresh/readingshare/WebViewActivity.java#L839-L844)），不依赖 `audioPlaying`，注释解释是 "不请求 AudioFocus，WebView 自己管理焦点"。

### 5. 通知栏按钮 → JS 的完整链路

```
通知栏 "暂停" 点击
   → PendingIntent 触发 Service.onStartCommand(ACTION_PLAY_PAUSE)
   → Service 反转 isPaused，调用 sMediaCallback.onPauseRequested()
   → Activity 的 MediaCallback.onPauseRequested() → runOnUiThread
   → webView.evaluateJavascript("if(window.__pauseMedia)window.__pauseMedia()")
   → window.__pauseMedia() → __activeMedia.forEach(pause)
   → 各元素 pause 事件触发 → AndroidMediaInterface.setMediaPlaying(false)
   → onMediaPlayingChanged(false) → MediaSessionCompat 状态更新
```

## 四、关键调用关系一览

| 触发源 | JS 入口 | Java 处理 |
| --- | --- | --- |
| 网页中 audio.play() | `el.addEventListener('play')` → `notify(true)` | `setMediaPlaying(true)` → `onMediaPlayingChanged` |
| 网页中 audio 自动播完 | `el.addEventListener('ended')` → `notify(false)` | `setMediaPlaying(false)` → MediaSession 状态更新 |
| 系统媒体键/锁屏 | MediaSessionCompat.Callback | `__playMedia / __pauseMedia / __stopMedia` |
| 通知栏按钮 | Service.MediaCallback (静态) | 同上 |
| 别的 App 要音频焦点 | OnAudioFocusChangeListener | 遍历 `document.getElementsByTagName('audio/video')` 全部 pause |
| WebView 重新获得焦点 | 同上 + `AUDIOFOCUS_GAIN` 分支 | 仅在 `audioPlaying=true` 时全部 play |
| 存档 WebView | `WebViewManager.storeWebView` | 注入脚本暂停所有媒体 |
| 通知栏"停止" | `onStopRequested` → `__stopMedia()` | 清空 active 集合 |

## 五、值得注意的设计点

1. **用元素引用 `Set` 而不是查询 DOM**: 每次 `__playMedia/__pauseMedia` 都遍历 `window.__activeMedia`，避免重复扫整个 DOM（且能跨 shadow DOM 友好的前提下更高效）。
2. **JS 监听器的幂等性**: `__mediaListenerAttached` + 每个元素上的 `__listenersAttached` 双层防重，跨页面导航 / 缓存复用都不挂多份监听。
3. **前台服务的"无 AudioFocus"策略**: Service 不主动 `requestAudioFocus`，完全依赖 WebView 自管。这样后台播放不会因为 App 抢焦点而打断其它 App 的音乐，但反过来 WebView 内音频焦点丢失时也不会被 Service 救回。
4. **MediaCallback 用静态字段做同进程桥**: 比 `BroadcastReceiver` / `Messenger` 简单得多，前提是 Service 与 Activity 同进程（同 APK 默认如此）。`onDestroy` 里 `setMediaCallback(null)` 防泄漏。
5. **`__stopMedia` 不仅 pause 还 `currentTime=0` + `clear()`**: 比单纯 pause 彻底 —— 集合清空意味着后续 `playMedia` 不会再启它，符合"停止"语义。

## 六、潜在改进点 (非本次任务，仅记录)

- [WebViewManager.java:29-36](app/src/main/java/person/notfresh/readingshare/WebViewManager.java#L29-L36) `HashMap` 取首 key 不能保证 FIFO；用 `LinkedHashMap(accessOrder=true)` 更合适。
- 通知的 `setContentTitle` 是固定字符串"音频播放中"，没用 `MediaSessionCompat.getMetadata()`，锁屏 UI 信息有限。
- `setMediaPlaying` 的回调没用 `value ->` 形式，注入的 JS 是同步 `notify()`，但 WebView 与 JS 的桥接本身是异步的，连续 play/pause 短间隔可能丢事件或抖动。
- `WebViewDesc.md` 中描述的 `isMediaSessionActive()` / `isTongyiSite()` 接口在当前代码中已不存在（[L1145-1150](app/src/main/java/person/notfresh/readingshare/WebViewActivity.java#L1145-L1150) 只剩 `setMediaPlaying`），文档与实现已脱节。