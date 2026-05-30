# 随机推荐模式（洗牌功能）设计

## 需求概述

在主页增加随机推荐模式：
- 默认按时间倒序排列（按日期分组显示）
- 用户可进入洗牌模式，对普通链接随机排序显示
- 每次点击洗牌按钮重新洗牌
- 新增链接后自动回到时间排序模式
- 置顶链接始终固定在最前面，不参与洗牌

## 最终实现

### 1. 状态管理

新增成员变量：
- `isShuffleMode: boolean` — 当前是否处于洗牌模式
- `lastLinkAddTime: long` — 上次添加链接的时间戳（用于判断是否刚添加链接）

模式状态保存到 SharedPreferences，退出应用后恢复。

### 2. 菜单项

在 `home_menu.xml` 中新增两个菜单项：
- `action_shuffle` — 洗牌按钮（旋转图标），始终显示（非选择/非排序模式）
- `action_exit_shuffle` — 退出洗牌按钮（X图标），仅洗牌模式时显示

**按钮行为**：
- 第一次点击"洗牌"按钮：进入洗牌模式并洗牌
- 在洗牌模式下再次点击"洗牌"按钮：重新洗牌（不退出模式）
- 点击"退出洗牌"按钮：退出洗牌模式

### 3. 菜单切换逻辑

在 `updateMenuVisibility()` 中控制：
- `action_shuffle` 在 `!isSelectionMode && !isSortMode` 时显示
- `action_exit_shuffle` 在 `isShuffleMode` 时显示
- 洗牌模式下隐藏"添加标签"和"排序标签"按钮

### 4. 数据加载逻辑

**时间模式（默认）**：
- 普通链接按时间倒序排序
- 按日期分组显示（带有日期标题）

**洗牌模式**：
- 调用 `Collections.shuffle(normalLinks)` 随机排序
- 使用 `adapter.setFlatLinks()` 扁平化显示（无日期分组）
- 所有普通链接混在一起，置顶链接单独显示在最前面

**添加链接成功时（5秒内）**：
- 强制按时间排序显示
- 自动退出洗牌模式

### 5. 洗牌按钮点击逻辑

`onOptionsItemSelected()` 中处理：
- `R.id.action_shuffle`：
  - 如果是洗牌模式 → 调用 `reshuffleLinks()` 重新洗牌
  - 如果不是洗牌模式 → 调用 `toggleShuffleMode()` 进入洗牌模式
- `R.id.action_exit_shuffle` → 调用 `toggleShuffleMode()` 退出洗牌模式

**`toggleShuffleMode()`** 实现：
- 切换 `isShuffleMode` 状态
- 保存状态到 SharedPreferences
- 调用 `refreshLinksList()` 重新加载数据
- 更新菜单可见性

**`reshuffleLinks()`** 实现：
- 保持洗牌模式状态
- 调用 `refreshLinksList()` 重新洗牌

### 6. 新增链接处理

在 `onDeleteLink` / `onUpdateLink` 等新增链接的场景：
- 调用 `refreshLinksList()` 按时间排序显示
- **退出洗牌模式**：`isShuffleMode = false`，保存状态
- 确保能看到新链接是否添加成功

### 7. 置顶链接处理

置顶链接始终显示在最前面，不参与洗牌。

### 8. LinksAdapter 改动

新增方法：
- `setFlatLinks(List<LinkItem> links)` — 设置扁平化的链接列表（无日期分组，用于洗牌模式）

## 改动范围

| 文件 | 改动内容 |
|------|---------|
| `HomeFragment.java` | 新增 `isShuffleMode`、`lastLinkAddTime` 变量、`toggleShuffleMode()`、`reshuffleLinks()`、`recordLinkAddTime()` 方法、修改 `refreshLinksList()` 和 `updateContentBySelectedTags()` 支持洗牌逻辑 |
| `LinksAdapter.java` | 新增 `setFlatLinks()` 方法 |
| `MainActivity.java` | 添加链接成功后调用 `HomeFragment.recordLinkAddTime()` |
| `home_menu.xml` | 新增 `action_shuffle` 和 `action_exit_shuffle` 两个菜单项 |

## 实现步骤

1. 在 `home_menu.xml` 新增两个菜单项
2. 在 `HomeFragment.java` 新增 `isShuffleMode` 和 `lastLinkAddTime` 成员变量
3. 在 `LinksAdapter.java` 新增 `setFlatLinks()` 方法
4. 实现 `toggleShuffleMode()` 和 `reshuffleLinks()` 方法
5. 修改 `updateMenuVisibility()` 控制菜单可见性和隐藏标签相关按钮
6. 修改 `refreshLinksList()` 和 `updateContentBySelectedTags()` 支持洗牌逻辑
7. 在 `onOptionsItemSelected()` 中处理洗牌菜单点击事件
8. 在新增/删除链接的场景退出洗牌模式
9. 在 `MainActivity` 中添加链接成功后通知 `HomeFragment` 记录时间