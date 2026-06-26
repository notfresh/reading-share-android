# WebView 离线缓存 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 WebView 增加"白名单 URL 离线缓存"功能 —— 用户把页面加入白名单后,每次打开联网时静默更新缓存,主框架加载失败时自动回退到磁盘缓存,顶部黄条提示。

**Architecture:** 用 `shouldInterceptRequest` 拦截器(OkHttp 录制 + 磁盘回放),新增 `WebCacheManager`(白名单/文件 I/O)+ `CachingWebViewClient`(拦截/录制/回退逻辑)+ `OfflineCacheFragment`(设置入口列表页);修改 `WebViewActivity` 装配客户端、控制顶部横条。

**Tech Stack:** Java / Android SDK / OkHttp(首次引入) / JUnit 4 / 项目内已有的 AppCompat / Material Components / LinearLayout(无 RecyclerView 依赖)

**Spec:** `docs/superpowers/specs/2026-06-11-webview-offline-cache-design.md`

**重要设计约束(来自 spec):**
- 非白名单 URL **完全不介入**,零开销
- HTML 主体不改写,仅在 `file://` 模式时通过 `loadDataWithBaseURL(origin, ..., data + <base>)` 注入 `<base href>`
- 缓存目录用 `onPageStarted` 拿到的最终 URL(经重定向后),非用户点击时的初始 URL
- 横条无关闭按钮

---

## 文件结构(变更清单)

| 类型 | 路径 | 职责 |
|---|---|---|
| 新增 | `app/src/main/java/person/notfresh/readingshare/util/WebCacheManager.java` | 白名单 + 文件 I/O + URL规范化 |
| 新增 | `app/src/main/java/person/notfresh/readingshare/web/CachingWebViewClient.java` | 拦截器 + 录制 + 回退 |
| 新增 | `app/src/main/java/person/notfresh/readingshare/ui/settings/OfflineCacheFragment.java` | 缓存列表页 |
| 新增 | `app/src/main/java/person/notfresh/readingshare/ui/settings/OfflineCacheAdapter.java` | RecyclerView 适配器 |
| 新增 | `app/src/main/res/layout/fragment_offline_cache.xml` | 列表页布局 |
| 新增 | `app/src/main/res/layout/item_offline_cache.xml` | 列表条目布局 |
| 新增 | `app/src/main/res/menu/offline_cache_menu.xml` | 列表页菜单(清空所有) |
| 新增 | `app/src/main/res/drawable/ic_cache_offline.xml` | 横条图标(用项目已有 ic_clear 简化版) |
| 新增 | `app/src/test/java/person/notfresh/readingshare/util/WebCacheManagerTest.java` | 单元测试 |
| 修改 | `app/build.gradle` | 加 okhttp 依赖 |
| 修改 | `app/src/main/res/values/strings.xml` | 加 10 个新字符串 |
| 修改 | `app/src/main/res/layout/activity_webview.xml` | 在 Toolbar 下方加 banner LinearLayout |
| 修改 | `app/src/main/res/menu/webview_menu.xml` | 加 `action_cache_page` 菜单项 |
| 修改 | `app/src/main/res/layout/fragment_slideshow.xml` | 加"离线缓存"入口行 |
| 修改 | `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java` | 装配客户端 + 菜单/横条 |
| 修改 | `app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java` | 加入口点击跳转 |

---

## Task 1: 引入 OkHttp 依赖

**Files:**
- Modify: `app/build.gradle` (查找 `dependencies { ... }` 块)
- Modify: `gradle/libs.versions.toml` (查找 `[versions]` 和 `[libraries]` 块)

- [ ] **Step 1: 在 `gradle/libs.versions.toml` 加 OkHttp 版本和库**

在 `[versions]` 段加:
```toml
okhttp = "4.12.0"
```

在 `[libraries]` 段加:
```toml
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
```

- [ ] **Step 2: 在 `app/build.gradle` 加依赖**

在 `dependencies { ... }` 块内(随便找个位置)加:
```gradle
implementation libs.okhttp
```

- [ ] **Step 3: 同步 Gradle**

```bash
cd c:/projects/duxiang-pack/duxiang-android
./gradlew :app:dependencies --configuration debugRuntimeClasspath 2>&1 | grep -i okhttp
```
Expected: 看到 `okhttp:4.12.0` 或 `com.squareup.okhttp3:okhttp:4.12.0` 字样

- [ ] **Step 4: Commit**

```bash
cd c:/projects/duxiang-pack/duxiang-android
git add gradle/libs.versions.toml app/build.gradle
git commit -m "build: add okhttp 4.12.0 dependency"
```

---

## Task 2: WebCacheManager 骨架 + URL 规范化(TDD)

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/util/WebCacheManager.java`
- Create: `app/src/test/java/person/notfresh/readingshare/util/WebCacheManagerTest.java`

- [ ] **Step 1: 写失败测试 - canonicalUrl 去掉 `#fragment`**

在 `WebCacheManagerTest.java`:
```java
package person.notfresh.readingshare.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class WebCacheManagerTest {

    @Test
    public void canonicalUrl_stripsFragment() {
        assertEquals("https://example.com/article", WebCacheManager.canonicalUrl("https://example.com/article#section-2"));
    }

    @Test
    public void canonicalUrl_keepsQuery() {
        assertEquals("https://example.com/article?id=1", WebCacheManager.canonicalUrl("https://example.com/article?id=1"));
    }

    @Test
    public void canonicalUrl_noFragmentUnchanged() {
        assertEquals("https://example.com/article", WebCacheManager.canonicalUrl("https://example.com/article"));
    }

    @Test
    public void canonicalUrl_nullSafe() {
        assertNull(WebCacheManager.canonicalUrl(null));
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

```bash
cd c:/projects/duxiang-pack/duxiang-android
./gradlew :app:testDebugUnitTest --tests "person.notfresh.readingshare.util.WebCacheManagerTest" 2>&1 | tail -30
```
Expected: `FAILED` with "cannot find symbol: class WebCacheManager"

- [ ] **Step 3: 创建 WebCacheManager.java 骨架 + canonicalUrl 实现**

```java
package person.notfresh.readingshare.util;

import android.content.Context;
import java.io.File;

public class WebCacheManager {

    private static WebCacheManager instance;
    private final File cacheRoot;
    private final android.content.SharedPreferences prefs;

    private WebCacheManager(Context context) {
        this.cacheRoot = new File(context.getFilesDir(), "web_cache");
        this.prefs = context.getSharedPreferences("web_cache_prefs", Context.MODE_PRIVATE);
        if (!cacheRoot.exists()) cacheRoot.mkdirs();
    }

    public static synchronized WebCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new WebCacheManager(context);
        }
        return instance;
    }

    static WebCacheManager forTest(File cacheRoot, android.content.SharedPreferences prefs) {
        WebCacheManager m = new WebCacheManager(cacheRoot, prefs);
        return m;
    }

    private WebCacheManager(File cacheRoot, android.content.SharedPreferences prefs) {
        this.cacheRoot = cacheRoot;
        this.prefs = prefs;
        if (!cacheRoot.exists()) cacheRoot.mkdirs();
    }

    public static String canonicalUrl(String url) {
        if (url == null) return null;
        int hashIdx = url.indexOf('#');
        return hashIdx >= 0 ? url.substring(0, hashIdx) : url;
    }
}
```

- [ ] **Step 4: 运行测试,确认通过**

```bash
cd c:/projects/duxiang-pack/duxiang-android
./gradlew :app:testDebugUnitTest --tests "person.notfresh.readingshare.util.WebCacheManagerTest" 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`,4 个测试全过

- [ ] **Step 5: Commit**

```bash
cd c:/projects/duxiang-pack/duxiang-android
git add app/src/main/java/person/notfresh/readingshare/util/WebCacheManager.java \
        app/src/test/java/person/notfresh/readingshare/util/WebCacheManagerTest.java
git commit -m "feat(webcache): add WebCacheManager skeleton with canonicalUrl"
```

---

## Task 3: getCacheDir + URL 哈希映射(TDD)

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/util/WebCacheManager.java`
- Modify: `app/src/test/java/person/notfresh/readingshare/util/WebCacheManagerTest.java`

- [ ] **Step 1: 加失败测试 - 相同 URL 给相同目录,不同 URL 给不同目录**

追加到 `WebCacheManagerTest.java`:
```java
    @Test
    public void getCacheDir_sameUrl_sameDir() {
        WebCacheManager m = WebCacheManager.forTest(
            new File(System.getProperty("java.io.tmpdir"), "wcm-test-1"),
            new TestSharedPreferences());
        File d1 = m.getCacheDir("https://example.com/a");
        File d2 = m.getCacheDir("https://example.com/a");
        assertEquals(d1, d2);
        assertTrue(d1.getName().matches("[0-9a-f]{40}"));
    }

    @Test
    public void getCacheDir_differentUrls_differentDirs() {
        WebCacheManager m = WebCacheManager.forTest(
            new File(System.getProperty("java.io.tmpdir"), "wcm-test-2"),
            new TestSharedPreferences());
        assertNotEquals(m.getCacheDir("https://example.com/a"), m.getCacheDir("https://example.com/b"));
    }

    @Test
    public void getCacheDir_usesCanonicalUrl() {
        WebCacheManager m = WebCacheManager.forTest(
            new File(System.getProperty("java.io.tmpdir"), "wcm-test-3"),
            new TestSharedPreferences());
        assertEquals(m.getCacheDir("https://example.com/a"), m.getCacheDir("https://example.com/a#frag"));
    }
```

