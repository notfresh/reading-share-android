# 文档项操作(添加到桌面+重命名)实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 文档列表项的"重命名"和"添加到桌面"两项长按菜单操作(共 7 个原子任务)。

**Architecture:** 与 LinksAdapter "编辑标题"/"添加到桌面"流程对齐。ShortcutUtil 新增 createDocumentShortcut(镜像 createSubjectShortcut);DocumentAdapter 在 showPopupMenu 加 2 个分支;DocumentFragment 实现 ACTION_PICK 图片选取并在 onActivityResult 中复用 ImageUtil 工具。重命名直接复用已有 onUpdateDocument 回调(Fragment 端 DAO 写入 + loadDocuments 已写好)。

**Tech Stack:** Android PopupMenu, AlertDialog, ShortcutManagerCompat, ImageUtil(已有), EditText, ACTION_PICK

**参考 spec:** `docs/superpowers/specs/2026-06-11-document-actions-design.md`

---

## 文件变更清单

| 文件 | 变更内容 |
|------|----------|
| `app/src/main/java/person/notfresh/readingshare/util/ShortcutUtil.java` | **修改** —— 追加 2 public + 2 private 方法(`createDocumentShortcut` 系列) |
| `app/src/main/res/menu/document_item_menu.xml` | **修改** —— 增 2 个 item(`action_rename` + `action_add_to_desktop`) |
| `app/src/main/java/person/notfresh/readingshare/adapter/DocumentAdapter.java` | **修改** —— 增 1 listener 方法、2 个 dialog 方法、2 个快捷方式辅助方法、2 个 menu 分支、加 imports(Bitmap/EditText/AlertDialog/ShortcutUtil) |
| `app/src/main/java/person/notfresh/readingshare/ui/document/DocumentFragment.java` | **修改** —— 增 1 常量、1 field、实现 onRequestCustomIcon、扩展 onActivityResult,加 imports(Bitmap/ImageUtil) |

---

### Task 1: ShortcutUtil 新增 createDocumentShortcut

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/util/ShortcutUtil.java:13-15`(imports)+ 末尾追加方法

- [ ] **Step 1: 增加 DocumentViewerActivity 的 import**

在 `ShortcutUtil.java` 的 import 段(`import person.notfresh.readingshare.WebShortcutActivity;` 之后)插入一行:

```java
import person.notfresh.readingshare.ui.document.DocumentViewerActivity;
```

- [ ] **Step 2: 在文件末尾追加 4 个新方法**

读 `ShortcutUtil.java` 末尾,确认 `createSubjectShortcut` 的便捷重载是最后方法。在其后(类体内,最后一个 `}` 之前)插入:

```java
/**
 * 创建文档桌面快捷方式
 * @param context 上下文
 * @param title 快捷方式名称(文档标题)
 * @param documentId 文档 ID,点击快捷方式后传给 DocumentViewerActivity
 * @param iconBitmap 可选,自定义图标 Bitmap,如果为 null 则使用默认图标
 * @return 是否创建成功
 */
public static boolean createDocumentShortcut(Context context, String title, long documentId, Bitmap iconBitmap) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                boolean modernSuccess = tryCreateDocumentShortcutModern(context, title, documentId, iconBitmap);
                if (modernSuccess) {
                    return true;
                }
            }
        }
        return createDocumentShortcutLegacy(context, title, documentId, iconBitmap);
    } catch (Exception e) {
        android.util.Log.e("ShortcutUtil", "Failed to create document shortcut", e);
        Toast.makeText(context, "创建快捷方式失败:" + e.getMessage(), Toast.LENGTH_SHORT).show();
        return false;
    }
}

/**
 * 创建文档桌面快捷方式(兼容旧接口,使用默认图标)
 */
public static boolean createDocumentShortcut(Context context, String title, long documentId) {
    return createDocumentShortcut(context, title, documentId, null);
}

