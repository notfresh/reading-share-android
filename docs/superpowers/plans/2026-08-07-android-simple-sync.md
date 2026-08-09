# Android 轻量级双向同步实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 端实现轻量级双向同步客户端，与网页插件端和服务端配合工作

**Architecture:** 创建 SimpleSyncManager 处理同步逻辑，SyncApiClient 处理网络请求和 AES-256-CBC 加密解密，修改 SettingFragment 添加同步配置 UI

**Tech Stack:** Android, Java, HttpURLConnection, AES-256-CBC, SHA-256

---

## 文件结构

- **创建**: `app/src/main/java/person/notfresh/readingshare/sync/SyncApiClient.java` - 网络请求 + 加密解密
- **创建**: `app/src/main/java/person/notfresh/readingshare/sync/SimpleSyncManager.java` - 同步核心逻辑
- **修改**: `app/src/main/res/layout/fragment_slideshow.xml` - 添加同步密钥输入框和同步按钮
- **修改**: `app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java` - 添加同步逻辑

---

## Task 1: 创建 SyncApiClient

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/sync/SyncApiClient.java`

- [ ] **Step 1: 创建 SyncApiClient 类框架**

```java
package person.notfresh.readingshare.sync;

import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SyncApiClient {
    private static final String TAG = "SyncApiClient";
    private static final String AUTH_TOKEN = "DUXIANG";

    private String serverUrl;
    private String secretKey;

    public SyncApiClient(String serverUrl, String secretKey) {
        this.serverUrl = serverUrl;
        this.secretKey = secretKey;
    }
}
```

- [ ] **Step 2: 添加 shouldEncrypt 方法**

```java
public boolean shouldEncrypt() {
    try {
        URL url = new URL(serverUrl);
        String host = url.getHost();
        return !(host.equals("localhost") || 
                 host.equals("127.0.0.1") || 
                 host.startsWith("192.168."));
    } catch (Exception e) {
        return true; // 默认加密
    }
}
```

- [ ] **Step 3: 添加密钥派生方法**

```java
private byte[] deriveKey(String password) {
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(password.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
        Log.e(TAG, "密钥派生失败", e);
        return null;
    }
}
```

- [ ] **Step 4: 添加 AES 加密方法**

```java
private String encryptAES(String plaintext) {
    try {
        byte[] key = deriveKey(secretKey);
        if (key == null) return null;

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // IV + 加密内容
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

        return Base64.encodeToString(combined, Base64.NO_WRAP);
    } catch (Exception e) {
        Log.e(TAG, "加密失败", e);
        return null;
    }
}
```

- [ ] **Step 5: 添加 AES 解密方法**

```java
private String decryptAES(String encryptedData) {
    try {
        byte[] key = deriveKey(secretKey);
        if (key == null) return null;

        byte[] combined = Base64.decode(encryptedData, Base64.NO_WRAP);
        byte[] iv = new byte[16];
        byte[] encrypted = new byte[combined.length - 16];
        System.arraycopy(combined, 0, iv, 0, 16);
        System.arraycopy(combined, 16, encrypted, 0, encrypted.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decrypted = cipher.doFinal(encrypted);

        return new String(decrypted, StandardCharsets.UTF_8);
    } catch (Exception e) {
        Log.e(TAG, "解密失败", e);
        return null;
    }
}
```

- [ ] **Step 6: 添加 sync 方法**

```java
public String sync(String requestJson) {
    try {
        URL url = new URL(serverUrl + "/api/sync");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        boolean encrypt = shouldEncrypt();
        String authHeader;
        String bodyJson;

        if (encrypt) {
            // 加密认证头
            authHeader = "Bearer " + encryptAES(AUTH_TOKEN);
            // 加密请求体
            bodyJson = "{\"encrypted\": " + requestJson + "}";
        } else {
            authHeader = "Bearer " + AUTH_TOKEN;
            bodyJson = requestJson;
        }

        conn.setRequestProperty("Authorization", authHeader);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = bodyJson.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        Log.d(TAG, "同步响应码: " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_OK) {
            String response = readResponse(conn);
            if (encrypt) {
                return decryptResponse(response);
            }
            return response;
        } else {
            Log.e(TAG, "同步失败: " + responseCode);
            return null;
        }
    } catch (Exception e) {
        Log.e(TAG, "同步请求失败", e);
        return null;
    }
}
```

- [ ] **Step 7: 添加辅助方法**

```java
private String readResponse(HttpURLConnection conn) {
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        return response.toString();
    } catch (Exception e) {
        Log.e(TAG, "读取响应失败", e);
        return null;
    }
}

private String decryptResponse(String response) {
    try {
        org.json.JSONObject obj = new org.json.JSONObject(response);
        if (obj.has("encrypted")) {
            org.json.JSONObject encrypted = obj.getJSONObject("encrypted");
            String data = encrypted.getString("data");
            return decryptAES(data);
        }
        return response;
    } catch (Exception e) {
        Log.e(TAG, "解析加密响应失败", e);
        return null;
    }
}
```

---

## Task 2: 创建 SimpleSyncManager

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/sync/SimpleSyncManager.java`

