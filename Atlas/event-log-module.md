# Event Log 模块 (plugin 式事件流)

> 一句话总结: 新增独立、插件式事件日志模块 `eventlog`,26 处调用点已埋点,EventLogActivity 提供翻页查看与清空。
> Commit `055dfc9` 新增事件日志模块（plugin 式）,30 个文件,+1306 / -6

---

## 阶段 1:研究 event-log-sync-protocol

**目标**:学一个外部仓库的思路,看能不能借鉴。

**做的事**:
- 克隆 `notfresh/event-log-sync-protocol` 到 `C:/projects/duxiang-pack/event-log-sync-protocol`
- 读 `PROTOCOL.md`(391 行)+ `app.py`(197 行)+ `test_app.py` + README
- 跟你讨论本项目现状(`SimpleSyncManager` 全量交换、没事件流、没游标)
- 输出对照表

**关键决策**:
- 协议的核心:**append-only 事件流** + 客户端按 `recorded_time` 折叠
- 两种时间分开:`event_time`(客户端)/ `recorded_time`(服务端权威)
- 幂等写入:sha256 五元组前 16 位
- tombstone 删除
- 全量替换(不是 diff)
- 拉模型(pull)

---

## 阶段 2:要不要做事件流模块

**你说"我要你写一个新的独立同步模块,插件式的,能记录各种日志"** —— 新增模块,不是改 `SimpleSyncManager`。

经过几轮讨论,**决定**:

| 维度 | 拍板 |
|---|---|
| 包名 | `eventlog` |
| 接入方式 | 业务代码手动调用插件(不拦截 DAO) |
| 覆盖范围 | 先只覆盖 links |
| 服务端 | 不做(纯客户端) |
| Repository | 接口 + 默认 `SqliteEventLogRepository` |
| action 持久化 | 大写枚举 CREATE / UPDATE / DELETE |
| device_id | `Settings.Secure.ANDROID_ID` |
| `recorded_time` 改名 | **`process_time`**(语义 = 本地处理时间),值 = 客户端 `now()` |
| `event_time` | 同 `process_time` = `now()` |
| `id` 算法 | `sha256(topic\|device_id\|event_time\|entity_id\|action)` 前 16 位 |

---

## 阶段 3:模块搭起来

**做了 8 个文件**(main + test):

```
eventlog/
├── EventAction.java            enum CREATE/UPDATE/DELETE
├── EventRecord.java            不可变 POJO
├── EventLogException.java      异常
├── EventLogRepository.java     接口
├── SqliteEventLogRepository.java  SQLite 实现
├── EventLogMigrations.java     DDL(后改名 EventLogSchema)
├── EventLogClient.java         门面单例
```

**测试**:10 个用例,覆盖 create/update/delete、id 计算、ISO-8601、参数校验、since 查询。

---

## 阶段 4:反复调整(用户多次拍板)

### 4a. Repository → Store(命名调整)
你说"叫 Repo 不如叫 Store" → 全部改名为 `EventLogStore` / `SqliteEventLogStore`。

### 4b. Migrations → Schema(命名调整)
我说 `EventLogMigrations` 你说"理解起来费劲" → 改名 `EventLogSchema`。

### 4c. Schema 版本管理:3 轮来回
1. 先版本号写在代码里(简单)
2. 你问"数据库升级" → 我引入 `eventlog_meta` 表管理版本号
3. 你说"再简化" → 改用源码硬编码 `SCHEMA_VERSION = 1`
4. 你反问"为啥不再加类似 user_version PRAGMA 的字段" → 我解释 `application_id` PRAGMA
5. 你问"meta 表能查吗" → 我确认能(普通 SQL),但你说"算了,简化问题,统一管理,使用统一的源码硬编码"
6. **最终:回到最初的源码硬编码方案,没有 `eventlog_meta` 表**

### 4d. `process_time` 的反复

这是最长的拉锯战:

| 轮次 | 你的话 | 我的反应 |
|---|---|---|
| 1 | "这个开关变量内部管" | 加 `bootstrapped` 内存字段 + getter/setter |
| 2 | "你新建了一个表?" | 意识到表方案你没要,撤回 |
| 3 | 翻页卡住 → "event log 有没有 event time,这个还是真实啊,不要强行用 process time" | **关键转折**:认识到 `process_time` 在我们的场景下没意义 |
| 4 | "现在好像就是对的" | 现状:保留 `process_time` 列但代码里**只用 event_time** |

**最终语义**:
- `event_time`:bootstrap 时 = `links.timestamp`,其他场景 = `now()` ← **真实时间,本地时区,ISO-8601 带 `+HH:MM`**
- `process_time`:bootstrap 时 = event_time,其他场景 = now() ← **保留字段,UTC,ISO-8601 带 `Z`**
- 翻页游标、索引、`since` 过滤**全部用 `event_time`**

---

## 阶段 5:bootstrap(冷启动灌历史)

