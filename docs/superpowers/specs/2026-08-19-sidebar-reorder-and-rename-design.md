# 侧边栏菜单顺序与「首页→链接」文案调整(2026-08-19)

## 一句话总结

把抽屉菜单第一项从「首页」改成「主题」,把「首页」的展示文案改为「链接」(用于强调该页是链接集合),并顺手修复 `default_tab` 设置值与 `handleNavigation()` switch 之间的索引不一致问题。

## 背景与动机

- 用户希望打开应用后第一眼看到「主题」而不是「首页」,因为主题才是高频入口;当前 `nav_home` 在抽屉中排第一项(详见 `app/src/main/res/menu/activity_main_drawer.xml:7-11`),`nav_subject` 排第五(行 27-31)。
- 「首页」实际是用户保存的链接列表,展示为「链接」对用户更直观。
- `MainActivity.handleNavigation()` 当前使用魔数 `0、2、3、4` 解释 `default_tab`,而 `default_tabs_array`(`strings.xml:20-25`)的实际索引是 `0=首页, 1=主题, 2=RSS, 3=随机`——这两套值不对齐,导致「主题」在代码中写成 `case 2`(应是 `case 1`),「RSS」写成 `case 3`(应是 `case 2`),「随机」写成 `case 4`(应是 `case 3`)。这是既存 bug,改名/重排后更容易混淆,所以顺手修。

## 改动范围

只动 3 个文件,不修改 HomeFragment / 导航图 / Toolbar 样式 / 其它侧边栏文案。

### 1. `app/src/main/res/menu/activity_main_drawer.xml`

- 把 `nav_subject` 的整段 `<item>`(行 27-31)移到 `nav_home` 之前。
- 调整后顺序:`nav_subject` → `nav_home` → `nav_archive` → `nav_rss` → `nav_documents` → `nav_slideshow`。
- item 内部 `id` / `icon` / `title` / `textAppearance` 全部保留。
- `group android:checkableBehavior="single"` 与外层结构不动。
- 顶部/底部空行与缩进按当前风格保留。

### 2. `app/src/main/res/values/strings.xml`

- 行 11:`<string name="menu_home">首页</string>` → `<string name="menu_home">链接</string>`。
- 行 20-25 `<string-array name="default_tabs_array">` 第 0 项 `<item>首页</item>` → `<item>链接</item>`。
- 其它 string 一律不动(含 `menu_subject=主题`, `menu_settings=设置`, `menu_archive=归档` 等)。

### 3. `app/src/main/java/person/notfresh/readingshare/MainActivity.java`

`handleNavigation()`(行 255-296)的 switch 索引重新对齐 `default_tabs_array`:

| 旧 case | 旧含义 | 新 case | 新含义 | 行为 |
|---|---|---|---|---|
| `case 0` | 首页 | `case 0` | 链接 | 不变 |
| `case 2` | 主题 | `case 1` | 主题 | 同步索引 |
| `case 3` | RSS | `case 2` | RSS | 同步索引 |
| `case 4` | 随机 | `case 3` | 随机 | 同步索引 |
| `default` | 首页 | `default` | 链接 | case 注释从"已废弃的 case 1 标签页"改为"已废弃的索引值,落到首页" |

具体改动:

- 行 272:`int defaultTab = prefs.getInt("default_tab", 2); // 默认为2,即主题页(跳过HomeFragment启动)` 注释中"默认为2"改为"默认为2(主题)",不动魔数(默认值调整不在本次范围)。
- 行 276:`case 0: // 首页` → `case 0: // 链接`。
- 行 279:`case 2: // 主题` → `case 1: // 主题`。
- 行 282:`case 3: // RSS` → `case 2: // RSS`。
- 行 285:`case 4: { // 随机:首页/主题/RSS` → `case 3: { // 随机:首页/主题/RSS`。
- 行 292:`default: // 默认首页(包括已废弃的case 1标签页)` → `default: // 默认首页(含已废弃索引值)`。

## 不在本次改动范围

