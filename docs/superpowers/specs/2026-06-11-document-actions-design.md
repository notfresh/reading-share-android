---
name: document-actions-design
description: 文档列表项的两项新操作——"添加到桌面"（与链接流程对齐：长按 → 菜单 → 图标选择对话框 → ShortcutUtil → DocumentViewerActivity）和"重命名"（长按 → 菜单 → 标题编辑对话框 → DocumentDao.updateDocumentTitle）。两功能复用同一菜单与同一回调接口
metadata:
  type: design
  created: 2026-06-11
  scope: DocumentAdapter + DocumentFragment + ShortcutUtil + document_item_menu.xml
  shortcut-id: document_<id>
  target-activity: DocumentViewerActivity
  reference: LinksAdapter "添加到桌面"（case 7, line 607+） + "编辑标题"（case 1, line 613）
---

# 文档项两项新操作 - 设计方案

##1. 目标

给文档列表项新增两项操作,均在长按弹出的菜单里:

### 1.1 添加到桌面
让用户能把文档"添加到桌面",点击桌面上生成的快捷方式后,直接进入 `DocumentViewerActivity` 阅读该文档。行为与现有网络链接的"添加到桌面"对齐:
- 长按文档项 → 弹出菜单 → 选"添加到桌面"
- 弹出图标选择对话框(2 选项,无 favicon 等价物)
- 选默认图标 → 直接创建;选相册图 → 打开相册,选完再创建
- 重复添加同一文档,第二次覆盖

### 1.2 重命名
让用户能修改文档的标题,只改 DB 标题字段(不动磁盘文件)。行为与现有网络链接的"编辑标题"对齐:
- 长按文档项 → 弹出菜单 → 选"重命名"
- 弹出标题编辑对话框(预填当前标题,EditText)
- 校验非空 → 调 `DocumentDao.updateDocumentTitle(id, newTitle)` → 列表刷新
- 快捷方式标签不同步(已知限制,见 §2)

##2. 关键决策

### 2.1 添加到桌面

| 决策 | 选择 | 理由 |
|---|---|---|
| 入口位置 | `document_item_menu.xml` 新增项(放在 分享 之后,删除 之前) | 与现有菜单结构一致;不破坏破坏性操作放最后的原则 |
| 图标选择对话框选项 | 2 个:默认图标 / 相册选图 | 文档没有 favicon 等自动来源;保留"用户选择"的同时简化 |
| 点击快捷方式行为 | 直接启动 `DocumentViewerActivity` | 与 URL 流程的"直接打开网页"对齐;viewer 内部已经按 `document_id` 读 DB |
| 是否需要中间桥接 Activity | **不需要**(不像 `WebShortcutActivity`) | `WebViewActivity` 用 singleTask,需桥接;`DocumentViewerActivity` 默认 standard,直接 intent 即可 |
| 快捷方式 ID 格式 | `document_<id>` | 与 subject 快捷方式 `subject_<id>` 风格一致;同一文档重复添加会覆盖 |
| 自定义图标缩放 | 复用现有 `ImageUtil.uriToBitmap` + `ImageUtil.resizeToSquareForShortcut` (256×256 正方形) | `HomeFragment.onActivityResult` 已经在用同一对工具,完全复用;无需新代码;OOM 在工具内 try-catch |
| 公共能力放置 | 在 `ShortcutUtil` 增方法,**不**抽出 `ShortcutIconPicker` | 用户选择"对齐 LinksAdapter 代码形态"而非抽抽象;最小改动 |
| 多文档类型(PDF/MD/LaTeX/TXT) | 统一处理,不区分 | 快捷方式只传 id,viewer 内部按 type 分发;快捷方式层面无差别 |

### 2.2 重命名

