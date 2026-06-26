---
name: webview-offline-cache-design
description: WebView 指定 URL 的离线缓存——用户主动加入白名单后,每次打开优先走网络并更新缓存,网络失败时回退到磁盘缓存并显示顶部"缓存模式"横条
metadata:
type: design
created: 2026-06-11
scope: WebViewActivity + 新增 OfflineCacheFragment
storage: <filesDir>/web_cache/<sha1(url)>/
whitelist-storage: SharedPreferences "web_cache_prefs"
---

# WebView离线缓存功能 - 设计方案

##1. 目标

让用户能把 WebView 中打开的指定网页加入"离线缓存"。加入后:
-联网打开 →正常显示,同时后台静默更新缓存
- 主框架加载失败(无网/超时/服务故障) → 自动回退到磁盘缓存,顶部显示"缓存模式"横条
- 未加入白名单的网页 →行为完全不变,无任何开销

针对**文字为主的特定网站**(如微信文章、知乎专栏、技术博客),不追求登录态、复杂媒体、PWA 等场景。

##2. 关键决策

|决策 | 选择 |理由 |
|---|---|---|
|介入方式 | **拦截器(shouldInterceptRequest)** | 在线时也能录制和更新缓存,无需双 WebView |
|缓存粒度 | **整页 + 全子资源(HTML/CSS/JS/img/font)** |离线能渲染完整页面;媒体不进缓存(见 §6) |
| HTML 是否改写 | **不改写主体,仅在 file://模式注入 `<base href>`** |主体保持原始性;file://模式注入 `<base>` 让相对路径子资源仍走原始域名解析 |
| 大小限制 | **无限制** | 用户主动管理;列表页可看到占用并删除 |
| 白名单门控 | **非白名单 URL 完全不介入** |零开销,行为不变 |
| URL规范化 |去掉 `#fragment`,保留 `?query` | fragment 仅客户端用,query 常影响内容 |
| 横条关闭按钮 | **不加** | 横条是状态指示,不该被关闭 |

##3. 架构

###3.1 新增组件

**WebCacheManager** (`util/WebCacheManager.java`)
- 单例,持有 `Context`引用
- 管理白名单:`SharedPreferences("web_cache_prefs").getStringSet("cached_urls", ...)` 
- URL →目录:`getCacheDir(url)` 返回 `File(getFilesDir(), "web_cache/" + sha1(url) + "/")`
- 文件操作:`saveResource(url, byte[], mime, encoding)`、`readResource(url)`、`saveMeta(url, title, sizeBytes)`、`readMeta(url)`、`deleteCache(url)`、`listAllCaches()`、`totalSize()`
- URL规范化:`String canonicalUrl(String url)` —移除 `#fragment`
- 不感知 WebView,不感知网络协议,纯文件/白名单管理层

**CachingWebViewClient** (独立文件 `web/CachingWebViewClient.java`,继承现有 `WebViewClient`)
- `onPageStarted(view, url, favicon)` —记录**当前页 URL**(经重定向后的最终 URL);若 url 不在白名单,所有拦截/录制逻辑跳过
- `shouldInterceptRequest(view, request)` —同步阻塞调用,内部用 OkHttp抓取,边写边返回 `WebResourceResponse`;网络失败 fallback 到磁盘
- `onPageFinished(view, url)` — 更新 meta.json(title/时间/大小);白名单和缓存目录均以 `onPageStarted`拿到的最终 URL 为 key
- `onReceivedError(view, request, error)` — 仅当 `isForMainFrame=true` 且 url 在白名单时,触发 file:// 回退
- 用 `OkHttpClient`(`OkHttpProvider.getInstance()` 或新建带默认配置的);请求头从 `WebResourceRequest.getRequestHeaders()`克隆,Cookie 从 `CookieManager.getCookie(url)`注入

**OfflineCacheFragment** (`ui/settings/OfflineCacheFragment.java` + `fragment_offline_cache.xml`)
- RecyclerView列出 `WebCacheManager.listAllCaches()` 每条:`{ title, url, host, cachedAt, sizeBytes }`
-顶部菜单「清空所有」带确认对话框
- 单条右侧删除按钮
- 点击条目启动 `WebViewActivity`加载原始 URL

###3.2 修改现有代码

**WebViewActivity.java**
- 创建 `CachingWebViewClient`替代现有 `WebViewClient`,装到 `webView.setWebViewClient(...)`
- 处理 `R.id.action_cache_page`菜单点击:`addToWhitelist(url)` 或 `removeFromWhitelist(url)`,然后 `webView.reload()`
- `onPrepareOptionsMenu`动态切换菜单文案
- 控制顶部横条 `R.id.cache_mode_banner`显隐(`VISIBLE`/`GONE`)
-横幅"重试"按钮 → `webView.loadUrl(originalUrl)`

**webview_menu.xml**
- 新增 `<item android:id="@+id/action_cache_page" android:title="@string/action_cache_page" app:showAsAction="never"/>`

