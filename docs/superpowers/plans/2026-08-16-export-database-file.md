# Export Database File Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "导出数据库" button in `SettingFragment` that copies the app's `links.db` SQLite file to `cacheDir/exports/` and pops the system share sheet so the user can send it to cloud/email/etc.

**Architecture:** Reuse the existing `ShareUtil` FileProvider/ACTION_SEND pattern. A new `ExportUtil.exportDatabaseFile(Context)` does a buffered file copy. A new `ShareUtil.shareDatabaseFile(Context, File)` builds and launches the share intent with mime `application/octet-stream`. `SettingFragment` binds the new button onClick with the same `new Thread(...) + Toast` pattern already used by `exportAndShare`.

**Tech Stack:** Android (Java), JUnit4 (`src/test`), Android SDK 26+ (minSdk 26 in `app/build.gradle`, 29 in `app/build.gradle.kts` — both declare the project; groovy `app/build.gradle` is the active one based on the existing `app/build/`, `app/libs/`, `app/release/` directories), AndroidX `FileProvider`, no new dependencies.

## Global Constraints

- DB file name literal: `links.db` (from `LinkDbHelper.DEFAULT_DATABASE_NAME`)
- Output filename pattern: `links_yyyyMMdd_HHmmss.db` (uses `ExportUtil.getCurrentTime()` which already produces `yyyyMMdd_HHmmss`)
- Output directory: `context.getCacheDir()/exports/` (mkdirs if missing)
- Copy buffer: 8192 bytes
- Share mime: `application/octet-stream`
- FileProvider authority: `${applicationId}.provider` (i.e. `person.notfresh.readingshare.provider`)
- Must add `<cache-path name="cache" path="exports/" />` to `app/src/main/res/xml/file_paths.xml` (current file only declares `files-path` + `external-files-path`; without `cache-path` `FileProvider.getUriForFile` throws `IllegalArgumentException`)
- Throw `IOException` with message containing `数据库尚未初始化` when `src.exists() == false`
- Error toast text on missing DB: `数据库尚未初始化`
- Follow existing Toast-on-error pattern from `shareLinksAsFile` (`util/ShareUtil.java:101-124`) with three catch blocks: `IOException`, `SecurityException`, generic `Exception`
- No new test dependencies. Project has JUnit4 in `src/test` but **no Robolectric**. New tests must be pure JVM (no `Context`). `ShareUtil.shareDatabaseFile` (which uses `Context.startActivity`) gets only manual verification.
- No new file-level I18n required for the button label — `fragment_slideshow.xml` already hardcodes Chinese strings for the existing "导出所有链接" button. Use hardcoded Chinese for the new button to match convention.

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `app/src/main/res/xml/file_paths.xml` | Modify | Whitelist `cacheDir/exports/` for FileProvider |
| `app/src/main/res/layout/fragment_slideshow.xml` | Modify | Add new `<Button>` for "导出数据库" |
| `app/src/main/java/person/notfresh/readingshare/util/ExportUtil.java` | Modify | Add `exportDatabaseFile(Context)` static method |
| `app/src/main/java/person/notfresh/readingshare/util/ShareUtil.java` | Modify | Add `shareDatabaseFile(Context, File)` static method |
| `app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java` | Modify | Wire `button_export_database` onClick in `onCreateView` |
| `app/src/test/java/person/notfresh/readingshare/util/ExportUtilDatabaseCopyTest.java` | Create | Pure-JVM unit tests for the file-copy helper |

The new unit test exercises only the parts of `ExportUtil.exportDatabaseFile` that don't need a `Context`: the byte-copy loop and the filename builder. To keep it pure-JVM without Robolectric, `ExportUtil.exportDatabaseFile` is split into two static methods: a thin `Context`-aware public method that resolves paths, and a context-free helper that does the actual `InputStream`→`OutputStream` copy. The helper is what the test exercises.

---

## Task 1: Whitelist cacheDir/exports/ in FileProvider

**Files:**
- Modify: `app/src/main/res/xml/file_paths.xml`

- [ ] **Step 1: Edit file_paths.xml**

Append a `<cache-path>` element. Current content:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path
        name="internal_files"
        path="documents/" />
    <external-files-path
        name="external_files"
        path="." />
</paths>
```

Replace with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path
        name="internal_files"
        path="documents/" />
    <external-files-path
        name="external_files"
        path="." />
    <cache-path
        name="cache"
        path="exports/" />
</paths>
```