| 决策 | 选择 | 理由 |
|---|---|---|
| 重命名入口 | 文档列表长按菜单新增"重命名"项(放在 分享 之后,添加到桌面 之前) | 与链接"编辑标题"在最上对齐;同属"出文档"类操作 |
| 重命名范围 | **只改 DB 标题字段,不动磁盘文件名** | 文件在磁盘上是 UUID 路径(`filesDir/documents/<type>/<uuid>.<ext>`),用户从不直接接触;改文件名无 UX 收益,且有 mv/权限/并发风险 |
| DAO | 复用现有 `DocumentDao.updateDocumentTitle(id, newTitle)` | DAO 已有该方法(在 `DocumentDao.java:198`),无需新代码 |
| 校验 | 标题不能为空;空字符串时阻止保存(无 placeholder) | 链接流程"编辑标题"同样只校验 `!isEmpty()`;不强制占位符 |
| 标题长度 | 不做硬限制(显示层 TextView 自带截断) | 链接流程也不限;实际中超过 100 字符几乎不会出现 |
| 标题去重 | 不去重 | 链接流程不去重;用户可能用同名表达分类 |
| 快捷方式标签同步 | **不同步**(已知限制) | PinShortcut 在 Android 上是静态快捷方式,改 label 必须重新走 `requestPinShortcut` + 系统确认弹窗,会打扰用户;用户接受后可手动删除+重加 |
| 实施位置 | `showRenameDialog()` 在 DocumentAdapter 内 + DAO 调用通过**已有的** `listener.onUpdateDocument(item, newTitle)` 委托给 DocumentFragment | Fragment 端的 `onUpdateDocument` (line 268) 已存在并实现 DAO 写入 + `loadDocuments()` 刷新 |
| 列表刷新 | `DocumentFragment.loadDocuments()` 整体重读 DB | 不做精细的 `notifyItemChanged(position)`,与 pin 状态变更共用同一刷新路径 |

##3. 架构

###3.1 改动文件总览

```
app/src/main/
├── java/person/notfresh/readingshare/
│   ├── util/ShortcutUtil.java          [+ createDocumentShortcut(...) + 2 private helpers]
│   ├── adapter/DocumentAdapter.java    [+ showIconSelectionDialog + showRenameDialog + 2 个 createShortcutWith* + 1 个 listener 回调 + 菜单分支]
│   └── ui/document/DocumentFragment.java [+ onRequestCustomIcon + REQUEST_CODE_PICK_ICON 常量 + pendingIconDocItem 字段 + onActivityResult 扩展]
└── res/menu/document_item_menu.xml     [+ action_rename + action_add_to_desktop 两项]
```

> 注:重命名功能复用 `OnDocumentActionListener.onUpdateDocument` 已有回调,DocumentFragment 中 `onUpdateDocument` (line 268) 已实现完整 DAO + 刷新逻辑,故 DocumentFragment 文件**仅"添加到桌面"需要改动**。

###3.2 ShortcutUtil 新增 API

参照 `createSubjectShortcut(Context, String title, long subjectId, Bitmap iconBitmap)` (ShortcutUtil.java:247) 的写法,新增:

```java
// 现代方式 + 降级,签名完全镜像 subject 快捷方式
public static boolean createDocumentShortcut(
    Context context, String title, long documentId, Bitmap iconBitmap) { ... }

// 便捷重载(用默认图标)
public static boolean createDocumentShortcut(
    Context context, String title, long documentId) {
    return createDocumentShortcut(context, title, documentId, null);
}

private static boolean tryCreateDocumentShortcutModern(
    Context context, String title, long documentId, Bitmap iconBitmap) { ... }

private static boolean createDocumentShortcutLegacy(
    Context context, String title, long documentId, Bitmap iconBitmap) { ... }
```

`tryCreateDocumentShortcutModern` 关键逻辑:
- shortcutId = `"document_" + documentId`
- `Intent shortcutIntent = new Intent(context, DocumentViewerActivity.class);`
- `shortcutIntent.setAction(Intent.ACTION_VIEW);`  // 与 subject 保持一致
- `shortcutIntent.putExtra("document_id", documentId);`
- `shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);`
- IconCompat:iconBitmap 非空时 `IconCompat.createWithBitmap(iconBitmap)`,否则 `IconCompat.createWithResource(context, R.mipmap.ic_launcher)`
- `ShortcutInfoCompat.Builder(context, shortcutId).setShortLabel(title).setLongLabel(title).setIcon(icon).setIntent(shortcutIntent).build();`
- 走 `ShortcutManagerCompat.requestPinShortcut(...)` + 降级广播

