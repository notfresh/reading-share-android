# 导出 SQLite 数据库文件 (links.db)

## 概述

在 Android 端设置页添加"导出数据库"按钮，将应用沙箱内的 SQLite 数据库文件 `links.db` 原样复制一份到 cache 目录，并通过系统分享面板交给用户（云盘、邮件、传电脑等）。

## 目标

让用户能够以**原始 SQLite 文件**的形式拿到本地所有数据，便于：
- 用 DB Browser for SQLite / `sqlite3` CLI 等工具离线查看
- 留作本地备份
- 后续可能用于跨设备迁移

## 范围与非目标

**范围内**：
- 单一 SQLite 文件 `links.db` 的复制与分享
- 在设置页加入口按钮
- 复用现有的 `ShareUtil` 分享流程与 `FileProvider`

**非目标**（本设计**不**包含）：
- 多 db 文件打包（项目里 `tag_embeddings` **是 links.db 内部的一张表**，并不存在独立 db 文件）
- 数据库导入/还原功能
- 一致性快照（WAL checkpoint / 在线备份 API）—— 用户已确认"简单复制即可"
- 加密或压缩

## 数据库事实确认

经核对代码：
- 项目只有一个 `SQLiteOpenHelper`：`db/LinkDbHelper.java`
- 数据库名常量：`"links.db"`（`LinkDbHelper.DEFAULT_DATABASE_NAME`）
- `tag_embeddings` **不是独立数据库**：它是 `TagEmbeddingDbHelper` 里定义的一段 `SQL_CREATE_TABLE`，由 `LinkDbHelper.onCreate()` 在 **links.db 内部** 创建（见 `LinkDbHelper.java:240`）
- 因此"导出数据库"在物理上等价于"导出 `links.db` 一个文件"

`links.db` 内含 12 张表：`links`、`tags`、`link_tags`、`config`、`rss_sources`、`rss_entries`、`documents`、`document_bookmarks`、`subjects`、`subject_items`、`subject_item_images`、`tag_embeddings`。

## 用户故事

> 作为用户，我希望能在设置页一键导出我的数据库文件，通过微信/邮箱/网盘传到电脑或云端，方便备份或离线分析。

## 功能需求

1. 设置页（`SettingFragment`）新增一个按钮 "导出数据库"
2. 点击后：
   - 后台线程复制 `links.db` 到 `context.getCacheDir()/exports/links_<时间戳>.db`
   - 主线程通过 `FileProvider` 取 URI，调起系统分享面板
3. 失败时给出友好 Toast，不崩溃
4. 文件名格式：`links_yyyyMMdd_HHmmss.db`（时间戳避免覆盖）

## 架构与组件

```
SettingFragment (UI)
   └─ 新按钮 onClick → ExportUtil.exportDatabaseFile(Context) → File
        · 源路径：context.getDatabasePath("links.db")
        · 目标路径：cacheDir/exports/links_<时间戳>.db
        · FileInputStream/FileOutputStream，8KB buffer
   └─ File → ShareUtil.shareDatabaseFile(Context, File) → 系统分享面板
        · FileProvider.getUriForFile(context, "${pkg}.provider", file)
        · ACTION_SEND, mime="application/octet-stream"
        · EXTRA_STREAM + ClipData.newUri (沿用 shareLinksAsFile 模式)
        · FLAG_GRANT_READ_URI_PERMISSION
        · Intent.createChooser
```

**改动点**：
- `util/ExportUtil.java`：新增 `exportDatabaseFile(Context)` 静态方法
- `util/ShareUtil.java`：新增 `shareDatabaseFile(Context, File)` 静态方法
- `ui/settings/SettingFragment.java`：在 `onCreateView` 末尾绑定新按钮 onClick
- `res/layout/fragment_slideshow.xml`：新增一个 `Button`（id：`button_export_database`，text：`"导出数据库"`）
- `res/values/strings.xml` + `values-en/strings.xml`：新增字符串 `export_database` / `Export Database`
- `res/xml/file_paths.xml`：新增 `<cache-path name="cache" path="exports/" />`（当前只声明了 `files-path` 和 `external-files-path`，缺少 cache 路径会触发 `FileProvider.getUriForFile` 抛 `IllegalArgumentException`）

**不改动**：AndroidManifest.xml（FileProvider 复用现有配置，`${applicationId}.provider` 已声明）。

## 数据流

```
[用户] 点 "导出数据库"
   ↓
[SettingFragment.onClick]
   ↓ (后台线程)
[ExportUtil.exportDatabaseFile]
  1. src = context.getDatabasePath("links.db")
  2. if !src.exists() → throw IOException("数据库尚未初始化")
  3. exportsDir = new File(cacheDir, "exports"); mkdirs
  4. dst = new File(exportsDir, "links_" + yyyyMMdd_HHmmss + ".db")
  5. copy(src → dst) with 8KB buffer
  6. return dst
   ↓
[SettingFragment.runOnUiThread]
   ↓
[ShareUtil.shareDatabaseFile]
  1. uri = FileProvider.getUriForFile(context, "${pkg}.provider", dst)
  2. Intent(ACTION_SEND).setType("application/octet-stream")
  3. EXTRA_STREAM = uri; EXTRA_SUBJECT = dst.name
  4. addFlags(FLAG_GRANT_READ_URI_PERMISSION)
  5. ClipData.newUri(...)
  6. startActivity(Intent.createChooser(...))
   ↓
[Android] 系统分享面板 → 用户选目标
```