- [ ] **Step 2: Verify**

Run: `cat app/src/main/res/xml/file_paths.xml`
Expected: three children (`files-path`, `external-files-path`, `cache-path`) inside `<paths>`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/xml/file_paths.xml
git commit -m "feat(export): allow FileProvider access to cacheDir/exports/"
```

---

## Task 2: Add new unit test for the file-copy helper (TDD)

**Files:**
- Create: `app/src/test/java/person/notfresh/readingshare/util/ExportUtilDatabaseCopyTest.java`

This test covers only the context-free helper that Task 3 will expose.

- [ ] **Step 1: Write the failing test**

Create the file with this content:

```java
package person.notfresh.readingshare.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ExportUtilDatabaseCopyTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void copyFile_copiesAllBytes() throws IOException {
        byte[] payload = "hello sqlite".getBytes(StandardCharsets.UTF_8);
        File src = tmp.newFile("src.db");
        try (OutputStream os = new FileOutputStream(src)) {
            os.write(payload);
        }
        File dst = new File(tmp.getRoot(), "dst.db");

        ExportUtil.copyFile(src, dst);

        assertArrayEquals(payload, Files.readAllBytes(dst.toPath()));
    }

    @Test
    public void copyFile_overwritesExisting() throws IOException {
        File src = tmp.newFile("src.db");
        try (OutputStream os = new FileOutputStream(src)) {
            os.write("new".getBytes(StandardCharsets.UTF_8));
        }
        File dst = tmp.newFile("dst.db");
        try (OutputStream os = new FileOutputStream(dst)) {
            os.write("old-old-old".getBytes(StandardCharsets.UTF_8));
        }

        ExportUtil.copyFile(src, dst);

        assertArrayEquals(
            "new".getBytes(StandardCharsets.UTF_8),
            Files.readAllBytes(dst.toPath())
        );
    }

    @Test
    public void copyFile_sourceMissingThrows() throws IOException {
        File src = new File(tmp.getRoot(), "does-not-exist.db");
        File dst = new File(tmp.getRoot(), "dst.db");

        try {
            ExportUtil.copyFile(src, dst);
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue("message should mention source file: " + e.getMessage(),
                e.getMessage() != null && e.getMessage().contains(src.getName()));
        }
    }
}
```

- [ ] **Step 2: Run test, confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "person.notfresh.readingshare.util.ExportUtilDatabaseCopyTest"`
Expected: FAIL with `error: cannot find symbol: method copyFile(File, File)` (location: class `ExportUtil`).

- [ ] **Step 3: Commit the failing test**

```bash
git add app/src/test/java/person/notfresh/readingshare/util/ExportUtilDatabaseCopyTest.java
git commit -m "test(export): add failing tests for ExportUtil.copyFile"
```

---

## Task 3: Implement `ExportUtil.exportDatabaseFile` + `copyFile` helper

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/util/ExportUtil.java`

Add two new static methods after the existing `getCurrentTime()` method (line 143). The public method uses `Context`; the helper is context-free and is what Task 2 tests.

- [ ] **Step 1: Add the two methods**

Insert these methods just before the final closing `}` of `ExportUtil`:

```java
    /**
     * 将应用的 SQLite 数据库文件 (links.db) 复制到 cacheDir/exports/，
     * 返回复制后的目标文件，供后续分享使用。
     *
     * @param context Android Context，用于解析源 db 路径与目标 cache 目录
     * @return 复制后的目标文件
     * @throws IOException 当源数据库文件不存在或读写失败时抛出
     */
    public static File exportDatabaseFile(Context context) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("context 不能为空");
        }
        File src = context.getDatabasePath("links.db");
        if (!src.exists()) {
            throw new IOException("数据库尚未初始化: " + src.getAbsolutePath());
        }

        File exportsDir = new File(context.getCacheDir(), "exports");
        if (!exportsDir.exists() && !exportsDir.mkdirs()) {
            throw new IOException("无法创建导出目录: " + exportsDir.getAbsolutePath());
        }

        String fileName = "links_" + getCurrentTime() + ".db";
        File dst = new File(exportsDir, fileName);
        copyFile(src, dst);
        return dst;
    }

    /**
     * 把源文件按字节复制到目标文件，使用 8KB 缓冲区。
     * 目标已存在时会被覆盖；源不存在时抛 IOException。
     *
     * 拆出来作为 public 是为了让单元测试能在没有 Context 的情况下直接覆盖。
     */
    public static void copyFile(File src, File dst) throws IOException {
        if (src == null || !src.exists()) {
            throw new IOException("源文件不存在: " + (src == null ? "null" : src.getAbsolutePath()));
        }
        if (dst == null) {
            throw new IllegalArgumentException("dst 不能为空");
        }
        byte[] buffer = new byte[8192];
        try (java.io.FileInputStream in = new java.io.FileInputStream(src);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        }
    }