###3.3 DocumentAdapter 改动

**`OnDocumentActionListener` 接口** 已存在 `onUpdateDocument(DocumentItem oldDocument, String newTitle)` (DocumentAdapter.java:47),且 `DocumentFragment.onUpdateDocument` (DocumentFragment.java:268) 已实现 DAO 写入 + `loadDocuments()` 刷新列表。**重命名直接复用此回调,无需新增接口方法,Fragment 端零改动。**

**接口新增 1 个回调** (仅"添加到桌面"用):
```java
void onRequestCustomIcon(DocumentItem item);
```

**`showPopupMenu()`** 改动:
- 在 `R.id.action_share` 分支后加 `R.id.action_rename` 分支
- 在 `R.id.action_rename` 分支后加 `R.id.action_add_to_desktop` 分支
- 重命名处理:直接调 `showRenameDialog(view, item)`
- 添加到桌面处理时先检查文件存在:`File f = new File(item.getFilePath()); if (!f.exists()) { Toast "文件不存在"; return; }`
- 然后调 `showIconSelectionDialog(view, item)`

**新增"重命名"方法**(与 LinksAdapter `showEditTitleDialog` 形态完全镜像):
```java
private void showRenameDialog(View view, DocumentItem item) {
    EditText input = new EditText(view.getContext());
    input.setText(item.getTitle());
    input.setSelection(item.getTitle() != null ? item.getTitle().length() : 0);

    new AlertDialog.Builder(view.getContext())
        .setTitle("重命名")
        .setView(input)
        .setPositiveButton("确定", (dialog, which) -> {
            String newTitle = input.getText().toString();
            if (!newTitle.isEmpty() && listener != null) {
                listener.onUpdateDocument(item, newTitle);  // 复用已有回调
            } else {
                Toast.makeText(view.getContext(), "标题不能为空", Toast.LENGTH_SHORT).show();
            }
        })
        .setNegativeButton("取消", null)
        .show();
}
```

**新增"添加到桌面"方法**(与 LinksAdapter 同名,形态完全镜像):
```java
private void showIconSelectionDialog(View view, DocumentItem item) {
    String[] options = {"使用默认图标", "从相册选择"};
    new AlertDialog.Builder(view.getContext())
        .setTitle("选择快捷方式图标")
        .setItems(options, (dialog, which) -> {
            if (which == 0) createShortcutWithDefaultIcon(view, item);
            else if (which == 1) {
                if (listener != null) listener.onRequestCustomIcon(item);
                else Toast.makeText(view.getContext(), "无法打开相册", Toast.LENGTH_SHORT).show();
            }
        })
        .setNegativeButton("取消", null)
        .show();
}

private void createShortcutWithDefaultIcon(View view, DocumentItem item) {
    String title = item.getTitle() == null || item.getTitle().isEmpty() ? "<无标题>" : item.getTitle();
    boolean success = ShortcutUtil.createDocumentShortcut(view.getContext(), title, item.getId());
    Toast.makeText(view.getContext(), success ? "已添加快捷方式" : "创建快捷方式失败", Toast.LENGTH_SHORT).show();
}

// 公开,供 DocumentFragment 在 onActivityResult 中回调
public void createShortcutWithCustomIcon(Context context, DocumentItem item, Bitmap customIcon) {
    String title = item.getTitle() == null || item.getTitle().isEmpty() ? "<无标题>" : item.getTitle();
    boolean success = ShortcutUtil.createDocumentShortcut(context, title, item.getId(), customIcon);
    Toast.makeText(context, success ? "已添加快捷方式" : "创建快捷方式失败", Toast.LENGTH_SHORT).show();
}
```

###3.4 DocumentFragment 改动

**重命名功能**:零改动。`onUpdateDocument` 已存在 (DocumentFragment.java:268),内部已调用 `documentDao.updateDocumentTitle()` + `loadDocuments()`。

