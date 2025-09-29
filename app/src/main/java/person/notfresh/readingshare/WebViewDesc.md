### 范围
- 涉及文件：`WebViewActivity`, `WebViewManager`, 相关服务 `WebViewBackgroundService`（间接）、适配器入口 `LinksAdapter`（打开页面）

### 现状与流程
- **页面打开**
  - `LinksAdapter` 点击链接 → `WebViewActivity` 携带 `url` 启动。
  - `WebViewActivity` 在 `onCreate`：
    - 先从 `WebViewManager.getInstance().getWebView(url)` 尝试命中缓存；
    - 命中：将缓存 `WebView` 从旧父容器移除后挂载到当前容器，Toast 提示“已恢复存档页面”，并初始化 `MediaSession`；
    - 未命中：`findViewById(R.id.webview)` 获取新实例，`setupWebView()` 配置后加载 `url`。
- **页面配置与媒体**
  - `setupWebView`：
    - 启用 JS、DOM storage、混合内容、媒体自动播放（多处重复 setMediaPlaybackRequiresUserGesture(false)）。
    - 设置 `WebChromeClient` 并在 `onPermissionRequest` 直接允许。
    - 注入 `AndroidMediaInterface` JS 接口；注入错误防护脚本。
    - 站点特例：`tongyi.aliyun.com` 在 `onPageFinished` 注入容错脚本（拦截 mediasession 报错并提供空实现）。
  - `MediaSessionCompat`：
    - 初始化、设置可播放/暂停的状态，`AndroidMediaInterface.setMediaPlaying` 回调中更新会话状态。
    - `isAudioPlaying` 仅返回标志位（TODO 注明实际检测有待完善）。
- **后台播放与服务**
  - `onPause`：如果认为在“播放中”，注入 JS 定时重置 `audio/video` 的 `play()` 以保持播放；启动 `WebViewBackgroundService`（API≥26 使用前台服务）。
  - `onStop`：`webView.setWillNotDraw(false)` 防止后台暂停渲染。
  - `onResume`：恢复 `WebView` 计时器与前台，停止 `WebViewBackgroundService`，若 `mediaSession` 为 null 则重新初始化。
  - `WakeLock`：`onCreate` 申请，`onDestroy` 释放。
- **缓存与恢复**
  - 菜单“存档返回”：从父容器移除 `webView`，调用 `WebViewManager.storeWebView(url, webView)` 缓存，置 `webView = null`，`preserveCache = true` 并 `finish()`。
  - `finish()`：
    - 若 `preserveCache == true`：不清理历史/缓存，但会 `webView.destroy()`（注意：存档路径把 `webView` 置 null 了，所以这里不会执行，对应注释“避免在 onDestroy 销毁”）。
    - 若未保留缓存：如果 `WebViewManager` 有该 URL，调用 `removeWebView(url)`；否则 `onDestroy` 会清理并 `destroy()` 实例。
- **WebViewManager**
  - 全局单例，`Map<String, WebView>` 缓存，最大 5 个，超过后“移除最早加入的”（但用 `HashMap` 的迭代首元素，顺序不稳定）。
  - `storeWebView` 时注入 JS 暂停媒体元素；`removeWebView`/`clearAll` 中销毁实例。

### 风险清单
- **缓存淘汰不稳定**
  - 使用 `HashMap` 取“第一个 key”并不能保证 FIFO/LRU 语义。高频使用会导致缓存效果不可预期。
  - 建议：改为 `LinkedHashMap`（`accessOrder=true` 实现 LRU），或引入专用 LRU 策略。
- **线程与 UI 约束**
  - 所有 `WebView` 操作应在主线程：`destroy()`、`evaluateJavascript()`、父容器移除/添加。`WebViewActivity` 基本在主线程，`WebViewManager` 没有保证，调用方必须小心。
  - 建议：在 `WebViewManager` 内部确保在主线程执行（如通过 `Handler(Looper.getMainLooper())` 包装），或明确在接口注释中强调主线程调用约束。
- **父容器关系与泄漏**
  - 缓存 `WebView` 强引用会间接持有 `Activity` 上下文（若 `WebView` 以 `Activity` 为 Context），跨 Activity 生命周期可能造成泄漏。
  - 现有“存档返回”路径合理地移除父容器并把引用交由 `WebViewManager`；但若 Activity 崩溃或异常中断，缓存可能残留。
  - 建议：统一在宿主 `Activity/Fragment` 销毁时调用 `WebViewManager.clearAll()` 或增加作用域绑定；使用 `Application` Context 创建 `WebView`（折中：自定义 Context 包装），并确保复用前/后正确移除父容器。
- **URL 作为 key 的稳定性**
  - 动态参数/fragment 导致同一页面多 key，降低命中率。
  - 建议：对 URL 进行标准化（移除无关 query、统一大小写、去 `#fragment`），或使用业务层边界（如域名+主路径）作为 key。
- **权限与安全**
  - `onPermissionRequest` 直接 `grant(request.getResources())` 风险较高；易被恶意站点提权（如摄像头/麦克风）。
  - 建议：白名单域名或显式弹窗确认，仅对确有需要的资源授权。
- **媒体保持策略的脆弱性**
  - 通过注入 JS `play()` 并 `setInterval` 方式保持播放，依赖站点脚本实现，易被站点改动或浏览器策略限制破坏。
  - 建议：尽量依赖标准媒体策略和服务端能力；对关键站点做更稳健的兼容；并考虑系统层面的 Background audio 能力与前台服务通知渠道。
- **重复/多余设置**
  - `setMediaPlaybackRequiresUserGesture(false)` 调用了多次；保持一次即可，减少噪音。
- **WakeLock 生命周期管理**
  - `onDestroy` 释放，若异常路径提前返回可能未释放；需保证所有退出路径释放（当前常规路径已处理，存档返回提前释放了 `mediaSession`，但对 WakeLock 未提前释放，仍依赖 `onDestroy`）。
- **站点特例散落**
  - 对 `tongyi.aliyun.com` 的特例逻辑分布在 `onCreate` 和 `onPageFinished`，后续还可能新增其他站点，建议聚合封装站点策略。

### 优化建议（按优先级）
- 高优先
  - 将 `WebViewManager` 改为 LRU（`LinkedHashMap<>(cap, load, true)`），并确保淘汰稳定；在其方法中强制主线程操作。
  - 审查并收紧 `onPermissionRequest` 授权策略（最少权限/提示确认/域名白名单）。
  - 统一 `WebView` 主线程销毁与父容器移除流程的工具方法，避免遗漏。
- 中优先
  - URL key 标准化，提升缓存命中率；抽象“站点策略”集中管理（特例注入脚本封装）。
  - 移除重复的 `setMediaPlaybackRequiresUserGesture(false)` 设置；对保持播放逻辑加超时/兜底。
- 低优先
  - 统计缓存命中率/淘汰次数/平均驻留时间，指导容量配置（5 的默认值可根据数据调整）。
  - 完善 `isAudioPlaying` 的实际检测（如通过 JS 查询或 MediaSession 状态回查）。

### 结论
- 现有方案能实现“WebView 存档与复用、后台继续播放”，但在缓存策略、权限安全与线程/生命周期严谨性方面有改进空间。完成上述优化后，稳定性、可维护性与安全性会显著提升。