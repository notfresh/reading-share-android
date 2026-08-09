# Subject Fragment Detail Design

## Goal

让 Subject 模块进入后直接显示上次查看的主题详情，主题列表作为详情页上的弹窗，用于切换和管理主题。

## Architecture

`SubjectFragment` 成为 Subject 功能的唯一页面容器，承载主题详情、主题项操作、拖拽排序和入口记忆。主题列表通过 `SubjectListDialog` 弹出，复用现有 `SubjectAdapter` 及其创建、编辑、删除、快捷方式和排序逻辑。`SubjectDetailActivity` 不再承担主流程；桌面快捷方式仍可通过它进入，随后可兼容转发到 Subject 模块。

## Behavior

- 有效的上次主题 ID：进入 Subject 后直接加载该主题详情。
- 没有上次主题或主题已删除：显示主题列表弹窗。
- 点击详情工具栏的“切换主题”：显示主题列表弹窗。
- 选择主题：关闭弹窗、刷新当前详情、保存上次主题 ID。
- 删除当前主题：清除无效的上次主题 ID，并显示主题列表弹窗。
- 不再提供入口偏好设置和 `SOURCE_LIST/SOURCE_DIRECT` 页面分支。
- 只有一个链接的主题项也直接打开网页；多个链接时继续传递上下文翻页参数。

## Scope

本次不修改数据库结构、不改变主题项数据模型、不修改主页和标签页批量添加主题项的行为。入口设置类可保留为未使用代码，待后续清理。

## Validation

- 运行 `testDebugUnitTest`。
- 运行 `:app:assembleDebug`。
- 至少覆盖 `SubjectFragment` 的无历史主题、恢复上次主题和删除当前主题后的列表弹窗状态测试；若 Fragment 测试基础设施不足，保留现有 `SubjectEntryManagerTest` 并通过构建验证生命周期代码。