**添加到桌面功能**:参照 `HomeFragment.onRequestCustomIcon` (HomeFragment.java:1481) 与 `onActivityResult` (line 1494) 的写法:

**新增常量**:
```java
private static final int REQUEST_CODE_PICK_ICON = 1001;
private DocumentItem pendingIconDocItem; // 临时保存,onActivityResult 时回填
```

> 注:DocumentFragment 已有 `onActivityResult` (line 135),需扩展加上 `REQUEST_CODE_PICK_ICON` 分支(当前只处理 `REQUEST_CODE_PICK_FILE`)。**不要重写整个方法,在原 if 后面追加一个 else if 分支。**

**实现 `onRequestCustomIcon(DocumentItem item)`**:
```java
@Override
public void onRequestCustomIcon(DocumentItem item) {
    pendingIconDocItem = item;
    Intent intent = new Intent(Intent.ACTION_PICK);
    intent.setType("image/*");
    startActivityForResult(intent, REQUEST_CODE_PICK_ICON);
}
```

**扩展 `onActivityResult`**(在已有 `REQUEST_CODE_PICK_FILE` 分支后追加):
```java
@Override
public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == Activity.RESULT_OK) {
        // ... 原有 import 逻辑,保持不变 ...
    }
    else if (requestCode == REQUEST_CODE_PICK_ICON && resultCode == Activity.RESULT_OK
            && data != null && pendingIconDocItem != null) {
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            Bitmap src = ImageUtil.uriToBitmap(requireContext(), uri);
            if (src != null) {
                Bitmap square = ImageUtil.resizeToSquareForShortcut(src);
                if (square != src) src.recycle();
                adapter.createShortcutWithCustomIcon(requireContext(), pendingIconDocItem, square);
            } else {
                Toast.makeText(requireContext(), "无法读取图片", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "处理图片时出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            pendingIconDocItem = null;
        }
    }
}
```

###3.5 document_item_menu.xml

```xml
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/action_share"
        android:title="分享"
        android:icon="@android:drawable/ic_menu_share" />
    <item
        android:id="@+id/action_rename"
        android:title="重命名"
        android:icon="@android:drawable/ic_menu_edit" />   <!-- 新增 -->
    <item
        android:id="@+id/action_add_to_desktop"
        android:title="添加到桌面"
        android:icon="@android:drawable/ic_menu_add" />   <!-- 新增 -->
    <item
        android:id="@+id/action_delete"
        android:title="删除"
        android:icon="@android:drawable/ic_menu_delete" />
</menu>
```

##4. 数据流

###4.1 添加到桌面

```
[长按文档项]
   ↓
DocumentAdapter.showPopupMenu()
   ↓ 用户点 action_add_to_desktop
DocumentAdapter: 文件存在性检查(file.exists())
   ↓ 存在
DocumentAdapter.showIconSelectionDialog() ── 2 选项
   ├─ [0] 默认图标
   │     ↓
   │  DocumentAdapter.createShortcutWithDefaultIcon(view, item)
   │     ↓ ShortcutUtil.createDocumentShortcut(ctx, title, id, null)
   │     ↓ 系统弹确认对话框 → 桌面创建
   │
   └─ [1] 相册选图
         ↓
       listener.onRequestCustomIcon(item)
         ↓ DocumentFragment 启动 ACTION_PICK
       [用户选图]
         ↓
       DocumentFragment.onActivityResult (新增 REQUEST_CODE_PICK_ICON 分支)
         ↓ Uri → ImageUtil.uriToBitmap → resizeToSquareForShortcut (256×256)
         adapter.createShortcutWithCustomIcon(ctx, item, bitmap)
         ↓ ShortcutUtil.createDocumentShortcut(ctx, title, id, bitmap)
         ↓ 系统弹确认对话框 → 桌面创建

[用户点桌面图标]
   ↓
Android 系统启动 Intent(action=VIEW, extras={"document_id": <id>})
   ↓
DocumentViewerActivity.onCreate
   ↓ 读 document_id → DocumentDao 查询 → 渲染文档
```