- [ ] **Step 2: 创建 TestSharedPreferences 工具类(测试夹具)**

在 `app/src/test/java/person/notfresh/readingshare/util/TestSharedPreferences.java`:
```java
package person.notfresh.readingshare.util;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TestSharedPreferences implements SharedPreferences {
    private final Map<String, Object> data = new HashMap<>();

    @Override public android.content.SharedPreferences.Editor edit() { return new Editor(); }
    @Override public java.util.Map<String, ?> getAll() { return data; }
    @Override public String getString(String k, String d) { return (String) data.getOrDefault(k, d); }
    @Override public int getInt(String k, int d) { return (Integer) data.getOrDefault(k, d); }
    @Override public long getLong(String k, long d) { return (Long) data.getOrDefault(k, d); }
    @Override public float getFloat(String k, float d) { return (Float) data.getOrDefault(k, d); }
    @Override public boolean getBoolean(String k, boolean d) { return (Boolean) data.getOrDefault(k, d); }
    @Override public boolean contains(String k) { return data.containsKey(k); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    @Override public Set<String> getStringSet(String k, Set<String> d) {
        @SuppressWarnings("unchecked")
        Set<String> s = (Set<String>) data.get(k);
        return s != null ? new HashSet<>(s) : d;
    }
    @Override public void apply() {}
    @Override public void startActivity(String... a) {}

    private class Editor implements android.content.SharedPreferences.Editor {
        @Override public android.content.SharedPreferences.Editor putString(String k, String v) { data.put(k, v); return this; }
        @Override public android.content.SharedPreferences.Editor putStringSet(String k, Set<String> v) { data.put(k, new HashSet<>(v)); return this; }
        @Override public android.content.SharedPreferences.Editor putInt(String k, int v) { data.put(k, v); return this; }
        @Override public android.content.SharedPreferences.Editor putLong(String k, long v) { data.put(k, v); return this; }
        @Override public android.content.SharedPreferences.Editor putFloat(String k, float v) { data.put(k, v); return this; }
        @Override public android.content.SharedPreferences.Editor putBoolean(String k, boolean v) { data.put(k, v); return this; }
        @Override public android.content.SharedPreferences.Editor remove(String k) { data.remove(k); return this; }
        @Override public android.content.SharedPreferences.Editor clear() { data.clear(); return this; }
        @Override public boolean commit() { return true; }
        @Override public void apply() {}
    }
}
```

- [ ] **Step 3: 实现 getCacheDir**

在 `WebCacheManager.java` 加方法:
```java
    public File getCacheDir(String url) {
        String canonical = canonicalUrl(url);
        String hash = sha1(canonical);
        return new File(cacheRoot, hash);
    }

    private static String sha1(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
```

- [ ] **Step 4: 运行测试,确认通过**

```bash
cd c:/projects/duxiang-pack/duxiang-android
./gradlew :app:testDebugUnitTest --tests "person.notfresh.readingshare.util.WebCacheManagerTest" 2>&1 | tail -20
```
Expected: 7 个测试全过(原 4 + 新 3)

- [ ] **Step 5: Commit**

```bash
cd c:/projects/duxiang-pack/duxiang-android
git add app/src/main/java/person/notfresh/readingshare/util/WebCacheManager.java \
        app/src/test/java/person/notfresh/readingshare/util/WebCacheManagerTest.java \
        app/src/test/java/person/notfresh/readingshare/util/TestSharedPreferences.java
git commit -m "feat(webcache): add getCacheDir with sha1 hash of canonical URL"
```

---

## Task 4: 白名单操作(TDD)

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/util/WebCacheManager.java`
- Modify: `app/src/test/java/person/notfresh/readingshare/util/WebCacheManagerTest.java`

- [ ] **Step 1: 加失败测试**

```java
    @Test
    public void whitelist_addContainsRemove() {
        TestSharedPreferences prefs = new TestSharedPreferences();
        WebCacheManager m = WebCacheManager.forTest(
            new File(System.getProperty("java.io.tmpdir"), "wcm-wl"), prefs);
        m.addToWhitelist("https://example.com/a");
        assertTrue(m.isInWhitelist("https://example.com/a"));
        assertTrue(m.isInWhitelist("https://example.com/a#frag"));
        m.removeFromWhitelist("https://example.com/a");
        assertFalse(m.isInWhitelist("https://example.com/a"));
    }

    @Test
    public void whitelist_persistsCanonical() {
        TestSharedPreferences prefs = new TestSharedPreferences();
        WebCacheManager m = WebCacheManager.forTest(
            new File(System.getProperty("java.io.tmpdir"), "wcm-wl2"), prefs);
        m.addToWhitelist("https://example.com/a#section-1");
        m.addToWhitelist("https://example.com/a#section-2");
        assertEquals(1, m.getWhitelist().size());
    }
```

- [ ] **Step 2: 实现白名单方法**

在 `WebCacheManager.java`:
```java
    private static final String KEY_WHITELIST = "cached_urls";

    public void addToWhitelist(String url) {
        String canonical = canonicalUrl(url);
        if (canonical == null) return;
        java.util.Set<String> set = new java.util.HashSet<>(getWhitelist());
        set.add(canonical);
        prefs.edit().putStringSet(KEY_WHITELIST, set).apply();
    }

    public void removeFromWhitelist(String url) {
        String canonical = canonicalUrl(url);
        if (canonical == null) return;
        java.util.Set<String> set = new java.util.HashSet<>(getWhitelist());
        set.remove(canonical);
        prefs.edit().putStringSet(KEY_WHITELIST, set).apply();
    }

    public boolean isInWhitelist(String url) {
        String canonical = canonicalUrl(url);
        return canonical != null && getWhitelist().contains(canonical);
    }

    public java.util.Set<String> getWhitelist() {
        return prefs.getStringSet(KEY_WHITELIST, java.util.Collections.emptySet());
    }
```

- [ ] **Step 3: 运行测试,确认通过**

```bash
cd c:/projects/duxiang-pack/duxiang-android
./gradlew :app:testDebugUnitTest --tests "person.notfresh.readingshare.util.WebCacheManagerTest" 2>&1 | tail -20
```
Expected: 9 个测试全过

- [ ] **Step 4: Commit**

```bash
cd c:/projects/duxiang-pack/duxiang-android
git add app/src/main/java/person/notfresh/readingshare/util/WebCacheManager.java \
        app/src/test/java/person/notfresh/readingshare/util/WebCacheManagerTest.java
git commit -m "feat(webcache): add whitelist add/remove/contains via SharedPreferences"
```

---

## Task 5: main.html 读写(TDD)

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/util/WebCacheManager.java`
- Modify: `app/src/test/java/person/notfresh/readingshare/util/WebCacheManagerTest.java`

- [ ] **Step 1: 加失败测试**

```java
    @Test
    public void mainHtml_roundTrip() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "wcm-html");
        WebCacheManager m = WebCacheManager.forTest(root, new TestSharedPreferences());
        m.saveMainHtml("https://example.com/a", "<html><body>hi</body></html>");
        assertEquals("<html><body>hi</body></html>", m.readMainHtml("https://example.com/a"));
    }

    @Test
    public void mainHtml_missingReturnsNull() {
        File root = new File(System.getProperty("java.io.tmpdir"), "wcm-html2");
        WebCacheManager m = WebCacheManager.forTest(root, new TestSharedPreferences());
        assertNull(m.readMainHtml("https://example.com/never-saved"));
    }
```

- [ ] **Step 2: 实现 saveMainHtml / readMainHtml**

```java
    public void saveMainHtml(String url, String html) throws java.io.IOException {
        File dir = getCacheDir(url);
        if (!dir.exists()) dir.mkdirs();
        java.io.FileWriter w = new java.io.FileWriter(new File(dir, "main.html"));
        try { w.write(html); } finally { w.close(); }
    }

    public String readMainHtml(String url) {
        File f = new File(getCacheDir(url), "main.html");
        if (!f.exists()) return null;
        try {
            return new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
```

- [ ] **Step 3: 运行测试**

```bash
cd c:/projects/duxiang-pack/duxiang-android
./gradlew :app:testDebugUnitTest --tests "person.notfresh.readingshare.util.WebCacheManagerTest" 2>&1 | tail -20
```
Expected: 11 个测试全过

- [ ] **Step 4: Commit**

