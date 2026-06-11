---
name: duxiang-cross-platform-design
description: 读享(Reading Share)跨平台设计与功能抽取——Android 现状抽象 + iOS/HarmonyOS 移植参考。灵魂: 收藏自己的收藏夹,直达目的地,没有中间商转注意力差价。
metadata:
  type: design
  created: 2026-06-11
  scope: 跨平台(iOS + HarmonyOS) 移植参考
  reader: 未来的你(iOS+鸿蒙实现者)
  source-project: 读享 Android(67 Java 文件,~3000 行核心)
---

# 读享 · 跨平台设计与功能抽取

## Layer 0 · 灵魂宣言

读享是一款**反平台的阅读收藏工具**。它不创造内容、不排名内容、不推荐内容、不从你的注意力差价中赚取收益。

它的全部价值在于三件事:

1. **收藏你自己的收藏夹** —— 你是唯一的策展人,没有别人的"精选"、没有"热门"、没有"编辑推荐";
2. **直达目的地** —— 一次点击直达原文链接所指的内容,不被任何中间页面劫持、不被任何"读完本文你可能还想看"截流;
3. **不赚注意力差价** —— 它不试图延长你的停留时间,不推送通知、不做"日活"指标、不通过把你的注意力二次贩卖给广告主/平台/算法变现。

**你的注意力是唯一资产,本应用是你存放这份资产的私人金库**。

字面上"读享"是 read-share,但这里的 share **不是分享给别人**,而是**分享给未来的自己**。

---

## Layer 1 · 「我们不做什么」清单(核心资产)

这一层是文档**最重要的一节**,所有 iOS / 鸿蒙实现者必须先读完。下面是「读享绝不会做的事」反平台立场清单,每一条都有"为什么不能加"的理由。

