# 主题列表拖拽排序设计方案

## 需求概述

为主题列表（SubjectFragment）添加长按拖拽排序功能，参考主题项列表（SubjectDetailActivity）的拖拽实现。

## 交互流程

### 普通模式
- 点击 Toolbar "排序"按钮 → 进入排序模式
- 长按显示操作菜单（编辑/删除/添加到桌面）

### 排序模式
- Toolbar 显示"完成"按钮，标题变为"排序中..."
- 长按 = 拖拽排序
- 松手自动保存到数据库
- 点击"完成" → 退出排序模式

## 实现方案

### 1. 数据层

#### Subject 模型
- 新增 `orderIndex` 字段（int 类型）
- 提供 getter/setter

#### SubjectDao
- 新增 `updateSubjectsOrderIndex(List<Subject> subjects)` 批量更新方法
- 修改 `getAllSubjects()` → `getAllSubjectsOrderByOrderIndex()` 按 orderIndex 排序

### 2. UI 层

#### 菜单
- `subject_menu.xml` 新增"排序"菜单项（图标：ic_sort 或类似）

#### SubjectFragment
- 新增排序模式状态管理：`isSortMode`、`itemTouchHelper`
- Toolbar 动态切换：普通模式显示"排序"按钮，排序模式显示"完成"按钮
- 排序模式下禁用操作菜单响应

#### SubjectAdapter
- 新增 `setSortMode(boolean)` 方法
- sortMode = true 时：长按不再弹菜单，直接进入拖拽
- sortMode = false 时：长按显示操作菜单

### 3. 拖拽实现

使用 `ItemTouchHelper`，与 SubjectDetailActivity 完全一致：

```java
ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
    // 上下拖拽，不支持侧滑
    ...
};
```

拖拽结束（clearView）：
1. 按当前列表顺序重新分配 orderIndex
2. 批量更新到数据库
3. 刷新列表

### 4. 常量

使用 `SubjectUtil.ORDER_INTERVAL = 10` 作为 orderIndex 间隔常量。

## 文件改动清单

| 文件 | 改动内容 |
|------|----------|
| `Subject.java` | 新增 `orderIndex` 字段及 getter/setter |
| `SubjectDao.java` | 新增 `updateSubjectsOrderIndex()` 批量更新方法 |
| `subject_menu.xml` | 新增"排序"菜单项 |
| `SubjectFragment.java` | 排序模式状态、Toolbar 动态切换、ItemTouchHelper |
| `SubjectAdapter.java` | `setSortMode()` 方法 |

## 设计约束

- 排序模式下不显示特殊视觉标记
- 排序模式的视觉提示：Toolbar 标题变为"排序中..."
- 操作菜单在排序模式下禁用