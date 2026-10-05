# FAB 区域 + 关于弹窗 — 阶段性汇报

> 状态: 已闭环,7 个版本号(2.3.6 → 2.3.12.1)一次性合到 `a1f911f`
> 时间: 2026-10-03 ~ 2026-10-05
> 阶段: 右下角 fab 区域从「单邮件 fab」演化成「4 个 fab + 折叠/展开 + 左手/右手镜像 + 关于弹窗」

## 1. 这次改了什么(一段话总结)

新增右下角浮动 fab 区域,承载:
- **fab_random**(主按钮,锚定位置不变):HomeFragment = 洗牌链接,SubjectFragment = 随机切换主题(重载式行为)
- **fab_expand_toggle**:展开/折叠两个空槽
- **fab_slot_1** = 切换链接/主题入口(跨 fragment 常驻,不受折叠影响)
- **fab_slot_2** = 空(预留)
- **fab**(邮件,移到容器最底部)
- 整个容器支持**左手/右手镜像**(`fab_side_left` boolean),运行时改 layoutParams
- 设置页底部新增**关于按钮**,弹窗显示 app 名 + versionName + GitHub 下划线链接(仿 `EventLogActivity.makeClickableHint` 样式)

## 2. 7 个版本号的演进

| versionName | versionCode | 内容 |
|---|---|---|
| 2.3.6 | 12 | 首次加入随机 fab(只显 HomeFragment) |
| 2.3.6.1 | 13 | fab 显示但点击弹 Toast bug 修复 |
| 2.3.6.2 | 14 | 仍未解决(继续踩坑中) |
| 2.3.6.3 | 15 | **关键修复**:HomeFragment 自己暴露 `activeInstance` 静态引用 |
| 2.3.7 | 16 | fab 区域折叠/展开形态 + 邮件 fab 下移 |
| 2.3.8 | 17 | 设置页加左手/右手 RadioGroup |
| 2.3.9 | 18 | fab_slot_1 = 多选入口(已被 2.3.10 覆盖) |
| 2.3.10 | 19 | fab_slot_1 = 切换链接/主题(跨 fragment 常驻) |
| 2.3.11 | 20 | 左手模式贴边修复(gravity 切换时 margin 镜像) |
| 2.3.12 | 21 | 主按钮在 SubjectFragment 也显示(随机切换主题) |
| **2.3.12.1** | **22** | 设置页底部新增关于按钮,弹窗显示 app 名 + versionName + GitHub 下划线链接 |

## 3. 关键 bug 与解法(本次最重要的工程教训)

### 3.1 「fab 显示但点击弹 Toast」—— 跨 fragment 找实例的陷阱

**症状**:
- 2.3.6:fab 根本不显示(用户勾选了开关也没用)
- 2.3.6.1:加 `addOnDestinationChangedListener` 后 fab 显示了,但**点击弹「请在主页使用随机功能」** —— 实际上**就在主页**

**根因(踩了三次坑才看清)**:

1. `findFragmentById(R.id.nav_host_fragment_content_main)` 拿到的是 **NavHostFragment 自己**(因为那个 id 属于 NavHostFragment),不是 HomeFragment。所以 `instanceof HomeFragment` 永远 false。
2. `navHost.getChildFragmentManager().getFragments().filter(HomeFragment)` 在点击瞬间拿不到 —— **Navigation 库内部 fragment swap 时机不稳**,onClick 触发时 child fragment 列表可能还没填充。
3. `addOnDestinationChangedListener` 缓存 `currentHomeFragment` —— listener 在 destination 切换前触发,fragment swap 还没完成,缓存永远是 null。

**正解**(`2.3.6.3` 闭环):

HomeFragment **自己声明在线状态**,生命周期自己管:

```java
// HomeFragment.java
public static volatile HomeFragment activeInstance;

@Override public void onResume() {
    super.onResume();
    activeInstance = this;
}

@Override public void onPause() {
    super.onPause();
    if (activeInstance == this) {
        activeInstance = null;
    }
}

// MainActivity 点击监听
binding.appBarMain.fabRandom.setOnClickListener(v -> {
    if (HomeFragment.activeInstance != null) {
        HomeFragment.activeInstance.onShuffleFabClicked();
    }
});
```

**用户原话**:"你在当前 activity 里面判断是哪个 fragment 的很难吗?为什么搞笑复杂?独立的不依赖任何东西去判断当前在哪个的下面不行吗?" —— **被骂醒的教训**。

**铁律**(已存 memory):
> 跨 fragment/activity 找实例,优先让目标自己声明在线状态,不要从容器反查。
> ❌ `findFragmentById(host_id) instanceof X`
> ❌ `getChildFragmentManager().getFragments().filter(X)`
> ❌ NavDestination listener 缓存 fragment
> ✅ `public static volatile X activeInstance` + onResume/onPause 自管理

`SubjectFragment` 在 2.3.12 完全照搬这个模式 —— 零踩坑。

### 3.2 左手模式 fab 贴边

**症状**:2.3.8 切到左手模式,fab 区域紧贴屏幕左边,没有右边距。

**根因**:`applyFabSide()` 只改了 `gravity`(START|BOTTOM),没改 margin。Android 在 `gravity=START` 时**自动无效化 marginEnd**,所以左手模式 leftMargin = 0,贴边。

**正解**(`2.3.11`):

运行时从 `@dimen/fab_margin` 读像素值,**不用镜像 leftMargin/rightMargin 的来回拷贝**(之前试过,逻辑太绕易错):

```java
int margin = (int) getResources().getDimension(R.dimen.fab_margin);
if (isLeft) {
    lp.gravity = GravityCompat.START | Gravity.BOTTOM;
    lp.leftMargin = margin;
    lp.rightMargin = 0;
} else {
    lp.gravity = GravityCompat.END | Gravity.BOTTOM;
    lp.rightMargin = margin;
    lp.leftMargin = 0;
}
```