```bash
cd c:/projects/duxiang-pack/duxiang-android
git add app/src/main/java/person/notfresh/readingshare/util/WebCacheManager.java \
        app/src/test/java/person/notfresh/readingshare/util/WebCacheManagerTest.java
git commit -m "feat(webcache): add saveMainHtml / readMainHtml"
```

---

## Task 6: meta.json 读写 + 损坏恢复(TDD)

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/util/WebCacheManager.java`
- Modify: `app/src/test/java/person/notfresh/readingshare/util/WebCacheManagerTest.java`

- [ ] **Step 1: 加失败测试**

```java
    @Test
    public void meta_roundTrip() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "wcm-meta");
        WebCacheManager m = WebCacheManager.forTest(root, new TestSharedPreferences());
        m.saveMeta("https://example.com/a", "My Title", 12345L);
        WebCacheManager.CacheMeta meta = m.readMeta("https://example.com/a");
        assertNotNull(meta);
        assertEquals("https://example.com/a", meta.url);
        assertEquals("My Title", meta.title);
        assertEquals(12345L, meta.sizeBytes);
        assertTrue(meta.cachedAt > 0);
    }

    @Test
    public void meta_corruptFileReturnsDefaults() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "wcm-meta2");
        WebCacheManager m = WebCacheManager.forTest(root, new TestSharedPreferences());
        File dir = m.getCacheDir("https://example.com/bad");
        dir.mkdirs();
        java.io.FileWriter w = new java.io.FileWriter(new File(dir, "meta.json"));
        w.write("{ this is not json");
        w.close();
        WebCacheManager.CacheMeta meta = m.readMeta("https://example.com/bad");
        assertNotNull(meta);
        assertEquals("https://example.com/bad", meta.url);
        assertEquals("", meta.title);
    }

    @Test
    public void meta_missingReturnsNull() {
        File root = new File(System.getProperty("java.io.tmpdir"), "wcm-meta3");
        WebCacheManager m = WebCacheManager.forTest(root, new TestSharedPreferences());
        assertNull(m.readMeta("https://example.com/never"));
    }
```

- [ ] **Step 2: 在 WebCacheManager 内嵌套类 CacheMeta + 实现 saveMeta / readMeta**

```java
    public static class CacheMeta {
        public String url;
        public String title;
        public long cachedAt;
        public long sizeBytes;
    }

    public void saveMeta(String url, String title, long sizeBytes) throws java.io.IOException {
        File dir = getCacheDir(url);
        if (!dir.exists()) dir.mkdirs();
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("url", canonicalUrl(url));
        o.put("title", title == null ? "" : title);
        o.put("cachedAt", System.currentTimeMillis());
        o.put("sizeBytes", sizeBytes);
        java.io.FileWriter w = new java.io.FileWriter(new File(dir, "meta.json"));
        try { w.write(o.toString()); } finally { w.close(); }
    }

    public CacheMeta readMeta(String url) {
        File f = new File(getCacheDir(url), "meta.json");
        if (!f.exists()) return null;
        try {
            String body = new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
            org.json.JSONObject o = new org.json.JSONObject(body);
            CacheMeta m = new CacheMeta();
            m.url = o.optString("url", canonicalUrl(url));
            m.title = o.optString("title", "");
            m.cachedAt = o.optLong("cachedAt", 0L);
            m.sizeBytes = o.optLong("sizeBytes", 0L);
            return m;
        } catch (Exception e) {
            // 损坏:返回带 url 的默认 meta,这样列表页能降级显示
            CacheMeta m = new CacheMeta();
            m.url = canonicalUrl(url);
            m.title = "";
            m.cachedAt = 0L;
            m.sizeBytes = 0L;
            return m;
        }
    }
```

- [ ] **Step 3: 运行测试**

```bash
cd c:/projects/duxiang-pack/duxiang-android
./gradlew :app:testDebugUnitTest --tests "person.notfresh.readingshare.util.WebCacheManagerTest" 2>&1 | tail -20
```
Expected: 14 个测试全过

- [ ] **Step 4: Commit**

```bash
cd c:/projects/duxiang-pack/duxiang-android
git add app/src/main/java/person/notfresh/readingshare/util/WebCacheManager.java \
        app/src/test/java/person/notfresh/readingshare/util/WebCacheManagerTest.java
git commit -m "feat(webcache): add meta.json save/read with corruption recovery"
```

---

## Task 7: resources.json 读写(TDD)

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/util/WebCacheManager.java`
- Modify: `app/src/test/java/person/notfresh/readingshare/util/WebCacheManagerTest.java`

- [ ] **Step 1: 加失败测试**

```java
    @Test
    public void resource_roundTrip() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "wcm-res");
        WebCacheManager m = WebCacheManager.forTest(root, new TestSharedPreferences());
        m.saveResource("https://example.com/page", "https://cdn.example.com/img.png", new byte[]{1,2,3}, "png");
        byte[] got = m.readResource("https://example.com/page", "https://cdn.example.com/img.png");
        assertArrayEquals(new byte[]{1,2,3}, got);
    }

    @Test
    public void resource_missingReturnsNull() {
        File root = new File(System.getProperty("java.io.tmpdir"), "wcm-res2");
        WebCacheManager m = WebCacheManager.forTest(root, new TestSharedPreferences());
        assertNull(m.readResource("https://example.com/page", "https://cdn.example.com/none.png"));
    }

    @Test
    public void resource_overwrites() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "wcm-res3");
        WebCacheManager m = WebCacheManager.forTest(root, new TestSharedPreferences());
        m.saveResource("https://example.com/page", "https://cdn.example.com/x.css", new byte[]{1}, "css");
        m.saveResource("https://example.com/page", "https://cdn.example.com/x.css", new byte[]{2,3}, "css");
        assertArrayEquals(new byte[]{2,3}, m.readResource("https://example.com/page", "https://cdn.example.com/x.css"));
    }
```

- [ ] **Step 2: 实现 saveResource / readResource**

resources.json 结构: `{ "原始资源URL": "r/<sha1>.<ext>", ... }`

```java
    public void saveResource(String pageUrl, String resUrl, byte[] data, String ext) throws java.io.IOException {
        File dir = getCacheDir(pageUrl);
        File rDir = new File(dir, "r");
        if (!rDir.exists()) rDir.mkdirs();
        String fileName = sha1(resUrl) + (ext != null && !ext.isEmpty() ? "." + ext : "");
        java.io.FileOutputStream out = new java.io.FileOutputStream(new File(rDir, fileName));
        try { out.write(data); } finally { out.close(); }

        // 写/更新 resources.json
        org.json.JSONObject map = readResourcesMap(pageUrl);
        map.put(resUrl, "r/" + fileName);
        java.io.FileWriter w = new java.io.FileWriter(new File(dir, "resources.json"));
        try { w.write(map.toString()); } finally { w.close(); }
    }

    public byte[] readResource(String pageUrl, String resUrl) {
        org.json.JSONObject map = readResourcesMap(pageUrl);
        String rel = map.optString(resUrl, null);
        if (rel == null) return null;
        File f = new File(getCacheDir(pageUrl), rel);
        if (!f.exists()) return null;
        try {
            return java.nio.file.Files.readAllBytes(f.toPath());
        } catch (Exception e) {
            return null;
        }
    }

    private org.json.JSONObject readResourcesMap(String pageUrl) {
        File f = new File(getCacheDir(pageUrl), "resources.json");
        if (!f.exists()) return new org.json.JSONObject();
        try {
            return new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8"));
        } catch (Exception e) {
            return new org.json.JSONObject();
        }
    }
```

- [ ] **Step 3: 运行测试**

```bash
cd c:/projects/duxiang-pack/duxiang-android
./gradlew :app:testDebugUnitTest --tests "person.notfresh.readingshare.util.WebCacheManagerTest" 2>&1 | tail -20
```
Expected: 17 个测试全过

- [ ] **Step 4: Commit**

```bash
cd c:/projects/duxiang-pack/duxiang-android
git add app/src/main/java/person/notfresh/readingshare/util/WebCacheManager.java \
        app/src/test/java/person/notfresh/readingshare/util/WebCacheManagerTest.java
git commit -m "feat(webcache): add resources.json + r/ binary blob save/read"
```

---