private static boolean tryCreateDocumentShortcutModern(Context context, String title, long documentId, Bitmap iconBitmap) {
    try {
        String shortcutId = "document_" + documentId;
        Intent shortcutIntent = new Intent(context, DocumentViewerActivity.class);
        shortcutIntent.setAction(Intent.ACTION_VIEW);
        shortcutIntent.putExtra("document_id", documentId);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        IconCompat icon;
        if (iconBitmap != null) {
            icon = IconCompat.createWithBitmap(iconBitmap);
        } else {
            icon = IconCompat.createWithResource(context, R.mipmap.ic_launcher);
        }

        ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(context, shortcutId)
                .setShortLabel(title)
                .setLongLabel(title)
                .setIcon(icon)
                .setIntent(shortcutIntent)
                .build();

        return ShortcutManagerCompat.requestPinShortcut(context, info, null);
    } catch (Exception e) {
        android.util.Log.e("ShortcutUtil", "Modern document shortcut failed", e);
        return false;
    }
}

private static boolean createDocumentShortcutLegacy(Context context, String title, long documentId, Bitmap iconBitmap) {
    Intent shortcutIntent = new Intent(context, DocumentViewerActivity.class);
    shortcutIntent.setAction(Intent.ACTION_VIEW);
    shortcutIntent.putExtra("document_id", documentId);
    shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

    Intent intent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
    intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
    intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
    if (iconBitmap != null) {
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, iconBitmap);
    } else {
        Intent.ShortcutIconResource iconResource = Intent.ShortcutIconResource.fromContext(context, R.mipmap.ic_launcher);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, iconResource);
    }
    context.sendBroadcast(intent);
    return true;
}
```

- [ ] **Step 3: 编译验证**

```bash
cd "c:/projects/duxiang-pack/duxiang-android" && ./gradlew :app:compileDebugJavaWithJavac
```

预期: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/util/ShortcutUtil.java
git commit -m "feat(shortcut): add createDocumentShortcut for document desktop shortcut"
```

---

### Task 2: document_item_menu.xml 增 2 个 item

**Files:**
- Modify: `app/src/main/res/menu/document_item_menu.xml`

- [ ] **Step 1: 在 `action_share` 之后、`action_delete` 之前插入 2 个 item**

完整文件内容(覆盖写入):

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/action_share"
        android:title="分享"
        android:icon="@android:drawable/ic_menu_share" />
    <item
        android:id="@+id/action_rename"
        android:title="重命名"
        android:icon="@android:drawable/ic_menu_edit" />
    <item
        android:id="@+id/action_add_to_desktop"
        android:title="添加到桌面"
        android:icon="@android:drawable/ic_menu_add" />
    <item
        android:id="@+id/action_delete"
        android:title="删除"
        android:icon="@android:drawable/ic_menu_delete" />
</menu>

```

> 注:XML 末尾的空行保留(与项目里其他 menu XML 一致)。

- [ ] **Step 2: 编译验证**

```bash
cd "c:/projects/duxiang-pack/duxiang-android" && ./gradlew :app:compileDebugJavaWithJavac
```

预期: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/res/menu/document_item_menu.xml
git commit -m "feat(menu): add rename and add-to-desktop items to document popup menu"
```

---