- [ ] **Step 1: 创建 SimpleSyncManager 类框架**

```java
package person.notfresh.readingshare.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.model.LinkItem;

public class SimpleSyncManager {
    private static final String TAG = "SimpleSyncManager";
    private static final String PREFS_NAME = "simple_sync";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_SECRET_KEY = "secret_key";

    private Context context;
    private LinkDao linkDao;

    public SimpleSyncManager(Context context) {
        this.context = context;
        this.linkDao = new LinkDao(context);
        this.linkDao.open();
    }

    public void close() {
        if (linkDao != null) {
            linkDao.close();
        }
    }
}
```

- [ ] **Step 2: 添加配置存取方法**

```java
public void saveConfig(String serverUrl, String secretKey) {
    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    prefs.edit()
        .putString(KEY_SERVER_URL, serverUrl)
        .putString(KEY_SECRET_KEY, secretKey)
        .apply();
    Log.d(TAG, "保存同步配置: " + serverUrl);
}

public String getServerUrl() {
    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    return prefs.getString(KEY_SERVER_URL, "");
}

public String getSecretKey() {
    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    return prefs.getString(KEY_SECRET_KEY, "");
}

public boolean hasConfig() {
    String url = getServerUrl();
    String key = getSecretKey();
    return url != null && !url.isEmpty() && key != null && !key.isEmpty();
}
```

- [ ] **Step 3: 添加 hash 计算方法**

```java
public static String computeHash(String title, String url) {
    try {
        String input = title + "::" + url;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    } catch (Exception e) {
        Log.e(TAG, "计算Hash失败", e);
        return null;
    }
}
```

- [ ] **Step 4: 添加获取本地链接 hash 集合方法**

```java
private Set<String> getLocalLinkHashes() {
    Set<String> hashes = new HashSet<>();
    List<LinkItem> allLinks = linkDao.getAllLinks();
    for (LinkItem link : allLinks) {
        String hash = computeHash(link.getTitle(), link.getUrl());
        if (hash != null) {
            hashes.add(hash);
        }
    }
    return hashes;
}
```

- [ ] **Step 5: 添加核心同步方法**

```java
public SyncResult sync() {
    if (!hasConfig()) {
        return SyncResult.failure("请先配置同步服务器地址和密钥");
    }

    try {
        String serverUrl = getServerUrl();
        String secretKey = getSecretKey();
        SyncApiClient client = new SyncApiClient(serverUrl, secretKey);

        // 1. 获取本地所有链接
        List<LinkItem> localLinks = linkDao.getAllLinks();
        Log.d(TAG, "本地链接数: " + localLinks.size());

        // 2. 构建请求 JSON
        JSONArray linksArray = new JSONArray();
        for (LinkItem link : localLinks) {
            JSONObject linkObj = new JSONObject();
            linkObj.put("title", link.getTitle());
            linkObj.put("url", link.getUrl());
            linkObj.put("hash", computeHash(link.getTitle(), link.getUrl()));
            linksArray.put(linkObj);
        }

        JSONObject requestJson = new JSONObject();
        requestJson.put("links", linksArray);

        // 3. 发送同步请求
        String responseJson = client.sync(requestJson.toString());
        if (responseJson == null) {
            return SyncResult.failure("同步请求失败，请检查网络和服务器配置");
        }

        // 4. 解析响应，获取服务器返回的链接
        JSONObject response = new JSONObject(responseJson);
        JSONArray serverLinks = response.getJSONArray("links");

        // 5. 获取本地已有的 hash
        Set<String> localHashes = getLocalLinkHashes();

        // 6. 创建本地不存在的链接
        int downloadedCount = 0;
        for (int i = 0; i < serverLinks.length(); i++) {
            JSONObject serverLink = serverLinks.getJSONObject(i);
            String hash = serverLink.getString("hash");

            if (!localHashes.contains(hash)) {
                String title = serverLink.getString("title");
                String url = serverLink.getString("url");

                LinkItem newLink = new LinkItem(title, url, "sync", "", "");
                linkDao.insertLink(newLink);
                downloadedCount++;
                Log.d(TAG, "创建新链接: " + title);
            }
        }

        Log.d(TAG, "同步完成: 上传 " + localLinks.size() + " 条, 下载 " + downloadedCount + " 条");
        return SyncResult.success(localLinks.size(), downloadedCount);

    } catch (Exception e) {
        Log.e(TAG, "同步失败", e);
        return SyncResult.failure("同步失败: " + e.getMessage());
    }
}
```

- [ ] **Step 6: 创建 SyncResult 内部类**

```java
public static class SyncResult {
    public boolean success;
    public String message;
    public int uploadedCount;
    public int downloadedCount;

    private SyncResult(boolean success, String message, int uploaded, int downloaded) {
        this.success = success;
        this.message = message;
        this.uploadedCount = uploaded;
        this.downloadedCount = downloaded;
    }

    public static SyncResult success(int uploaded, int downloaded) {
        return new SyncResult(true, "同步成功", uploaded, downloaded);
    }

    public static SyncResult failure(String message) {
        return new SyncResult(false, message, 0, 0);
    }
}
```