## Task 8: listAllCaches / hasCache / deleteCache / totalSize(TDD)

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/util/WebCacheManager.java`
- Modify: `app/src/test/java/person/notfresh/readingshare/util/WebCacheManagerTest.java`

- [ ] **Step 1: 加失败测试**

```java
    @Test
    public void listAllCaches_sortedByCachedAtDesc() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "wcm-list");
        WebCacheManager m = WebCacheManager.forTest(root, new TestSharedPreferences());
        m.saveMainHtml("https://example.com/a", "A");
        m.saveMeta("https://example.com/a", "Title A", 100L);
        Thread.sleep(10);
        m.saveMainHtml("https://example.com/b", "B");
        m.saveMeta("https://example.com/b", "Title B", 200L);
        java.util.List<WebCacheManager.CacheEntry> list = m.listAllCaches();
        assertEquals(2, list.size());
        assertEquals("https://example.com/b", list.get(0).url);
        assertEquals("https://example.com/a", list.get(1).url);
    }

    @Test
    public void hasCache_dirExistsWithMainHtml() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "wcm-has");
        WebCacheManager m = WebCacheManager.forTest(root, new TestSharedPreferences());
        assertFalse(m.hasCache("https://example.com/x"));
        m.saveMainHtml("https://example.com/x", "x");
        assertTrue(m.hasCache("https://example.com/x"));
    }

    @Test
    public void deleteCache_removesAllFiles() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "wcm-del");
        WebCacheManager m = WebCacheManager.forTest(root, new TestSharedPreferences());
        m.saveMainHtml("https://example.com/del", "<html/>");
        m.saveMeta("https://example.com/del", "T", 1L);
        m.saveResource("https://example.com/del", "https://cdn/x.png", new byte[]{1}, "png");
        m.deleteCache("https://example.com/del");
        assertFalse(m.hasCache("https://example.com/del"));
        assertFalse(m.getCacheDir("https://example.com/del").exists());
    }

    @Test
    public void totalSize_sumsAllFiles() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "wcm-size");
        WebCacheManager m = WebCacheManager.forTest(root, new TestSharedPreferences());
        m.saveMainHtml("https://example.com/s", "12345");
        m.saveResource("https://example.com/s", "https://cdn/y.bin", new byte[]{1,2,3,4,5,6,7}, "bin");
        long size = m.totalSize();
        assertTrue("size should be 12", size == 12L);
    }
```

- [ ] **Step 2: 实现 CacheEntry + 4 个方法**

```java
    public static class CacheEntry {
        public String url;
        public String title;
        public long cachedAt;
        public long sizeBytes;
        public String host;
    }

    public boolean hasCache(String url) {
        return new File(getCacheDir(url), "main.html").exists();
    }

    public void deleteCache(String url) {
        File dir = getCacheDir(url);
        if (dir.exists()) {
            deleteRecursively(dir);
        }
    }

    private static void deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursively(c);
        }
        f.delete();
    }

    public java.util.List<CacheEntry> listAllCaches() {
        java.util.List<CacheEntry> out = new java.util.ArrayList<>();
        if (!cacheRoot.isDirectory()) return out;
        File[] dirs = cacheRoot.listFiles();
        if (dirs == null) return out;
        for (File d : dirs) {
            File mainHtml = new File(d, "main.html");
            if (!mainHtml.exists()) continue;
            // 通过 main.html 文件名无法反推 url;从 meta.json 读 url
            CacheMeta meta = readMetaFromDir(d);
            if (meta == null) continue;
            CacheEntry e = new CacheEntry();
            e.url = meta.url;
            e.title = meta.title;
            e.cachedAt = meta.cachedAt;
            e.sizeBytes = computeDirSize(d);
            e.host = hostOf(e.url);
            out.add(e);
        }
        java.util.Collections.sort(out, (a, b) -> Long.compare(b.cachedAt, a.cachedAt));
        return out;
    }

    private CacheMeta readMetaFromDir(File dir) {
        File f = new File(dir, "meta.json");
        if (!f.exists()) {
            // main.html 存在但 meta 不存在(部分损坏) → 仍允许展示/删除
            CacheMeta m = new CacheMeta();
            return m;
        }
        try {
            String body = new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
            org.json.JSONObject o = new org.json.JSONObject(body);
            CacheMeta m = new CacheMeta();
            m.url = o.optString("url", "");
            m.title = o.optString("title", "");
            m.cachedAt = o.optLong("cachedAt", 0L);
            m.sizeBytes = o.optLong("sizeBytes", 0L);
            return m;
        } catch (Exception e) {
            CacheMeta m = new CacheMeta();
            return m;
        }
    }

    private long computeDirSize(File d) {
        long total = 0L;
        if (d.isDirectory()) {
            File[] children = d.listFiles();
            if (children != null) for (File c : children) total += computeDirSize(c);
        } else {
            total += d.length();
        }
        return total;
    }

    public long totalSize() {
        return computeDirSize(cacheRoot);
    }

    private static String hostOf(String url) {
        try {
            return new java.net.URI(url).getHost();
        } catch (Exception e) {
            return "";
        }
    }
```

- [ ] **Step 3: 运行测试**

```bash
cd c:/projects/duxiang-pack/duxiang-android
./gradlew :app:testDebugUnitTest --tests "person.notfresh.readingshare.util.WebCacheManagerTest" 2>&1 | tail -20
```
Expected: 21 个测试全过

- [ ] **Step 4: Commit**

```bash
cd c:/projects/duxiang-pack/duxiang-android
git add app/src/main/java/person/notfresh/readingshare/util/WebCacheManager.java \
        app/src/test/java/person/notfresh/readingshare/util/WebCacheManagerTest.java
git commit -m "feat(webcache): add listAllCaches, hasCache, deleteCache, totalSize"
```

---

## Task 9: CachingWebViewClient 骨架 + 工具方法

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/web/CachingWebViewClient.java`

- [ ] **Step 1: 创建骨架**

```java
package person.notfresh.readingshare.web;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import person.notfresh.readingshare.util.WebCacheManager;

public class CachingWebViewClient extends WebViewClient {

    private static final String TAG = "CachingWebView";
    private static final int CONNECT_TIMEOUT_S = 10;
    private static final int READ_TIMEOUT_S = 15;

    private final WebCacheManager cache;
    private final OkHttpClient http;
    private final Context appContext;

    @Nullable private String currentPageUrl;     // onPageStarted 拿到的 URL
    @Nullable private String currentPageHash;     // 缓存目录 hash

    public CachingWebViewClient(Context context) {
        this.appContext = context.getApplicationContext();
        this.cache = WebCacheManager.getInstance(appContext);
        this.http = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();
    }

    // --- 监听器:Activity 用来在离线/在线间切横条 ---
    public interface Listener {
        void onEnterOfflineMode(String cachedAtLabel);
        void onExitOfflineMode();
    }
    @Nullable private Listener listener;
    public void setListener(Listener l) { this.listener = l; }

    // --- 辅助 ---
    static String originOf(String url) {
        try {
            URI u = URI.create(url);
            String s = u.getScheme();
            String h = u.getHost();
            int p = u.getPort();
            if (s == null || h == null) return null;
            return p < 0 ? s + "://" + h + "/" : s + "://" + h + ":" + p + "/";
        } catch (Exception e) { return null; }
    }
}
```

- [ ] **Step 2: 编译通过**

```bash
cd c:/projects/duxiang-pack/duxiang-android
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
cd c:/projects/duxiang-pack/duxiang-android
git add app/src/main/java/person/notfresh/readingshare/web/CachingWebViewClient.java
git commit -m "feat(webcache): add CachingWebViewClient skeleton with OkHttp + Listener"
```

---

