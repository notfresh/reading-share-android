# 侧边栏菜单顺序与「首页→链接」调整 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把侧边栏菜单的第一项从「首页」改为「主题」,把侧边栏与设置页默认 Tab 中的「首页」文案改为「链接」,顺手修复 `MainActivity.handleNavigation()` switch 中 `default_tab` 索引与 `default_tabs_array` 不对齐的既存 bug。

**Architecture:** 三处文件级 modify,无新增文件;无新增运行时组件;case 数值替换为与 string-array 索引对齐。

**Tech Stack:** Android(Java),Material Components NavigationView,AndroidX Navigation Component(Graph),Gradle。

## Global Constraints

- 仅修改 3 个文件:`app/src/main/res/menu/activity_main_drawer.xml`、`app/src/main/res/values/strings.xml`、`app/src/main/java/person/notfresh/readingshare/MainActivity.java`。其它文件一律不动。
- 不引入新依赖、不改 Gradle 配置、不改 minSdk/targetSdk、不改资源 qualifier。
- 不改菜单项视觉样式(`textAppearance`、`checkableBehavior`、图标)。
- 不改 `nav_home` / `nav_subject` 的 `id` 与导航目标 fragment。
- 不改 `default_tab` 的默认值(仍为 `2`),不主动迁移旧值。
- 每个 Task 完成后必须 `git commit`,commit message 严格使用类型前缀:`docs` / `feat` / `fix`。
- 用户要求"尽量小范围修改"和"不要随便执行编译等命令"——本计划不强制要求本地 build 验证(用户会自行验证)。

---

## Task 1: 调整侧边栏菜单顺序(主题移到第一项)

**Files:**
- Modify: `app/src/main/res/menu/activity_main_drawer.xml:7-36`

**Interfaces:**
- Consumes:无
- Produces:菜单 `<item>` 顺序变为 `nav_subject → nav_home → nav_archive → nav_rss → nav_documents → nav_slideshow`。

**前置核对:**

```bash
git status --short
```

预期:存在未 commit 修改(本计划开始前仓库其它改动),但 `activity_main_drawer.xml` 不应出现在 modified 列表里——若有,说明该文件被外部改动,先 `git diff app/src/main/res/menu/activity_main_drawer.xml` 确认未冲突。

- [ ] **Step 1: 备份原始顺序**

```bash
git diff app/src/main/res/menu/activity_main_drawer.xml
```

预期:无差异(`No newline` 类提示除外)。

- [ ] **Step 2: 用 Edit 替换整段菜单主体**

使用 `Edit` 工具,`old_string` 与 `new_string` 如下。`old_string` 包含行 7-37(从 `<item android:id="@+id/nav_home"` 开始到 `</group>` 之前),完整替换。

**old_string:**

```xml
        <item
            android:id="@+id/nav_home"
            android:icon="@drawable/ic_home"
            android:title="@string/menu_home"
            android:textAppearance="@style/NavigationDrawerText"/>
        <item
            android:id="@+id/nav_archive"
            android:icon="@drawable/ic_archive"
            android:title="@string/menu_archive"
            android:textAppearance="@style/NavigationDrawerText"/>
        <item
            android:id="@+id/nav_rss"
            android:icon="@drawable/rss"
            android:title="@string/menu_rss"
            android:textAppearance="@style/NavigationDrawerText"/>
        <item
            android:id="@+id/nav_documents"
            android:icon="@drawable/ic_documents"
            android:title="@string/menu_documents"
            android:textAppearance="@style/NavigationDrawerText"/>
        <item
            android:id="@+id/nav_subject"
            android:icon="@drawable/ic_subject"
            android:title="@string/menu_subject"
            android:textAppearance="@style/NavigationDrawerText"/>
        <item
            android:id="@+id/nav_slideshow"
            android:icon="@drawable/ic_settings"
            android:title="@string/menu_settings"
            android:textAppearance="@style/NavigationDrawerText"/>
```

**new_string:**

```xml
        <item
            android:id="@+id/nav_subject"
            android:icon="@drawable/ic_subject"
            android:title="@string/menu_subject"
            android:textAppearance="@style/NavigationDrawerText"/>
        <item
            android:id="@+id/nav_home"
            android:icon="@drawable/ic_home"
            android:title="@string/menu_home"
            android:textAppearance="@style/NavigationDrawerText"/>
        <item
            android:id="@+id/nav_archive"
            android:icon="@drawable/ic_archive"
            android:title="@string/menu_archive"
            android:textAppearance="@style/NavigationDrawerText"/>
        <item
            android:id="@+id/nav_rss"
            android:icon="@drawable/rss"
            android:title="@string/menu_rss"
            android:textAppearance="@style/NavigationDrawerText"/>
        <item
            android:id="@+id/nav_documents"
            android:icon="@drawable/ic_documents"
            android:title="@string/menu_documents"
            android:textAppearance="@style/NavigationDrawerText"/>
        <item
            android:id="@+id/nav_slideshow"
            android:icon="@drawable/ic_settings"
            android:title="@string/menu_settings"
            android:textAppearance="@style/NavigationDrawerText"/>
```