###4.2 重命名

```
[长按文档项]
   ↓
DocumentAdapter.showPopupMenu()
   ↓ 用户点 action_rename
DocumentAdapter.showRenameDialog(view, item)
   ↓ AlertDialog("重命名", EditText 预填当前标题,光标在末尾)
[用户编辑,点确定]
   ↓ 校验非空
   ↓ listener.onUpdateDocument(item, newTitle) ← 复用已有回调
   ↓ DocumentFragment.onUpdateDocument (DocumentFragment.java:268)
   ↓ documentDao.updateDocumentTitle(id, newTitle)
   ↓ loadDocuments() → 重读 DB → 列表刷新
```

##5. 边界与陷阱

### 5.1 添加到桌面

| 坑 | 后果 | 规避 |
|---|---|---|
| 文档 filePath 已不存在(用户在文件管理器删了文件,DB 还在) | 创建快捷方式无意义,点了会黑屏 | `showPopupMenu` 处理前先 `file.exists()` 检查,提示"文件不存在" |
| 同一文档重复添加 | 用户困惑(桌面有两个一样的图标) | shortcutId 用 `document_<id>` 确定性生成,系统按 id 覆盖 |
| 标题为 null/空 | 桌面显示"添加到桌面"默认名,难以识别 | `createShortcutWith*` 内做空值防御,fallback `<无标题>` |
| 用户从相册选了 50MB 大图 | 缩放时 OOM | `ImageUtil.uriToBitmap` 内部已用 `BitmapFactory.Options.inSampleSize` 第一次解码(需确认);resize 失败 try-catch 兜底;**实施时检查 ImageUtil 内部是否已有降采样逻辑** |
| Bitmap 已 recycle 后再被用 | 闪退 | `createScaledBitmap` 返回新对象时,仅在新对象 != 旧对象时 recycle 旧的;别在 catch 里碰已 recycle 的引用 |
| 系统不支持 PinShortcut(老设备/某些定制 ROM) | requestPinShortcut 失败 | `ShortcutUtil` 现有降级路径(INSTALL_SHORTCUT 广播)覆盖;无需新代码 |
| 文档被 DB 删除后,快捷方式仍存在 | 用户点快捷方式跳到 viewer 后报"文件不存在" | 属于 viewer 的错误处理范畴,本 spec 不改 viewer;`DocumentViewerActivity` 已有对应逻辑 |
| 文档在桌面"打开"中,被外部编辑/移动 | viewer 读到不一致数据 | viewer 内部已有 reload 路径;快捷方式只是入口,不改数据流 |
| ACTION_PICK 在某些设备上没装"相册"应用 | Intent 解析不到 | HomeFragment 同款代码无额外防御;`Toast "无法打开相册"` 在 listener 为 null 时已经兜底,但 ACTION_PICK 启动失败是另一个路径——本 spec 不处理(链接流程也没处理,保持一致) |
| onMenuItemClick 时 adapter 已被 detach | 回调 NPE | DocumentAdapter 的 listener 字段在 onDetachedFromRecyclerView 中已处理;showPopupMenu 用 view.getContext() 即可,不依赖 adapter 实例 |
| DocumentFragment.onActivityResult 现有 REQUEST_CODE_PICK_FILE 分支被覆盖 | 文档导入功能 broken | 实施时**追加** else if 分支,**不要重写整个方法**;改动后跑一次"导入文档"流程验证 |

### 5.2 重命名

