你阅读一下分享的功能,看看是否完备，不要修改代码，列出对应的代码位置

-----------------------

I'll scan for all share-related implementations and intent-filters, then list their exact code locations for review.

状态更新
- 我查完全局所有分享相关代码与菜单项，汇总入口、接收端与文件分享实现位置，未修改任何代码。

### 接收分享（进入本应用）
- `AndroidManifest.xml` 中 `SEND/SEND_MULTIPLE` 过滤器（之前已列出）
- 处理逻辑：
```228:314:app/src/main/java/person/notfresh/readingshare/MainActivity.java
private void handleIntent(Intent intent) { ... if (Intent.ACTION_SEND.equals(action) ... }
```

### 统一封装（当前主通道）
- 文本/文件分享工具：
```1:92:app/src/main/java/person/notfresh/readingshare/util/ShareUtil.java
// shareLinkAsText(...), shareLinkAsFile(...), excludeSelfFromChooser(...)
```

### 列表页面中的分享（从应用发出）
- 适配器长按“分享单条”调用：
```331:343:app/src/main/java/person/notfresh/readingshare/adapter/LinksAdapter.java
case 3:
    ShareUtil.shareLinkAsText(context, item);
    return true;
```

- 其他页面/适配器直接构造分享 Intent（文本/文件）
```991:1062:app/src/main/java/person/notfresh/readingshare/ui/tag/TagsFragment.java
Intent shareIntent = new Intent(Intent.ACTION_SEND); ... startActivity(Intent.createChooser(...));
Uri uri = FileProvider.getUriForFile(...); shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
```
```297:338:app/src/main/java/person/notfresh/readingshare/ui/home/HomeFragment.java
Intent shareIntent = new Intent(Intent.ACTION_SEND); ... Intent chooserIntent = Intent.createChooser(...);
Uri uri = FileProvider.getUriForFile(...); shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
```
```278:319:app/src/main/java/person/notfresh/readingshare/ui/archive/ArchiveFragment.java
Intent shareIntent = new Intent(Intent.ACTION_SEND); ... 文件分享同上
```
```540:545:app/src/main/java/person/notfresh/readingshare/ui/rss/RSSFragment.java
Intent shareIntent = new Intent(Intent.ACTION_SEND); startActivity(Intent.createChooser(...));
```
```103:106:app/src/main/java/person/notfresh/readingshare/ui/rss/RSSAdapter.java
Intent shareIntent = new Intent(Intent.ACTION_SEND); startActivity(Intent.createChooser(...));
```
```80:84:app/src/main/java/person/notfresh/readingshare/adapter/PublishedTagsAdapter.java
Intent shareIntent = new Intent(Intent.ACTION_SEND); Intent.createChooser(...)
```
```282:292:app/src/main/java/person/notfresh/readingshare/ui/settings/SettingFragment.java
Uri uri = FileProvider.getUriForFile(...); Intent shareIntent = new Intent(Intent.ACTION_SEND); putExtra(EXTRA_STREAM, uri);
```



### 完备性检查结论
- 文本分享：完备（多处直接构造 + `ShareUtil.shareLinkAsText` 封装）。
- 文件分享：完备（多处直接构造，`ShareUtil.shareLinkAsFile` 也已提供，可统一迁移）。
- 接收分享：完备（Manifest 过滤器 + `MainActivity.handleIntent` 保存入库）。
- 统一性：部分页面仍手写分享逻辑，尚未统一迁移到 `ShareUtil`（可选后续收敛）。
- WebView 内分享入口：菜单已留位，尚未实现具体分享行为。