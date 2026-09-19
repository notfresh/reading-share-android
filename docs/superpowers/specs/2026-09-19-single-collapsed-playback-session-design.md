# 单折叠后台播放会话设计

**日期**: 2026-09-19
**状态**: 待用户评审

## 目标

用户折叠页面 A 后，可以继续浏览和关闭普通页面 X；A 的音频、mini 入口和恢复页面均不得被 X 的生命周期影响。系统同时只保留一个折叠后台播放页面；折叠 B 时替换 A。

## 核心约束

- 折叠页面 A 的原 WebView 不得 `removeView`、`destroy`、重新加载或迁移到其他 parent。Chromium 在 WebView detach 时会停止 AudioTrack。
- A 必须从创建时就运行在独立 document task；折叠时仅将这个 task 退到后台。
- 普通页面 X 也是独立 document task，但它没有后台播放会话所有权。
- 前台 Service、mini、通知栏控制只服务于当前折叠会话的 owner A。

## 架构

新增 `CollapsedPlaybackSession` 单例，代表唯一的后台折叠会话。它持有：

- `ownerTaskId`：A 所在 task，用于 mini 点击恢复。
- `url`：A 的页面地址，用于通知和恢复兜底。
- `MediaCallback`：只指向 A 的播放控制实现。
- `active`：会话是否有效。

`WebViewBackgroundService` 不再以“最后一个启动 Service 的 WebViewActivity”为数据来源，而是读取 `CollapsedPlaybackSession`。普通页面 X 不得覆盖 session 的 URL、taskId 或 callback。

## 流程

### 折叠 A

1. A 将自身 callback、URL 和 taskId 注册为唯一 session owner。
2. Service 从 session 显示 mini，并以 session callback 提供通知栏控制。
3. A 调用 `moveTaskToBack(true)`；WebView 保持 attach，音频继续。
4. A 的 task 退后台后显示主页 task。

### 打开和关闭 X

1. X 新建为普通 document task，创建和销毁自己的 WebView、wake lock、media session 与 audio focus。
2. X 不注册、清除或替换 `CollapsedPlaybackSession`。
3. X 不启动、停止或销毁后台播放 Service。
4. X 退出后，A 的 mini、Service、callback 和音频保持不变。

### 点击 mini

1. Service 读取 session 的 `ownerTaskId`，调用 `moveTaskToFront` 恢复 A。
2. 卸载 mini。
3. A 在前台恢复时清除 session，并停止后台 Service。

### 折叠 B

1. 如果 A session 有效，controller 先请求 A 停止播放，并清除 A session、mini 和 Service。
2. B 注册为新的唯一 owner。
3. Service 为 B 显示 mini，B 的 task 退后台。

## 生命周期边界

| 操作 | 普通页面 X | 折叠 owner A |
| --- | --- | --- |
| `onResume` | 不操作后台播放 Service/session | 恢复前台时结束 session 和 Service |
| `onPause` | 不启动后台播放 Service | 折叠后 Service 已由 session 启动 |
| `onDestroy` | 只释放 X 自己的资源 | 未显式替换时不应销毁；被替换时由 session controller 明确停止 |
| 通知栏播放控制 | 不注册 callback | 只调用 A 的 callback |

## 错误处理

- mini 展开时 task 不存在：清除失效 session 和 mini，使用 session URL 新开普通阅读页作为兜底。
- 进程被杀：不恢复 session 或 mini，属于非目标。
- 折叠 B 时旧 owner 不可用：直接清理旧 session 并继续注册 B。

## 验证

1. A 播放并折叠，回主页后音频不断、mini 存在。
2. 打开 X，A 音频不断且 mini 不消失。
3. 退出 X，A 音频和 mini 仍存在；点 mini 恢复 A 而非 X。
4. 折叠 B，A 停止且 mini 只恢复 B。
5. A 折叠后，通知栏播放/暂停仍只控制 A。