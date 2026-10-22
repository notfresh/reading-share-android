# 2.3.19.8
设置页下拉风格统一：默认Tab / 最近标签窗口 从裸 Spinner 换成 Material exposed dropdown。

## 改动
- `fragment_slideshow.xml`：两个 `<Spinner>` 换成空宿主 `LinearLayout`
  (`default_tab_dropdown_host` / `recent_tags_window_dropdown_host`)
- `SettingFragment`：改用 `SettingDropdown`(与「阅读页数据来源」同一实例类型),
  选项走 `R.array.default_tabs_array` / `recent_tags_window_array`
- 新增 hint:`setting_default_tab_hint`(启动页 / Start page)、
  `setting_recent_tags_window_hint`(窗口大小 / Window size),中英同步
- `default_tab` 的索引迁移逻辑(旧 1=标签废弃 → 迁移、新索引↔旧索引映射)原样保留,行为不变
- 删掉 `SettingFragment` 里不再使用的 `Spinner`/`ArrayAdapter`/`AdapterView` import

## 注意
`SettingDropdown` 只在用户真的点选时回调(程序化 setOptions 不触发),
首次进入设置页不会因为"恢复显示"而意外改写 `default_tab` / 窗口大小。

# 2.3.19.7
修复:设置页数据来源下拉"选了没反应"(表现为选「某个标签」后没有任何标签可选)。

## 根因
`SettingDropdown.suppress` 初值写成了 `true`,只有程序化 `setText` 时才置位、之后恢复成"上一个值",
等于从启动到大永远为 true → `OnItemClickListener` 里第一行 `if (suppress) return` 把用户点选全部吞掉:
- 类型下拉的回调不触发 → 新类型不落盘,二级参数下拉也就永远不重填
- 所以「置顶链接」时参数框不隐藏、「某个标签」时看不到标签列表
改为初值 `false`,`showSelection` 里临时置位后恢复。

## 其他
- 类型下没有任何可选项(没标签/没主题)时不再"界面显示标签、实际存全部":回滚类型下拉并 Toast 提示
  (`reader_source_no_options`,中英同步)
- 参数下拉在「全部链接 / 置顶链接」时整块隐藏(不摆占位项),这一点原本就是这样,这次只是让点选真的能触发
- 阅读页 `onResume` 比对来源指纹(`type|param`),设置页改完切回 Tab 立即按新来源重建队列,
  不用杀进程重开

# 2.3.19.6
修复:进阅读页必崩(表现为"打开秒退",当默认Tab=阅读时就是冷启动崩溃)。

## 根因
2.3.19.2 把底部三按钮换成 WebViewActivity 那套"底部 2 个 Button + 右侧 3 个 ImageButton"时,
`ReaderFragment.randomButton` 字段类型仍是 `Button`,而 `reader_floating_random` 实际是 `ImageButton`
→ `findViewById` 泛型推断到 `Button`,运行时 `ClassCastException`,且发生在 `onViewCreated`,必崩。

## 改动
- `ReaderFragment.randomButton` 类型改 `ImageButton`(本机修复,commit 2ca1e76)
- `DbConnection.checkpoint()`:补 `moveToFirst()` 读游标 —— `PRAGMA wal_checkpoint` 不读游标不执行,
  之前 WAL 从未真正刷回主库(本机修复,commit fef2663)
- `App.java` 生命周期日志加 `BuildConfig.DEBUG` 门;`build.gradle` 打开 `buildConfig true`,
  release 构建不再输出 `AppLifecycle` 日志
- 移除 2.3.19.5 临时加的 `util/CrashRecorder.java` 与其两处调用(根因已定位,排障脚手架撤掉)
- 新增 `util/CrashClipboard.java`:未捕获异常**当场**把错误栈写进剪贴板(不是等下次启动)——
  崩溃时界面打不开,用户没有第二次机会看到提示,重开聊天窗口直接粘贴即可;不联网、不上报、不加权限

## 教训
布局里换控件类型(Button → ImageButton)时,`findViewById` 的泛型形参跟着字段走,
字段类型不改 = 运行时 ClassCastException,而编译器完全不会报。改这类"同 id 换实现类"的布局,
必须同时扫一遍所有 `findViewById(R.id.<该 id>)` 的接收字段类型。

# 2.3.19.4
优化:阅读页数据来源的两个下拉换成 Material exposed dropdown(原 Spinner 是 Holo 观感,丑)。

## 改动
- 新增 `ui/settings/SettingDropdown.java`:Material 填充框 + 展开菜单,封装成"挂上去就能用"的小件;
  只在用户选中时回调,程序化 setText 不触发(内部 suppress),避免联动死循环