## Task 10: onPageStarted + 白名单门控 + shouldInterceptRequest 录制

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/web/CachingWebViewClient.java`

- [ ] **Step 1: 加 onPageStarted / 录制 / GET-only 门控**

```java
    @Override
    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        if (url == null) return;
        // 仅白名单 URL 介入
        if (!cache.isInWhitelist(url)) {
            currentPageUrl = null;
            currentPageHash = null;
            // 用户切到非白名单 URL,退出离线模式
            if (listener != null) listener.onExitOfflineMode();
            return;
        }
        currentPageUrl = WebCacheManager.canonicalUrl(url);
        currentPageHash = null; // 留待 onPageFinished 用 meta 校验
    }

    @Nullable
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        // 门控:非白名单 → 不介入
        if (currentPageUrl == null) return null;
        // 仅录制主框架 + GET
        String method = request.getMethod();
        if (method == null || !"GET".equalsIgnoreCase(method)) return null;
        String url = request.getUrl().toString();
        if (url.startsWith("data:") || url.startsWith("blob:")) return null;
        // Range 请求(视频/音频)不缓存
        if (request.getRequestHeaders() != null
            && request.getRequestHeaders().containsKey("Range")) return null;

        try {
            Request ok = buildOkHttpRequest(url, request);
            try (Response resp = http.newCall(ok).execute()) {
                if (!resp.isSuccessful()) {
                    return fallbackToCache(url);
                }
                ResponseBody body = resp.body();
                if (body == null) return fallbackToCache(url);
                String contentType = resp.header("Content-Type");
                // 视频/音频 → 不缓存,直接放行
                if (contentType != null) {
                    String ct = contentType.toLowerCase();
                    if (ct.startsWith("video/") || ct.startsWith("audio/")) return null;
                }
                byte[] bytes = body.bytes();
                persistSubResource(url, bytes, contentType);
                String mime = mimeOf(contentType, url);
                String encoding = encodingOf(contentType);
                return new WebResourceResponse(mime, encoding,
                    new ByteArrayInputStream(bytes));
            }
        } catch (Exception e) {
            Log.w(TAG, "interceptor failed for " + url + ": " + e.getMessage());
            return fallbackToCache(url);
        }
    }

    @Nullable
    private WebResourceResponse fallbackToCache(String url) {
        if (currentPageUrl == null) return null;
        byte[] bytes = cache.readResource(currentPageUrl, url);
        if (bytes == null) return null;
        String mime = mimeOf(null, url);
        return new WebResourceResponse(mime, "utf-8", new ByteArrayInputStream(bytes));
    }

    private Request buildOkHttpRequest(String url, WebResourceRequest src) {
        Request.Builder b = new Request.Builder().url(url).get();
        // 克隆 WebView 请求头
        Map<String, String> h = src.getRequestHeaders();
        if (h != null) {
            for (Map.Entry<String, String> e : h.entrySet()) {
                String k = e.getKey();
                if (k == null) continue;
                // OkHttp 禁止设置某些头
                if (k.equalsIgnoreCase("Content-Length")
                    || k.equalsIgnoreCase("Content-Type")
                    || k.equalsIgnoreCase("Host")) continue;
                b.header(k, e.getValue());
            }
        }
        // 注入 Cookie
        try {
            String cookie = CookieManager.getInstance().getCookie(url);
            if (!TextUtils.isEmpty(cookie)) b.header("Cookie", cookie);
        } catch (Exception ignored) {}
        return b.build();
    }

    private void persistSubResource(String url, byte[] bytes, @Nullable String contentType) {
        if (currentPageUrl == null) return;
        try {
            String ext = extensionOf(url, contentType);
            cache.saveResource(currentPageUrl, url, bytes, ext);
        } catch (IOException e) {
            Log.w(TAG, "saveResource failed: " + e.getMessage());
        }
    }

    static String extensionOf(String url, @Nullable String contentType) {
        // 优先用 URL 后缀
        int q = url.indexOf('?');
        String path = q >= 0 ? url.substring(0, q) : url;
        int slash = path.lastIndexOf('/');
        String tail = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = tail.lastIndexOf('.');
        if (dot > 0 && dot < tail.length() - 1) {
            return tail.substring(dot + 1).toLowerCase();
        }
        // fallback: 从 Content-Type 取
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.startsWith("image/")) return ct.substring(6);
            if (ct.startsWith("text/css")) return "css";
            if (ct.contains("javascript")) return "js";
        }
        return "bin";
    }

    static String mimeOf(@Nullable String contentType, String url) {
        if (contentType != null) {
            int semi = contentType.indexOf(';');
            String main = (semi >= 0 ? contentType.substring(0, semi) : contentType).trim();
            if (!main.isEmpty()) return main;
        }
        String ext = extensionOf(url, null);
        switch (ext) {
            case "html": return "text/html";
            case "css":  return "text/css";
            case "js":   return "application/javascript";
            case "json": return "application/json";
            case "png":  return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "gif":  return "image/gif";
            case "svg":  return "image/svg+xml";
            case "webp": return "image/webp";
            case "ico":  return "image/x-icon";
            case "woff": return "font/woff";
            case "woff2":return "font/woff2";
            case "ttf":  return "font/ttf";
            case "eot":  return "application/vnd.ms-fontobject";
            default:     return "application/octet-stream";
        }
    }

    static String encodingOf(@Nullable String contentType) {
        if (contentType == null) return "utf-8";
        int semi = contentType.indexOf(';');
        if (semi < 0) return "utf-8";
        String rest = contentType.substring(semi + 1);
        for (String part : rest.split(",")) {
            String t = part.trim().toLowerCase();
            if (t.startsWith("charset=")) return t.substring(8);
        }
        return "utf-8";
    }
```

- [ ] **Step 2: 编译**

```bash
cd c:/projects/duxiang-pack/duxiang-android
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
cd c:/projects/duxiang-pack/duxiang-android
git add app/src/main/java/person/notfresh/readingshare/web/CachingWebViewClient.java
git commit -m "feat(webcache): implement shouldInterceptRequest with OkHttp fetch + GET gate"
```

---

## Task 11: onPageFinished 写 meta + onReceivedError 回退

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/web/CachingWebViewClient.java`

- [ ] **Step 1: 加 onPageFinished**

```java
    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        if (url == null) return;
        if (!cache.isInWhitelist(url)) return;
        // currentPageUrl 已在 onPageStarted 设定;若 url 与之一致则可继续
        String canonical = WebCacheManager.canonicalUrl(url);
        if (canonical == null || !canonical.equals(currentPageUrl)) return;
        // 写 meta:title 取自 view.getTitle(),sizeBytes = 当前目录总大小
        String title = view.getTitle();
        long size = 0L;
        try {
            java.io.File dir = cache.getCacheDir(canonical);
            size = computeDirSize(dir);
        } catch (Exception ignored) {}
        try {
            cache.saveMeta(canonical, title, size);
        } catch (java.io.IOException e) {
            Log.w(TAG, "saveMeta failed: " + e.getMessage());
        }
        if (listener != null) listener.onExitOfflineMode();
    }

    private static long computeDirSize(java.io.File d) {
        if (!d.exists()) return 0L;
        if (d.isFile()) return d.length();
        long total = 0L;
        java.io.File[] cs = d.listFiles();
        if (cs != null) for (java.io.File c : cs) total += computeDirSize(c);
        return total;
    }
```

- [ ] **Step 2: 加 onReceivedError + file:// 回退**

```java
    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
        super.onReceivedError(view, request, error);
        if (request == null || !request.isForMainFrame()) return;
        String url = request.getUrl() == null ? null : request.getUrl().toString();
        if (url == null) return;
        if (!cache.isInWhitelist(url)) return;
        String canonical = WebCacheManager.canonicalUrl(url);
        if (canonical == null) return;
        if (!cache.hasCache(canonical)) return;
        // 进入 file:// 模式
        enterOfflineMode(view, canonical);
    }

    private void enterOfflineMode(WebView view, String canonicalUrl) {
        String html = cache.readMainHtml(canonicalUrl);
        if (html == null) return;
        String origin = originOf(canonicalUrl);
        if (origin == null) origin = canonicalUrl;
        // 注入 <base> 到 head 前面
        String injected = injectBaseHref(html, origin);
        view.loadDataWithBaseURL(origin, injected, "text/html", "utf-8", null);
        currentPageUrl = canonicalUrl; // 后续子资源走 file:// 模式也用此 url 查 resources.json
        WebCacheManager.CacheMeta meta = cache.readMeta(canonicalUrl);
        String label = "缓存模式";
        if (meta != null && meta.cachedAt > 0) {
            String date = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date(meta.cachedAt));
            label = "缓存模式 · " + date + " 抓取";
        }
        if (listener != null) listener.onEnterOfflineMode(label);
    }

    static String injectBaseHref(String html, String origin) {
        String baseTag = "<base href=\"" + origin + "\">";
        // 优先插到 <head> 之后
        int headEnd = html.toLowerCase().indexOf("</head>");
        if (headEnd >= 0) {
            return html.substring(0, headEnd) + baseTag + html.substring(headEnd);
        }
        // 否则插到开头
        return baseTag + html;
    }
```

- [ ] **Step 3: 编译**

```bash
cd c:/projects/duxiang-pack/duxiang-android
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
cd c:/projects/duxiang-pack/duxiang-android
git add app/src/main/java/person/notfresh/readingshare/web/CachingWebViewClient.java
git commit -m "feat(webcache): add onPageFinished meta write + onReceivedError file:// fallback"
```

---

## Task 12: 资源 - strings + banner 布局 + 菜单项

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/drawable/ic_cache_offline.xml`
- Modify: `app/src/main/res/layout/activity_webview.xml`
- Modify: `app/src/main/res/menu/webview_menu.xml`

- [ ] **Step 1: strings.xml 加 10 个字符串**

定位到现有 `</resources>` 之前,加:
```xml
    <string name="action_cache_page_add">缓存本页</string>
    <string name="action_cache_page_remove">取消缓存本页</string>
    <string name="banner_cache_mode">缓存模式</string>
    <string name="banner_retry">重试</string>
    <string name="offline_cache_title">离线缓存</string>
    <string name="offline_cache_empty">暂无离线缓存\n在 WebView 中点击「缓存本页」添加</string>
    <string name="offline_cache_clear_all">清空所有缓存</string>
    <string name="offline_cache_clear_all_confirm">确定删除所有离线缓存吗?</string>
    <string name="settings_offline_cache">离线缓存</string>
    <string name="settings_offline_cache_subtitle">%1$d 篇 · 共 %2$s</string>
    <string name="toast_added_to_cache">已加入缓存,正在录制…</string>
    <string name="toast_cache_complete">缓存完成 · %1$d 个资源</string>
    <string name="toast_removed_from_cache">已取消缓存</string>
    <string name="toast_no_network_offline">无网络,加载缓存版本</string>
    <string name="confirm_delete_cache">确定要删除此条缓存吗?</string>