**activity_webview.xml**
- 在 `Toolbar`下方加一个默认 `visibility="gone"` 的 `LinearLayout`(id=`cache_mode_banner`),含:图标 + 文案 + 重试按钮

**SettingFragment.java / 设置页布局**
- 新增一行入口跳转到 `OfflineCacheFragment`,显示「N 篇 · 共 X MB」

**strings.xml**
- 新增:`action_cache_page_add`、`action_cache_page_remove`、`banner_cache_mode`、`banner_retry`、`offline_cache_title`、`offline_cache_empty`、`offline_cache_clear_all`、`offline_cache_clear_all_confirm`、`settings_offline_cache`、`settings_offline_cache_subtitle`

##4. 磁盘布局

```
<filesDir>/web_cache/
 <sha1(url)>/
 meta.json { url, title, cachedAt: long, sizeBytes: long }
 main.html 主 HTML原文
 resources.json { "<原始资源 URL>": "r/<sha1>.<ext>", ... }
 r/
 <sha1(resUrl)>.<ext> 二进制资源(CSS/JS/img/font)
```

- 用 SHA1(url) 做目录名 →稳定、短、可重入
-资源文件名沿用 sha1 防冲突 + ext 给 WebView 提供 MIME(若 OkHttp response 没给 Content-Type,fallback 按扩展名)
- meta.json损坏时,列表页降级显示,不崩

##5. 数据流与状态机

**前置条件**:非白名单 URL 不走任何拦截/录制逻辑,`shouldInterceptRequest` 直接返回 `null`,`onReceivedError` 不触发回退。

**URL规范化与重定向处理**:
- 入库前 `canonicalUrl(url)`去掉 `#fragment`
-缓存目录用 `onPageStarted`拿到的最终 URL(经重定向后)——而非用户点击时的初始 URL
- 白名单 key同样用 `onPageStarted` 的最终 URL

### 场景1:用户首次点「缓存本页」

```
菜单点击
 → WebCacheManager.addToWhitelist(currentUrl)
 → Toast "已加入缓存,正在录制…"
 → webView.reload()
 → CachingWebViewClient.shouldInterceptRequest 每个请求:
 ├── OkHttp 请求(克隆 WebView 请求头 + Cookie)
 ├──成功 →写 web_cache/<hash>/r/<sha1>.<ext>
 ├──同步更新 resources.json
 └── 返回 WebResourceResponse(byte stream)
 → onPageFinished → 更新 meta.json(title, cachedAt, sizeBytes)
 → Toast "缓存完成 · N 个资源"
```

### 场景2:已缓存 URL,有网,加载成功

```
loadUrl(url)
 → shouldInterceptRequest 每个资源走"网络优先":
 ├── 网络成功 →写盘覆盖 + 返回响应
 └── 网络失败 → fallback读 web_cache/<hash>,命中即返回
 → onPageFinished →静默更新 meta.json
 →顶部横条不显示
```

### 场景3:已缓存 URL,主框架失败

```
loadUrl(url)
 → CachingWebViewClient.onReceivedError(isForMainFrame=true)
 → 检查 WebCacheManager.hasCache(url)
 ├── 有缓存 → webView.loadUrl("file://" + main.html) + 横条 VISIBLE
 │ 注:实际由 `loadDataWithBaseURL`加载,
 │ baseUrl传原始 origin(如 https://mp.weixin.qq.com/),
 │ data注入 `<base href="${origin}/">` 后再拼 main.html
 └── 无缓存 → 默认错误页,不动
```

### 场景4:file:// 回退后,子资源请求

```
shouldInterceptRequest(view, request):
 →客户端仍传入 baseUrl = https://original.com/,
 WebView 据此将相对 URL解析为 https://original.com/...
 →查 resources.json(以原始 https URL 为 key),命中 →读盘返回字节流
 → 未命中 → return null(WebView 默认失败,浏览器自身容错)
横条状态:「📡缓存模式 · YYYY-MM-DD抓取 · [重试]」
重试按钮 → webView.loadUrl(originalUrl)重新走场景2/3
```

## 6. 边界情况与错误处理

|场景 | 处理 |
|---|---|
| URL 含 `#fragment` | 入库前 strip,白名单和缓存目录都用规范化后的 |
| 重定向 |缓存**最终 URL**(取最近一次 `onPageStarted` 拿到的 URL),白名单 key 也用该 URL |
| 子资源抓取失败 |记 log,继续抓其余;离线体验中该资源坏,不影响 HTML |
| POST 请求 |拦截器对非 GET 请求返回 `null`,跳过 |
| Range 请求 /视频/音频 | 同上返回 `null`,不进缓存 |
| 请求头/Cookie | OkHttp克隆 `WebResourceRequest.getRequestHeaders()`;Cookie 从 `CookieManager` 取 |
|加载中点"缓存本页" | 不等待,直接 `reload()`(从干净状态重录) |
| MIME 类型 | 从 OkHttp `Content-Type`拆 `mimeType` 和 `encoding`;无则按文件扩展名 |
|磁盘写失败 | try/catch IOException,记 log,跳过该资源,不抛 UI |
| 并发删除(列表页删除时正在被 WebView读) |接受瞬时报错,刷新即恢复 |
| meta.json损坏 |列表页降级显示「未知标题 ·0 B」,仍允许删除 |
|登录态/cookie失效 | **固有限制** — 用户文档里写明,不试图续期 |
| HTML缺 `<base>`标签 | **file://模式时注入** `<base href="${origin}/">` 到 main.html头部;`loadDataWithBaseURL(origin, ...)`保留 cookie/origin上下文;原始 main.html 在磁盘上保持不变 |