### Task 3: DocumentAdapter - 重命名 dialog + 菜单分支

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/adapter/DocumentAdapter.java`(imports + showPopupMenu + 新增 showRenameDialog)

- [ ] **Step 1: 增加 imports**

在 `import android.content.Intent;`(第 4 行)之后插入:

```java
import android.graphics.Bitmap;
```

在 `import android.widget.PopupMenu;`(第 12 行)之后插入:

```java
import android.widget.EditText;
```

在 `import androidx.core.content.FileProvider;`(第 16 行)之前插入:

```java
import androidx.appcompat.app.AlertDialog;
```

在 `import person.notfresh.readingshare.model.DocumentItem;`(第 32 行)之后插入:

```java
import person.notfresh.readingshare.util.ShortcutUtil;
```

- [ ] **Step 2: 在 showPopupMenu 加 action_rename 分支**

定位到 `showPopupMenu` 方法(约第 269 行)。在 `if (id == R.id.action_share) {` 分支后,`else if (id == R.id.action_delete) {` 分支前,插入:

```java
            } else if (id == R.id.action_rename) {
                showRenameDialog(view, item);
                return true;
```

> 完整新 showPopupMenu 方法体应变成:
>
> ```java
> popupMenu.setOnMenuItemClickListener(menuItem -> {
>     int id = menuItem.getItemId();
>     if (id == R.id.action_share) {
>         shareDocument(item);
>         return true;
>     } else if (id == R.id.action_rename) {
>         showRenameDialog(view, item);
>         return true;
>     } else if (id == R.id.action_delete) {
>         if (listener != null) {
>             listener.onDeleteDocument(item);
>         }
>         return true;
>     }
>     return false;
> });
> ```

- [ ] **Step 3: 新增 showRenameDialog 方法**

在 `showPopupMenu` 方法之后(`private void showPopupMenu(View view, DocumentItem item) { ... }` 的 `}` 之后,`/** 分享文档文件 */` 注释之前)插入:

```java
    /**
     * 显示重命名对话框
     * 复用 OnDocumentActionListener.onUpdateDocument 已有回调(由 DocumentFragment.onUpdateDocument 实现 DAO 写入 + loadDocuments)
     */
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
                        listener.onUpdateDocument(item, newTitle);
                    } else {
                        Toast.makeText(view.getContext(), "标题不能为空", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
```

- [ ] **Step 4: 编译验证**

```bash
cd "c:/projects/duxiang-pack/duxiang-android" && ./gradlew :app:compileDebugJavaWithJavac
```

预期: `BUILD SUCCESSFUL`。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/adapter/DocumentAdapter.java
git commit -m "feat(document-adapter): add rename dialog and menu wiring"
```

---

### Task 4: DocumentAdapter - "添加到桌面" dialog 与辅助方法 + 接口扩展 + DocumentFragment stub

> 本任务在 DocumentAdapter 中加 2 个 dialog 方法 + 2 个快捷方式辅助方法 + 1 个 menu 分支,并扩展 `OnDocumentActionListener` 接口新增 `onRequestCustomIcon`。由于接口变化,DocumentFragment 必须同步实现该方法(此处先放 stub:只 Toast 提示,真正启动 ACTION_PICK 在 Task 5)。

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/adapter/DocumentAdapter.java`(interface + showPopupMenu + 3 个新方法)
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/document/DocumentFragment.java`(加 1 个 stub 方法,放在 `onPinStatusChanged` 之后)

- [ ] **Step 1: DocumentAdapter - 在 OnDocumentActionListener 接口加 onRequestCustomIcon**

定位到 `public interface OnDocumentActionListener { ... }` 段。在 `void onPinStatusChanged();` 后插入:

```java
        void onRequestCustomIcon(DocumentItem item);
```

完整接口应变成:

```java
    public interface OnDocumentActionListener {
        void onDeleteDocument(DocumentItem document);
        void onUpdateDocument(DocumentItem oldDocument, String newTitle);
        void onPinStatusChanged();
        void onRequestCustomIcon(DocumentItem item);
    }
```

- [ ] **Step 2: DocumentAdapter - 在 showPopupMenu 加 action_add_to_desktop 分支**

定位到 `showPopupMenu` 方法。修改为:

```java
    private void showPopupMenu(View view, DocumentItem item) {
        PopupMenu popupMenu = new PopupMenu(context, view);
        android.view.MenuInflater inflater = popupMenu.getMenuInflater();
        inflater.inflate(R.menu.document_item_menu, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.action_share) {
                shareDocument(item);
                return true;
            } else if (id == R.id.action_rename) {
                showRenameDialog(view, item);
                return true;
            } else if (id == R.id.action_add_to_desktop) {
                File f = new File(item.getFilePath());
                if (!f.exists()) {
                    Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show();
                    return true;
                }
                showIconSelectionDialog(view, item);
                return true;
            } else if (id == R.id.action_delete) {
                if (listener != null) {
                    listener.onDeleteDocument(item);
                }
                return true;
            }
            return false;
        });

        popupMenu.show();
    }
```

- [ ] **Step 3: DocumentAdapter - 在 showRenameDialog 之后插入 3 个新方法**

```java
    /**
     * 显示快捷方式图标选择对话框(2 选项:默认图标 / 相册选图)
     */
    private void showIconSelectionDialog(View view, DocumentItem item) {
        String[] options = {"使用默认图标", "从相册选择"};

        new AlertDialog.Builder(view.getContext())
                .setTitle("选择快捷方式图标")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        createShortcutWithDefaultIcon(view, item);
                    } else if (which == 1) {
                        if (listener != null) {
                            listener.onRequestCustomIcon(item);
                        } else {
                            Toast.makeText(view.getContext(), "无法打开相册", Toast.LENGTH_SHORT).show();
                        }
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

    /**
     * 使用自定义图标创建快捷方式(由 DocumentFragment.onActivityResult 回调)
     */
    public void createShortcutWithCustomIcon(Context context, DocumentItem item, Bitmap customIcon) {
        String title = item.getTitle() == null || item.getTitle().isEmpty() ? "<无标题>" : item.getTitle();
        boolean success = ShortcutUtil.createDocumentShortcut(context, title, item.getId(), customIcon);
        Toast.makeText(context, success ? "已添加快捷方式" : "创建快捷方式失败", Toast.LENGTH_SHORT).show();
    }
```

- [ ] **Step 4: DocumentFragment - 加 onRequestCustomIcon stub**

定位到 `DocumentFragment.java` 的 `onPinStatusChanged` 方法(约第 273-276 行):

```java
    @Override
    public void onPinStatusChanged() {
        loadDocuments();
    }
}
```

在 `onPinStatusChanged` 之后、类的 `}` 之前插入:

```java
    @Override
    public void onRequestCustomIcon(DocumentItem item) {
        // 占位实现:启动 ACTION_PICK + onActivityResult 处理在 Task 5
        Toast.makeText(requireContext(), "请稍候", Toast.LENGTH_SHORT).show();
    }
```

- [ ] **Step 5: 编译验证**

```bash
cd "c:/projects/duxiang-pack/duxiang-android" && ./gradlew :app:compileDebugJavaWithJavac
```

预期: `BUILD SUCCESSFUL`。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/adapter/DocumentAdapter.java app/src/main/java/person/notfresh/readingshare/ui/document/DocumentFragment.java
git commit -m "feat(document-adapter): add-to-desktop dialog and custom icon flow with stub"
```

---

### Task 5: DocumentFragment - 完整实现 onRequestCustomIcon + 扩展 onActivityResult

> 本任务实现 ACTION_PICK 启动 + onActivityResult 中读取所选图片并回调 `adapter.createShortcutWithCustomIcon`。
> 注意 `REQUEST_CODE_PICK_FILE` 已是 1001,本任务新常量必须不同(用 1002)。

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/document/DocumentFragment.java`(imports + 常量 + field + 替换 stub + 扩展 onActivityResult)

- [ ] **Step 1: 加 imports**

在 `import android.content.Intent;`(第 3 行)之后插入:

```java
import android.graphics.Bitmap;
```

在 `import person.notfresh.readingshare.db.DocumentDao;`(第 39 行)之后插入:

```java
import person.notfresh.readingshare.util.ImageUtil;
```

- [ ] **Step 2: 加 REQUEST_CODE_PICK_ICON 常量**

定位到 `private static final int REQUEST_CODE_PICK_FILE = 1001;`(第 44 行)。在其后插入:

```java
    private static final int REQUEST_CODE_PICK_ICON = 1002;
    private DocumentItem pendingIconDocItem;
```

- [ ] **Step 3: 替换 onRequestCustomIcon stub 为完整实现**

定位到上一任务加的 stub:

```java
    @Override
    public void onRequestCustomIcon(DocumentItem item) {
        // 占位实现:启动 ACTION_PICK + onActivityResult 处理在 Task 5
        Toast.makeText(requireContext(), "请稍候", Toast.LENGTH_SHORT).show();
    }
```

替换为:

```java
    @Override
    public void onRequestCustomIcon(DocumentItem item) {
        pendingIconDocItem = item;
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_CODE_PICK_ICON);
    }
```

- [ ] **Step 4: 扩展 onActivityResult**

定位到 `onActivityResult` 方法(第 135 行附近)。当前实现:

```java
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == android.app.Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                handleFileImport(uri);
            }
        }
    }
```

替换为:

```java
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == android.app.Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                handleFileImport(uri);
            }
        } else if (requestCode == REQUEST_CODE_PICK_ICON && resultCode == android.app.Activity.RESULT_OK
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

- [ ] **Step 5: 编译验证**

```bash
cd "c:/projects/duxiang-pack/duxiang-android" && ./gradlew :app:compileDebugJavaWithJavac
```

预期: `BUILD SUCCESSFUL`。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/person/notfresh/readingshare/ui/document/DocumentFragment.java
git commit -m "feat(document-fragment): implement custom icon picker via ACTION_PICK"
```

---

### Task 6: 构建完整 APK

**Files:** 无源码变更(仅构建产物)

- [ ] **Step 1: 构建 debug APK**

```bash
cd "c:/projects/duxiang-pack/duxiang-android" && ./gradlew :app:assembleDebug
```

预期: `BUILD SUCCESSFUL`,产物在 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 2: 提交(如 git status 干净则跳过)**

```bash
git status
```

预期: 无任何文件变化(仅产物在 build/,已被 .gitignore 忽略)。无需提交。

---

### Task 7: 手动端到端验证

> 本任务在真实设备/模拟器上跑,逐项覆盖 spec §8 的 18 个测试用例。**没有自动化测试框架,所有验证靠手测 + 截图/笔记**。
> 设备要求: Android 7.0+(API 24+,为兼容 INSTALL_SHORTCUT 降级路径;PinShortcut 需 API 26+ 走主路径)。

- [ ] **Step 1: 安装到设备**

```bash
cd "c:/projects/duxiang-pack/duxiang-android" && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

预期: `Success`。

- [ ] **Step 2: 准备测试数据**

如果设备上没有文档,先:
- 打开 app → 进入"文档"tab → 点右上角"导入文档"(原有功能)→ 选一个 PDF、一个 Markdown、一个 LaTeX、一个 TXT
- 至少 4 个文档,覆盖 4 种 DocumentType

预期: 列表显示文档。

- [ ] **Step 3: 跑"重命名"测试组(spec §8.2 #11-#18)**

逐项验证并打勾:
- [ ] #11 长按 → 重命名 → 改标题 → 确定 → 列表刷新为新标题
- [ ] #12 清空标题 → 确定 → 弹 Toast "标题不能为空",列表不变
- [ ] #13 输入全空白 → 确定 → 行为等同 #12(项目内不强制 trim,与链接流程一致)
- [ ] #14 取消 → 列表无变化,无 Toast
- [ ] #15 重命名后 kill app 再开 → 新标题仍在
- [ ] #16 重命名后点桌面快捷方式(若有)→ viewer toolbar 显示新标题
- [ ] #17 Markdown/LaTeX/TXT 各跑一次 #11
- [ ] #18 重命名后文档位置/分组是否变化(预期不变,因 timestamp 未变)

- [ ] **Step 4: 跑"添加到桌面"测试组(spec §8.1 #1-#10)**

逐项验证并打勾:
- [ ] #1 PDF → 添加到桌面 → 默认图标 → 系统确认 → 桌面有应用图标 → 点击 → 进入 viewer
- [ ] #2 PDF → 添加到桌面 → 相册图(选 1MB 内)→ 桌面图标变所选图 → 点击 → 正常打开
- [ ] #3 重复 #1 → 桌面只剩一个图标(覆盖)
- [ ] #4 Markdown/LaTeX/TXT 各跑一次 #1
- [ ] #5 mv 文件让 filePath 失效 → 添加到桌面 → 弹 Toast "文件不存在",不进图标选择
- [ ] #6 DB 删除文档后点已有快捷方式 → viewer 报"文件不存在"
- [ ] #7 添加到桌面 → 相册选图时按返回取消 → 无副作用,无 Toast
- [ ] #8 长按菜单"分享"/"重命名"/"删除"功能完全不受影响
- [ ] #9 桌面点击快捷方式 → 在 viewer 内退出 → 返回合理
- [ ] #10 文档列表"导入文档"功能完全不受影响(确认 onActivityResult 分支未破坏)

- [ ] **Step 5: 记录结论**

如果全部通过:在 PR 描述里写"手动验证 18/18 通过"。
如果有失败:在 issue 或 todo 中记录失败用例与现象,**不提交代码直到修复**。

- [ ] **Step 6: 提交(若有测试/截图/笔记)**

如果手动验证过程中修改了任何源码(可能为修复测试中发现的 bug),提交:

```bash
git add -A
git commit -m "fix: <描述>"
```

---

## 自检 checklist(对应 spec §9)

完成所有 7 个 Task 后,逐项打勾:

### 添加到桌面
- [ ] `ShortcutUtil.createDocumentShortcut` 与 `createSubjectShortcut` 形态一致
- [ ] `DocumentAdapter.OnDocumentActionListener` 已加 `onRequestCustomIcon`,DocumentFragment 已实现
- [ ] `DocumentFragment.pendingIconDocItem` 在 onActivityResult 正常/异常路径都置 null(用 finally)
- [ ] `document_item_menu.xml` 新 item 的 icon 用 `@android:drawable/ic_menu_add`
- [ ] shortcutIntent 加了 `FLAG_ACTIVITY_NEW_TASK` 与 `FLAG_ACTIVITY_CLEAR_TOP`
- [ ] `DocumentFragment.onActivityResult` 扩展时**未覆盖**原有 `REQUEST_CODE_PICK_FILE` 分支

### 重命名
- [ ] `showRenameDialog` 中 EditText 预填当前标题,光标置于末尾
- [ ] 空标题校验在 AlertDialog 确定回调内
- [ ] 重命名调的是**已有的** `listener.onUpdateDocument(item, newTitle)`,**未新增** listener 回调方法
- [ ] `DocumentFragment.onUpdateDocument` 未被修改
- [ ] `document_item_menu.xml` 新增的 `action_rename` icon 用 `@android:drawable/ic_menu_edit`

### 通用
- [ ] 不修改 `AndroidManifest.xml`
- [ ] 不修改 `DocumentViewerActivity` 任何代码
- [ ] 不修改 `DocumentDao`(`updateDocumentTitle` 已存在)
- [ ] `showPopupMenu` 中所有 case(分享/重命名/添加到桌面/删除)都有处理且不互相影响
- [ ] 菜单顺序: 分享 → 重命名 → 添加到桌面 → 删除
- [ ] `REQUEST_CODE_PICK_ICON = 1002`,与 `REQUEST_CODE_PICK_FILE = 1001` 不冲突

---

## 不在范围内(再次提醒)

- 不同 DocumentType 的专属图标资源
- 文档首页缩略图
- 批量"添加多个到桌面" / 拖拽到桌面
- 任何对 `LinksAdapter` / `WebShortcutActivity` 的重构
- 抽出共享 `ShortcutIconPicker` 工具类
- 改名磁盘文件
- 同步更新已有桌面快捷方式标签(已知限制)
- 在 `DocumentViewerActivity` 内加"重命名"入口
- 批量重命名 / 标题去重 / 标题历史记录
