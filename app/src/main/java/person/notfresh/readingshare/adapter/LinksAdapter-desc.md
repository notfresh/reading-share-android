I opened `LinksAdapter.java` and reviewed key sections for behavior, risks, and quick fixes.

### 作用与结构
- 显示分组的链接列表（置顶+按日期分组），提供点击打开、长按菜单（编辑、删除、分享、置顶、多选、获取摘要）、标签与备注管理、搜索过滤、阅读次数统计、导出分享等。

### 关键实现位置
- 分组与展平数据
```195:222:app/src/main/java/person/notfresh/readingshare/adapter/LinksAdapter.java
public void setGroupedLinks(Map<String, List<LinkItem>> groupedLinks) {
    items.clear();
    originalItems.clear();
    this.groupedLinks = groupedLinks;
    if (!pinnedLinks.isEmpty()) {
        items.add("置顶");
        items.addAll(pinnedLinks);
    }
    for (Map.Entry<String, List<LinkItem>> entry : groupedLinks.entrySet()) {
        items.add(entry.getKey());
        List<LinkItem> normalLinks = entry.getValue().stream()
                .filter(link -> !pinnedLinks.contains(link))
                .collect(Collectors.toList());
        items.addAll(normalLinks);
    }
    originalItems.addAll(items);
    notifyDataSetChanged();
}
```

- 列表项点击行为（打开链接/唤起外部 app）
```820:842:app/src/main/java/person/notfresh/readingshare/adapter/LinksAdapter.java
itemView.setOnClickListener(v -> {
    try {
        String url = adapter.extractRealUrl(item.getUrl());
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (url.contains("b23.tv")) {
            handleBilibiliLink(url);
        } else if (url.contains("weread.qq.com")) {
            handleWereadLink(url);
        } else {
          
            adapter.openLink(v.getContext(), url, getAdapterPosition());
        }
    } catch (Exception e) {
        Snackbar.make(v, "无法打开此链接: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
    }
});
```

- 长按菜单与分享
```320:343:app/src/main/java/person/notfresh/readingshare/adapter/LinksAdapter.java
popup.getMenu().add(0, 3, 0, "分享单条");
// ...
case 3:
    shareAsText(item);
    return true;
```
```494:514:app/src/main/java/person/notfresh/readingshare/adapter/LinksAdapter.java
private void shareAsText(LinkItem item) {
    StringBuilder shareText = new StringBuilder();
    shareText.append(item.getTitle()).append("\n");
    if (item.getRemark() != null && !item.getRemark().isEmpty()) {
        shareText.append(item.getRemark()).append("\n");
    }
    shareText.append(item.getUrl());
    Intent shareIntent = new Intent(Intent.ACTION_SEND);
    shareIntent.setType("text/plain");
    shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
    Intent chooserIntent = Intent.createChooser(shareIntent, "分享到");
    String myPackageName = context.getPackageName();
    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, 
        new ComponentName[]{new ComponentName(myPackageName, myPackageName + ".MainActivity")});
    context.startActivity(chooserIntent);
}
```

- 导出为文件再分享
```516:540:app/src/main/java/person/notfresh/readingshare/adapter/LinksAdapter.java
private void shareAsFile(LinkItem item, boolean isJson) {
    // ... ExportUtil.exportToJson/CSV → FileProvider → ACTION_SEND with EXTRA_STREAM
}
```

- 阅读次数统计与展示
```694:707:app/src/main/java/person/notfresh/readingshare/adapter/LinksAdapter.java
linkItem.incrementClickCount();
linkDao.updateClickCount(linkItem.getId(), linkItem.getClickCount());
notifyItemChanged(position);
Intent intent = new Intent(context, WebViewActivity.class);
```
```791:799:app/src/main/java/person/notfresh/readingshare/adapter/LinksAdapter.java
// 显示阅读次数
clickCountText.setText("阅读 " + item.getClickCount() + " 次");
```

### 风险与改进建议（高优先）
- 静态 `context` 可能导致内存泄漏
```71:72:app/src/main/java/person/notfresh/readingshare/adapter/LinksAdapter.java
private static Context context;  // 添加 context 引用
```
  - 建议：改为非静态成员，或使用 `WeakReference<Context>`；静态方法里避免直接持有 Activity 上下文。

