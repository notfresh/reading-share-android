Android WebView 后台音频播放的核心原理是 进程保活 + 渲染器不被暂停，涉及三个层面：

1. 进程保活 — 前台服务（最关键）
Android 系统在 App 进入后台后会逐步回收进程。唯一可靠的对抗方式是启动一个前台服务（Foreground Service）：

前台服务要求显示一条持久通知

系统会给前台服务进程最高优先级，不会轻易杀死它

foregroundServiceType="mediaPlayback" 告诉系统这是媒体播放场景（Android 10+ 必须声明）

没有前台服务，进程在后台几秒~几分钟内就会被杀死，音频自然停止。

2. CPU 不休眠 — WakeLock

即使进程活着，CPU 可能进入休眠导致代码停止执行。PowerManager.PARTIAL_WAKE_LOCK 保持 CPU 运转，让音频流不断裂。WakeLock 需要在前台服务的 onCreate 里 .acquire()，在 onDestroy 里 .release()。

3. 系统声明"我在播音频" — AudioFocus + MediaSession
这是旧代码完全缺失的部分，也是最容易被忽视的：

AudioFocusRequest：App 必须向 AudioManager 请求音频焦点（requestAudioFocus），否则来电、其他 App 播放时系统不会通知你，你也无法正确暂停/恢复。请求焦点还有一个副作用——告诉系统这个 App 正在用音频通道，有助于系统保活判断。

MediaSessionCompat：向系统注册媒体会话，支持锁屏控制、耳机按键、蓝牙设备控制。必须设置 setCallback 处理 onPlay()/onPause() 才有意义。

4. WebView 渲染器不暂停 — 不调用 webView.onPause()

WebView.onPause() 会触发 pauseTimers()，暂停所有 JS 定时器和渲染。只要不主动调用它，WebView 的 JS 引擎和媒体管道就会继续运行（前提是进程还活着）。旧代码的 onPause() 确实没有调用 webView.onPause()，这一点是对的。

整体流程

所以旧代码的根本问题是：检测门（JS 事件监听）没有，其余三层即使正确实现了也没有机会被触发。