```

- [ ] **Step 2: 创建横条图标 ic_cache_offline.xml(简化版云图标)**

在 `app/src/main/res/drawable/ic_cache_offline.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#5D4037">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M19,18H6c-2.21,0 -4,-1.79 -4,-4 0,-2.05 1.53,-3.76 3.56,-3.97C5.21,7.46 7.69,5 10.78,5c2.45,0 4.59,1.48 5.51,3.61C16.43,8.55 16.74,8.5 17,8.5c1.93,0 3.5,1.57 3.5,3.5 0,0.17 -0.02,0.34 -0.04,0.5H19c1.66,0 3,1.34 3,3s-1.34,3 -3,3zM3,18h16v2H3z"/>
</vector>
```

- [ ] **Step 3: 在 activity_webview.xml 加 banner**

定位到 `<androidx.appcompat.widget.Toolbar .../>` 之后、`<FrameLayout android:id="@+id/webview_container">` 之前,加:
```xml
    <LinearLayout
        android:id="@+id/cache_mode_banner"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="12dp"
        android:background="#FFF8E1"
        android:visibility="gone">

        <ImageView
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:src="@drawable/ic_cache_offline"
            android:contentDescription="@string/banner_cache_mode"/>

        <TextView
            android:id="@+id/cache_mode_banner_text"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="8dp"
            android:textColor="#5D4037"
            android:textSize="14sp"
            tools:text="缓存模式 · 2026-06-11 抓取"
            xmlns:tools="http://schemas.android.com/tools"/>

        <Button
            android:id="@+id/cache_mode_banner_retry"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/banner_retry"
            android:backgroundTint="#FFB300"
            android:textColor="#FFFFFF"
            android:minWidth="64dp"/>
    </LinearLayout>
```

- [ ] **Step 4: 在 webview_menu.xml 加 action_cache_page**

定位到 `</menu>` 之前,加:
```xml
    <item
        android:id="@+id/action_cache_page"
        android:title="@string/action_cache_page_add"
        app:showAsAction="never"/>
```

- [ ] **Step 5: 编译**

```bash
cd c:/projects/duxiang-pack/duxiang-android
./gradlew :app:assembleDebug 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
cd c:/projects/duxiang-pack/duxiang-android
git add app/src/main/res/values/strings.xml \
        app/src/main/res/drawable/ic_cache_offline.xml \
        app/src/main/res/layout/activity_webview.xml \
        app/src/main/res/menu/webview_menu.xml
git commit -m "feat(webcache): add banner layout, menu item, strings, drawable"
```

---

## Task 13: OfflineCacheFragment 列表页(布局 + 适配器 + Fragment)

**Files:**
- Create: `app/src/main/res/layout/fragment_offline_cache.xml`
- Create: `app/src/main/res/layout/item_offline_cache.xml`
- Create: `app/src/main/res/menu/offline_cache_menu.xml`
- Create: `app/src/main/java/person/notfresh/readingshare/ui/settings/OfflineCacheAdapter.java`
- Create: `app/src/main/java/person/notfresh/readingshare/ui/settings/OfflineCacheFragment.java`

- [ ] **Step 1: 创建 fragment_offline_cache.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/offline_cache_list"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="8dp"
        android:clipToPadding="false"/>

    <TextView
        android:id="@+id/offline_cache_empty"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:gravity="center"
        android:text="@string/offline_cache_empty"
        android:textSize="16sp"
        android:textColor="#888888"
        android:visibility="gone"/>
</FrameLayout>
```

- [ ] **Step 2: 创建 item_offline_cache.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="12dp"
    android:gravity="center_vertical"
    android:background="?android:attr/selectableItemBackground">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/item_cache_title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="16sp"
            android:textStyle="bold"
            android:maxLines="2"
            android:ellipsize="end"/>

        <TextView
            android:id="@+id/item_cache_subtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:textColor="#888888"
            android:layout_marginTop="4dp"/>
    </LinearLayout>

    <ImageButton
        android:id="@+id/item_cache_delete"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:background="?android:attr/selectableItemBackgroundBorderless"
        android:src="@drawable/ic_close"
        android:contentDescription="@string/delete"
        android:tint="#888888"/>
</LinearLayout>
```

- [ ] **Step 3: 创建 offline_cache_menu.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <item
        android:id="@+id/action_clear_all"
        android:title="@string/offline_cache_clear_all"
        app:showAsAction="never"/>
</menu>
```

- [ ] **Step 4: 创建 OfflineCacheAdapter.java**

```java
package person.notfresh.readingshare.ui.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import person.notfresh.readingshare.R;
import person.notfresh.readingshare.util.WebCacheManager;

public class OfflineCacheAdapter extends RecyclerView.Adapter<OfflineCacheAdapter.VH> {

    public interface OnItemClick {
        void onClick(WebCacheManager.CacheEntry entry);
        void onDelete(WebCacheManager.CacheEntry entry);
    }

    private List<WebCacheManager.CacheEntry> data = new ArrayList<>();
    private final OnItemClick listener;
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public OfflineCacheAdapter(OnItemClick listener) { this.listener = listener; }

    public void submit(List<WebCacheManager.CacheEntry> items) {
        this.data = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_offline_cache, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        WebCacheManager.CacheEntry e = data.get(position);
        h.title.setText(e.title == null || e.title.isEmpty() ? e.url : e.title);
        String date = e.cachedAt > 0 ? dateFmt.format(new Date(e.cachedAt)) : "未知日期";
        String size = formatSize(e.sizeBytes);
        h.subtitle.setText((e.host == null ? "" : e.host) + " · " + date + " · " + size);
        h.itemView.setOnClickListener(v -> listener.onClick(e));
        h.delete.setOnClickListener(v -> listener.onDelete(e));
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, subtitle;
        final ImageButton delete;
        VH(View v) {
            super(v);
            title = v.findViewById(R.id.item_cache_title);
            subtitle = v.findViewById(R.id.item_cache_subtitle);
            delete = v.findViewById(R.id.item_cache_delete);
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0);
    }
}
```

- [ ] **Step 5: 创建 OfflineCacheFragment.java**

```java
package person.notfresh.readingshare.ui.settings;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import person.notfresh.readingshare.R;
import person.notfresh.readingshare.WebViewActivity;
import person.notfresh.readingshare.util.WebCacheManager;

public class OfflineCacheFragment extends Fragment {

    private RecyclerView list;
    private TextView empty;
    private OfflineCacheAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        return inflater.inflate(R.layout.fragment_offline_cache, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        list = view.findViewById(R.id.offline_cache_list);
        empty = view.findViewById(R.id.offline_cache_empty);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new OfflineCacheAdapter(new OfflineCacheAdapter.OnItemClick() {
            @Override
            public void onClick(WebCacheManager.CacheEntry entry) {
                Intent i = new Intent(requireContext(), WebViewActivity.class);
                i.putExtra("url", entry.url);
                startActivity(i);
            }
            @Override
            public void onDelete(WebCacheManager.CacheEntry entry) {
                new AlertDialog.Builder(requireContext())
                    .setMessage(R.string.confirm_delete_cache)
                    .setPositiveButton(R.string.delete, (d, w) -> {
                        WebCacheManager.getInstance(requireContext())
                            .removeFromWhitelist(entry.url);
                        WebCacheManager.getInstance(requireContext())
                            .deleteCache(entry.url);
                        refresh();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            }
        });
        list.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        WebCacheManager mgr = WebCacheManager.getInstance(requireContext());
        List<WebCacheManager.CacheEntry> items = mgr.listAllCaches();
        adapter.submit(items);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.offline_cache_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_clear_all) {
            new AlertDialog.Builder(requireContext())
                .setMessage(R.string.offline_cache_clear_all_confirm)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    WebCacheManager mgr = WebCacheManager.getInstance(requireContext());
                    for (WebCacheManager.CacheEntry e : mgr.listAllCaches()) {
                        mgr.removeFromWhitelist(e.url);
                        mgr.deleteCache(e.url);
                    }
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
```

- [ ] **Step 6: 编译**

```bash
cd c:/projects/duxiang-pack/duxiang-android
./gradlew :app:assembleDebug 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
cd c:/projects/duxiang-pack/duxiang-android
git add app/src/main/res/layout/fragment_offline_cache.xml \
        app/src/main/res/layout/item_offline_cache.xml \
        app/src/main/res/menu/offline_cache_menu.xml \
        app/src/main/java/person/notfresh/readingshare/ui/settings/OfflineCacheAdapter.java \
        app/src/main/java/person/notfresh/readingshare/ui/settings/OfflineCacheFragment.java
git commit -m "feat(webcache): add OfflineCacheFragment with list and clear-all"
```

---

## Task 14: 设置入口 + 装配到 WebViewActivity