### 讨论流程
1. 我最早设计了 `bootstrap()` 方法 + `BootstrapItem` POJO → 你嫌复杂
2. 改方案:业务方自己拿 `store.append()` 循环写
3. 加 `bootstrapped` 标志 + `setBootstrapFlag()` / `isBootstrapped()` / `resetBootstrapFlag()`(EventLogClient 内部管)
4. **id 公式稳定的幂等性**:`event_time = links.timestamp` → 五个字段稳定 → id 不变 → `INSERT OR IGNORE` 兜底

### 实现位置
`App.onCreate` 后台线程跑,扫描 `LinkDao.getAllLinks()` 灌进 events 表。

### Bug 教训
- 我最初用 `now()` 当 `process_time` → 2953 条同毫秒 → 游标卡死
- 你发现 2953/50 只显示 50 条 → 诊断出 `process_time` 重复 → 改成 `event_time = process_time = links.timestamp`

---

## 阶段 6:22 处调用点埋点

**扫描了所有 link 写点**,决定**只改调用点,不动 DAO**(B 方案)。

**5 处 CREATE**:
- `MainActivity:602`、`SimpleSyncManager:175`、`SubjectFragment:255`、`SubjectDetailActivity:376`、`SettingFragment:466`(loop)

**6 处 DELETE**:
- `HomeFragment:848,858,1215`(含循环)、`TagsFragment:934,1296`(含循环)、`ArchiveFragment:200`

**11+ 处 UPDATE**:
- `HomeFragment:882,895,911`、`TagsFragment:949,987`、`ArchiveFragment:208,221,236`、`LinksAdapter:364,382,389,626,665,1070,1143`

**工具类 `model/LinkJson.java`**:把 `LinkItem` 转 JSON 字符串(放在 model 包,eventlog 模块不依赖业务类)。

**最终总数 26 处**(CREATE 5 + DELETE 6 + UPDATE 15)。

---

## 阶段 7:EventLogActivity UI

### 你选的方案
- 形态:**全屏 Dialog** → 改成全屏 Activity(你说"和浏览历史一样的风格")
- 列表粒度:先按所有 topic 展示(实际只有 `links`)
- 详情:点击看 JSON
- 设计:最小可用

### 翻页设计 3 轮来回
1. 触底加载(`OnScrollListener`)→ 你问"怎么翻页"
2. 改成显式"加载更多"按钮(按你拍板)
3. 加底部状态栏 `loaded / total 条`

### 数据流反复
1. 第一版:list 倒序 append(最早进 list 末尾 → 最新在 list[0])✓
2. 你说"最新的在最上面"→ 改成 insert 到顶部
3. **关键问题**:你说"按 event_time 倒序"语义下,"加载更多"该翻老还是翻新?
4. **A 翻老账方案**:进页面看最新 50 条,加载更多看更老的 → 加 `until(topic, untilEventTime, limit)` API
5. **最终选 A**

---

## 阶段 8:清空 + 重置

### 三个相关改动
1. `EventLogActivity` 加"清空事件"按钮
2. `EventLogClient.deleteAll()` + `resetBootstrapFlag()`
3. 清空按钮同时调两者 → **清空 = 真清空,下次启动会重灌历史**

### 你确认"现在好像就是对的"

---

## 阶段 9:设置页布局调整

你说"事件日志放到同步的那块下面"——指放到"后台服务器设置"组的**上面**,且按钮**与屏幕同宽**。

`fragment_slideshow.xml` 调整:
- 删掉原来 `wrap_content` 的事件日志按钮(line 40-45)
- 在 "后台服务器设置" TextView 上方插入 `match_parent` 宽度的新按钮

---

## 阶段 10:推送

### 三次确认
1. **推送范围**:全部(包含之前的 `.gitignore`、`gradle.properties`、`build.gradle` 等无关改动)
2. **commit message**:中文一句话 → "新增事件日志模块(play 式)"
3. **xshlink-log.txt**:忽略

### 结果
- Commit: `055dfc9`
- Push: `a677349..055dfc9 master -> master`
- 30 文件 / +1306 / -6

---

## 几个反复出现的元模式

按你 AGENTS.md 几条 + 我们对话规律,我观察到:

| 模式 | 表现 |
|---|---|
| **过度设计倾向被我多次抓回** | bootstrap 方法、BootstrapItem POJO、eventlog_meta 表、`EventLogMigrations` 独立类、Repository 接口、isBootstrapped 字段外置等——都曾被我提出来,然后被你拒绝 |
| **命名比代码重要** | 你多次要求改名字(Repo→Store、Migrations→Schema、`recorded_time`→`process_time`),每次都果断;说明代码先想好名字再写 |
| **"真实" vs "人造" 概念** | 你会区分字段是真实数据还是人造标记。`event_time` 是实体真实创建时间(调用方传入),`process_time` 是日志生成时刻(SDK 自动填);游标 / 索引 / 过滤只用 `event_time`,不依赖 `process_time` |
| **加按钮 > 自动触发** | 触底加载 → 改成"加载更多"按钮。你想要可见的、可点的、明确的交互 |
| **PLUGIN 式 = 业务代码主动调** | 我反复想"拦截 DAO"或"加 wrapper",你坚持"业务代码手动调"。最少耦合、最透明 |
| **删干净之前的设计不要恋战** | `eventlog_meta` 表建了又拆,schema 写了两版再撤回,你不会坚持之前的成果——感觉错了就回退 |

