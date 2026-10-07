# eventlog 同步功能 — 阶段性汇报

> 状态: 已部署到 reading-share-android + event-log-sync-protocol,端到端初步走通。
> 时间: 2026-10-02
> 阶段: 双向同步 push + pull 链路可用,UI/手动同步/同步日志/服务端可观测性已就绪。
> 未提交: docs/eventlog-push-plan.md(原阶段性汇报草稿,本文件替代之)。

## 1. 仓库与 commit 状态

### `event-log-sync-protocol`(协议 + 服务端)

| commit | 内容 |
|---|---|
| `f577a5d` | feat(protocol): §4.1.1 POST /events/batch + §10.5 多端双向部署案例 |
| `aa9cd6a` | feat(protocol): §10.5 客户端同步触发策略(启动一次 + 手动按钮) |
| `b8c4ca7` | feat(server): POST /events/batch(初版独立端点,后被合并) |
| `2423fa7` | refactor(protocol): §4.1 单条+批量合并到 POST /events 一个端点 |
| `0c34a4c` | refactor(server): POST /events 接受单条或批量 body(识别结构) |
| `2e0e2b8` | feat(server): bind 0.0.0.0:80 + GET /stats observability endpoint |

### `reading-share-android`(客户端)

| commit | 内容 |
|---|---|
| `c8094be` | refactor(eventlog): POST /events URL(批量/单条共用) |
| `d9de819` | feat(eventlog): schema 升级 + process_time 维度查询 |
| `bb27767` | feat(eventlog): SyncPointStore + SyncConfig(同步基础设施) |
| `e417467` | feat(eventlog): Pusher/Puller 接口 + HTTP 实现(PROTOCOL §4.1 + §4.2) |
| `98b7c27` | feat(eventlog): EventLogClient 接入 pushPending + pull 门面方法 |
| `91ff4ea` | feat(sync): SimpleSyncManager 静态 getter(轻量读配置) |
| `5bd72d6` | feat(eventlog): SyncLogStore(同步结果持久化日志) |
| `8625baf` | feat(ui): 手动同步按钮 + 最近同步日志展示(EventLogActivity) |
| `a1c94b9` | feat(eventlog): 启用启动同步(解 TODO) |
| `3dd29fd` | refactor(ui): 删除 SettingFragment "开始同步" 按钮 |
| `feef16f` | fix(ui): secret 与 URL 独立失焦保存 + secret 框密码可见切换 |
| `1ae3bf3` | fix(eventlog): pull 捕获所有 Exception(不再只 EventLogException) |
| `8ce1089` | fix(eventlog): secret trim 写入 + 读取双端(防换行污染 Authorization 头) |
| `6aea36c` | fix(eventlog): 删重复赋值 this.secret = secret |
| `c7eb879` | feat(eventlog): pull LWW 折叠到 link 表(§5.4) |

## 2. 已落地的能力

| 能力 | 落地位置 |
|---|---|
| PROTOCOL §4.1 单条+批量同端点 | PROTOCOL.md + app.py post_events() |
| PROTOCOL §10.5 双游标契约(push=本地 process_time / pull=服务端 process_time) | EventLogClient + cursor key `push\|pull:<baseUrl>:<topic>` |
| PROTOCOL §10.5 游标 key 必须含 server_url | cursor key 拼装规则 |
| 服务端纯管道(不分配客户端 process_time 权威) | app.py 单次写入 now_utc() 但保留客户端原值,客户端 POST 必须填本地入库时间 |
| §2 鉴权(Authorization = EVENT_LOG_SECRET 字符串相等) | app.py require_auth() + HttpEventLogPusher/Puller |
| §10.5 客户端同步触发策略:启动一次 + 手动按钮 | App.java triggerStartupSync() + EventLogActivity.triggerManualSync() |
| §10.5 同步可观测性(persistent log) | eventlog_sync_log 表 + SyncLogStore + EventLogActivity refreshLastSync() |
| §5.4 LWW 折叠(pull → link 表) | LinkApplier 接口 + LinkEventApplier 实现 + LinkDao.replaceById() |
| UI 手动同步按钮 + 密码可见切换 | activity_event_log.xml + fragment_slideshow.xml |
| 配置入口复用 SettingFragment(server_url + secret_key 失焦保存) | SettingFragment + SimpleSyncManager 静态 setter/getter |
| 服务端 GET /stats 鉴权后聚合 | app.py stats() |

## 3. 已知 bug 与未完成项

### Bug(已修但需要客户端侧最终验证)