- 新增 `res/layout/view_setting_dropdown.xml`(FilledBox.ExposedDropdownMenu) +
  `res/layout/item_dropdown_option.xml`(统一 16dp 内边距、16sp)
- 阅读页数据来源区块:类型 / 参数两个下拉都用它,hint 分别为「来源类型」「具体项」
- 参数下拉在「全部链接 / 置顶链接」时整块隐藏,不再摆「（无需选择）」这种占位项
- 与项目既有 TextInputLayout 输入框同一套视觉(填充框),不引入新风格

# 2.3.19.3
修复:阅读页选「某个主题」来源时显示「该来源暂无链接」,即使该主题里确有链接。

## 根因
`reader/SubjectSource.java` 用了 `new SubjectDao(Context)` —— 这是 @Deprecated 构造,
`database` 字段要等 `open()` 之后才有值。ReaderSource 的 `loadPage` 在后台线程直接
`database.query(...)` → NPE,被 SubjectSource 自己的 catch 吞掉 → 快照为空 → 阅读页判定无内容。
其他三个来源(全部/置顶/标签)都走 `DbConnection.get(ctx).writable()`,所以只有"主题"坏。

## 修法
- 改成 `new SubjectDao(DbConnection.get(ctx).writable())`,与其他来源一致
- 快照成功时补一条 `Log.d("SubjectSource", "subject snapshot subjectId=X links=N")`,
  下次出问题能一眼分清"主题项为空"还是"被过滤掉"

## 教训
`@Deprecated` 的 `XxxDao(Context)` 构造不会给 `database` 赋值 —— 把它当"兼容旧代码"用是陷阱:
没有 `open()` 就查询 = NPE,而 NPE 在 catch 里静默降级成"空数据",UI 只表现为"没内容"。
# 2.3.19.2
特性:阅读页体验对齐 WebViewActivity(第二轮反馈:页面布局 / 导航条 / 弹窗按钮要一致)。

## 导航控件(先复制同款,第二刀与 Activity 合并成同一份 include)
- 底部条:半透明黑底 `#66000000` + padding 12dp + 「上一篇 / 下一篇」,按钮 `backgroundTint=darker_gray`
- 悬浮层:右侧竖排 3 个 48dp 图标按钮(`floating_arrow_bg` + ic_expand_less/more/random)
- 显隐策略逐条对齐:`reading_mode=normal` 不显示;`smooth` 按 `nav_button_style`(bottom/floating/both) 显示;
  显示后 3 秒自动隐藏;触摸网页临时唤起;手动隐藏持久化到 `settings/nav_controls_hidden`
- 菜单项「显示/隐藏导航控件」仅丝滑模式可见(同 Activity)
- 新增 `ui/reader/ReaderControlsController.java`,注释标明"Activity 的复制版,第二刀合并"

## 弹窗与文案对齐
- 加标签:补「当前标签」(点删) + 「最近标签」(点击填入),保存时整体替换,并把新增标签写入最近标签
- 改标题:弹窗标题改回「修改标题」(`edit_title`),与 Activity 一致
- 提取正文 toast 改为 Activity 原文「提取Web内容完成，已经写入剪切板」
- 导航按钮文案改「上一篇 / 下一篇 / 随机跳转」

## toolbar
- 页面标题跟随时更新(与 WebViewActivity 一致);离开阅读页由 NavigationUI 恢复各页 label

# 2.3.19.1
特性:阅读页补上顶部菜单(对齐 WebViewActivity 的 webview_menu,刻意去掉 Activity 专属项)。

## 菜单项(9 个)
- 刷新 / 浏览器打开 / 分享 / 提取正文到剪贴板 / 配置跳转规则 / 链接历史
- 加标签(复用 dialog_add_tag + TagSuggestionHelper 联想)
- 改标题(按当前 URL 更新 links 表)
- 拦截模式切换(0 弹窗 → 2 拦截 → 1 跳转,循环;图标红/白/灰染色与 Activity 一致)

## 刻意不做
- 强制返回 / 存档返回(Activity finish 语义,Fragment 无对应)
- 折叠 mini player、丝滑模式的导航控件开关(阅读页已有固定底部控件)

## 实现
- `ui/reader/ReaderMenuActions.java`:9 个动作各自命名方法;ReaderFragment 只实现 `Page`
  接口(webView/linkId/url/title)并委托,不再往 Fragment 里堆 if-else
- `res/menu/reader_menu.xml`:独立菜单,不动 `webview_menu.xml`
- 加标签/改标题只对"队列链接"生效:用户点进页内别的链接后 `linkId` 置空,
  避免把标签打到错误的链接上(Activity 那版是 URL 全表线性查找,这里按 id 直取)
- 新增 14 条中英文案

# 2.3.19
特性:新增「阅读」Tab —— 内嵌 WebView 的阅读页,按可配置数据来源成队列,支持上一条 / 下一条 / 随机。