---

## 我犯的几个具体错误

| 错 | 教训 |
|---|---|
| `process_time` 字段用了 `now()` → 2953 条全相同 → 翻页卡死 | 字段语义要先想清楚:它代表什么?同值场景是否合理? |
| EventLogClient 加 `eventlog_meta` 表 → 你说"你新建了一个表?" | 引入新概念前先问一下,不要假设"扩展性更好"就动手 |
| Repository 拆接口 + 多个类 → 你说"叫 Repo 不如叫 Store" | 名字值得花心思 |
| bootstrap 提了 `BootstrapItem` POJO → 你嫌复杂 | 不要为"扩展性"增加类 |
| `Repository` / `Migrations` / `EventLogRepository` 多余的抽象层 | 先看现有项目代码风格(你项目里没接口、没 helper),对齐而不是发明 |
| 反复在 git 上动手前没充分确认 → 推送时又一次"先确认" | 你 AGENTS.md 第三条反复强调要先讨论再动手——我应该每次都先问 |

---

## 与协议原文的关系

本模块的设计约定现在直接写在 `event-log-sync-protocol/PROTOCOL.md` 里,本文件只记录本项目的实现历史与决策过程。要查时间字段语义、单端场景、观测 UI 等的**最终定义**,请读协议原文,不要从这里反推:

| 主题 | 协议章节 |
|---|---|
| 时间字段设计总览(`event_time` 本地时区 vs `process_time` UTC、为什么需要两个字段、时区选择理由) | `PROTOCOL.md` §2.5 |
| 字段表(§3.1)与 `event_time` / `process_time` 区分(§3.3) | `PROTOCOL.md` §3.1 / §3.3 |
| 单端场景、单端翻页游标、已知部署、事件日志观测 UI(`EventLogActivity` 即对应 §10.4 参考实现) | `PROTOCOL.md` §10.1 / §10.2 / §10.3 / §10.4 |

> 历史背景:本模块早期版本与协议原文存在若干差异(都用 UTC、翻页游标原本尝试依赖 `process_time` 等);这些差异已合并入协议原文,本文件不再保留"差异表"。协议仓库原 `DIVERGENCE.md` 已随之上游删除。

---

## 关键决策清单(按时间倒序)

| # | 决策 | 备注 |
|---|---|---|
| 1 | `event_time` 用本地时区(ISO-8601 带 `+HH:MM`),`process_time` 用 UTC(ISO-8601 带 `Z`);`event_time` = 实体真实创建时间,由调用方传入;`process_time` = 日志生成时刻,由 SDK 内部填 `now()` | 详见 `PROTOCOL.md` §2.5 / §3.3 |
| 2 | 推送 commit message 用中文一句话 | "新增事件日志模块(play 式)" |
| 3 | 翻页方向 A:进页面看最新,加载更多翻老账 | 加 `until(topic, untilEventTime, limit)` API |
| 4 | 事件日志按钮放到设置页"后台服务器设置"组上方,match_parent 宽度 | 跟浏览历史按钮分两块 |
| 5 | 清空按钮 = `deleteAll()` + `resetBootstrapFlag()` | 你确认"现在好像就是对的" |
| 6 | 翻页交互 = 显式"加载更多"按钮 | 不做触底加载 |
| 7 | bootstrap 时 `event_time = process_time = links.timestamp` | 业务调用时 = now() |
| 8 | 26 处调用点埋点(不动 DAO) | 5 CREATE + 6 DELETE + 15 UPDATE |
| 9 | LinkJson 工具类放 `model` 包 | eventlog 模块不依赖 LinkItem |
| 10 | Schema 版本号源码硬编码 `SCHEMA_VERSION = 1` | 不要 meta 表,不要 PRAGMA |
| 11 | `EventLogClient` 单例 + `bootstrapped` 内部字段 | 业务方用 `isBootstrapped()` / `setBootstrapFlag()` / `resetBootstrapFlag()` |
| 12 | `id = sha256(topic\|device\|event_time\|entity\|action)` 前 16 位 | 按协议原文 |
| 13 | action 枚举大写 | CREATE/UPDATE/DELETE |
| 14 | 设备 id 取 `Settings.Secure.ANDROID_ID` | 启动时缓存 |
| 15 | 命名:Repository→Store、Migrations→Schema | 你多次拍板改名 |
| 16 | 范围:先只覆盖 links | 后续 subjects/documents 等再扩展 |
| 17 | 服务端:暂不做 | 纯客户端 + 本地事件表 |
| 18 | 插件式:业务代码手动调 | 不拦截 DAO,不加 wrapper |