- HomeFragment(`app/src/main/java/person/notfresh/readingshare/ui/home/HomeFragment.java`)内部 Toolbar/菜单/代码注释。
- 导航图 `mobile_navigation.xml` 中 `nav_home` / `nav_subject` 的 `android:label`(决定 Fragment 顶部 Toolbar 标题,保持"首页"以与本次范围一致)。
- 任何菜单项的视觉样式(`NavigationDrawerText`)、图标、`checkable` 行为。
- 共享偏好键 `default_tab` 的默认值(`MainActivity.java:272` 仍为 `2`,由于范围控制;新装用户未主动设置过的情况下,会落到 `default` 分支即首页,这是历史行为)。
- 旧 `default_tab` 值的迁移:不主动把旧 `2` 映射到新 `1`,因为只有「主题」原本就是 bug 配置,真正选过主题的用户通常已经迁移到 `2` 后又改回过其它值;迁移逻辑不属于本次任务。

## 行为差异

| 维度 | 改前 | 改后 |
|---|---|---|
| 侧边栏第一项 | 首页 | 主题 |
| 侧边栏第二项 | 归档 | 链接(原首页) |
| 侧边栏「首页」文案 | 首页 | 链接 |
| 设置页「默认打开」第 0 项 | 首页 | 链接 |
| `default_tab=0` 跳转目标 | 首页 | 首页(行为不变,只是注释文字变) |
| `default_tab=2` 跳转目标 | 主题 | RSS(索引修复后的副作用) |
| 旧用户已设 `default_tab=2` | 进入主题 | 进入 RSS |
| 旧用户已设 `default_tab=3` | 进入 RSS | 进入 RSS(行为不变) |
| 旧用户已设 `default_tab=4` | 进入随机 | 进入 RSS(索引修复后落到 default 分支=首页) |

## 风险

- **旧用户偏好值**:存在少数用户主动选过"主题"且偏好值被保存为 `2` 的情况,升级后会落到 RSS。
  - 风险等级:中。
  - 缓解:在 release notes 中标注「默认打开页选项已修正,旧选『主题』的设置现在会跳到 RSS,请重新选择」。
  - 不在本次代码层做迁移(范围控制)。
- **新装用户默认值**:未设置过 `default_tab` 的新装用户会得到默认 `2`(RSS),而不是「主题」。
  - 风险等级:低-中(用户首次启动会落到 RSS 而非主题,违反「让主题更突出」的目标)。
  - 决定:不在本次改动默认值。后续如果要调整,需要产品/用户决策。
- **`navigation/mobile_navigation.xml` startDestination** 仍为 `nav_home`,所以即使 `default_tab` 未生效时(没有 Intent `navigate_to`、SharedPreferences 为空),首屏仍是 HomeFragment,与本改动方向(让主题更突出)略有冲突。
  - 决定:本任务不动 startDestination。

## 验证

- 编译:`./gradlew :app:assembleDebug`(用户已限定不随便跑编译,建议提交前手动跑一次)。
- 手动:
  - 打开抽屉,菜单第一项显示"主题"。
  - 第二项显示"链接",点击能跳到 HomeFragment,且顶部 Toolbar 标题仍为"首页"。
  - 其它项顺序不变(归档 / RSS / 文档 / 设置)。
  - 进入设置 → 「默认打开」,第一项文案为「链接」;选择后保存,重启应用能进入正确页。
  - 不需要新测试用例(无现存测试基础设施覆盖菜单)。

## 关联文件路径

- `app/src/main/res/menu/activity_main_drawer.xml:7-36`
- `app/src/main/res/values/strings.xml:11` 与 `:20-25`
- `app/src/main/java/person/notfresh/readingshare/MainActivity.java:255-296`
- 关联不动文件:`app/src/main/res/navigation/mobile_navigation.xml`(导航图,不改);`app/src/main/res/values/styles.xml:22-27`(`NavigationDrawerText`,不改);`app/src/main/res/layout/nav_header_main.xml`(抽屉头,不改)。