- [ ] **Step 3: 校验顺序**

```bash
grep -n 'android:id="@+id/nav_' app/src/main/res/menu/activity_main_drawer.xml
```

预期(行号近似,只关心顺序):`nav_subject` 在 `nav_home` 之前,其它项顺序未变。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/menu/activity_main_drawer.xml
git -c user.name="Kimi" -c user.email="kimi@local" commit -m "feat(drawer): reorder nav_subject above nav_home in side menu"
```

预期:`1 file changed` 且仅该文件被加入 commit。

---

## Task 2: 重命名侧边栏 + 设置页默认 Tab 中「首页」文案为「链接」

**Files:**
- Modify: `app/src/main/res/values/strings.xml:11`
- Modify: `app/src/main/res/values/strings.xml:20-25`

**Interfaces:**
- Consumes:Task 1 已完成(`menu_home` string 引用不变,仅改值)
- Produces:`R.string.menu_home` 显示"链接";`R.array.default_tabs_array[0]` 显示"链接"。

- [ ] **Step 1: 替换 `menu_home` string 值**

使用 `Edit`,`old_string`:

```xml
    <string name="menu_home">首页</string>
```

`new_string`:

```xml
    <string name="menu_home">链接</string>
```

- [ ] **Step 2: 替换 `default_tabs_array` 第 0 项**

`old_string`:

```xml
    <string-array name="default_tabs_array">
        <item>首页</item>
        <item>主题</item>
        <item>RSS</item>
        <item>随机</item>
    </string-array>
```

`new_string`:

```xml
    <string-array name="default_tabs_array">
        <item>链接</item>
        <item>主题</item>
        <item>RSS</item>
        <item>随机</item>
    </string-array>
```

- [ ] **Step 3: 校验**

```bash
grep -n -E 'menu_home|default_tabs_array|<item>' app/src/main/res/values/strings.xml | head -20
```

预期:`menu_home` 行的值为"链接";`default_tabs_array` 四个 `<item>` 依次为 链接 / 主题 / RSS / 随机。

- [ ] **Step 4: 全仓搜索确认没有遗漏的"首页" string 资源引用**

```bash
grep -rn '"首页"\|@string/menu_home\b' app/src/main
```

预期:不出现 `menu_home` 字符串资源的新引用(只允许出现 `default_tabs_array` 第 0 项位置的"首页"字符串——但已被 Task 2 Step 2 改掉,所以理论上应为零命中)。若出现其它文件硬编码"首页"文案,属于本次不动的 HomeFragment 内部,忽略。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml
git -c user.name="Kimi" -c user.email="kimi@local" commit -m "feat(ui): rename drawer Home label and default-tab option to 链接"
```

---

## Task 3: 修复 `handleNavigation()` switch 中 `default_tab` 索引

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/MainActivity.java:255-296`

**Interfaces:**
- Consumes:Task 2 已完成,`default_tabs_array` 索引含义固定为 `0=链接, 1=主题, 2=RSS, 3=随机`。
- Produces:switch 各 `case` 值与 string-array 索引对齐。

**前置核对:**

```bash
git diff app/src/main/java/person/notfresh/readingshare/MainActivity.java | head -50
```

确认外部未对 `handleNavigation()`(行 255-296)做冲突性修改。若有冲突,先停手让用户决定。

- [ ] **Step 1: 替换 `case 0` 注释**

`old_string`:

```java
                case 0: // 首页
```

`new_string`:

```java
                case 0: // 链接
```

- [ ] **Step 2: 替换 `case 2 → case 1`**

`old_string`:

```java
                case 2: // 主题
```

`new_string`:

```java
                case 1: // 主题
```

- [ ] **Step 3: 替换 `case 3 → case 2`**

`old_string`:

```java
                case 3: // RSS
```

`new_string`:

```java
                case 2: // RSS