## 数据来源(单选项,设置页「阅读页数据来源」)
- 全部链接(`getLinksPage` 分页,不碰全表)
- 置顶链接(`getPinnedLinks`)
- 某个标签(`getLinksByTag`)
- 某个主题(`subject_items` 里 linkId 非空、未删、未归档)

## 行为
- 进页加载队列第一条;上一条 / 下一条环形走已加载窗口;随机不复当前项
- 页面内 http(s) 由 WebView 加载;app scheme 走既有白名单规则(`WebViewOverrideRules`)+ 全局 external_link_mode 三档
- 返回键:能回退先回退网页,否则交还 Navigation

## 架构(为下一刀铺路)
- `core/reader/ReaderSource` + `ReaderQueue` + `UrlRoute` 纯内核,无 Android 依赖,带单测
- `reader/ReaderSources` 注册表:新增来源 = 一个实现类 + 一行注册
- `util/WebViewDefaults` / `util/ExternalLinkOpener` 共享单元
- **本次不改 `WebViewActivity`**;下一刀把它也收到这些单元上(预计净减约 200 行)

## B

# 2.3.18.2
特性:全局关掉 Material Button 默认 ALL CAPS(2.3.18.1 patch 反馈:英文版按钮显示成 "BROWSE HISTORY" / "EXPORT ALL LINKS" / "+ ADD RULE" / "ABOUT" 等全大写,不符合 sentence case 美学)。

## 改动
- `values/themes.xml` + `values-night/themes.xml` Theme.MyApplication 加 `<item name="textAllCaps">false</item>`
- 影响范围:整个 app 所有 MaterialButton(普通 Button / OutlinedButton / TextButton / icon button 等)
- 不影响 RadioButton/Switch(各自有样式)
- 中文 button 文字不变(中文无大小写概念,纯是渲染行为变化)

## 设计原因
- Android Material Components 默认 button textAllCaps=true → 英文按钮强制大写
- Material Design 当前推荐 sentence case(只句首大写),与代码现状(string 已全部是 sentence case)对齐
- 集中改 theme 不动 layout:避免给每个 Button 加 `android:textAllCaps="false"` 这种重复 boilerplate

# 2.3.18.1
特性:补完 Home 弹窗 / 设置页 中英双语(2.3.18 后续 patch,用户反馈添加弹窗内控件仍有中文)。

## 范围
- `dialog_add_tag.xml` 4 处 hint/label 外提(`home_dialog_add_tag_*`)
- `dialog_add_remark.xml` 1 处 hint 外提(`home_dialog_add_remark_hint`)
- `item_link.xml` 5 处:阅读 0 次默认 / 显示更多 / 添加标签按钮 / 备注按钮 / 查看完整备注
  - "添加标签" → `link_button_add_tag`("Add tag")
  - "备注" → `link_button_add_remark`("Note")
  - "阅读 0 次" → `link_click_count_zero`("0 reads")
  - "显示更多" / "查看完整备注" 复用 2.3.18 已有的 `link_show_more` / `link_view_full_remark`
- `fragment_slideshow.xml`(设置页)32 处全部外提,新增 `setting_*` 30+ 个 string
- `SettingFragment.java` 运行时 dialog/toast 全部外提:
  - Snackbar "已保存：保留 N 条历史"
  - 同步状态文本:正在同步 / 同步成功 / 同步失败
  - 文件选择器 / 导入对话框 / 导出对话框的 title + 确定/取消 + 去重 checkbox + 导入结果摘要

## 命名约定补充
- `home_dialog_add_tag_*` — 添加标签 dialog 内部
- `home_dialog_add_remark_hint` — 添加备注 dialog 内部
- `link_button_*` — 列表项内按钮文字(独立于 `link_menu_*` / `link_dialog_*`)
- `link_click_count_zero` — 布局默认 "0 reads"(运行时真实值走 `link_click_count` + `%1$d`)
- `setting_*` — 设置页区块
- `setting_import_summary` 占位:`%1$s`=格式(CSV/JSON)、`%2$d`=导入成功、`%3$d`=跳过重复、`%4$d`=失败

# 2.3.18
特性:HomeFragment 中英双语 + 设置页语言切换(跟随系统 / 中文 / English)。

## 范围
- HomeFragment.java(57 处硬编码中文外提为 string resource)
- LinksAdapter.java(80 处硬编码中文外提 — 只服务于 HomeFragment 列表)
- fragment_home.xml + home_menu.xml(layout/menu 硬编码 → @string/)
- values/strings.xml + values-en/strings.xml(新增 110+ 条双语条目)
- SettingFragment.java — 新增语言切换区块(动态 inflate,不动 layout)