| 坑 | 后果 | 规避 |
|---|---|---|
| 标题为空(用户删了所有字符) | 列表项显示空标题;DB 写入空字符串 | AlertDialog 确定按钮回调内校验 `!newTitle.isEmpty()`,为空时 Toast 提示"标题不能为空",不调 DAO |
| 标题仅含空白字符(空格、换行) | 显示成空白 | 实施时**可选**做 `newTitle.trim().isEmpty()` 校验;若决定不做,与链接流程保持一致即可(链接流程也只校验 `!isEmpty()`) |
| 重命名后快捷方式标签不同步 | 桌面图标名仍为旧名 | **已知限制**,在 §2.2 已说明;用户接受后可手动删除+重加 |
| 文档正在 viewer 内打开时,后台重命名 | viewer 持有的 DocumentItem 引用仍是旧标题,但下次重新进入 viewer 会读到新标题 | viewer 内部不监听标题变更,只在 onCreate 时读;不引发崩溃;`loadDocuments()` 重读 DB 后列表项标题刷新 |
| 同一文档并发重命名(多窗口/快速点击) | DB 写覆盖,后者生效 | 实际不会发生(单窗口应用);不防御 |
| 极长标题(1000+ 字符) | 列表项显示截断或挤压 | 与链接流程一致,不做硬限制;TextView 自带省略号 |

##6. 不在范围内(明确剔除)

- 不同 DocumentType 的专属图标资源
- 文档首页缩略图(PDF 用 PdfRenderer,MD/LaTeX/TXT 文字渲染)
- 批量"添加多个到桌面" / 拖拽到桌面
- 任何对 `LinksAdapter` / `WebShortcutActivity` 的重构
- 抽出共享 `ShortcutIconPicker` 工具类
- 改名磁盘文件(只改 DB 标题)
- 同步更新已有桌面快捷方式标签(已知限制,见 §2.2)
- 在 `DocumentViewerActivity` 内也加"重命名"入口(用户只需在列表改,避免两处入口维护成本)
- 批量重命名 / 标题去重 / 标题历史记录
- 单条文档右键菜单新增"置顶"等其他功能(用户未提,不做;置顶功能 DocumentItem 已有字段,但属于另一独立 feature)

##7. 集成点(integration checklist)

1. `ShortcutUtil.java` 在 `createSubjectShortcut` 之后追加 3 个方法
2. `DocumentAdapter.java` 增 1 个 listener 回调(`onRequestCustomIcon`) + 4 个新方法(`showRenameDialog`、`showIconSelectionDialog`、`createShortcutWithDefaultIcon`、`createShortcutWithCustomIcon`) + 修改 `showPopupMenu` 加 2 个分支(`action_rename` 和 `action_add_to_desktop`)
3. `DocumentFragment.java` 增 1 个常量 + 1 个 field + 1 个回调方法(`onRequestCustomIcon`) + 在已有 `onActivityResult` 内**追加** REQUEST_CODE_PICK_ICON 分支
4. `DocumentFragment.java` 已有 `onUpdateDocument` (line 268),重命名功能直接复用,**Fragment 端零新代码**
5. `document_item_menu.xml` 增 2 个 item(`action_rename` 和 `action_add_to_desktop`)
6. 不需要修改 `AndroidManifest.xml`(`DocumentViewerActivity` 已有,非 singleTask,可直接被外部启动)
7. 不需要新增权限
8. 不需要修改 `DocumentViewerActivity`
9. 不需要修改 `DocumentDao`(`updateDocumentTitle` 已存在)

##8. 测试方案(手动)

不引入单元测试框架。验证清单:

### 8.1 添加到桌面

| # | 场景 | 期望 |
|---|---|---|
| 1 | 长按一个 PDF → 添加到桌面 → 选默认图标 | 系统弹确认 → 桌面出现应用图标 → 点 → 直接进入 viewer 阅读 |
| 2 | 长按一个 PDF → 添加到桌面 → 选相册图(选 1MB 内图片) | 系统弹确认 → 桌面图标变成所选图 → 点 → 正常打开 |
| 3 | 重复 #1 同文档 | 第二次确认后桌面只剩一个图标(覆盖) |
| 4 | 同一流程换成 Markdown / LaTeX / TXT | 桌面图标正常,点击进入 viewer,内容正确 |
| 5 | 手动让一个文档 filePath 失效(如 mv 文件)→ 长按 → 添加到桌面 | 弹 Toast "文件不存在",不进入图标选择对话框 |
| 6 | 文档被 DB 删除后,点击已有快捷方式 | viewer 显示"文件不存在"等错误(由 viewer 现有逻辑处理) |
| 7 | 长按 → 添加到桌面 → 选相册图时取消选图 | 无副作用,无 Toast(取决于 onActivityResult 行为) |
| 8 | 长按菜单的"分享"、"重命名"和"删除"功能 | 完全不受影响,正常工作 |
| 9 | 从桌面点击快捷方式 → 在 viewer 内退出 | 返回系统桌面或上一个 app(取决于 FLAG_ACTIVITY_NEW_TASK + CLEAR_TOP 组合) |
| 10 | 文档列表的"导入文档"功能 | 完全不受影响(确认 onActivityResult 分支未破坏原有逻辑) |

