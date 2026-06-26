---
name: mainactivity
description: MainActivity 的角色、职责与关键代码路径 — 读享(dúxiǎng) 项目的应用入口与导航宿主
metadata:
  type: project
---

# MainActivity 探索笔记

**文件**: [MainActivity.java](app/src/main/java/person/notfresh/readingshare/MainActivity.java) (726 行)
**包名**: `person.notfresh.readingshare`
**注册**: `AndroidManifest.xml` 中 `.MainActivity`，LAUNCHER + 三个 SEND intent-filter

## 一句话定位

MainActivity 是「读享」应用的**单一宿主 Activity**：承载 DrawerLayout + NavigationView + NavHostFragment，用 Jetpack Navigation 在 7 个 Fragment 之间切换；同时承担两个跨 Fragment 行为 —— **剪贴板链接自动捕获** 与 **系统分享(SEND)入库**。

## Manifest 中的入口契约

[AndroidManifest.xml:28-51](app/src/main/AndroidManifest.xml#L28-L51) 注册为：
- `MAIN/LAUNCHER`：冷启动入口
- `SEND` + `text/*`：从其他 App 分享文本入库
- `SEND_MULTIPLE` + `text/*|image/*`：分享多个文件入库

主题用 `Theme.MyApplication.NoActionBar`（无 ActionBar），自己用 Toolbar 实现。

## 核心职责拆解

### 1. 导航宿主 (onCreate, onNewIntent)

[MainActivity.java:104-117](app/src/main/java/person/notfresh/readingshare/MainActivity.java#L104-L117)：用 `Navigation.findNavController(this, R.id.nav_host_fragment_content_main)` 拿到 NavController，挂上 6 个 top-level 目的地（[mobile_navigation.xml](app/src/main/res/navigation/mobile_navigation.xml)）：`nav_home / nav_slideshow / nav_rss / nav_archive / nav_documents / nav_subject`。

DrawerLayout + NavigationView 的 item 选中回调（[MainActivity.java:133-159](app/src/main/java/person/notfresh/readingshare/MainActivity.java#L133-L159)）：自己实现了 `setNavigationItemSelectedListener`，**没有用 NavigationUI 默认联动**，每条菜单项对应一个 `navController.navigate(...)`。先 `closeDrawer`，再 `postDelayed(250ms)` 后跳转 —— 等待关闭动画完成避免视觉跳变。

**默认 Tab 逻辑** ([handleNavigation, MainActivity.java:262-311](app/src/main/java/person/notfresh/readingshare/MainActivity.java#L262-L311))：从 `getPreferences(MODE_PRIVATE)` 的 `default_tab` 读上次保存的偏好：
- 0 → 首页；2 → 主题；3 → RSS；4 → 随机切到三者之一
- 也支持 `navigate_to` extra 强制跳转（来自 `DocumentViewerActivity` 返回时携带）

### 2. 剪贴板监听

**触发时机** ([MainActivity.java:200-209](app/src/main/java/person/notfresh/readingshare/MainActivity.java#L200-L209))：`onWindowFocusChanged(true)` 时调用 `checkClipboard()`。

**前置条件**（[MainActivity.java:396-438](app/src/main/java/person/notfresh/readingshare/MainActivity.java#L396-L438)）：
1. `hasFocus == true`
2. `isAppInForeground()`（检查 `RunningAppProcessInfo`）
3. 延时 500ms 后读取（确保焦点完全就位）

**去重机制**：用 `SharedPreferences("clipboard_prefs")` 持久化 `last_clipboard_text`，与当前剪贴板内容对比，相同则跳过（避免重复弹窗）。

**URL 提取与处理**（[handleClipboardText, MainActivity.java:455-496](app/src/main/java/person/notfresh/readingshare/MainActivity.java#L455-L496)）：
- 正则 `https?://[^\s,，]+` 抓第一个 URL
- 移除 query string 后若以 `.xml` 结尾 → 跳过（RSS 源不是要保存的链接）
- URL 之前的文字作为标题候选（截到 20 字 + `...`）

### 3. 系统分享入口

[handleIntent, MainActivity.java:313-389](app/src/main/java/person/notfresh/readingshare/MainActivity.java#L313-L389)：仅处理 `ACTION_SEND` + `text/plain`。调试日志极其详尽（[MainActivity.java:322-356](app/src/main/java/person/notfresh/readingshare/MainActivity.java#L322-L356)）—— 打印所有 extras、flags、scheme、component、可处理的应用列表。最后调用同一个 `showSaveLinkDialog`，但 `clearClipboardOnSave = false`。

### 4. 保存链接弹窗

[showSaveLinkDialog, MainActivity.java:507-658](app/src/main/java/person/notfresh/readingshare/MainActivity.java#L507-L658)：自绘 `dialog_save_link` 布局，包含：
- 标题/URL/标签输入框
- 「最近标签」FlexboxLayout 可点选
- 「保存到主题」Spinner（下拉首项 = "不添加到主题"）

**保存动作**：
1. 写入 `LinkDao`（[LinkDao.java](app/src/main/java/person/notfresh/readingshare/db/LinkDao.java)）
2. 若选了主题，用 `SubjectUtil.addLinkToSubjectById` 关联
3. 仅剪贴板流程清空剪贴板
4. `navController.navigate(R.id.nav_home)` 跳首页
5. 注册一次性 `OnDestinationChangedListener`，等 `nav_home` 真正显示后调 `HomeFragment.recordLinkAddTime()` —— 给洗牌模式做时间锚点

**后台抓标题**（[fetchTitleFromUrl, MainActivity.java:660-684](app/src/main/java/person/notfresh/readingshare/MainActivity.java#L660-L684)）：单线程 `ExecutorService` 异步抓，`weixin.qq.com` 走 `CrawlUtil.getWeixinArticleTitle`，否则 `CrawlUtil.fetchTitleCommon`。抓到的标题在主线程 setText（覆盖输入框）。

### 5. FAB = 反馈邮件

[MainActivity.java:164-191](app/src/main/java/person/notfresh/readingshare/MainActivity.java#L164-L191)：悬浮按钮直接打开腾讯邮箱的「写邮件」Compose Activity（精确到 `com.tencent.qqmail.launcher.third.LaunchComposeMail` 类名），避免出现「记事本」选项；找不到时回退到通用 `ACTION_SENDTO mailto:`。收件人 `notfresh@foxmail.com`，主题「读享反馈」。

### 6. 抽屉头部个人信息

[updateNavHeader, MainActivity.java:687-724](app/src/main/java/person/notfresh/readingshare/MainActivity.java#L687-L724)：从 `SharedPreferences("UserProfile")` 读 username/email/profile_image。头像文件若不存在，自动从 prefs 移除 URI 并回到默认 `ic_launcher`。在 `onResume` 触发刷新。

**头部点击**（[MainActivity.java:120-127](app/src/main/java/person/notfresh/readingshare/MainActivity.java#L120-L127)）：关闭抽屉 → 延时 250ms → 跳 `UserProfileActivity`。

## 一些细节观察

- **生命周期**：`onDestroy` 只关 `LinkDao`，剪贴板监听无显式注销（依赖系统回调）；`onNewIntent` 会更新 `navController` 引用后再 `handleNavigation`，确保从其他 Activity 返回时不丢导航意图。
- **菜单转发**：`onOptionsItemSelected`（[MainActivity.java:231-239](app/src/main/java/person/notfresh/readingshare/MainActivity.java#L231-L239)）先把点击派给当前 Fragment，Fragment 不处理才走 super —— 让 Fragment 拥有自己的 toolbar menu。
- **废弃标签页**：注释 [MainActivity.java:110](app/src/main/java/person/notfresh/readingshare/MainActivity.java#L110) 说明 `nav_tags` 已合并到 `nav_home`。
- **`@Def Line300` / `@mark`**：源码中残留作者定位注释（如 `@mark` 在 [MainActivity.java:201](app/src/main/java/person/notfresh/readingshare/MainActivity.java#L201)），是历史调试痕迹，不是规范。

## 数据持久化映射

| 入口 | 持久化目标 |
|---|---|
| 剪贴板/SEND 保存链接 | `LinkDao` (SQLite) + 可选 `Subject` 关联 |
| 剪贴板去重 | `SharedPreferences("clipboard_prefs")` |
| 默认 Tab | `getPreferences("default_tab")` |
| 用户头像/名 | `SharedPreferences("UserProfile")` |
| 最近标签 | `RecentTagsManager` (单独工具类) |

## 关联文件

- 导航图：[mobile_navigation.xml](app/src/main/res/navigation/mobile_navigation.xml)
- 抽屉菜单：[activity_main_drawer.xml](app/src/main/res/menu/activity_main_drawer.xml)
- 布局：[activity_main.xml](app/src/main/res/layout/activity_main.xml)
- 入口 Activity：[AndroidManifest.xml:28-51](app/src/main/AndroidManifest.xml#L28-L51)
- 同包内其他顶层 Activity：`WebViewActivity`、`UserProfileActivity`、`WebShortcutActivity`、`ClickStatisticsActivity`、`WebViewBackgroundService`、`BackgroundAudioWebView`、`WebViewManager`