## 切换机制
- 用 androidx AppCompatDelegate.setApplicationLocales() —— 无自建 LocaleManager、无 attachBaseContext 改造
- API 33+ 写系统设置;老版本由 androidx 1.6.1 自己持久化
- 用户在设置页点选后,系统自动 recreate Activity

## 字符串命名约定
- `home_*` — HomeFragment 动态文案(toast/title/snackbar/dialog)
- `home_action_*` — home_menu.xml 菜单项 title
- `link_*` — LinksAdapter 显示相关(列表行 / dialog / popup)
- `tag_op_*` — HomeFragment 标签操作 dialog 选项
- `language_*` — 设置页语言区块

# 2.3.17.5
特性:WebView 跳转白名单模式 —— 针对个别网址配置单独的跳转策略。

## 数据模型
- 新增 `model/WebViewOverrideRule.java`(不可变 record-like,JSON 序列化适配老数据)
- 新增 `model/OverrideAction.java` 枚举 ASK / OPEN / BLOCK(与既有 externalLinkMode 三档一一对应)
- 新增 `util/WebViewOverrideRules.java` 数据层(`loadAll` / `firstMatch` / `add` / `update` / `remove` / `recordRedirectedUrl`)
  - 单一真相源:SharedPreferences("settings") / `override_rules_json`
  - 复合字段(2.3.17.5 后):`contextUrl`(用户手填的原始 URL) + `redirectedUrl`(系统自动记录的重定向后 URL) + `action`
- 兼容:老 2.3.17 之前的 pattern 字段在 `fromJson` 自动映射到新 contextUrl

## 匹配语义(2.3.17.5 最终版,合并初版与 5 刀修复)
- 候选 URL:`webView.getUrl()`(重定向后当前页) + `webView.getOriginalUrl()`(302 前的原始链)
- 命中条件:任一 candidate 被规则的 `contextUrl` 或 `redirectedUrl` 子串包含(忽略大小写)即胜出
- 首次命中且 `redirectedUrl` 为空 + 已发生重定向(候选 URL ≠ contextUrl) → 系统自动写 `redirectedUrl` 到 SP
- 这样无论用户配的是原始短链(`xhslink.cn`)还是重定向后的真页(`xiaohongshu.com`),规则都能正确命中

## UI 配置入口(两处共用一个 DialogFragment)
1. **设置抽屉**(fragment_slideshow.xml)「拦截名单」区块:RecyclerView + 每行(`pattern + 3 选 1 + 删除`) + 底部「+」按钮 + 空态提示
2. **WebView toolbar**:菜单项「配置本页跳转」(`action_configure_override_rule`);弹 dialog 时 `pattern` 预填 `webView.getOriginalUrl()`(若空回退 `getUrl()`,再回退 `currentUrl`)
3. row 点击 TextView 区域 → 弹只读详情 dialog 显示完整 contextUrl + redirectedUrl + action
4. item layout 重新设计(垂直 LinearLayout 包两层:主行 + 副 redirectedUrl 行);副行存在时显示,空时整行隐藏

## WebView 拦截接入
- `WebViewActivity.handleUrlOverride` 入口先 white-list 命中检查,命中走 `applyRuleAction(url, action)`,未命中走既有全局 mode 0/1/2 逻辑
- 命中后**永远 return true** —— 避免 chrome 内核对 `xhsdiscover://` 等 scheme 触发 ERR_UNKNOWN_URL_SCHEME 渲染错误页
- 三个 action 复用既有方法:
  - ASK → `showExternalOpenConfirmDialog`
  - OPEN → `openExternalUri`
  - BLOCK → return true(静默不处理)

## 踩坑记录(关键)
1. **白名单匹配语义走错关键两步**(2.3.17.2/.3 反复纠偏):
   - 初版用「跳出来的 url」子串匹配 → xhsdiscover:// 不含 xiaohongshu.com,误判
   - 改用「webview 当前页 url」匹配 → 但 dialog 预填 currentUrl 变量是短链,与重定向后 webview 不符
   - 最终用「webview 原 URL」+「webview 当前 URL」两条候选,匹配时一并查
2. **shouldOverrideUrlLoading 应永远 return true**(2.3.17.4)
   - 之前 return `action == ASK` 仅在 ASK 时返 true,OPEN/BLOCK 命中时 webview 仍尝试加载 scheme,ERR 错误页出现
3. **row 点击区不传透 RadioGroup / 删除按钮**(2.3.17.5)
   - 把 contextUrl 与 redirectedUrl 包成单独的 LinearLayout 给 click listener,RadioGroup / 删除按钮的 click 不触发

## 数据与回滚
- 老 2.3.17 之前 SP 里的 `pattern` 字段,反序列化时自动映射到 `contextUrl`(读优先读新字段,缺时回退老字段)
- 回滚成本:单 commit revert,不改 schema 表结构,不引入新依赖
- versionCode 36 → 40,versionName 2.3.17 → 2.3.17.5