两端对称,数值完全相同(竖屏 16dp / 横屏 48dp / 大屏 200dp,跟 `@dimen/fab_margin` 一致)。

### 3.3 fab_random 主按钮的「重载式行为」

用户拍板「同一个 fab 在 HomeFragment 洗牌链接 / SubjectFragment 切主题」,A 方案(重载 if-else):

```java
// MainActivity onClick
if (dest.getId() == R.id.nav_home) {
    HomeFragment.activeInstance.onShuffleFabClicked();
} else if (dest.getId() == R.id.nav_subject) {
    SubjectFragment.activeInstance.onRandomSubjectFabClicked();
}
```

**取舍**:
- ✅ 用户一句话能概括,实现简单
- ⚠️ 同一 fab id 两个语义,后人读 onClick 可能困惑
- ⚠️ 图标 `ic_menu_rotate` 在两个 fragment 含义略不同(洗牌 vs 随机切)

**用户权衡后选了 A**,尊重用户决定。

### 3.4 fab 显隐:用 `isHomeDestination` → `isRandomFabDestination`

最初 `isHomeDestination()` 只判 nav_home;2.3.12 扩展为 `isRandomFabDestination()` 判 `nav_home || nav_subject`。同时 `fab_expand_toggle` 跟主按钮同进退(没主按钮没展开意义)。

## 4. 关于弹窗(`2.3.12.1`)

用户原话:"那个事件日志(中间有一个链接不是可以点击吗)参考那个样式就行"。

参照 `EventLogActivity.java:142-149` 的 `makeClickableHint()` —— `SpannableString + UnderlineSpan` 模式,但 About 弹窗需要**可点击**(跳浏览器),所以用 `URLSpan` 而非 `UnderlineSpan`(`URLSpan` 自带 UnderlineSpan)。

**关键细节**:debug 构建默认不生成 `BuildConfig`(需要 `buildFeatures.buildConfig = true`),用 `PackageManager.getPackageInfo().versionName` 拿版本号,通用方案。

**弹窗内容**:

```
读享            ← 加粗
版本 2.3.12.1

https://github.com/notfresh/reading-share-android   ← 下划线,可点跳转
[关闭]
```

`versionCode` 不展示(主流 app 做法,versionCode 是给系统的,用户不需要看到)。

## 5. 改了哪些文件

```
M app/build.gradle                                              ← 版本号
M app/src/main/java/person/notfresh/readingshare/MainActivity.java
   - fab_random onClick(重载式 HomeFragment / SubjectFragment)
   - applyFabSide()(运行时镜像 gravity + margin)
   - updateRandomFabVisibility()(HomeFragment + SubjectFragment 双判)
   - updateFabExpandState()(fab_slot_1 常驻 / fab_slot_2 随折叠 toggle)
   - fab_expand_toggle / fabSlot1 点击监听
M app/src/main/java/person/notfresh/readingshare/ui/home/HomeFragment.java
   - public static volatile activeInstance
   - onResume / onPause 自我管理 activeInstance
   - public void onShuffleFabClicked()(暴露给 fab_random)
M app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java
   - 「显示随机入口」CheckBox 处理
   - 「FAB 区域位置」左手/右手 RadioGroup
   - 「关于」按钮 + showAboutDialog() 方法
M app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectFragment.java
   - public static volatile activeInstance(照搬 HomeFragment 模式)
   - public void onRandomSubjectFabClicked()(暴露给 fab_random)
M app/src/main/res/layout/app_bar_main.xml
   - LinearLayout 容器从单 fab 演化成 4 个 fab
   - 所有 fab 半透明(alpha=0.5)
M app/src/main/res/layout/fragment_slideshow.xml
   - 「显示随机入口」CheckBox
   - 「FAB 区域位置」RadioGroup
   - 「关于」按钮(末尾)
M dev-docs/release.md                                            ← 7 条 release note
```

## 6. 流程001(下载链接)留存

| versionName | 流程001 token | 备注 |
|---|---|---|
| 2.3.6 | `IRXfAKPA` | 已过期(4h GC) |
| 2.3.6.1 | `VYA918uV` | 已过期 |
| 2.3.6.2 | `UP6BNHmG` | 已过期 |
| 2.3.6.3 | `hIC-yRzA` | 已过期,闭环 |
| 2.3.7 | `_qlBKACy` | 已过期 |
| 2.3.8 | `PwVe3BU8` | 已过期 |
| 2.3.9 | `wJBgaXq6` | 已过期 |
| 2.3.10 | `pP1ZabHh` | 已过期 |
| 2.3.11 | `3Dzvce07` | 已过期 |
| 2.3.12 | `pDMQpwQE` | 已过期 |
| 2.3.12.1 | `5WUxN4NP` / `NEqZSF1J` | 重新发布,后者为最新 |

## 7. 未完成 / 待办

- `fab_slot_2` 还是空,用户尚未指定填什么
- 动画(展开/折叠的 transition)用户已确认不需要(代码量 < 20 行的纪律)
- 左手/右手模式无动画切换(瞬切),用户没要求加动画

## 8. 给未来自己的提示

- **不要在 `addOnDestinationChangedListener` 里 cache fragment 引用** —— Navigation 内部 swap 时机不稳
- **不要 `findFragmentById(host_id) instanceof X`** —— 那个 id 是 NavHostFragment 自己的
- **左手/右手镜像 margin 必须运行时从 dimens.xml 读**,不要 `leftMargin = rightMargin`(来回拷,逻辑绕易错)
- **debug 构建不生成 BuildConfig** —— 用 `PackageManager.getPackageInfo().versionName` 通用方案