**Files:**
- Modify: `app/src/main/res/layout/fragment_slideshow.xml`
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java`
- Modify: `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java`

- [ ] **Step 1: 在 fragment_slideshow.xml 末尾加"离线缓存"入口行**

定位到 `</LinearLayout>` 之前(在搜索历史 max count TextInputLayout 之后),加:
```xml
        <LinearLayout
            android:id="@+id/offline_cache_entry"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="12dp"
            android:clickable="true"
            android:focusable="true"
            android:background="?android:attr/selectableItemBackground"
            android:layout_marginTop="24dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/settings_offline_cache"
                android:textSize="18sp"/>

            <TextView
                android:id="@+id/offline_cache_subtitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="12sp"
                android:textColor="#888888"
                android:layout_marginTop="4dp"/>
        </LinearLayout>
```

- [ ] **Step 2: 在 SettingFragment.java 末尾的 `onCreateView` 之后,加入口点击 + 刷新副标题**

在 SettingFragment.java 文件的 `onCreateView` 方法末尾(在 `return root;` 之前),加:
```java
        // 离线缓存入口
        View entry = root.findViewById(R.id.offline_cache_entry);
        TextView sub = root.findViewById(R.id.offline_cache_subtitle);
        if (entry != null && sub != null) {
            refreshOfflineCacheSubtitle(sub);
            entry.setOnClickListener(v -> {
                requireFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, new OfflineCacheFragment())
                    .addToBackStack(null)
                    .commit();
            });
        }
```

在类内其它方法附近加:
```java
    private void refreshOfflineCacheSubtitle(TextView sub) {
        person.notfresh.readingshare.util.WebCacheManager mgr =
            person.notfresh.readingshare.util.WebCacheManager.getInstance(requireContext());
        java.util.List<person.notfresh.readingshare.util.WebCacheManager.CacheEntry> items =
            mgr.listAllCaches();
        long total = mgr.totalSize();
        String sizeStr;
        if (total < 1024 * 1024) sizeStr = String.format(java.util.Locale.US, "%.1f KB", total / 1024.0);
        else sizeStr = String.format(java.util.Locale.US, "%.2f MB", total / 1024.0 / 1024.0);
        sub.setText(getString(R.string.settings_offline_cache_subtitle, items.size(), sizeStr));
    }

    @Override
    public void onResume() {
        super.onResume();
        View root = getView();
        if (root != null) {
            TextView sub = root.findViewById(R.id.offline_cache_subtitle);
            if (sub != null) refreshOfflineCacheSubtitle(sub);
        }
    }
```

- [ ] **Step 3: 改 WebViewActivity.java 装配 CachingWebViewClient**

定位到 `setupWebView()` 方法,找到 `webView.setWebViewClient(new WebViewClient() {...})` 块(第 461 行附近)。**整个内联匿名类替换为 CachingWebViewClient**,并加横条/菜单相关字段。改动:

1. 在类内加字段(放在 `private String currentUrl;` 之后):
```java
    private android.widget.LinearLayout cacheModeBanner;
    private android.widget.Button cacheModeBannerRetry;
    private android.widget.TextView cacheModeBannerText;
    private CachingWebViewClient cachingClient;
```

2. 在 `setupWebView()` 末尾追加:
```java
        // 装配缓存客户端
        cachingClient = new CachingWebViewClient(this);
        cachingClient.setListener(new CachingWebViewClient.Listener() {
            @Override public void onEnterOfflineMode(String label) {
                if (cacheModeBanner != null) {
                    cacheModeBanner.setVisibility(View.VISIBLE);
                    if (cacheModeBannerText != null) cacheModeBannerText.setText(label);
                }
            }
            @Override public void onExitOfflineMode() {
                if (cacheModeBanner != null) cacheModeBanner.setVisibility(View.GONE);
            }
        });
        webView.setWebViewClient(cachingClient);
```

3. 把原匿名 WebViewClient 中 `shouldOverrideUrlLoading` 和 `onPageFinished` 的逻辑迁到我们的客户端。但为了不破坏现有功能,**保留 CachingWebViewClient 的一个基类回调,委托给一个外部小辅助类**。

更简单做法:把原匿名 WebViewClient 的方法体直接复制到 `CachingWebViewClient` 的对应方法(`super.shouldOverrideUrlLoading(view, request)` 等)。**但 CachingWebViewClient 的职责已明确**——为避免膨胀,采取下面这个最小修改路径:

A. 在 `WebCacheManager` 类内(本计划 Task 11 完成后)补一个 `Listener` 接口,只暴露离线/在线切横条的能力
B. 把原匿名 WebViewClient 的 `shouldOverrideUrlLoading` 行为**通过 `WebViewActivity` 设置一个 `WebViewClient` 包装层**保留

**本计划采用方案 B(更稳)**:在 `WebViewActivity` 里包一层 `WebViewClient`,把 `shouldOverrideUrlLoading` 转发到现有的 `handleUrlOverride`,再委托给 `CachingWebViewClient`。代码:

```java
        // 包装层:保留原 shouldOverrideUrlLoading 行为,其它委托给 CachingWebViewClient
        webView.setWebViewClient(new WebViewClient() {
            private CachingWebViewClient delegate = cachingClient;

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlOverride(view, url, true);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request != null && request.getUrl() != null
                        ? request.getUrl().toString() : null;
                boolean isMainFrame = request == null || request.isForMainFrame();
                return handleUrlOverride(view, url, isMainFrame);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                delegate.onPageStarted(view, url, favicon);
            }

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return delegate.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                delegate.onPageFinished(view, url);
                // 保留原匿名 onPageFinished 中的标题提取、媒体监听 JS 注入
                injectTitleAndMediaListeners(view);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                delegate.onReceivedError(view, request, error);
            }
        });
```

4. 把原匿名 `onPageFinished` 方法体(标题提取 + 媒体监听 JS 注入,WebViewActivity.java 第 477-570 行)抽成 `injectTitleAndMediaListeners(WebView view)` 私有方法:

```java
    private void injectTitleAndMediaListeners(WebView view) {
        try {
            String t = view.getTitle();
            if (t != null && !t.trim().isEmpty()) {
                pageTitleCache = t.trim();
            }
            view.evaluateJavascript("document.title", value -> {
                try {
                    if (value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                        String jsTitle = value.substring(1, value.length() - 1);
                        if (!jsTitle.trim().isEmpty()) pageTitleCache = jsTitle.trim();
                    }
                } catch (Exception ignore) {}
            });
            String wechatTitleScript =
                "(function(){" +
                "  function txt(x){var y=(x||''); return y.replace(/\\s+/g,' ').trim();}" +
                "  var el=document.getElementById('activity-name')||document.querySelector('h1.rich_media_title, h2.rich_media_title');" +
                "  var t=el?txt(el.innerText||el.textContent):'';" +
                "  if(!t){var metas=document.getElementsByTagName('meta'); for(var i=0;i<metas.length;i++){ var p=metas[i].getAttribute('property'); if(p==='og:title'){ t=txt(metas[i].getAttribute('content')); break; } }}" +
                "  if(!t){t=txt(document.title);} return t;" +
                "})();";
            view.evaluateJavascript(wechatTitleScript, value -> {
                try {
                    if (value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                        String jsTitle = value.substring(1, value.length() - 1);
                        if (!jsTitle.trim().isEmpty()) pageTitleCache = jsTitle.trim();
                    }
                } catch (Exception ignore) {}
            });
            view.postDelayed(() -> view.evaluateJavascript(wechatTitleScript, value2 -> {
                try {
                    if (value2 != null && value2.length() >= 2 && value2.startsWith("\"") && value2.endsWith("\"")) {
                        String jsTitle2 = value2.substring(1, value2.length() - 1);
                        if (!jsTitle2.trim().isEmpty()) pageTitleCache = jsTitle2.trim();
                    }
                } catch (Exception ignore) {}
            }), 500);
        } catch (Exception ignore) {}
        view.evaluateJavascript(
            "(function(){" +
            "if(window.__mediaListenerAttached)return;" +
            "window.__mediaListenerAttached=true;" +
            "window.__activeMedia=new Set();" +
            "function notify(p){if(window.AndroidMediaInterface)window.AndroidMediaInterface.setMediaPlaying(p);}" +
            "function attach(el){" +
            "if(el.__listenersAttached)return;el.__listenersAttached=true;" +
            "el.addEventListener('play',function(){console.log('__media play event');window.__activeMedia.add(el);notify(true);});" +
            "el.addEventListener('pause',function(){console.log('__media pause event');window.__activeMedia.delete(el);if(window.__activeMedia.size===0)notify(false);});" +
            "el.addEventListener('ended',function(){console.log('__media ended event');window.__activeMedia.delete(el);if(window.__activeMedia.size===0)notify(false);})" +
            "}" +
            "var existing=[].slice.call(document.getElementsByTagName('audio'))" +
            ".concat([].slice.call(document.getElementsByTagName('video')));" +
            "existing.forEach(attach);" +
            "existing.forEach(function(el){if(!el.paused&&!el.ended){window.__activeMedia.add(el);}});" +
            "if(window.__activeMedia.size>0)notify(true);" +
            "var obs=new MutationObserver(function(ms){ms.forEach(function(m){" +
            "m.addedNodes.forEach(function(n){" +
            "if(n.tagName==='AUDIO'||n.tagName==='VIDEO'){attach(n);}" +
            "else if(n.querySelectorAll){[].slice.call(n.querySelectorAll('audio,video')).forEach(attach);}" +
            "});});});" +
            "obs.observe(document.body||document.documentElement,{childList:true,subtree:true});" +
            "})()", null);
    }