# 2.3.17
特性:WebView 跳转白名单模式 —— 初版,简化版。详见后续修复 commit。
- 初版字段命名 `pattern`(子串匹配,命中 web 跳出来的 url),后续发现「网页 JS 触发 scheme 跳转不命中问题」,后续 commit 改字段为 contextUrl + 加 redirectedUrl。

# 2.3.16
修复:数据库在「多任务一键全部关闭」(SIGKILL) 后出现"主库空 + journal 残留"的丢数据现象。
- 根因:之前用 SQLite 默认 DELETE journal 模式,长连接(`SQLiteOpenHelper` 进程级持有)下,SIGKILL 会让 `links.db-journal` 残留成"未完成事务",下次启动 SQLite 走回滚 → 看起来"丢数据"。高频写入字段(`click_count`、追加型字段如 `remark`)受影响最明显,创建型字段(`title`/`url`/`timestamp`)相对稳定。
- 修法 1:`LinkDbHelper` 启用 **WAL 模式** —— 构造器 `setWriteAheadLoggingEnabled(true)` + `onConfigure` 强制 `PRAGMA journal_mode = WAL` + `synchronous = NORMAL`。WAL 文件是数据库的正常部分,SQLite 启动不会因为 WAL 存在就回滚,SIGKILL 留下的也只是"已 commit 但未 checkpoint"的最近几笔写入(毫秒级窗口),不会触发整体回滚。
- 修法 2:`DbConnection` 新增 `checkpoint()` 方法,执行 `PRAGMA wal_checkpoint(TRUNCATE)`;`App.onCreate` 注册 `ActivityLifecycleCallbacks`,最后一个 Activity `onStop` 时自动调 checkpoint,把 WAL 页面刷回主库 —— 覆盖"上滑退出 / 返回键退出 / 系统内存杀"路径。
- 修法 3:从 `links.db-journal` 还原了 3105 条链接 + 197 标签 + 3463 events + 176 tag_embeddings + 294 history + 10 subject_items + 2 subjects,数据已写回设备。
- 不覆盖:多任务面板"全部关闭"(SIGKILL)那一刻"已 commit 但未 checkpoint"的最近写入仍可能丢(毫秒级);其它退出路径全部安全。
- 数据库 schema 不变(versionCode 35→36,版本名 2.3.15.9→2.3.16;`DATABASE_VERSION` 字段未升级,无迁移)。
- 影响:覆盖范围包括 title/url/timestamp(创建型)、remark(追加型)、click_count(累加型)三类写入路径。

# 2.3.15.9
调整:恢复「当前标签」面板 —— 显示该链接已打的 tag。
- 2.3.15.8 把 currentTagsContainer 删了,实测用户希望显示(能看到+可点删除),但仍希望联想按打字触发。
- 改:输入框 + 最近标签 + 当前标签(链接已打 tag,点击删除)+ 联想(打字才出现);excluded 集合随当前标签删除列表更新。

# 2.3.15.8
调整:添加标签对话框中,「当前标签」面板与「已有标签(输入即联想)」初始可见列表**全部默认不展示**,只保留输入框 + 最近标签 + 打字时的子串自动联想。
- 之前 2.3.15.6/2.3.15.7 尝试展示,实测用户期望「自动联想」即可,不主动铺所有 tag。
- 删:`LinksAdapter.showAddTagDialog` 里 currentTagsContainer 的渲染 / 点击删除逻辑;`TagSuggestionHelper.applyFilter` 空 query 路径回到「整块隐藏」。
- 留:输入框 + 最近标签 + suggestion(打字出现,清空又消失);excluded 集合(当前 link 已有的 tag 不参与联想)仍生效。

# 2.3.15.7
修复:`LinksAdapter.showAddTagDialog` 入口漏处理「当前标签」面板(链接已打的 tag)。
- 根因:HomeFragment 列表项长按「添加标签」走 `LinksAdapter:1408`,该函数只接联想 + 最近标签,**完全没读 item.getTags() 填充 `current_tags_container`**。
- WebViewActivity:895 入口的 showAddTagDialog 处理过这块。
- 修:对齐 WebViewActivity 的实现,在 LinksAdapter 里 findViewById 拿 `current_tags_container` / `current_tags_label`,遍历 item.getTags() 渲染 chip;点击删除时同时从 existingTags 与 suggestionExcluded 移除(让刚删的 tag 立即在联想里出现)。