## 错误处理

| 错误 | 触发条件 | 处理 |
|---|---|---|
| 数据库未初始化 | `src.exists() == false`（全新安装未触发任何 DAO） | 抛 `IOException("数据库尚未初始化")`，catch 后 Toast 提示 |
| 复制失败（IO 错误） | 磁盘满 / 权限异常 | `FileOutputStream` 抛 `IOException` → `ShareUtil` catch → Toast |
| `cacheDir` 创建失败 | 系统存储异常 | 同上 |
| `FileProvider` 配置缺失 | 路径不在 `file_paths.xml` 白名单 | 抛 `IllegalArgumentException` → 现有 catch 链 Toast 报错（同时 Log.e 提示检查 `file_paths.xml`） |
| 用户取消分享 | 系统行为 | 无需处理；`cacheDir` 文件保留，由系统按需清理 |

## 边界与并发

- **并发写**：应用其他 Fragment 可能正在写库。复制过程中数据库被并发修改时，理论上可能产生不一致快照。用户已明确接受此风险，**不在本设计范围**。
- **多线程**：复制操作在 `SettingFragment` 现有 `new Thread(...).start()` 模式中执行（与 `exportAndShare`/`exportAndSave` 一致），避免主线程 IO。
- **大文件**：单 SQLite 文件通常 < 50 MB（业务数据），8KB buffer 流式复制，内存占用稳定。
- **重复点击**：每次生成新时间戳文件名，不冲突；旧的 cache 文件保留至系统清理。

## 测试

### 单元测试（`src/test`，使用 Robolectric）

新增 `ExportUtilTest.java`：

| 用例 | 步骤 | 断言 |
|---|---|---|
| `testExportDatabaseFile_copiesContent` | Robolectric `RuntimeEnvironment.application`；预置 `databases/links.db` 写入 `"hello"`；调 `exportDatabaseFile`；读目标文件 | byte 相等 + 文件名匹配 `^links_\d{8}_\d{6}\.db$` |
| `testExportDatabaseFile_overwritesExisting` | 连续调用两次 | 第二次目标 size 与新源一致，无残留旧字节 |
| `testExportDatabaseFile_sourceMissingThrows` | 删除源 `links.db`；调 `exportDatabaseFile` | 抛 `IOException`，message 含"数据库尚未初始化" |

`ShareUtil.shareDatabaseFile` 涉及 `Context.startActivity`，纯 JVM 跑不动，留到手动验证。

### 手动验证（最低门槛）

| 场景 | 步骤 | 期望 |
|---|---|---|
| 有数据，正常导出 | 插入若干 link/tag/subject；点 "导出数据库" | 系统分享面板出现，文件名形如 `links_20260816_143022.db`；选"邮件给自己"；附件能下载 |
| 还原校验 | 用 DB Browser for SQLite 打开收到的 `.db` | 能看到 `links`/`tags`/`subjects`/`tag_embeddings` 等所有表；行数一致 |
| 全新安装 | 清空应用数据；启动；不进行任何操作；点按钮 | Toast 提示"数据库尚未初始化"，不崩溃 |
| 重复点击 | 在 5 秒内点 3 次 | cache 目录出现 3 个时间戳不同的文件；最后 1 个被分享面板使用 |
| 冷启动 | 重启手机后再点 | 行为同上 |

### 验收标准

- [ ] 设置页出现 "导出数据库" 按钮，文案清晰
- [ ] 点击 → 系统分享面板弹出，文件名形如 `links_<时间戳>.db`
- [ ] 接收方用 SQLite 工具打开，能看到 12 张表
- [ ] 任何失败路径均有 Toast 提示，不崩溃
- [ ] `cacheDir` 不被本次导出永久污染（旧文件会被系统清理）

## 实现步骤概览

1. `file_paths.xml` 新增 `cache-path`
2. `strings.xml` / `values-en/strings.xml` 新增文案
3. `fragment_slideshow.xml` 新增按钮
4. `ExportUtil.exportDatabaseFile` 实现
5. `ShareUtil.shareDatabaseFile` 实现
6. `SettingFragment` 绑定新按钮 onClick（沿用现有 `new Thread` + Toast 模式）
7. `ExportUtilTest` 三个单测
8. 手动验证 5 个场景

## 复用参考

- `ShareUtil.shareLinksAsFile`（`util/ShareUtil.java:44-125`）：FileProvider + ACTION_SEND + ClipData 的完整模式，新方法 `shareDatabaseFile` 沿用此模式，仅 mime 改为 `application/octet-stream`
- `SettingFragment.exportAndShare`（`ui/settings/SettingFragment.java:488-541`）：后台线程复制 + UI 线程分享的模式，新按钮的 onClick 直接照搬
- `ExportUtil.exportToJson`（`util/ExportUtil.java:32`）：文件输出到 cacheDir/exports/ 的目录约定