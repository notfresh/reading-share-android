# 文档添加到桌面 + 重命名 (2026-06-11)

## 发现
- DocumentAdapter 与 LinksAdapter 在 popup 菜单 + dialog 模式上高度同构(都基于 OnXxxActionListener 接口,菜单项 dispatch 逻辑一致)
- HomeFragment 已有完整的"链接添加到桌面"实现,可作为文档版的对齐参考
- ShortcutUtil 已经有 createSubjectShortcut(Context, String, long) + tryCreateSubjectShortcutModern + createSubjectShortcutLegacy 三件套,文档版沿用同结构
- ImageUtil.uriToBitmap + resizeToSquareForShortcut 已经存在并被 HomeFragment 复用,文档版无需新增图像处理代码
- DocumentFragment 已有 onUpdateDocument listener 完整实现(改 DAO.updateDocumentTitle + loadDocuments 刷新),重命名功能直接复用,Fragment 侧零新代码
- DocumentViewerActivity 不是 singleTask,无需 WebShortcutActivity 风格的桥接 Activity
- 项目无自动化测试框架,所有验收靠 spec §8 的 18 个手测用例

## 学到
- **"沿用现有模式" 比 "抽象成共享代码" 优先级更高**:DocumentAdapter 和 LinksAdapter 95% 重复,但项目选择让两边各自维护,reviewer 也明确说"defensible",说明 YAGNI 在此胜过 DRY
- **staged delivery 模式** 解决"接口变更 + 实现"拆任务导致的编译中断:Task 3-4 加接口方法和 stub(只 Toast "请稍候")保持 build green,Task 5 才填实现
- **Bitmap.recycle() 的正确写法**:`if (square != src) src.recycle();` —— resize 可能返回同一实例(已是方形),盲目 recycle 会破坏 square
- **RequestCode 命名空间化防冲突**:REQUEST_CODE_PICK_FILE = 1001 (已用) → 新加 REQUEST_CODE_PICK_ICON = 1002,spec 写错了,plan 修的
- **subagent 的 stage 上下文需在 prompt 里显式声明**:code quality reviewer 一开始把 stub 误判为 "dead UI",re-dispatch 时附 plan 上下文后通过 —— 提示模板应预设 "staged delivery 警告"
- **Task 1 的 Minor 在 final review 升为 Important**:ShortcutUtil.createDocumentShortcutLegacy 比 createSubjectShortcutLegacy 少资源 ID 兜底/默认 bitmap 兜底/提示 Toast,单独看是 Minor,横向对比才发现是"功能缺失"——重要问题往往在 final review 才暴露
- **用户的"顺便加一个"** 是合理的需求扩展,不需要走完整 spec → plan 循环,在 brainstorming 时直接并入,代价极低

## 完成
- 6 个 feat commit (c906924 → e21cf22)
- Debug APK 编译通过 (7.27 MB, app/build/outputs/apk/debug/app-debug.apk)
- spec §8 的 18 个手测用例全部覆盖
- 4 个新方法在 ShortcutUtil(createDocumentShortcut × 3 重载)
- 1 个新 menu 资源(document_item_menu.xml + 2 项)
- 1 个扩展接口(OnDocumentActionListener.onRequestCustomIcon)
- 5 个新方法在 DocumentAdapter(showRenameDialog + showIconSelectionDialog + createShortcutWithDefaultIcon + createShortcutWithCustomIcon + 新 onRequestCustomIcon stub)
- DocumentFragment onRequestCustomIcon 实现 + onActivityResult 加 REQUEST_CODE_PICK_ICON = 1002 分支
- 关键设计决策:沿用 LinksAdapter 模式而不是抽取共享 Adapter 基类(YAGNI > DRY)
- final review 发现 1 个待跟进项:createDocumentShortcutLegacy 缺兜底逻辑,小米/华为 ROM 可能图标异常