# 2.3.15.6
修复:添加标签对话框打开时,联想区(标题"已有标签(输入即联想)")不显示任何已有标签。
- 根因:`TagSuggestionHelper.SuggestionAdapter.applyFilter` 在 query 为空时直接 return 清空 filtered,导致标题和 RecyclerView 都被 hide。
- 修:空 query 时改为「展示全量已有标签(排除 exclude 项)」,只对 exclude 集合内的 tag 过滤;非空 query 时按子串 contains 过滤。
- 用户体验:刚打开添加标签 dialog → 立刻看到所有已有 tag;开始打字 → 按子串过滤缩窄。

# 2.3.15.5
修复:备注数据库写入有,但 `createLinkItemFromCursor` 不读,列表里永远只显示 URL 抽出的空备注。
- 根因:`LinkDao.insertLink` / `updateLink` / `updateLinkRemark` 都正确写 `COLUMN_REMARK`,但 `createLinkItemFromCursor` 把这一列漏掉了。
- 修:在 `LinkDao.createLinkItemFromCursor` 加 `cursor.getString(COLUMN_REMARK)` + `item.setRemark(remark)`。
- 影响面:`getLinksPage` / `searchLinks` / `getLinkById` / `getLinksWithoutTags` / `getLinksByTag` 等所有走 `createLinkItemFromCursor` 的查询 → 现在都能正确带出备注。

# 2.3.15.4
调整:备注显示上限从 2 行改为 5 行 + 触发条件 lineCount >= 5。
- 2.3.15.3 上线后实测:2 行太短,长备注信息不完整;`getLineCount() > 2` 偶发 layout 时机问题导致"查看完整备注"链接不出现。
- 现在 maxLines=5,只触发条件改为 `>= 5`(达到上限必被截断,稳定),展示更友好。

# 2.3.15.3
优化:链接列表备注显示。
- 列表始终只显示 2 行(原先用 countLines 测换行符数,对大段连续文字判定失效 → 长备注撑爆屏幕)。
- 改用 `TextView.getLineCount()` 测量实际行数;超过 2 行 → 显示「查看完整备注」链接。
- 点击链接 → 弹 AlertDialog(ScrollView + TextView)展示完整备注文本,可滚动、`setTextIsSelectable(true)` 长按可选中复制。
- 移除旧的"展开/收起"模式(展开后 maxLines=MAX_VALUE 会撑爆列表项)。

# 2.3.15.2
调整:小红书域名识别扩展到主域 `xiaohongshu.com`。
- 之前只识别 `xhslink.cn` / `xshlink.cn` / `xhslink.com` 短链系列 → 现加入 `xiaohongshu.com` 主域及子域。
- 抽公共方法 `XiaohongshuSkipStrategy.isXiaohongshuUrl(String url)` 作为单一判断入口,`XiaohongshuSkipStrategy.matches()` 与 `WebViewActivity.isXshlinkUrl()` 均调它,后续新增小红书域名只改一处。
- 影响:`XiaohongshuSkipStrategy` 剪贴板检测 SKIP 策略覆盖主域(避免后台抓取被官方推广文案污染);`WebViewActivity.recordLinkHistory` 标题优先用 DB 历史标题的保护覆盖主域。

# 2.3.15.1
修复:置顶标签对话框滑动 RecyclerView 时 CheckBox 状态丢失/被误取消。
- 根因:`onBindViewHolder` 里 `setChecked(current.contains(...))` 会触发 ViewHolder 复用前残留的 listener,把状态写回 manager。
- 修复:`Adapter.setHasStableIds(true)` + `getItemId(hashCode)`;新增 `TagRow.checked` 作为主真相源(不读 manager);`onBindViewHolder` 先 `setOnCheckedChangeListener(null)` 再 setChecked;`onCheckedChange` 先更新 row 再写 manager,达到上限时 rollback row.checked。

# 2.3.15
新增:置顶标签(最多 3 个,写死上限),用户配置的 tag 永远排在最前,不会被任何重排打乱。
- 入口:HomeFragment 顶栏菜单"置顶标签"(原 `action_pinned_tags`)。
- 弹 dialog 复选框:已置顶(按顺序)在前,未置顶(按字母)在后,3 个上限,达到上限再勾 → toast 提示。
- 持久化:SharedPreferences `pinned_tags_prefs` / `pinned_tags`,封装在 `util/PinnedTagsManager.java`(`MAX_PINNED = 3` 写死)。
- 数据层:`LinkDao.getTagsWithCount(Set<String> pinnedTagNames)` 重载 —— 第一步按 pinned 顺序输出,第二步按 `tag_order`,第三步按 id 升序。
- AI 重排:`TagEmbeddingManager.sortTagsBySimilarity` 算法不变,排序完成后按 pinned 顺序 prepend 结果,删除原位置(用户原话:"把置顶的拎到最前面,做一个插入,把选中的从原位置删掉")。
- 触发刷新:HomeFragment implements `PinnedTagsPickerDialog.OnPinnedChangedListener`,保存 → `loadTags()` 重排 tag 区。
- 不动 UI:置顶 tag 在 tag 区不特殊显示(用户要求"不做任何特殊显示处理")。

