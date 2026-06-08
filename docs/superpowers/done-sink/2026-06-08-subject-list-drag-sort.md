# 主题列表拖拽排序 (2026-06-08)

## 发现 (What we discovered)
- SubjectFragment 是主题列表页，之前没有拖拽排序功能
- SubjectDetailActivity 已有 ItemTouchHelper 拖拽实现（主题项列表）
- SubjectAdapter 通过长按弹出操作菜单（编辑/删除/添加到桌面）
- Subject 模型原有字段：id, title, describe, createTime，缺少 orderIndex
- SubjectDao 没有批量更新 orderIndex 的方法
- 数据库 schema 需要新增 order_index 列

## 学到 (What we learned)
- 项目使用 ItemTouchHelper 处理拖拽
- orderIndex 间隔使用 SubjectUtil.ORDER_INTERVAL = 10
- 数据库版本从 13 升级到 14，通过 ALTER TABLE 添加新列
- 排序模式切换时：Toolbar 标题和菜单按钮文字联动切换

## 完成 (What we accomplished)
- Subject 模型新增 orderIndex 字段及 getter/setter
- LinkDbHelper 新增 order_index 列，DATABASE_VERSION → 14
- SubjectDao 新增 updateSubjectsOrderIndex() 批量更新方法，getAllSubjects() 改用 orderIndex 排序
- subject_menu.xml 新增"排序"菜单项
- SubjectAdapter 新增 sortMode，禁用排序模式下的操作菜单
- SubjectFragment 整合 ItemTouchHelper，支持长按拖拽排序