| bug | 修复 | 验证状态 |
|---|---|---|
| secret 含换行 → Authorization 头抛 IllegalArgumentException | commit 8ce1089(写入读 + HTTP client 构造都 trim) | 已修,用户手动重装后生效 |
| server_url / secret_key 写入不同 SharedPreferences 文件导致 URL 改动不生效 | commit feef16f(统一走 SimpleSyncManager.saveServerUrl/saveSecretKey) | 已修 |
| pull 抛非 EventLogException 被吞,sync_log 无记录 | commit 1ae3bf3(改 catch (Exception)) | 已修 |
| UI 上"开始同步"按钮已弃用但还在 | commit 3dd29fd(删除) | 已修 |

### 已知未完成项

| 项 | 原因 |
|---|---|
| App.onCreate 启动同步 race condition:triggerStartupSync 跑时 bootstrapEventLogIfNeeded 还没把本地 link 灌进 events 表,pushPending 看到 batch 空写"推送 0 条"日志 | 未修。客户端 link 永远推不上去,但 pull 不受影响 |
| 服务端 process_time 覆盖策略:现服务端 POST /events 时把客户端 event_time 字段作为权威存(单条实现);批量端点把 process_time 用单一 now_utc 覆盖客户端原值。§11.1 默认多端是"服务端权威",但本部署客户端必填 event_time 仅作占位,服务端覆盖 | 已 commit 0c34a4c。PROTOCOL §10.5 段落已描述 |
| ANDROID_ID 不稳定性:用户曾问"device_id 会经常变化吗"。清数据 / 重签名 key / 切用户会变,导致 eventlog id 五元组洗牌、服务端幂等失效 | 未解决,plan 文档记为独立待办 |
| 端到端 emulator 测试:从未跑过真 emulator(本机无 Android 模拟器)。目前只跑了服务端 curl 模拟 + 用户手机真机手动测试 | 未补 |
| 老 seed 事件(link-1..10)data 字段无 `id`,服务端未重写,客户端 pull 拉到这批会折叠失败(item.id=0 → LinkDao.replaceById 跳过) | 客户端通过 LinkJson.optLong("id", 0L) 拿默认值,需后续重 push 或清服务端 |

### 客户端 debug 签名问题

用户多次报告"包安装失败 / 重装后才生效"。原因链:

1. 不同 commit 编译的 debug APK 用同一个 debug.keystore 签名 → 覆盖安装应成功
2. 但部分场景(SharedPreferences 状态不一致)需要重装才能让新代码生效
3. 用户当前走的是"装新 APK + 手动填 secret + 失焦保存"路径,**重装是隐性步骤**

## 4. 服务端运行时状态

- **进程**: PID 398809(sudo python3 app.py via sudo -E env PATH=$PATH hermes-agent venv)
- **绑定**: 0.0.0.0:80(对外暴露)
- **SECRET**: `qRUCwiMWUloTCHeFtl6IOKwctcG-RyoQ`(已通过对话泄露,仅本地测试用)
- **DB**: `/root/projects/event-log-sync-protocol/events.db`
- **诊断钩子**: `@app.before_request` 打印 Authorization 头前 50 字符(临时,后续可移除)
- **临时端点**: `/events/batch` 作为 `/events` 的 transient alias(同 _write_batch 实现,客户端已不用)
- **统计**: 截至汇报时 16 条 link + 3 条 note

## 5. 下次继续的入口

如果用户回来想继续推进,入口候选(按"短快"原则):

1. **修 bootstrap race condition**(让 triggerStartupSync 等 bootstrap 完成)
2. **清服务端老 seed**(link-1..10 data 无 id 字段,客户端折叠失败)+ 重 push 用数字 entity_id + data.id
3. **服务端诊断钩子清理**(@app.before_request Authorization 日志临时)
4. **ANDROID_ID 不稳定文档** — plan 已记为待办,独立写一篇
5. **手 push 按钮改造**:让 secret 输入框从 onFocusChange 改 TextWatcher(用户已决定保留失焦,跳过)
6. **手动同步按钮"未配置"状态可观测**:弹对话框后写一条 sync_log(失败记录),便于排查
7. **eventlog 客户端 SDK 单测**:mock EventLogStore / SyncPointStore / Pusher / Puller,跑 pushPending + pull 流程(60% 真度)

## 6. 用户已拍板但未落地的决定

- 服务端 SECRET 永久保持当前 `qRUCwiMWUloTCHeFtl6IOKwctcG-RyoQ`(短,非生产强度,用户接受)
- 启动同步保持"onCreate 一次"(不做周期/前后台触发,用户拍)
- 手动同步按钮 + 同步日志 UI 留在 EventLogActivity(不在 SettingsFragment)
- /events/batch 端点保留 transient alias(用户接受"端点可换 URL")

---

汇报人: Hermes Agent · 汇报时间: 2026-10-02 20:40 UTC+8