```

- [ ] **Step 4: 替换 `case 4 → case 3`**

`old_string`:

```java
                case 4: { // 随机：首页/主题/RSS
```

`new_string`:

```java
                case 3: { // 随机：链接/主题/RSS
```

- [ ] **Step 5: 替换 `default` 注释**

`old_string`:

```java
                default: // 默认首页（包括已废弃的case 1标签页）
```

`new_string`:

```java
                default: // 默认链接（含已废弃的索引值）
```

- [ ] **Step 6: 同步行 272 注释(默认值仍为 2,只是文字调整)**

`old_string`:

```java
            int defaultTab = prefs.getInt("default_tab", 2); // 默认为2，即主题页(跳过HomeFragment启动)
```

`new_string`:

```java
            int defaultTab = prefs.getInt("default_tab", 2); // 默认为2，即RSS页(索引修复后,主题对应case 1)
```

注意:这一处默认值的副作用是——新装用户首启会落到 RSS(`case 2`),而非主题。这是已经写进 spec 风险段的事实;此处只在注释里如实标注,**不调整默认值**。

- [ ] **Step 7: 校验 switch 段最终内容**

```bash
sed -n '270,300p' app/src/main/java/person/notfresh/readingshare/MainActivity.java
```

预期(行号可能因前面步骤的替换产生 ±1 偏差,关注 case 值与注释文字):出现 `case 0: // 链接`、`case 1: // 主题`、`case 2: // RSS`、`case 3: { // 随机:链接/主题/RSS`、`default: // 默认链接(含已废弃的索引值)`,且不再出现 `case 4:`。

- [ ] **Step 8: 确认文件其它逻辑未受影响**

```bash
git diff app/src/main/java/person/notfresh/readingshare/MainActivity.java
```

预期:差异仅出现在 `handleNavigation()` switch 段;`navigateTo()`、Toolbar、DrawerLayout、NavigationView 绑定等其它逻辑 0 改动。

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/person/notfresh/readingshare/MainActivity.java
git -c user.name="Kimi" -c user.email="kimi@local" commit -m "fix(nav): align handleNavigation switch with default_tabs_array indices"
```

---

## Task 4: 自检 + release notes

**Files:**
- Modify: 无(纯校验)

- [ ] **Step 1: 校验全部三处改动独立 commit**

```bash
git log --oneline -3
```

预期:最近 3 个 commit 依次为本次的 Task 1 / Task 2 / Task 3,每个 commit 只改一个文件。

- [ ] **Step 2: 校验无意外的其它改动夹带**

```bash
git status --short
```

预期:工作区干净(无 modified / untracked 中的本次任务文件)。仓库原本就在工作中的其它文件保持原状。

- [ ] **Step 3: 校验 case 数值唯一性**

```bash
grep -nE 'case [0-9]:|default:' app/src/main/java/person/notfresh/readingshare/MainActivity.java | head -10
```

预期:在 `handleNavigation()` 内出现 `case 0:`、`case 1:`、`case 2:`、`case 3:`、`default:`,无重复,无 `case 4:` 残留。

- [ ] **Step 4: 准备 release notes 草稿(给用户参考,不写入仓库)**

在终端输出以下文字供用户复制到 release notes:

> 侧边栏菜单顺序调整:「主题」移至第一项;原「首页」项保留在第二位,展示为「链接」(强调该页是链接集合)。
>
> 设置页「默认打开」选项同步调整:第一项「首页」改为「链接」;同时修复 default_tab 索引与代码 switch 不对齐的 bug。
>
> ⚠️ 行为变化:曾在设置中选择「主题」并保存的旧用户,升级后会落到「RSS」页(原 bug 索引错位);曾在设置中选择「随机」的旧用户,升级后会落到默认页。请升级后重新选择。

不写文件,只口头给出。

---

## Self-Review 报告

(写计划时即做的内联自审)

1. **Spec 覆盖**:
   - spec 改动 1:`activity_main_drawer.xml` 重排 → Task 1 ✓
   - spec 改动 2:`strings.xml` `menu_home` 文案 → Task 2 Step 1 ✓
   - spec 改动 3:`strings.xml` `default_tabs_array` 第 0 项 → Task 2 Step 2 ✓
   - spec 改动 4:`MainActivity.java` switch 索引 → Task 3 Steps 1-5 ✓
   - spec 改动 5:`default` 注释 → Task 3 Step 5 ✓
   - spec 改动 6:行 272 默认值注释 → Task 3 Step 6 ✓
   - spec 风险段:不主动迁移旧值 / 不调整默认值 — 已在 Task 3 Step 6 与 release notes 中明确 ✓
   - spec 验证段:无现存测试基础设施,跳过测试步骤 ✓

2. **占位符扫描**:无 TBD/TODO/"implement later"/"similar to" 类表述;每个步骤含具体代码或命令。

3. **类型 / 名称一致性**:
   - 索引定义在 Task 3 的 Interfaces 块:`0=链接, 1=主题, 2=RSS, 3=随机`,后续 Step 引用一致。
   - case 值 `0、1、2、3` 在所有 Step 中统一。
   - `menu_home` string name 在 Task 2 全文一致。
   - `default_tabs_array` array name 在 Task 2、Task 3 一致。