---

## Task 3: 修改布局文件

**Files:**
- Modify: `app/src/main/res/layout/fragment_slideshow.xml`

- [ ] **Step 1: 在服务器设置区域后添加同步配置区域**

在 `</com.google.android.material.textfield.TextInputLayout>` (server_url_input 之后) 和 `阅读模式` TextView 之间添加：

```xml
<!-- 同步设置区域 -->
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="同步设置"
    android:textSize="18sp"
    android:layout_marginTop="24dp"
    android:layout_marginBottom="8dp"/>

<com.google.android.material.textfield.TextInputLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="8dp"
    android:hint="同步密钥">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/sync_secret_key_input"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="textPassword"
        android:maxLines="1" />

</com.google.android.material.textfield.TextInputLayout>

<Button
    android:id="@+id/button_sync"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="开始同步"
    android:layout_marginTop="16dp"/>

<TextView
    android:id="@+id/sync_status_text"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text=""
    android:layout_marginTop="8dp"
    android:textColor="#808080"/>
```

---

## Task 4: 修改 SettingFragment

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java`

- [ ] **Step 1: 添加 import 语句**

```java
import person.notfresh.readingshare.sync.SimpleSyncManager;
```

- [ ] **Step 2: 添加成员变量**

```java
private TextInputEditText syncSecretKeyInput;
private SimpleSyncManager syncManager;
private TextView syncStatusText;
```

- [ ] **Step 3: 在 onCreateView 中初始化同步管理器**

在 `linkDao.open();` 之后添加：

```java
// 初始化同步管理器
syncManager = new SimpleSyncManager(requireContext());
```

- [ ] **Step 4: 在 onCreateView 中绑定密钥输入框和状态文本**

在 `serverUrlInput` 初始化代码之后添加：

```java
// 初始化同步密钥输入框
syncSecretKeyInput = root.findViewById(R.id.sync_secret_key_input);
syncStatusText = root.findViewById(R.id.sync_status_text);

// 加载保存的密钥
String savedKey = syncManager.getSecretKey();
syncSecretKeyInput.setText(savedKey);

// 监听密钥输入框焦点变化
syncSecretKeyInput.setOnFocusChangeListener((v, hasFocus) -> {
    if (!hasFocus) {
        String newKey = syncSecretKeyInput.getText().toString().trim();
        String serverUrl = serverUrlInput.getText().toString().trim();
        if (!newKey.isEmpty() && !serverUrl.isEmpty()) {
            syncManager.saveConfig(serverUrl, newKey);
        }
    }
});
```

- [ ] **Step 5: 添加同步按钮点击事件**

在 `button_export` 点击事件之后添加：

```java
// 同步按钮点击事件
root.findViewById(R.id.button_sync).setOnClickListener(v -> {
    // 先保存当前配置
    String serverUrl = serverUrlInput.getText().toString().trim();
    String secretKey = syncSecretKeyInput.getText().toString().trim();

    if (serverUrl.isEmpty() || secretKey.isEmpty()) {
        syncStatusText.setText("请填写服务器地址和同步密钥");
        return;
    }

    syncManager.saveConfig(serverUrl, secretKey);
    performSync();
});
```

- [ ] **Step 6: 添加 performSync 方法**

```java
private void performSync() {
    syncStatusText.setText("正在同步...");

    new Thread(() -> {
        SimpleSyncManager.SyncResult result = syncManager.sync();

        requireActivity().runOnUiThread(() -> {
            if (result.success) {
                String status = String.format("同步成功: 上传 %d 条, 下载 %d 条",
                    result.uploadedCount, result.downloadedCount);
                syncStatusText.setText(status);
            } else {
                syncStatusText.setText("同步失败: " + result.message);
            }
        });
    }).start();
}
```

- [ ] **Step 7: 在 onDestroyView 中关闭同步管理器**

```java
@Override
public void onDestroyView() {
    super.onDestroyView();
    if (linkDao != null) {
        linkDao.close();
    }
    if (syncManager != null) {
        syncManager.close();
    }
}
```

---

## Task 5: 提交代码

- [ ] **Step 1: 提交代码**

```bash
git add app/src/main/java/person/notfresh/readingshare/sync/ app/src/main/res/layout/fragment_slideshow.xml app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java
git commit -m "feat(sync): 添加轻量级双向同步功能

- 添加 SyncApiClient 处理网络请求和 AES-256-CBC 加密
- 添加 SimpleSyncManager 处理同步核心逻辑
- 在设置页面添加同步密钥输入和同步按钮
- 支持局域网明文传输，公网加密传输"
```

---

## 实现验证检查清单

- [ ] SyncApiClient 正确实现了 shouldEncrypt() 局域网判断
- [ ] SyncApiClient 正确实现了 AES-256-CBC 加密和解密
- [ ] SyncApiClient 正确设置了 Authorization 请求头
- [ ] SimpleSyncManager 正确计算 SHA256(title + "::" + url) hash
- [ ] SimpleSyncManager 正确实现了双向同步逻辑
- [ ] SettingFragment 正确保存和加载同步配置
- [ ] 同步结果正确显示上传数和下载数