```

- [ ] **Step 2: Run the unit test from Task 2**

Run: `./gradlew :app:testDebugUnitTest --tests "person.notfresh.readingshare.util.ExportUtilDatabaseCopyTest"`
Expected: 3 tests pass.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/person/notfresh/readingshare/util/ExportUtil.java
git commit -m "feat(export): add ExportUtil.exportDatabaseFile + copyFile helper"
```

---

## Task 4: Implement `ShareUtil.shareDatabaseFile`

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/util/ShareUtil.java`

Add a new public static method modeled exactly on `shareLinksAsFile` (which lives at `util/ShareUtil.java:44-125`), with mime changed and the file pre-existing path used directly.

- [ ] **Step 1: Add `shareDatabaseFile` just before `processFileName`**

Insert after the closing brace of `shareLinkAsFile` (around line 299, before the `WebView 专用重载` block at line 304):

```java
    /**
     * 分享已生成的数据库文件 (.db)。文件应已存在于 FileProvider 白名单路径下，
     * 推荐调用方使用 {@link ExportUtil#exportDatabaseFile(android.content.Context)} 生成。
     */
    public static void shareDatabaseFile(Context context, File file) {
        try {
            if (file == null || !file.exists()) {
                throw new IOException("文件不存在，无法分享");
            }
            Uri fileUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".provider",
                file
            );
            Log.d("ShareUtil", "数据库文件 URI 生成成功: " + fileUri);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/octet-stream");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            ClipData clipData = ClipData.newUri(context.getContentResolver(), file.getName(), fileUri);
            shareIntent.setClipData(clipData);

            Intent chooser = Intent.createChooser(shareIntent, "分享数据库文件");
            excludeSelfFromChooser(context, chooser);
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            context.startActivity(chooser);
            Log.d("ShareUtil", "数据库分享选择器已启动");
        } catch (IOException e) {
            Log.e("ShareUtil", "数据库分享失败", e);
            Toast.makeText(context, "分享失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (IllegalArgumentException e) {
            Log.e("ShareUtil", "FileProvider URI 生成失败，请检查 file_paths.xml 是否声明了 cache-path", e);
            Toast.makeText(context, "无法生成文件 URI，请检查 FileProvider 配置", Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            Log.e("ShareUtil", "数据库分享权限不足", e);
            Toast.makeText(context, "分享权限不足: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e("ShareUtil", "数据库分享异常", e);
            Toast.makeText(context, "分享失败：" + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL. (No tests for this method — it uses `Context.startActivity`, exercised manually per the spec's manual verification list.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/person/notfresh/readingshare/util/ShareUtil.java
git commit -m "feat(export): add ShareUtil.shareDatabaseFile"
```

---

## Task 5: Add the "导出数据库" button to settings layout

**Files:**
- Modify: `app/src/main/res/layout/fragment_slideshow.xml`

- [ ] **Step 1: Insert the new button right after the existing `button_export`**

Locate the existing button:

```xml
        <Button
            android:id="@+id/button_export"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="导出所有链接"
            android:layout_marginTop="16dp"/>
```

Insert the new button immediately after it (before the next `<TextView>` that explains CSV format):

```xml
        <Button
            android:id="@+id/button_export"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="导出所有链接"
            android:layout_marginTop="16dp"/>

        <Button
            android:id="@+id/button_export_database"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="导出数据库"
            android:layout_marginTop="8dp"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="CSV的格式需要与导出格式匹配"
```

- [ ] **Step 2: Verify the layout parses**

Run: `./gradlew :app:processDebugResources`
Expected: BUILD SUCCESSFUL. (XML parse failure would surface here.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/fragment_slideshow.xml
git commit -m "feat(export): add export database button to settings layout"
```

---

## Task 6: Wire the button in `SettingFragment.onCreateView`

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java`

- [ ] **Step 1: Locate the existing `button_export` binding**

In `onCreateView`, after the existing export button binding (around line 278-280):

```java
        // 添加导出按钮的点击事件
        root.findViewById(R.id.button_export).setOnClickListener(v -> {
            showExportDialog();
        });
```

- [ ] **Step 2: Add the new binding immediately after it**

Insert this block right after the `button_export` block:

```java
        // 导出数据库（links.db 原文件）
        root.findViewById(R.id.button_export_database).setOnClickListener(v -> {
            exportDatabase();
        });
```

- [ ] **Step 3: Add the helper method to `SettingFragment`**

Add the following method anywhere inside `SettingFragment` (after `exportAndSave`, before `showExportSuccessDialog` is fine, around line 589):

```java
    /**
     * 在后台线程复制 links.db，然后回到主线程通过系统分享面板交给用户。
     * 失败时给出 Toast，沿用 exportAndShare 的并发模式。
     */
    private void exportDatabase() {
        ProgressDialog progressDialog = new ProgressDialog(requireContext());
        progressDialog.setMessage("正在导出数据库...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            File exported = null;
            String errorMessage = null;
            try {
                exported = ExportUtil.exportDatabaseFile(requireContext());
            } catch (Exception e) {
                Log.e("SettingFragment", "导出数据库失败", e);
                errorMessage = e.getMessage();
            }

            final File finalExported = exported;
            final String finalError = errorMessage;
            requireActivity().runOnUiThread(() -> {
                progressDialog.dismiss();
                if (finalExported != null) {
                    ShareUtil.shareDatabaseFile(requireContext(), finalExported);
                } else {
                    String msg = (finalError != null && !finalError.isEmpty())
                        ? finalError
                        : "请重试";
                    Snackbar.make(requireView(), "导出数据库失败：" + msg, Snackbar.LENGTH_LONG).show();
                }
            });
        }).start();
    }
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java
git commit -m "feat(export): wire export database button in SettingFragment"
```

---

## Task 7: Final verification

- [ ] **Step 1: Run the unit test**

Run: `./gradlew :app:testDebugUnitTest --tests "person.notfresh.readingshare.util.ExportUtilDatabaseCopyTest"`
Expected: 3 tests pass.

- [ ] **Step 2: Confirm debug build still assembles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual verification (per spec §手动验证)**

The engineer (or QA) performs these on a real device or emulator. Each maps to a row in the spec.

1. **有数据，正常导出**: Insert a link/tag/subject through the app's normal UI; open Settings → scroll to "导出数据库"; tap. Expect system share sheet to appear with a file named `links_<timestamp>.db`. Pick "Save to Drive" or "Email" and confirm the received attachment opens in DB Browser for SQLite with all 12 tables visible.
2. **全新安装**: Clear app data; launch; do nothing else; tap "导出数据库". Expect a Snackbar/Toast saying the DB isn't initialized; app does not crash.
3. **重复点击**: Tap the button 3 times within 5 seconds. Expect 3 different files in `cacheDir/exports/` (verifiable with `adb shell run-as person.notfresh.readingshare ls cache/exports/`); the last one is the one offered in the share sheet.
4. **冷启动**: Reboot device; relaunch app; tap the button. Expect normal behavior, no stale state.

- [ ] **Step 4: Final commit (no source changes)**

If Step 1/2 reveal no regressions, no commit is needed. If manual verification finds issues, fix and commit per task.

---

## Self-Review Notes

- **Spec coverage**: Every section of `docs/superpowers/specs/2026-08-16-export-database-file-design.md` is covered by a task. Specifically: FileProvider whitelist (Task 1), unit tests (Task 2 + Task 3), `exportDatabaseFile` (Task 3), `shareDatabaseFile` (Task 4), button (Task 5), `SettingFragment` wiring (Task 6), manual verification (Task 7).
- **No Robolectric needed**: Tasks 2/3 exercise only the context-free `copyFile(File, File)` helper, satisfying the global constraint "No new test dependencies."
- **Type consistency**: `ExportUtil.exportDatabaseFile(Context) -> File` is referenced consistently in Task 4 (consumed as `File` parameter to `ShareUtil.shareDatabaseFile(Context, File)`) and in Task 6 (binding handler calls both). `shareDatabaseFile` signature is defined once in Task 4 and called exactly once in Task 6.
- **No placeholders**: All code blocks contain real code; all commands include the exact gradle invocation; all file paths use the repository-relative form.