### 8.2 重命名

| # | 场景 | 期望 |
|---|---|---|
| 11 | 长按一个文档 → 重命名 → 改标题 → 确定 | 列表项标题立即更新为新标题;返回桌面查看快捷方式(若已添加)名称仍是旧名(已知限制) |
| 12 | 长按 → 重命名 → 清空标题 → 确定 | 弹 Toast "标题不能为空",列表不变 |
| 13 | 长按 → 重命名 → 只输入空白字符 → 确定 | 行为等同 #12(若做了 trim 校验)/ 或写入了空白字符串(若没做);与链接流程一致则不必做 trim |
| 14 | 长按 → 重命名 → 取消 | 列表无变化,无 Toast |
| 15 | 重命名后,关闭 app 再打开 | DB 持久化,新标题仍在 |
| 16 | 重命名后,点击该文档的桌面快捷方式(若已添加) | 进入 viewer 后 toolbar 显示新标题(因为 viewer 按 id 重读 DB) |
| 17 | 同一流程换成 Markdown / LaTeX / TXT | 重命名均生效 |
| 18 | 重命名后,文档排序/分组是否变化 | 若按时间分组:新标题不影响 timestamp,组不变;列表内位置由 DAO 查询顺序决定 |

##9. 自检用 checklist

### 9.1 添加到桌面

- [ ] `ShortcutUtil.createDocumentShortcut` 与 `createSubjectShortcut` 形态一致(签名、内部 modern/legacy 分支、降级广播)
- [ ] `DocumentAdapter.OnDocumentActionListener` 接口已加 `onRequestCustomIcon`,所有实现类(本项目内仅 `DocumentFragment`)已同步
- [ ] `DocumentFragment.pendingIconDocItem` 在 onActivityResult 正常路径完成后置 null,异常路径也置 null(用 finally)
- [ ] `document_item_menu.xml` 新 item 的 icon 用 `@android:drawable/ic_menu_add`(系统内置,无需新 drawable)
- [ ] shortcutIntent 加了 `FLAG_ACTIVITY_NEW_TASK`(因为是从 home screen 启动,需要新 task 上下文)
- [ ] `DocumentFragment.onActivityResult` 扩展时**未覆盖**原有 `REQUEST_CODE_PICK_FILE` 分支,导入文档功能仍正常

### 9.2 重命名

- [ ] `showRenameDialog` 中 EditText 预填当前标题,光标置于末尾(`setSelection(length)`)
- [ ] 空标题校验在 AlertDialog 确定回调内,`!newTitle.isEmpty()` 时才调 `listener.onUpdateDocument`
- [ ] 重命名调的是**已有的** `listener.onUpdateDocument(item, newTitle)`,**未新增** listener 回调方法
- [ ] `DocumentFragment.onUpdateDocument` (line 268) 未被修改;重命名复用现有 DAO + loadDocuments 路径
- [ ] `document_item_menu.xml` 新增的 `action_rename` icon 用 `@android:drawable/ic_menu_edit`

### 9.3 通用

- [ ] 不修改 `AndroidManifest.xml`
- [ ] 不修改 `DocumentViewerActivity` 任何代码
- [ ] 不修改 `DocumentDao`(`updateDocumentTitle` 已存在)
- [ ] `showPopupMenu` 中所有 case(分享/重命名/添加到桌面/删除)都有处理且不互相影响
- [ ] 菜单顺序为: 分享 → 重命名 → 添加到桌面 → 删除(破坏性放最后)
