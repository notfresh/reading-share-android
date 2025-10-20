# NetworkOnMainThreadException 错误分析与解决方案

## 问题描述

在使用WebViewActivity的提取内容功能时，应用抛出了`android.os.NetworkOnMainThreadException`异常。这个错误表明我们在主线程（UI线程）中执行了网络请求操作。

## 错误日志

```
android.os.NetworkOnMainThreadException
```

## 问题定位

通过代码分析，问题出现在`WebViewActivity.java`文件中处理`action_extract_content`菜单项点击事件的部分：

```java
else if (item.getItemId() == R.id.action_extract_content) {
    Log.d("WebViewActivityMenu", "click extract");
    String url = webView != null ? webView.getUrl() : "";
    if(url.equals("")){
        Log.d("WebViewActivityMenu", "null url");
        Toast.makeText(this, "Url为空", Toast.LENGTH_SHORT).show();
        return true;
    }
    Log.d("WebViewActivityMenu", "@1");
    try {
        Log.d("WebViewActivityMenu", "@1.01");
        //String content = "ABCD";//CrawlUtil.getArticleByUrl(url);
        String content = CrawlUtil.getArticleByUrl(url);  // 问题代码行
        Log.d("WebViewActivityMenu", "@2");
        Log.d("WebViewActivityMenu", content);
        // 将提取的内容写入剪贴板
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("extracted_content", content);
        clipboard.setPrimaryClip(clip);
        Log.d("WebViewActivityMenu", "@3");
        Toast.makeText(this, "提取Web内容完成，已经写入剪切板", Toast.LENGTH_SHORT).show();

    } catch (Exception e) {
        Toast.makeText(this, "提取失败", Toast.LENGTH_SHORT).show();
        Log.d("WebViewActivityMenu", e.toString());
    }
    // TODO: 实现提取Web内容的具体逻辑
    return true;
}
```

在`CrawlUtil.getArticleByUrl(url)`方法中，使用了Jsoup库进行网络请求：

```java
public static String getArticleByUrl(String url) throws IOException {
    // 使用jsoup连接并获取页面，添加更多请求头模拟真实浏览器
    Document doc = Jsoup.connect(url)
            .userAgent(Config.UserAgent.CHROME_USER_AGENT)
            .header("Accept", Config.Headers.ACCEPT)
            .header("Accept-Language", Config.Headers.ACCEPT_LANGUAGE)
            .header("Accept-Encoding", Config.Headers.ACCEPT_ENCODING)
            .header("Connection", Config.Headers.CONNECTION)
            .header("Cache-Control", Config.Headers.CACHE_CONTROL)
            .timeout(Config.Network.WECHAT_CRAWL_TIMEOUT)
            .maxBodySize(Config.Misc.MAX_BODY_SIZE)
            .followRedirects(Config.Misc.FOLLOW_REDIRECTS)
            .get();  // 网络请求在此执行
    // 提取文章内容
    return "AB1234";
    // ... 其他内容提取逻辑
}
```

## 问题原因

Android系统为了防止UI卡顿和ANR（Application Not Responding）错误，禁止在主线程中执行耗时操作，包括网络请求。当我们在主线程中执行`Jsoup.connect(url).get()`时，就会抛出`NetworkOnMainThreadException`异常。

## 解决方案

需要将网络请求操作移到后台线程执行。可以使用以下几种方式：

### 方案一：使用ExecutorService（推荐）

修改`WebViewActivity.java`中的代码：

```java
else if (item.getItemId() == R.id.action_extract_content) {
    Log.d("WebViewActivityMenu", "click extract");
    String url = webView != null ? webView.getUrl() : "";
    if(url.equals("")){
        Log.d("WebViewActivityMenu", "null url");
        Toast.makeText(this, "Url为空", Toast.LENGTH_SHORT).show();
        return true;
    }
    
    // 显示加载提示
    Toast.makeText(this, "正在提取内容...", Toast.LENGTH_SHORT).show();
    
    // 在后台线程执行网络请求
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Handler handler = new Handler(Looper.getMainLooper());
    
    executor.execute(() -> {
        try {
            String content = CrawlUtil.getArticleByUrl(url);
            
            // 在主线程更新UI
            handler.post(() -> {
                // 将提取的内容写入剪贴板
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("extracted_content", content);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "提取Web内容完成，已经写入剪切板", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            // 在主线程显示错误信息
            handler.post(() -> {
                Toast.makeText(this, "提取失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e("WebViewActivityMenu", "提取失败", e);
            });
        }
    });
    return true;
}
```

### 方案二：使用AsyncTask（已废弃，不推荐）

### 方案三：使用现代Android开发方式 - Kotlin协程或LiveData

如果项目迁移到Kotlin，可以使用协程来处理：

```kotlin
// 在Kotlin中使用协程的示例
lifecycleScope.launch {
    try {
        val content = withContext(Dispatchers.IO) {
            CrawlUtil.getArticleByUrl(url)
        }
        // 更新UI
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("extracted_content", content);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "提取Web内容完成，已经写入剪切板", Toast.LENGTH_SHORT).show();
    } catch (e: Exception) {
        Toast.makeText(this, "提取失败: ${e.message}", Toast.LENGTH_SHORT).show();
        Log.e("WebViewActivityMenu", "提取失败", e);
    }
}
```

## 配置参数

根据`Config.java`中的配置：

```java
public static class Network {
    // 连接超时时间（毫秒）
    public static final int CONNECT_TIMEOUT = 3 * 1000;
    // 默认读取超时时间（毫秒）
    public static final int DEFAULT_READ_TIMEOUT = 10 * 1000;
    // 微信文章抓取超时时间（毫秒）
    public static final int WECHAT_CRAWL_TIMEOUT = 10000;
}

public static class Misc {
    // 不限制响应大小
    public static final int MAX_BODY_SIZE = 0;
    // 允许重定向
    public static final boolean FOLLOW_REDIRECTS = true;
}
```

## 验证解决方案

修改代码后，需要重新编译并运行应用，执行以下步骤验证：

1. 打开包含网页的WebViewActivity
2. 点击菜单中的"提取Web内容"选项
3. 观察是否还会出现NetworkOnMainThreadException错误
4. 检查内容是否成功提取并复制到剪贴板

## 预防措施

1. 所有网络请求都应该在后台线程执行
2. 使用适当的线程管理机制（如ExecutorService、协程等）
3. 在进行网络请求时显示加载提示，提升用户体验
4. 正确处理网络请求异常情况
5. 遵循Android开发最佳实践，避免在主线程执行耗时操作