# 随机推荐模式（洗牌功能）设计

## 需求概述

在主页增加随机推荐模式：
- 默认按时间倒序排列
- 用户可进入洗牌模式，对非置顶链接随机排序显示
- 每次点击洗牌按钮重新洗牌
- 新增链接后自动回到时间排序模式
- 置顶链接始终固定在最前面，不参与洗牌

## 设计方案

### 1. 状态管理

新增成员变量：
- `isShuffleMode: boolean` — 当前是否处于洗牌模式
- `shuffledLinks: List<LinkItem>` — 缓存洗牌后的非置顶链接（可选优化）

模式状态保存到 SharedPreferences，退出应用后恢复。

### 2. 菜单项

在 `home_menu.xml` 中新增两个菜单项：
- `action_shuffle` — 洗牌按钮，非洗牌模式时显示
- `action_exit_shuffle` — 退出洗牌按钮，洗牌模式时显示

### 3. 菜单切换逻辑

在 `updateMenuVisibility()` 中增加：
- `action_shuffle` 仅在 `!isSelectionMode && !isSortMode && !isShuffleMode` 时显示
- `action_exit_shuffle` 仅在 `isShuffleMode` 时显示

### 4. 数据加载逻辑

**`refreshLinksList()`** 修改：
- 获取置顶链接 `pinnedLinks`
- 获取非置顶链接 `normalLinks`
- **时间模式（默认）**：非置顶链接按时间倒序排序
- **洗牌模式**：`Collections.shuffle(normalLinks)` 随机排序
- 设置数据到 adapter：`setPinnedLinks(pinnedLinks)` + `setGroupedLinks(按日期分组后的结果)`

**添加链接成功时**：
- 调用 `refreshLinksList()` 自动按时间排序（现有逻辑）
- 调用 `toggleShuffleMode()` 退出洗牌模式，确保 UI 一致

### 5. 洗牌按钮点击逻辑

`onOptionsItemSelected()` 中新增：
- `R.id.action_shuffle` → 调用 `toggleShuffleMode()`
- `R.id.action_exit_shuffle` → 调用 `toggleShuffleMode()`

**`toggleShuffleMode()`** 实现：
- 切换 `isShuffleMode` 状态
- 保存状态到 SharedPreferences
- 调用 `refreshLinksList()` 重新加载数据
- 更新菜单可见性

### 6. 新增链接处理

在 `onDeleteLink` / `onUpdateLink` 等新增链接的场景：
- 调用 `refreshLinksList()` 按时间排序显示
- **退出洗牌模式**：`isShuffleMode = false`，保存状态
- 确保能看到新链接是否添加成功

### 7. 置顶链接处理

置顶链接（`linkDao.getPinnedLinks()`）始终显示在最前面，不参与洗牌。

## 改动范围

| 文件 | 改动内容 |
|------|---------|
| `HomeFragment.java` | 新增 `isShuffleMode` 变量、`toggleShuffleMode()` 方法、`onOptionsItemSelected()` 中新增 case、修改 `refreshLinksList()` 逻辑 |
| `home_menu.xml` | 新增 `action_shuffle` 和 `action_exit_shuffle` 两个菜单项 |
| `strings.xml` | 新增菜单项字符串（如需） |

## 实现步骤

1. 在 `home_menu.xml` 新增两个菜单项
2. 在 `HomeFragment.java` 新增 `isShuffleMode` 成员变量
3. 实现 `toggleShuffleMode()` 方法
4. 修改 `updateMenuVisibility()` 控制菜单可见性
5. 修改 `refreshLinksList()` 支持洗牌逻辑
6. 在 `onOptionsItemSelected()` 中处理菜单点击事件
7. 在新增/删除链接的场景退出洗牌模式