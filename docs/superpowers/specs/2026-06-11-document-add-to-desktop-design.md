---
name: document-add-to-desktop-design
description: 文档列表项的"添加到桌面"功能——与链接的右键"添加到桌面"对齐：长按文档弹出菜单，点击后弹图标选择对话框（默认图标 / 相册选图），创建指向 DocumentViewerActivity 的桌面快捷方式
metadata:
  type: design
  created: 2026-06-11
  scope: DocumentAdapter + DocumentFragment + ShortcutUtil + document_item_menu.xml
  shortcut-id: document_<id>
  target-activity: DocumentViewerActivity
  reference: LinksAdapter "添加到桌面"（case 7, line 607+）
---

# 文档"添加到桌面"功能 - 设计方案

##1. 目标

让用户能把文档列表中的某个文档"添加到桌面"。点击桌面上生成的快捷方式后,直接进入 `DocumentViewerActivity` 阅读该文档。

行为与现有网络链接的"添加到桌面"对齐:
- 长按文档项 → 弹出菜单 → 选"添加到桌面"
- 弹出图标选择对话框(2 选项,无 favicon 等价物)
- 选默认图标 → 直接创建;选相册图 → 打开相册,选完再创建
- 重复添加同一文档,第二次覆盖

##2. 关键决策

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

##3. 架构

###3.1 改动文件总览

```
app/src/main/
├── java/person/notfresh/readingshare/
│   ├── util/ShortcutUtil.java          [+ createDocumentShortcut(...) + 2 private helpers]
│   ├── adapter/DocumentAdapter.java    [+ 菜单处理 + showIconSelectionDialog() + 2 个 createShortcutWith* 方法]
│   └── ui/document/DocumentFragment.java [+ onRequestCustomIcon 实现 + REQUEST_CODE_PICK_ICON + onActivityResult]
└── res/menu/document_item_menu.xml     [+ action_add_to_desktop 项]
```

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

**`OnDocumentActionListener` 接口** 新增回调:
```java
void onRequestCustomIcon(DocumentItem item);
```

**`showPopupMenu()`** 改动:
- 在 `R.id.action_share` 分支后加 `R.id.action_add_to_desktop` 分支
- 处理时先检查文件存在:`File f = new File(item.getFilePath()); if (!f.exists()) { Toast "文件不存在"; return; }`
- 然后调 `showIconSelectionDialog(view, item)`

**新增方法**(与 LinksAdapter 同名,形态完全镜像):
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

参照 `HomeFragment.onRequestCustomIcon` (HomeFragment.java:1481) 与 `onActivityResult` (line 1494) 的写法:

**新增常量**:
```java
private static final int REQUEST_CODE_PICK_ICON = 1001;
private DocumentItem pendingIconDocItem; // 临时保存,onActivityResult 时回填
```

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

**新增 `onActivityResult`**(完全镜像 `HomeFragment.onActivityResult` line 1494 的处理,只是用 `ImageUtil` 工具):
```java
@Override
public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_CODE_PICK_ICON && resultCode == Activity.RESULT_OK
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
       DocumentFragment.onActivityResult
         ↓ Uri → MediaStore → Bitmap → 缩放到 96×96
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

##5. 边界与陷阱

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

##6. 不在范围内(明确剔除)

- 不同 DocumentType 的专属图标资源
- 文档首页缩略图(PDF 用 PdfRenderer,MD/LaTeX/TXT 文字渲染)
- 批量"添加多个到桌面" / 拖拽到桌面
- 任何对 `LinksAdapter` / `WebShortcutActivity` 的重构
- 抽出共享 `ShortcutIconPicker` 工具类
- 重命名快捷方式(系统长按桌面图标已支持,不在 app 内做)
- 单条文档右键菜单新增"重命名"等其他功能(用户未提,不做)

##7. 集成点(integration checklist)

1. `ShortcutUtil.java` 在 `createSubjectShortcut` 之后追加 3 个方法
2. `DocumentAdapter.java` 增 listener 回调 + 3 个新方法 + 修改 `showPopupMenu` 1 个分支
3. `DocumentFragment.java` 增 1 个常量 + 1 个 field + 1 个回调方法 + 1 个 onActivityResult
4. `document_item_menu.xml` 增 1 个 item
5. 不需要修改 `AndroidManifest.xml`(`DocumentViewerActivity` 已有,非 singleTask,可直接被外部启动)
6. 不需要新增权限

##8. 测试方案(手动)

不引入单元测试框架。验证清单:

| # | 场景 | 期望 |
|---|---|---|
| 1 | 长按一个 PDF → 添加到桌面 → 选默认图标 | 系统弹确认 → 桌面出现应用图标 → 点 → 直接进入 viewer 阅读 |
| 2 | 长按一个 PDF → 添加到桌面 → 选相册图(选 1MB 内图片) | 系统弹确认 → 桌面图标变成所选图 → 点 → 正常打开 |
| 3 | 重复 #1 同文档 | 第二次确认后桌面只剩一个图标(覆盖) |
| 4 | 同一流程换成 Markdown / LaTeX / TXT | 桌面图标正常,点击进入 viewer,内容正确 |
| 5 | 手动让一个文档 filePath 失效(如 mv 文件)→ 长按 → 添加到桌面 | 弹 Toast "文件不存在",不进入图标选择对话框 |
| 6 | 文档被 DB 删除后,点击已有快捷方式 | viewer 显示"文件不存在"等错误(由 viewer 现有逻辑处理) |
| 7 | 长按 → 添加到桌面 → 选相册图时取消选图 | 无副作用,无 Toast(取决于 onActivityResult 行为) |
| 8 | 长按菜单的"分享"和"删除"功能 | 完全不受影响,正常工作 |
| 9 | 从桌面点击快捷方式 → 在 viewer 内退出 | 返回系统桌面或上一个 app(取决于 FLAG_ACTIVITY_NEW_TASK + CLEAR_TOP 组合) |

##9. 自检用 checklist

- [ ] `ShortcutUtil.createDocumentShortcut` 与 `createSubjectShortcut` 形态一致(签名、内部 modern/legacy 分支、降级广播)
- [ ] `DocumentAdapter.showPopupMenu` 中三个 case 都有处理且不互相影响
- [ ] `DocumentAdapter.OnDocumentActionListener` 接口已加 `onRequestCustomIcon`,所有实现类(本项目内仅 `DocumentFragment`)已同步
- [ ] `DocumentFragment.pendingIconDocItem` 在 onActivityResult 正常路径完成后置 null,异常路径也置 null(用 finally)
- [ ] `document_item_menu.xml` 新 item 的 icon 用 `@android:drawable/ic_menu_add`(系统内置,无需新 drawable)
- [ ] shortcutIntent 加了 `FLAG_ACTIVITY_NEW_TASK`(因为是从 home screen 启动,需要新 task 上下文)
- [ ] 不修改 `AndroidManifest.xml`
- [ ] 不修改 `DocumentViewerActivity` 任何代码