**不在范围内**(YAGNI):
- 自动过期清理
-缓存大小限额 /预警
-视频音频缓存
- PWA / Service Worker
-批量预热
-跨域 cookie同步

## 7. UI规范

### 7.1菜单项(webview_menu.xml 新增)

```xml
<item android:id="@+id/action_cache_page"
 android:title="@string/action_cache_page_add"
 app:showAsAction="never"/>
```

`onPrepareOptionsMenu` 根据 `WebCacheManager.isInWhitelist(currentUrl)`切换 `setTitle`:
- 未缓存 → `R.string.action_cache_page_add` 「缓存本页」
- 已缓存 → `R.string.action_cache_page_remove` 「取消缓存本页」(点击 → `removeFromWhitelist` + `webCacheManager.deleteCache(url)` + Toast)

###7.2顶部横条(activity_webview.xml)

```xml
<LinearLayout
 android:id="@+id/cache_mode_banner"
 android:layout_width="match_parent"
 android:layout_height="wrap_content"
 android:orientation="horizontal"
 android:gravity="center_vertical"
 android:padding="12dp"
 android:background="#FFF8E1"
 android:visibility="gone">
 <ImageView ... src="@drawable/ic_cache_offline" />
 <TextView android:id="@+id/cache_mode_banner_text" .../>
 <Button android:id="@+id/cache_mode_banner_retry"
 android:text="@string/banner_retry" />
</LinearLayout>
```

-淡黄色背景 `#FFF8E1`,状态指示而非通知
-左侧图标 + 文案「缓存模式 ·2026-06-11抓取」 + 「重试」按钮
- 仅一个交互按钮(重试),**无关闭按钮**
- 进入场景3/4 时 VISIBLE,场景2 时 GONE

### 7.3离线缓存列表页(fragment_offline_cache.xml + RecyclerView)

条目:
```
┌──────────────────────────────────────────────┐
│ 文章标题(来自 meta.json) │
│ host ·2026-06-11 ·245 KB 🗑 删除 │
└──────────────────────────────────────────────┘
```

- 点击条目 →启动 `WebViewActivity`加载原始 URL
-右侧删除 → `removeFromWhitelist(url)` +递归删目录 +刷新列表
-顶部菜单 `⋮` → 「清空所有缓存」带 AlertDialog确认
-列表按 `cachedAt`倒序
- 空列表显示「暂无离线缓存 · 在 WebView 中点击「缓存本页」添加」

### 7.4 设置页入口

`SettingFragment` 新增一项:

```
离线缓存 >
N 篇 · 共 X.X MB
```

点击 → `OfflineCacheFragment`。

## 8. 测试策略

###8.1单元测试(不依赖 WebView)

`WebCacheManagerTest`:
- 白名单 add/remove/contains
- canonicalUrl正确移除 `#fragment`
- URL → sha1目录名稳定性
- meta.json序列化/反序列化(含损坏恢复)
- `deleteCache(url)`递归删除正确
- `listAllCaches()`排序按 cachedAt倒序
- `totalSize()`累加正确

### 8.2手动验收清单(写进 PR描述)

1.缓存一个微信文章(文字为主),飞行模式重开 →顶部黄条 +文字/图片完整
2.同一文章联网重开 → 无黄条,`meta.json.cachedAt` 已更新
3.菜单切到「取消缓存本页」 →磁盘目录消失,重开走默认错误页
4. 设置→离线缓存 →看到 N 条,按时间倒序,点进去能离线读
5. 没缓存过的页面,飞行模式 → 默认错误页,**无黄条**(白名单门控生效)
6.缓存大页面(含20 张图)→ 不阻塞 UI,离线时图片全在
7.缓存一个 https POST 表单页(如登录页)→ POST 请求未被拦截,行为不变
8.列表页点「清空所有」带确认 →全部目录删除,白名单清空
9. 单条删除 →目录 + 白名单同步移除
10. meta.json手动损坏 →列表页降级显示,允许删除不崩

## 9. 风险与已知限制

- **Session失效**:登录态页面缓存后再次访问可能拿到登录页内容,**固有限制**,文档中说明用户应避免缓存此类页面
- **动态 JS 内容**:依赖 JS异步加载的内容在缓存时未必完整;我们采用"快照式"录制,首次抓到的就是缓存内容
- **横条不可关闭**:用户可能不喜欢状态指示长期可见;这是有意识的取舍(见 §2)
- **磁盘无上限**:用户需自行管理;未来若发现滥用可加限额
