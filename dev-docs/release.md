## B

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