- 文本设置潜在 bug（资源 ID 误用）
```776:782:app/src/main/java/person/notfresh/readingshare/adapter/LinksAdapter.java
clickCountText.setText(item.getClickCount());
```
  - 这里会走 `setText(int resId)` 重载，可能崩溃或显示异常。虽被 try/catch 吃掉，但应修正为 `String.valueOf(item.getClickCount())` 或删除这段重复设置（下方已正确设置“阅读 X 次”）。

- 数据库连接未全量关闭
```447:453:app/src/main/java/person/notfresh/readingshare/adapter/LinksAdapter.java
if (linkDao != null) {
    linkDao.close();
}
```
  - `archiveLinkDao` 在 `commonInit` 打开，但未在分离时关闭；建议同步关闭以避免泄漏。

- 选择集一致性取决于 `LinkItem.equals/hashCode`
  - `selectedItems`、`pinnedLinks.contains(link)` 等依赖对象相等判断。确保 `LinkItem` 正确实现 `equals/hashCode`（基于唯一 ID），否则选择/置顶判定可能失效。

- 频繁 `notifyDataSetChanged()`
  - 多处直接全量刷新，体验与性能可优化为粒度更新（`notifyItemInserted/Removed/Changed` 或 `DiffUtil`）。

- 分享 chooser 的排除自身逻辑兼容性
  - 使用 `Intent.EXTRA_EXCLUDE_COMPONENTS` 排除自身：
```509:513:app/src/main/java/person/notfresh/readingshare/adapter/LinksAdapter.java
chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, 
    new ComponentName[]{new ComponentName(myPackageName, myPackageName + ".MainActivity")});
```
  - 该额外字段在较新系统上可用，旧设备会忽略。可接受，但如需全面兼容，考虑自定义 chooser 列表或在旧版上宽容退化。

### 其他可优化点（中优先）
- `onDetachedFromRecyclerView` 仅关闭主库；建议统一管理 `LinkDao` 生命周期（主库与归档库）。
- `setGroupedLinks` 每次重建全部 `items`；如数据量大，建议按 section 变化进行增量更新。
- `filter` 过滤后未更新 `links` 主列表，仅更新 `items`；若有依赖 `getLinks()` 的逻辑，需确认预期一致性。
- 打开外部 app 的分支：
  - `handleWereadLink` 未设置包名，能更宽容 fallback，但可能命中特定 URL scheme 未安装时异常，已捕获并回退浏览器，OK。
  - `handleBilibiliLink` 先 scheme 再浏览器，容错合理。

如果你愿意，我可以直接提交以下小改动：
- 移除/修正 `clickCountText.setText(item.getClickCount())`。
- 关闭 `archiveLinkDao`。
- 去掉静态 `context`，改为实例 `Context` 或 `WeakReference<Context>`。 

状态更新
- 我已通读 `LinksAdapter`，标出了分享、点击、分组、统计等关键代码段，并给出几处高优先级问题与修复建议。


# >>> 第一次修改 >>>

- 新增 `util/ShareUtil.java`
  - 抽取分享逻辑为通用工具：
    - `shareLinkAsText(Context, LinkItem)`: 构建文本分享 Intent，调起 chooser，并“尽力”排除自身 `MainActivity`。
    - `shareLinkAsFile(Context, LinkItem, boolean isJson)`: 复用 `ExportUtil` 导出 JSON/CSV，`FileProvider` 生成 Uri，构建文件分享 Intent，调起 chooser。
  - 封装公共方法 `excludeSelfFromChooser(...)` 以集中处理排除自身和标志位。

- 重构 `adapter/LinksAdapter.java`
  - 长按菜单“分享单条”改为调用 `ShareUtil.shareLinkAsText(context, item)`（如你当前光标所示的第 343 行）。
  - 原本适配器内的 `shareAsText(...)` 与 `shareAsFile(...)` 实现移除，避免重复代码。
  - 引入 `import person.notfresh.readingshare.util.ShareUtil;`。

- 未变更
  - `ExportUtil` 保持“只负责导出文件”的职责；分享流程统一迁移到 `ShareUtil`。
  - 现有 Manifest、FileProvider 配置未改。

- 影响
  - 功能不变，结构更清晰：分享逻辑集中、可复用、便于未来在其他 Adapter/Fragment 中统一调用。
  - 兼容性与体验与原实现一致（“排除自身”仍为最佳努力）。