```

5. 在 `onCreate` 内 `setupWebView()` 之后,加横条引用和重试按钮处理:
```java
        cacheModeBanner = findViewById(R.id.cache_mode_banner);
        cacheModeBannerText = findViewById(R.id.cache_mode_banner_text);
        cacheModeBannerRetry = findViewById(R.id.cache_mode_banner_retry);
        if (cacheModeBannerRetry != null) {
            cacheModeBannerRetry.setOnClickListener(v -> {
                if (webView != null && currentUrl != null) {
                    webView.loadUrl(currentUrl);
                }
            });
        }
```

6. 在 `onCreateOptionsMenu` 后(在文件内)加 `onPrepareOptionsMenu` 动态切换菜单文案:
```java
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_cache_page);
        if (item != null && currentUrl != null) {
            person.notfresh.readingshare.util.WebCacheManager mgr =
                person.notfresh.readingshare.util.WebCacheManager.getInstance(this);
            item.setTitle(mgr.isInWhitelist(currentUrl)
                ? R.string.action_cache_page_remove
                : R.string.action_cache_page_add);
        }
        return super.onPrepareOptionsMenu(menu);
    }
```

7. 在 `onOptionsItemSelected` 的 `else if` 链末尾(`else if (item.getItemId() == R.id.action_add_tag)` 之后)加:
```java
        } else if (item.getItemId() == R.id.action_cache_page) {
            if (webView == null || currentUrl == null) return true;
            person.notfresh.readingshare.util.WebCacheManager mgr =
                person.notfresh.readingshare.util.WebCacheManager.getInstance(this);
            if (mgr.isInWhitelist(currentUrl)) {
                mgr.removeFromWhitelist(currentUrl);
                mgr.deleteCache(currentUrl);
                Toast.makeText(this, R.string.toast_removed_from_cache, Toast.LENGTH_SHORT).show();
            } else {
                mgr.addToWhitelist(currentUrl);
                Toast.makeText(this, R.string.toast_added_to_cache, Toast.LENGTH_SHORT).show();
                webView.reload();
            }
            invalidateOptionsMenu();
            return true;
        }
```

- [ ] **Step 4: 编译**

```bash
cd c:/projects/duxiang-pack/duxiang-android
./gradlew :app:assembleDebug 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
cd c:/projects/duxiang-pack/duxiang-android
git add app/src/main/res/layout/fragment_slideshow.xml \
        app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java \
        app/src/main/java/person/notfresh/readingshare/WebViewActivity.java
git commit -m "feat(webcache): wire CachingWebViewClient into WebViewActivity + add settings entry"
```

---

## Task 15: 手动验收(无代码改动)

执行下述清单,每条记录结论(在 PR 描述中)。

- [ ] **Step 1: 安装并启动 app**

```bash
cd c:/projects/duxiang-pack/duxiang-android
./gradlew :app:installDebug
adb shell am start -n person.notfresh.readingshare/.WebViewActivity --es url "https://mp.weixin.qq.com/s/test-article"
```

- [ ] **Step 2: 验证非白名单 URL 不介入**

- 打开任意 URL(没在白名单)
- 开飞行模式
- 重新打开 → 走默认错误页,**不显示**缓存模式黄条

- [ ] **Step 3: 验证首次录制**

- 打开一个微信文章
- 菜单点击「缓存本页」
- Toast: "已加入缓存,正在录制…"
- 看到"已加入缓存,正在录制…" 后页面 reload,完成后 Toast 应消失(无需"缓存完成"toast,我们 spec 没要求在录制完成时给 toast;如果 onPageFinished 没到,会一直保持)
- 进 `adb shell run-as person.notfresh.readingshare ls files/web_cache/` 应能看到 hash 目录

- [ ] **Step 4: 验证离线回退 + 横条**

- 缓存某个微信文章后,打开飞行模式
- 重新打开该 URL
- 顶部出现黄色横条 "缓存模式 · 2026-06-11 抓取"
- 页面文字/图片正常显示(对比原始有差异可接受)

- [ ] **Step 5: 验证重试按钮**

- 在离线模式下点击横条右侧"重试"
- 仍显示横条(无网络)但 reload 重新尝试

- [ ] **Step 6: 验证取消缓存**

- 打开已缓存的 URL,菜单显示"取消缓存本页"
- 点击 → 目录删除(`adb shell run-as ... ls files/web_cache/` 看不到 hash)
- 重新打开飞行模式 → 默认错误页,无横条

- [ ] **Step 7: 验证设置列表页**

- 缓存 2-3 个不同 URL
- 进设置 → 看到 "离线缓存 > 3 篇 · 共 X.XX MB"
- 点击进入 → 列表显示 3 条,按日期倒序
- 点击其中一条 → 启动 WebViewActivity
- 点某条删除按钮 → 确认对话框 → 删除 → 列表刷新
- 菜单 "清空所有缓存" → 确认 → 列表清空

- [ ] **Step 8: 验证 POST 不被拦截**

- 打开一个登录页(POST 表单)
- 不缓存
- 确认登录提交流程未受影响

- [ ] **Step 9: 验证 meta.json 损坏不崩**

```bash
adb shell run-as person.notfresh.readingshare sh -c \
  'echo "{ broken json" > files/web_cache/<some-hash>/meta.json'
```
- 进设置 → 离线缓存列表,该条降级显示 "未知标题 · 0 B"
- 能正常删除不崩

- [ ] **Step 10: 记录所有结果到 PR 描述**

把 9 个 step 的结论写成 PR 描述里的 "Test plan" 部分,任何 ❌ 都不能合并。

---

## Self-Review

**1. Spec 覆盖度**

| Spec 章节 | 对应 Task |
|---|---|
| §2 关键决策(7 个) | 散落在各 Task 的实现选择中,无遗漏 |
| §3 架构 3.1 新增组件(WebCacheManager/CachingWebViewClient/OfflineCacheFragment) | Task 2-8, Task 9-11, Task 13 |
| §3.2 修改现有代码 | Task 1, 12, 14 |
| §4 磁盘布局 | Task 6, 7, 8 隐式 |
| §5 场景 1-4 数据流 | Task 10 (录制), Task 11 (回退), Task 14 (装配) |
| §6 边界情况(13 条) | 全部对应实现,canonicalUrl/POST/Range/meta 损坏/重定向均有测试或代码 |
| §7 UI 规范 | Task 12 (banner+menu), Task 13 (list page), Task 14 (settings entry) |
| §8 测试策略 | Task 2-8 (单元), Task 15 (手动) |
| §9 风险与限制 | 在 PR 描述中说明,不在代码内 |

**2. 占位符扫描**

- 所有 `<...>` 占位符都在代码上下文里有明确值(比如 `<baseUrl>`,`<hash>`,`%1$d` 等是 Android 字符串模板语法)
- 没有"TBD"、"TODO"、"later"
- 没有 "similar to Task N" 重复省略
- 步骤 1 写失败测试,步骤 3 实现,步骤 4 验证,步骤 5 commit —— TDD 模式贯穿

**3. 类型一致性**

- `WebCacheManager.canonicalUrl(String) → String` 在 Task 2 定义,后续 Task 4/5/6/7/8/10/11 全部使用
- `WebCacheManager.getCacheDir(String) → File` 在 Task 3 定义
- `WebCacheManager.saveResource(String pageUrl, String resUrl, byte[] data, String ext)` 参数顺序在 Task 7 定义,Task 10 调用顺序一致
- `CacheMeta.url/title/cachedAt/sizeBytes` 字段在 Task 6 定义,Task 8/11/13/14 使用
- `CacheEntry.url/title/cachedAt/sizeBytes/host` 字段在 Task 8 定义,Task 13 适配器使用一致
- `CachingWebViewClient.Listener.onEnterOfflineMode(String label)` / `onExitOfflineMode()` 在 Task 9 定义,Task 11 触发,Task 14 装配

**一处需要确认**:`Task 14 Step 3` 的方案 B(包装层)增加了 `WebViewActivity` 内一个匿名 `WebViewClient`,把 `onPageFinished` 拆出来变成 `injectTitleAndMediaListeners(view)` 私有方法。这增加了少量重构。**好处**:`CachingWebViewClient` 保持单职责(只关心缓存/拦截),`WebViewActivity` 的标题提取+媒体监听 JS 注入保持原状不被污染。**风险**:抽方法时若漏粘代码会丢功能。**缓解**:`./gradlew :app:assembleDebug` 编译会过,但**强烈建议冒烟**:安装 debug apk,打开任意网页,验证标题更新和音频后台播放功能仍工作(用 Task 15 Step 1-2 顺手做)。