# 2.3.14
新增:HomeFragment 多选模式支持批量删除。
- 顶栏菜单新增「批量删除」图标(`@android:drawable/ic_menu_delete`),仅在选模式时显示,退出选模式自动隐藏。
- 空选择时直接 toast 提示;选中时弹 AlertDialog 二次确认("确认删除选中的 N 条链接?此操作不可撤销"),确定后才执行。
- 抽出公共方法 `deleteSelectedLinks(List<LinkItem> items)`,复用现有「分享后删除」路径的循环,两处都走同一份写事件日志 + 删 + 刷列表。
- 行为契约:跟 `shareAsFile` 内的「分享后删除」保持一致(写 delete 事件 + adapter.removeLinkItem + refreshLinksList)。

# 2.3.13.1
调整:标签联想点击行为 —— 从"追加"改为"替换最后一个逗号段"。
- 用户反馈:"要用联想的词替换原始词语,而不是两个词"。
- helper `TagSuggestionHelper.replaceLastSegment()`:输入框里找到最后一个英文/中文逗号的位置,保留前半段和逗号,用候选 tag 替换其后的片段。无逗号则整段替换。
- 与「最近使用」面板的 append 行为有意不同,更符合"用联想词替换原始词"的直觉。

# 2.3.13
新增:打标签对话框联想功能 —— 在输入框下方加 suggestion RecyclerView,每字符(150ms)根据输入最后一个逗号段做子串 contains 过滤已有标签。
- 复用现有 `item_recent_tag` chip 样式失败(行高+占满宽度冲突),新建 `item_tag_suggestion.xml` —— 白底 selectableItemBackground,占满宽度,可点。
- 抽公共 helper `util/TagSuggestionHelper.java`,两个入口共用(LinksAdapter 列表项的「添加标签」+ WebViewActivity 顶栏的「添加标签」)。
- 排除当前 link 已有的 tag(LinksAdapter 读 item.getTags();WebViewActivity 用 mutable HashSet 引用,后续 existingTags 填充后下次筛选生效)。
- 无匹配整块隐藏(title + recycler 都 GONE)。
- 点击候选 → 替换输入框最后一个逗号段(逗号之前的保留,逗号也保留;无逗号则整段替换),与"最近使用"面板的 append 行为不同,更符合"用联想词替换原始词"的直觉。

# 2.3.12.1
新增：设置页底部加「关于」按钮，弹出关于弹窗。
- 弹窗内容:app 名(加粗) + 版本号(BuildConfig.VERSION_NAME 动态读) + GitHub 项目地址(下划线 + 可点击跳转)。
- 链接样式仿 EventLogActivity.makeClickableHint:SpannableString + URLSpan,样式与项目内已有下划线链接一致。
- 弹窗只显示 versionName,不显示 versionCode(主流 app 做法,versionCode 是给系统的,用户不需要看到)。

# 2.3.12
新增：主按钮(fab_random)在 SubjectFragment 也显示，点击 = 随机切换主题（与顶部 action_random_subject 完全等价）。
- HomeFragment：fab_random = 洗牌链接（原有行为）。
- SubjectFragment：fab_random = 随机切换主题（新增）。
- 其他 fragment：不显示 fab_random（原有行为）。
- SubjectFragment 自己暴露 `public static volatile activeInstance` + `onRandomSubjectFabClicked()`，完全照搬 HomeFragment 已验证的模式。

# 2.3.11
修复：左手模式 fab 区域贴边(没 margin),与右手模式不对称。
根因:applyFabSide() 只改了 gravity,没镜像 margin —— Android 在 gravity=START 时 marginEnd 自动无效化,导致左手模式 leftMargin=0,贴左边。
修法:运行时读 @dimen/fab_margin 的实际像素值,左手=leftMargin=fab_margin,右手=rightMargin=fab_margin,两端完全对称。

# 2.3.10
调整：fab_slot_1 改为「切换链接/主题」入口(跨 fragment 一直显示)。
- 点击 = 跳到对面:HomeFragment 时跳 nav_subject,其他时跳 nav_home。
- 跨 fragment 始终 visible(不受折叠态影响)。
- 图标复用项目里已有的 @drawable/ic_switch_page(与 home_menu / subject_detail_menu 里的切换菜单项一致)。
- HomeFragment.toggleSelectionMode() 改回 private(2.3.9 临时公开的多选入口不再需要)。

