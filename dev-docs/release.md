## B

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