| # | 我们不做 | 替代方案 | 为什么 |
|---|---------|---------|--------|
| 1 | 不做"推荐" / "你可能也喜欢" | 洗牌模式(仅基于你自己库内的随机) | 算法推荐的数据来源是别人的行为,不是你的兴趣 |
| 2 | 不做"热门" / "趋势" / "今日要闻" | Archive 浏览 + 标签筛选 | 热门是注意力二次贩卖的入口 |
| 3 | 不做账号体系、不做云端强制绑定 | 导入/导出 JSON+CSV 三种出口 | 账号体系是平台锁定你的第一步 |
| 4 | 不做"分享到 XX 平台"按钮(除读享格式卡片外) | 走系统 share sheet + 读享富文本卡片格式 | 平台分享按钮是给平台导流的饵 |
| 5 | 不做"打开 App 立即弹广告/弹升级" | 启动直达上次阅读位置 | 启动页是注意力劫持最廉价的手段 |
| 6 | 不做后台通知推送 | 用户主动订阅主题(Subject)的更新检查 | 推送是平台强拉回你的绳索 |
| 7 | 不做任何形式的 A/B / 灰度测试 / 行为埋点 | 仅本地 click_count 用于个人统计 | 行为埋点是平台对你的二次剥削 |
| 8 | 不做"关注某用户" / "收藏夹公开订阅" | 读享输出格式是单向广播到自己的站(duxiang.ai 可选) | 社交关系是平台留住你的护城河 |
| 9 | 不做"阅读打卡" / "成就" / "等级" | 仅本地 click_count + 时间分布 | 游戏化是平台延长停留的工具 |
| 10 | 不做"AI 总结"自动生成作为默认 | 仅在 CrawlUtil 中可选调用,存为 summary 字段 | AI 总结是平台截取你判断力的入口 |
| 11 | WebView 默认拦截所有外链跳转(intent://, snssdk://, weixin:// 等) | 用户可在设置中切到"允许外链跳走" | 链接是平台间跳转的最常见劫持点 |
| 12 | 不做"分享到读享好友"功能 | 读享输出格式仅用于"分享给未来的自己"(或导出到自己的站) | 社交分享是平台社交裂变的引擎 |
| 13 | 不做云端"为我推荐主题" | 主题完全是用户自己创建的本地资产 | 推荐 = 算法注意力 |
| 14 | 不做"打开 App 引导问卷" / 新手教程过 3 步以上 | 首次启动直接进入 Home | 引导是平台教育用户接受新劫持的步骤 |

### 新功能准入门槛

任何想加进读享的新功能,先问三个问题:

- 这功能会不会延长用户停留?
- 这功能会不会从用户注意力中抽取数据送给第三方?
- 这功能会不会成为"被中间商利用的入口"?

三个问题任何一个答"是",**默认否决**。例外需要明确书面记录原因。

---

## Layer 2 · 概念模型

读享的世界由 **5 个核心实体**和 **4 个跨切关注点**构成。每个实体都是**你私有**的,没有"别人的"概念。

### 2.1 核心实体(按重要性)

#### ① Link —— 一切的入口

```
Link {
  id: UUID
  url: URL                    // 唯一标识
  title: String               // 用户可改、AI 摘要可参考
  remark: String              // 链接周围的原始文本(如微信分享卡片下方的描述)
  summary: String?            // 可选 AI 摘要(CrawlUtil 调 duxiang.ai/api/abstract)
  sourceApp: String           // 来源 app 包名(取证级审计字段)
  originalIntent: String      // 原始 Intent 字符串
  targetActivity: String      // 目标 Activity 名
  timestamp: Long             // 添加时间
  pinned: Boolean             // 是否置顶
  clickCount: Int             // 本地点击次数(仅用于个人统计,不上报)
  isArchived: Boolean         // 是否已归档(实际存在 archive.db 中)
}
```

**语义**:
- **provenance 是一等公民**:`sourceApp / originalIntent / targetActivity` 是"我在哪看到这条链接"的取证级记录,未来回看时能反查来源
- **三个生命周期**:存活在主 db / 已归档在 archive db / 已删除(不可恢复)
- **链接永远不主动过期**:错判"我以后可能想回来看这个" 的成本是归档,而不是删除

#### ② Tag —— 用户的心理文件系统

```
Tag {
  id: UUID
  name: String                // 用户自取
  orderIndex: Int             // 拖拽排序(间隔 10,便于插入)
  highlight: Boolean          // 视觉强调(不是"收藏",只是更亮一点)
  createdAt: Long
}

LinkTag { linkId, tagId }     // 多对多关联表
```

**语义**:
- **不是属性,是文件夹** —— 标签是用户自己心智模型的镜像,没有"自动标签"、没有"系统推荐标签"
- **删除时强制二选一**:删标签(链接保留)/ 删标签及全部关联链接(用户必须选)—— 因为"标签描述的是链接还是用户的心智分类"是两个不同的决策
- **顺序由用户掌控**:拖拽排序,不算法化

#### ③ Subject —— 多模态研究画布(最原创的概念)

```
Subject {
  id: UUID
  name: String                // 用户自取(如「Transformer 论文」「东京咖啡地图」)
  orderIndex: Int             // 拖拽排序(间隔 10)
  createdAt: Long
  iconPath: String?           // 可选自定义图标(用于桌面快捷方式)
}

SubjectItem {
  id: UUID
  subjectId: UUID
  // 三种内容之一,可组合:
  linkId: UUID?               // 关联一个 Link
  remark: String?             // 用户自写文本片段
  images: [ImagePath]         // 最多 10 张图
  orderIndex: Int
  isArchived: Boolean         // 单独项的归档(不是删,只是不再参与列表显示)
  archivedAt: Long?
  isLinkDeleted: Boolean      // 墓碑字段——源 Link 已被删时,SubjectItem 仍保留作为引用痕迹
}
```

**语义**:
- **不是文件夹** —— 一个 SubjectItem 可以同时含 1 个链接 + 1 段自写备注 + 几张图。这是"研究进行中的快照"
- **归档而非删除** —— 单项可以 archive,源 Link 删除后仍保留墓碑
- **可视化为桌面快捷方式** —— Subject 可以创建桌面图标,直达该 Subject 的详情

#### ④ Document —— 一等公民的本地文档

```
Document {
  id: UUID
  filePath: String            // app-private 存储路径
  fileType: Enum              // PDF / Markdown / LaTeX / Text
  title: String               // 从 PDF 提取的标题/或文件名
  importedAt: Long
  bookmarks: [DocumentBookmark]  // 每页书签,精确到页码+坐标
}

DocumentBookmark {
  documentId: UUID
  page: Int
  offset: Float               // 滚动偏移
  label: String?              // 用户可选备注
  createdAt: Long
}
```

**语义**:
- **与 Link 平级** —— 不是"附件",是数据模型的一等公民,参与搜索、参与统计
- **自带阅读器** —— hand-rolled PDF renderer(不依赖第三方库,因为"完全离线可用"是承诺)
- **hand-rolled outline 提取** —— 用正则扫描 PDF 字节流找 `/Title`/`/Dest`,处理 UTF-16BE/UTF-16LE/UTF-8 三种编码破损

#### ⑤ Archive —— 平行宇宙(不是标签)

```
// 物理上是独立 SQLite 文件:
MainDB:   links + tags + link_tags + documents + subjects + ...
ArchiveDB: links(同一 schema 的子集)
```

**语义**:
- **平行宇宙而非 tag**:归档的链接不污染标签计数,不参与 HomeFragment 搜索,不能进 Subject,但**不被删除**
- **同一 UI 组件 + 不同 db handle**:LinksAdapter 接受 db name + userEnv 参数,同样的 swipe 手势在 MainDB 是"归档(深蓝按钮)",在 ArchiveDB 是"还原(深红按钮)"
- **滑动手势是唯一销毁式入口**:左滑显示归档按钮,1 秒自动收起(防误触)

### 2.2 跨切关注点(不属于任何实体但贯穿全局)

#### ① Provenance 审计 —— 任何接收入口都记录

所有接收入口(Share Intent、Clipboard、文件导入、手动添加)必须记录:
- 来源 app 包名
- 原始 Intent 字符串
- 目标 Activity(如果是 share intent 路由过)
- 入库时间

#### ② RecentTagsManager —— 标签输入肌肉记忆持久化

```
RecentTags {
  tagIds: [UUID]              // LRU 5-10(用户可配)
  // SharedPreferences 中以逗号分隔字符串存储
}
```

保存对话框显示最近 5-10 个标签为可点击 chip,避免重复输入。第 200 次打"机器学习"标签时的省力器。

#### ③ ClickStatistics —— 仅本地的个人统计

```
ClickStatistics {
  linkId: UUID
  count: Int
  lastClickedAt: Long
}
```

只有"个人统计"页面用,反算法立场:**不参与推荐、不参与排序加权**。

#### ④ SearchHistory —— 仅本地的搜索历史下拉

```
SearchHistory {
  query: String
  searchedAt: Long
  // 按 searchedAt 倒序,最多 N 条
}
```

仅本地,下拉提示用户最近搜过什么。**不上报、不"热门搜索"、不"相关搜索"**。

---

## Layer 3 · 平台无关接口契约

按"持久化层 → 入站层 → 阅读器层 → 出站层 → 跨切层"组织。所有接口用 TypeScript 风格定义(最易读、最易翻译成 Swift/Kotlin/ArkTS)。

### 3.1 持久化层(Persistence)

```typescript
// 物理上是两个独立 db 文件,接口统一
interface Storage {
  mainDB(): Database      // 主库:links, tags, link_tags, documents, subjects, subject_items, configs
  archiveDB(): Database   // 归档库:links(同 schema 子集)
  // 关键原则: 不存在"软删除标志位"作为查询条件
  // 归档 = 物理移到 archive.db
}

interface LinkRepository {
  insert(link: Link): UUID
  getById(id: UUID): Link?
  list(filter: LinkFilter, sort: LinkSort): Link[]   // filter 支持 tag-set, no-tag, pinned, by-date
  update(id: UUID, patch: Partial<Link>): void
  delete(id: UUID): void                              // 物理删除,不可恢复
  archive(id: UUID): void                             // 物理移到 archive.db
  restore(id: UUID): void                             // 从 archive.db 移回 main.db
  incrementClickCount(id: UUID): void                 // 仅本地统计
}

interface TagRepository {
  insert(tag: Tag): UUID
  list(order: 'custom' | 'name' | 'count'): Tag[]
  reorder(tagIds: UUID[]): void                       // 拖拽排序
  delete(id: UUID, mode: 'tag-only' | 'cascade'): void   // 强制二选一
}

interface SubjectRepository {
  insert(subject: Subject): UUID
  list(order: 'custom' | 'name'): Subject[]
  insertItem(subjectId: UUID, item: SubjectItem): UUID
  listItems(subjectId: UUID, includeArchived: boolean): SubjectItem[]
  reorderItems(subjectId: UUID, itemIds: UUID[]): void
  archiveItem(itemId: UUID): void
  markLinkDeleted(itemId: UUID): void                 // 墓碑——源 Link 没了但 SubjectItem 保留
}

interface DocumentRepository {
  insertFromFile(srcPath: String, fileType: FileType): UUID
  addBookmark(documentId: UUID, bookmark: DocumentBookmark): void
  listBookmarks(documentId: UUID): DocumentBookmark[]
}
```

**关键约束**:
- **跨平台一致性**:`archiveDB()` 必须在物理上独立(不能是 flag),iOS 用独立 .sqlite 文件、鸿蒙用独立 rdb
- **删除的不可恢复性**:`delete()` 是物理删除;想要"撤销"得在删除前 archive
- **Tag 删除模式枚举**:`tag-only` 留链接、`cascade` 留 ID 关联、用户**必须**显式选

### 3.2 入站层(Inbound — 三种入口都必须有 provenance)

```typescript
interface InboundLink {
  url: URL
  title: String?
  remark: String?            // 链接周围原始文本
  source: Provenance         // 必填——三种入口之一
}

interface Provenance {
  kind: 'share-intent' | 'clipboard' | 'file-import' | 'manual'
  sourceApp: String?         // share 入口有,manual 没有
  originalIntent: String?    // share 入口有
  targetActivity: String?    // share 路由过的目标
  observedAt: Long
}

interface ShareIntentReceiver {
  // 三种 MIME 必支持: text/*(含 URL/纯文本/HTML) + application/pdf
  // 三种动作: ACTION_SEND(单条) + ACTION_SEND_MULTIPLE(批量) + ACTION_VIEW(URL 直接打开)
  register(): void           // 启动时注册 intent-filter
  handle(intent: SystemIntent): InboundLink?    // 解析后交给 LinkRepository
}

interface ClipboardWatcher {
  start(): void              // 仅在 App 进入前台 + 有焦点时启动
  stop(): void               // App 进入后台 或 失焦 立即停止
  debounceMs: 500            // 500ms 延迟,防误触
  // 只嗅探一次,不在后台 polling
  onUrlDetected(url: URL, callback: (link: InboundLink) => void): void
}

interface FileImporter {
  // 支持: PDF / Markdown(.md) / LaTeX(.tex) / 纯文本(.txt)
  import(srcPath: String, mimeType: String): Either<Document, InboundLink>
  // PDF 走 DocumentRepository
  // .md/.tex/.txt 当作"text-typed link"——内容做 remark,文件名做 title
}
```

**关键约束**:
- **Provenance 不可缺** —— 任何入站路径必须填 Provenance,没有"无源"链接
- **Clipboard 嗅探边界** —— **仅前台+有焦点+500ms 延迟+一次性**,三个条件全部 AND
- **Share Intent 解析宽容** —— 微信/小红书/B 站分享的"卡片文本"是脏的,URL 提取要鲁棒(在前 100 行 HTML 内找 `<title>`,site-specific 选择器有 juejin 路径)

### 3.3 阅读器层(Reader — 信任边界)

```typescript
interface WebViewContainer {
  load(url: URL): void       // 启动加载
  // 关键接口:scheme 拦截策略
  onCustomSchemeIntercepted(scheme: String): SchemeDecision
  // 'allow' = 跳走 / 'block' = 留在本应用 / 'confirm' = 弹确认
  // 默认: 'block'(这是 WebView 信任边界的核心)

  onPageTitleReady(callback: (title: String) => void): void
  // 1. onPageStarted 立即回调
  // 2. 500ms 延迟重试(应对 SPA 异步加载)

  goBack(): Boolean          // 返回上一页,栈空时退出容器
  goForward(): Boolean
}

// 三个关键 WebView 子系统(每个都是独立子系统)
interface WebViewBackgroundPlayer {
  // 后台播放音频时,WebView 必须存活
  startForegroundService(): void    // 启动前台服务
  acquireWakeLock(): void           // 唤醒锁
  setupMediaSession(metadata: MediaMetadata): void   // 系统媒体控制
  stop(): void
}

interface WebViewCacheManager {     // 已设计,见 2026-06-11 spec
  isInWhitelist(url: URL): Boolean
  addToWhitelist(url: URL): void
  removeFromWhitelist(url: URL, deleteFiles: Boolean): void
  listAllCaches(): CachedPageMeta[]
  totalSize(): Long
}

interface WebViewCacheClient {       // 装在 WebView 上的拦截器
  onShouldInterceptRequest(req: WebResourceRequest): WebResourceResponse?
  // 仅在白名单 URL 上工作,非白名单返回 null(零开销)
  // 内部 OkHttp 抓取,边写边返回;失败回退磁盘
  onMainFrameFailed(url: URL): void
  // 触发 file:// 回退 + 顶部"缓存模式"横条
}

interface DocumentReader {
  open(documentId: UUID): void       // 启动 PDF/MD/LaTeX 阅读
  // 必须支持: 横竖屏切换、工具栏自动隐藏、点哪跳哪、书签
  // 离线 100% 可用
}

interface PDFOutlineExtractor {
  extract(filePath: String): PDFOutlineNode[]
  // 实现: 直接正则扫描 PDF 字节流,找 /Title / /Dest
  // 编码回退顺序: UTF-16BE BOM → UTF-16LE BOM → UTF-8
  // 父节点计数推断层级
  // 不依赖第三方 PDF 库
}
```

**关键约束(不可妥协)**:
- **WebView 默认拦截所有非 http(s) scheme** —— 这是文档最重要的非功能性约束
- **后台播放必须前台服务** —— 不能被系统杀死
- **PDF 阅读器必须 hand-rolled** —— 不依赖第三方库("100% 离线可用"是承诺)
- **WebView 离线缓存只缓存文字为主页面** —— 不缓存视频音频、不缓存 POST、不缓存登录态页(已设计的硬约束)

### 3.4 出站层(Outbound — 你的数据,你出口)

```typescript
interface OutboundShareFormatter {
  // 输出读享格式卡片——IM 富文本卡片规范
  format(link: Link, summary: String?, remark: String?): String
  // 格式:
  // 【{title}】
  // 摘要:{summary 或 remark 或空}
  // 链接:{url}
  // 来自:ReadingShare
}

interface SystemShareSheet {
  shareText(text: String, title: String?): void
  shareJSON(json: String, fileName: String): void       // 用系统 chooser
  // 必须: 排除读享自身不出现在 chooser 中
}

interface ImportExport {
  export(format: 'json' | 'csv', scope: 'all' | 'links' | 'tags' | 'subjects'): FilePath
  // 三种出口: 系统 chooser / app-private dir / public Documents dir
  // JSON 后缀约定: .readshare.json
  import(path: FilePath, mode: 'merge' | 'overwrite'): ImportResult
}

interface PersonalSync {
  // 可选功能——用户显式启用后才走
  // 不构成账号体系,只是单向 push 到用户自有的 duxiang.ai 端点
  pushLinks(links: [Link], targetUrl: URL, extraData: JSONObject): SyncResult
  pushTaggedCollection(tagId: UUID, targetUrl: URL): SyncResult
  configureSSL(trustAll: Boolean): void    // 自签名证书支持
}
```

**关键约束**:
- **输出格式是身份卡** —— 不是裸 URL,是读享人格式(这是反平台立场的体现:你分享的不是 URL,是"我是读享用户")
- **ImportExport 必须支持三种出口** —— 数据属于用户,用户想往哪搬往哪搬
- **PersonalSync 是可选 + 显式启用** —— 默认关闭,不构成"账号体系"

### 3.5 跨切层(Cross-cutting)

```typescript
interface RecentTagsManager {
  record(tagId: UUID): void
  top(n: Int): Tag[]                // 默认 5,用户可配 1-10
  // LRU 持久化在 SharedPreferences(Android)/ UserDefaults(iOS)/ Preferences(鸿蒙)
}

interface SearchHistory {
  record(query: String): void
  recent(n: Int): String[]          // 倒序
  clear(): void
}

interface ClickStatistics {
  recordClick(linkId: UUID): void
  getForLink(linkId: UUID): ClickStats
  getForAllLinks(): [Link, ClickStats]   // 倒序,仅本地
  // 永远不上报
}

interface ConfigStore {
  get(key: String): ConfigValue?
  set(key: String, value: ConfigValue): void
  // 关键配置项:
  // - defaultTab: 'home' | 'subject' | 'rss' | 'random'
  // - recentTagsWindow: 1-10
  // - externalLinkMode: 'block' | 'confirm' | 'allow'   // WebView 外链策略
  // - readingMode: 'normal' | 'smooth'                    // 平滑翻页模式
  // - syncServerUrl: String?                              // 个人同步端点
}

interface URLNormalizer {
  canonical(url: URL): URL
  // 规则: 去掉 #fragment,保留 ?query
  // 同一个 URL 不同 fragment 视为同一缓存
}

interface DesktopShortcut {
  create(subjectId: UUID, iconPath: String?): void
  createForUrl(url: URL, title: String, iconPath: String?): void
  // iOS: NSUserActivity / Shortcuts API
  // Android: app shortcut API
  // 鸿蒙: form / shortcut
}

interface Shuffler {
  shuffle(links: [Link]): [Link]
  // 规则:
  // - pinned 不参与洗牌
  // - 5 秒内新保存的链接不参与洗牌(LINK_ADD_INTERVAL_MS)
  // - 仅基于本地库内随机,无任何算法推荐
}
```

**关键约束**:
- **ConfigStore 的 5 个 key 必须在 iOS/鸿蒙 一致实现** —— 这些是用户跨设备带不走的"使用习惯"
- **Shuffler 必须本地随机** —— 不接受"个性化推荐算法",哪怕是基于用户历史的协同过滤
- **RecentTagsManager 窗口大小可配** —— 1 到 10 之间

---

## Layer 4 · 平台特定实现差异表

按 9 个子系统组织,每个子系统一张表:**抽象 → Android(现状参考)→ iOS(建议)→ HarmonyOS(建议)→ 反平台提醒**。

### 4.1 持久化层(Storage)

| 抽象 | Android(参考) | iOS(建议) | HarmonyOS(建议) | 反平台提醒 |
|---|---|---|---|---|
| `Storage.mainDB()` | SQLiteOpenHelper + raw SQL | GRDB.swift / SQLite.swift | `@ohos.data.relationalStore` | — |
| `Storage.archiveDB()` | **独立 archive.db 文件** | **独立 .sqlite 文件** | **独立 rdb** | **必须物理独立**,不是 flag |
| `LinkRepository.insert()` | `LinkDbHelper` raw SQL | GRDB Codable | relationalStore ValuesBucket | — |
| `TagRepository.delete(mode)` | `deleteTag` / `deleteTagWithLinks` 两方法 | enum `TagDeleteMode` 强制 | enum `TagDeleteMode` 强制 | 不可妥协:必须强制二选一 |
| `SubjectRepository.markLinkDeleted` | `isLinkDeleted` boolean | 同样 boolean | 同样 boolean | 墓碑是研究画布的真相 |

### 4.2 入站层(Inbound)

| 抽象 | Android | iOS | HarmonyOS | 反平台提醒 |
|---|---|---|---|---|
| **Share Intent 注册** | `<intent-filter>` in AndroidManifest.xml | **Share Extension**(target 独立 App Extension)+ 主 App 通过 App Group 通信 | Share Kit (FA) + WantAgent | 必须支持 text/* + PDF |
| **Share Intent 解析** | `Intent.EXTRA_TEXT` / `EXTRA_STREAM` | `NSExtensionItem.attachments` / `userInfo` | `Want.data` + `Want.parameters` | 微信/小红书分享的脏文本要鲁棒解析 |
| **Provenance 提取** | `getCallingPackage()` / `Intent.extras` | ⚠️ **iOS 无法获取来源 app**——降级为 `sourceApp=null, originalIntent=ShareExtension` | `Want.parameters["ohos.extra.param.srcBundleName"]` | **iOS 限制**:provenance 在 iOS 上是降级版本 |
| **Clipboard 嗅探** | `ClipboardManager.addPrimaryClipChangedListener` | `UIPasteboard.general` 监听 `changeCount` | `pasteboard.getSystemPasteboard().on('update')` | **三 AND 必须** |
| **File Importer** | `Intent.ACTION_VIEW` + `application/pdf` MIME | `UIDocumentPickerViewController` | `picker.select` | 路径必须复制到 app-private 存储 |

### 4.3 阅读器层 — WebView

| 抽象 | Android | iOS | HarmonyOS | 反平台提醒 |
|---|---|---|---|---|
| **WebView 容器** | `android.webkit.WebView` | `WKWebView` | `@ohos.web.webview.WebviewController` | — |
| **scheme 拦截** | `WebViewClient.shouldOverrideUrlLoading` | `WKNavigationDelegate.decidePolicyFor` | `onInterceptRequest` / `onUrlLoadIntercept` | **默认 block** 所有非 http(s) scheme |
| **title 提取(3 回退)** | 1. onPageStarted 立即 2. 500ms 重试 3. onReceivedTitle | 1. `WKNavigationDelegate.didCommit` 2. `evaluateJavaScript("document.title")` 3. 500ms 重试 | 1. `onPageBegin` 2. `runJavaScript` 3. 500ms 重试 | — |
| **后台播放音频** | `WebViewBackgroundService` + 前台通知 + WakeLock + MediaSession | `AVAudioSession` + `MPNowPlayingInfoCenter` + Background Modes audio | `AVSession`(后台音频) + `ohos.media.avsession` | 必须用**前台服务/Background Mode**保活 |
| **WebView 5 实例 LRU** | `WebViewManager` 5 槽 LRU | 自实现 LRU 持有 5 个 `WKWebView` | 自实现 LRU 持有 5 个 `Web` | 5 是经验值,跨平台可调 |
| **WebView 离线缓存** | `CachingWebViewClient` + OkHttp + file:// + `<base>` 注入 | `WKURLSchemeHandler` 自定义 scheme + 拦截 | `WebviewController` 拦截 + 自定义 scheme | 完整 spec 见 `2026-06-11-webview-offline-cache-design.md` |

### 4.4 阅读器层 — Document

| 抽象 | Android | iOS | HarmonyOS | 反平台提醒 |
|---|---|---|---|---|
| **PDF Renderer** | `android.graphics.pdf.PdfRenderer` | `PDFKit`(`PDFDocument` / `PDFPage`) | 需**自实现**或跨平台库(PDFium WASM 桥接) | Android/PDFKit 是原生,鸿蒙缺原生 → 评估跨平台 |
| **PDF Outline 提取** | `PdfOutlineExtractor`(hand-rolled 正则扫字节流) | **复用同一份** Java/TS 代码,Swift 重写一份 | **复用同一份** TS 代码 | 不依赖第三方 PDF 库——**手写正则**是承诺 |
| **Markdown 渲染** | `commonmark-java` | `MarkdownUI` (SwiftUI) | Web 组件 + marked.js | — |
| **书签持久化** | `DocumentBookmark` 入 documents 表 | `DocumentBookmark` 入主 db | 同 | 必须可导出 JSON 跨设备 |

### 4.5 出站层(Outbound)

| 抽象 | Android | iOS | HarmonyOS | 反平台提醒 |
|---|---|---|---|---|
| **读享格式卡片** | `OutboundShareFormatter` 输出 `【】\n摘要:\n链接:\n来自:ReadingShare` | 同字符串 | 同字符串 | 字符级一致——这是身份卡 |
| **系统 Share Sheet** | `Intent.createChooser` + `EXTRA_EXCLUDE_COMPONENTS` 排除自己 | `UIActivityViewController` + `excludedActivityTypes` | `Share Kit` chooser | 排除读享自身 |
| **导出 JSON/CSV** | 三种出口(chooser / public Documents / app-private) | 三种出口(`UIActivityViewController` / FileManager 公共目录 / app sandbox `Documents`) | 三种出口(Share / 公共目录 / app sandbox) | 公共目录在三平台对应**用户可访问**的目录 |
| **PersonalSync HTTP** | `HttpURLConnection` + 自签 SSL trust-all | `URLSession` 自签证书(Info.plist `NSAppTransportSecurity` 配置) | `http.createHttp` + 证书配置 | **必须显式启用**——不构成账号体系 |

### 4.6 跨切层(Cross-cutting)

| 抽象 | Android | iOS | HarmonyOS | 反平台提醒 |
|---|---|---|---|---|
| **RecentTagsManager** | `SharedPreferences("recent_tags")` LRU | `UserDefaults` LRU | `@ohos.data.preferences` LRU | 1-10 可配窗口 |
| **SearchHistory** | `SharedPreferences` LRU | `UserDefaults` LRU | `@ohos.data.preferences` LRU | 仅本地 |
| **ClickStatistics** | `click_count` 字段 + `getTagsWithCount` 聚合 | 同 schema | 同 schema | **永远不上报**——这是反平台硬约束 |
| **ConfigStore 5 key** | 已在 §3.5 列出 | 必一致 | 必一致 | 跨设备使用习惯同步走 JSON 导出 |
| **URLNormalizer** | `canonicalUrl(url)` strip `#fragment` | 同 | 同 | — |
| **Shuffler** | 本地 `Collections.shuffle` + 5s 保护窗 | `Array.shuffle(using:)` + 5s 保护窗 | `Array.shuffle` + 5s 保护窗 | **仅本地随机**——禁止任何协同过滤 |
| **DesktopShortcut** | App Shortcuts API (Static + Dynamic) | `NSUserActivity` 配合 Spotlight / Shortcuts | Shortcut Kit / form | 长按桌面图标直达 Subject 或 URL |

### 4.7 关键反平台差异(必须警觉)

| 反平台立场 | Android | iOS | HarmonyOS | 风险 |
|---|---|---|---|---|
| WebView 默认拦截外链 | `shouldOverrideUrlLoading` 拦截自定义 scheme | `WKNavigationDelegate` 拦截 `decidePolicyFor` non-http URL | `onUrlLoadIntercept` 拦截 | iOS **不拦截** Universal Link 时会跳走——Universal Link 也要列入拦截 |
| Clipboard 仅前台嗅探 | `onResume` 启动 / `onPause` 停止 | `applicationDidBecomeActive` / `willResignActive` | `onPageShow` / `onPageHide` | iOS 14+ 系统会**弹通知**"X app 读了你剪贴板"——必须如实告知用户 |
| 启动直达 | `Splash → MainActivity → HomeFragment` 0 引导 | `LaunchScreen` → Home 0 引导 | Splash → Index 0 引导 | **禁加** 启动引导问卷/广告 |
| 不做"分享到 XX 平台"按钮 | 仅有"分享"系统 chooser | 仅有 `UIActivityViewController` 系统 chooser | 仅有 Share Kit 系统 chooser | 不能加"B 站分享"自定义按钮——那是给 B 站导流 |

### 4.8 关键技术风险(提前预警)

1. **iOS WebView 离线缓存**:WKURLSchemeHandler 对 cross-origin 资源有限制,可能需要 `WKContentRuleList` 配合
2. **鸿蒙 PDF Renderer 缺原生**:需评估 `pdfium` 跨平台方案,**这会破坏"100% 离线 + 不依赖第三方"承诺**——需要决策
3. **iOS Provenance 缺失**:iOS 不暴露来源 app,必须在文档中显式承认 iOS 版的 provenance 是降级版本
4. **鸿蒙 Share Intent 心智**:WantAgent 模型与 Android Intent 类似,但参数结构不同,需要重新设计 Provenance 解析

---

## 附录 A · 已沉淀的设计资产索引

- WebView 离线缓存: `2026-06-11-webview-offline-cache-design.md`
- 搜索历史 Chrome 优化: `2026-06-09-search-history-chrome-polish-design.md`
- 搜索历史输入框: `2026-06-09-search-history-input-chrome-design.md`
- 搜索历史下拉: `2026-06-09-search-history-dropdown-reference.md`
- 洗牌模式: `2026-05-30-shuffle-mode-design.md`
- 主题列表拖拽排序: `2026-05-30-subject-list-drag-sort-design.md`
- 文档书签与阅读进度: `2026-05-13-document-bookmark-reading-progress-design.md`
- 软著申请: `2026-06-10-duxiang-ruanzhu-design.md`

## 附录 B · 关键文件路径(Android 现状参考)

- `MainActivity.java` —— 启动 / 分享接收 / 剪贴板嗅探入口
- `WebViewActivity.java` + `WebViewManager.java` —— 阅读器主体(含 5 实例 LRU)
- `BackgroundAudioWebView.java` + `WebViewBackgroundService.java` —— 后台播放
- `core/Synchronizer.java` —— PersonalSync 桥接(~400 行)
- `db/LinkDao.java` + `db/LinkDbHelper.java` —— 主库 schema + DAO
- `model/LinkItem.java` —— Link 实体构造时即解析
- `ui/home/HomeFragment.java` —— 主屏幕(列表+搜索+标签+洗牌+多选+快捷方式)
- `ui/subject/SubjectFragment.java` —— 主题画布
- `ui/document/DocumentFragment.java` + `DocumentViewerActivity.java` + `BookmarkItem.java` —— 文档
- `util/PdfOutlineExtractor.java` —— hand-rolled PDF outline(正则扫字节流)
- `util/ShareUtil.java` —— 读享格式卡片输出
- `util/CrawlUtil.java` —— 标题/描述抓取
- `util/RecentTagsManager.java` —— 标签 LRU
- `util/SwipeActionsHelper.java` —— 滑动归档/还原(深蓝/深红按钮)
- `util/SubjectUtil.java` —— 主题项目添加
- `util/BilibiliUrlConverter.java` —— B 站短链 + 自定义 scheme
- `WebShortcutActivity.java` —— 桌面快捷方式入口

---

## 附录 C · 不在范围内(明确放弃)

- **任何云端账号体系** —— 数据属于用户,用户持有导出权
- **任何形式的算法推荐** —— 包括"基于你历史的协同过滤"
- **任何平台分享按钮** —— 除读享格式卡片外不主动分享到第三方
- **任何后台通知推送** —— 用户主动订阅(Subject 主题)才检查更新
- **任何启动引导/广告/升级提示** —— 启动直达 Home
- **视频/音频内容缓存** —— WebView 离线缓存仅限文字为主页面
- **登录态/复杂媒体的 PWA** —— 不做 PWA、不做 Service Worker
- **AI 自动总结**作为默认行为 —— 仅可选字段,用户主动触发