# 2.3.9
新增：展开态 fab_slot_1 槽位填入「多选」入口(完全照搬顶部 action_enter_selection 的行为)。
- 点 fab_slot_1 = 进入/退出选择模式,与顶部栏多选菜单项完全等价。
- 默认折叠态不可见,点 fab_expand_toggle 后才出现。
- 与 fab_random(随机)位置互不影响。
- 图标复用项目里已有的 @drawable/ic_select_all(与顶部多选菜单项一致)。

# 2.3.8
新增：设置页「FAB 区域位置」RadioGroup(左手模式 / 右手模式)。
- 右手模式(默认):fab 区域在右下角(2.3.7 行为)。
- 左手模式:fab 区域整体跑到左下角,镜像布局。
- 通过运行时改 `fabContainer` 的 CoordinatorLayout.LayoutParams.gravity,不动 xml。
- 整套 fab 区域(邮件 + 随机 + 展开/折叠 + 预留槽位)一起切换位置。

# 2.3.7
新增：右下角 FAB 区域支持「折叠 / 展开」两种形态（横向锚定在右下角竖条区域）。
- 折叠态：邮件 fab + 随机 fab + 展开/折叠 toggle(共 3 个可见 fab)。
- 展开态：在 toggle 上方预留 2 个空槽位（fab_slot_1 / fab_slot_2），后续按需填入次级功能。
- 所有 fab 半透明（alpha=0.5），便于不遮挡链接列表。
- 主按钮（随机 fab）位置锚定不变；切换展开/折叠只影响预留槽位显隐。
后续：将根据用户反馈把具体次级功能填入预留槽位（添加链接 / 回到顶部 / 跳到当前 WebView 链接等）。

# 2.3.6.3
修复：2.3.6.2 在主页点击「随机」FAB 仍弹「请在主页使用随机功能」。
根因：通过 NavHostFragment 反查 HomeFragment 时机不对（NavHostFragment 在 commit 期间会将自己 attach 到 nav_host_fragment_content_main 这个 id）。
修法：HomeFragment 自己声明 `public static volatile HomeFragment activeInstance`,onResume 设、onPause 清,生命周期完全对齐,MainActivity 点击 FAB 直接读这个静态引用 —— 不依赖任何 Navigation 内部时序。

# 2.3.6.2
修复：2.3.6.1 在主页点击「随机」FAB 弹「请在主页使用随机功能」（其实就在主页）。根因：onClick 用 `findFragmentById(nav_host_fragment_content_main)` 拿到的是 NavHostFragment 而非 HomeFragment。改为 NavDestination 监听器缓存 `currentHomeFragment`，点击直接用缓存引用。

# 2.3.6.1
修复：2.3.6 中设置页勾选「显示随机入口」后，右下角随机 FAB 仍不显示。
根因：fab_random 显隐依赖 `navController.getCurrentDestination()`，onCreate 调用时 navController 尚未 ready 导致永远 isHome=false。改为 NavDestination 监听器驱动 + 兜底调用。

# 2.3.6
新增：设置页「显示随机入口」开关 + 右下角浮动「随机」按钮（与顶部栏随机按钮行为一致：未在洗牌模式则进入并洗牌，已在则重新洗牌；仅在主页显示）。默认开启。

# 2.3.5
修复：打开「事件日志」页面崩溃（列表第一页的游标为空时被直接塞进了 SQL 参数）。2.3.1 ~ 2.3.4 均受影响，建议升级。

# 2.3.4
新增：事件日志「详情」弹窗可看字段级差异（old → data）——每条 update / delete 事件现在都带「变更前」快照（old），删除也留底。
（配套的同步服务端已同步升级；客户端向下兼容旧服务端。）

# 2.3.3
新增：本地一产生事件就自动推送（防抖合并 1.5 秒）——A 设备改完，B 设备点一下同步就能看到，不用等 A 手动点同步。

# 2.3.2
设置页「阅读模式」给「常规」补上说明：常规（不显示控制层）。

# 2.3.1
事件日志列表改为按 process_time（写入时刻）排序，不再忽新忽旧。

# 2.3
修复：删除一条链接并同步后，事件日志里会多冒出一条 CREATE（事件 id 的大小写与服务端不一致，导致远程拉取时按 id 去重失效）。
新增：置顶（is_pinned）纳入同步——任一设备置顶后，其他设备拉取即可恢复置顶状态；CSV/JSON 导入的置顶状态不再被丢掉。

# 1.5.2
修复了按URL全部删除的缺陷。实现了单个删除

# 1.5.1
首页增加了日历查看的功能，非常满意！

# 1.5.0
增加了分享到duxiang.ai的功能

## Bug List
下滑的时候，多选模式会自动取消。

# 1.4.2
实现了微信公众号文章的标题解析。

# v1.4

1. 增加rss的解析

> 其中rss的实现全靠基本手搓，不用Rome库
