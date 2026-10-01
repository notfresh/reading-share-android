# 实现方案 - 主题详情页"选链接" UI 优化

## 背景

`SubjectDetailActivity` 是主题详情页。通过右上角菜单的"+"(`R.id.action_add_item`)进入 `AddSubjectItemDialog`,在该 Dialog 内点"选择链接"按钮时,弹出的选择器只用一个 `AlertDialog.setItems(String[])` 把所有链接标题铺成纯文本数组(无搜索、无样式、无视觉一致性)。在链接超过几十条之后基本无法用。

历史占位: `res/layout/dialog_select_link.xml` 已经是空 `RecyclerView` 占位,但实际没人接进 `AddSubjectItemDialog.java:201` 的 `showSelectLinkDialog()`。

## 目标

替换"丑弹窗"为可搜索、可分页、可识已收录状态的链接选择器;沿用方案 B 落库路径(选链接不直接入库,回到 `AddSubjectItemDialog` 继续填备注/图片,统一"保存"入库)。

## 设计要点(SE 原则)

- **单一来源**:SQL 拼装收敛到 `LinkDao.buildSearchQuery(spec)` 一处;搜索 spec / 范围枚举独立到 util 包,不污染 DAO;布局独立 `item_link_picker.xml`,不与主页 `item_link.xml` 耦合。
- **命名单元**:搜索语法解析只在 `LinkSearchSpec.parse()`;`SelectLinkAdapter` 只渲染 + 置灰,零 DAO 依赖;`SelectLinkDialog` 才是协调者。
- **表面最小**:不引入"未来可能用到"的抽象;不持久化任何状态。

## 模块拆分

```
util/LinkSearchSpec.java        值对象: query/scope/limit,负责 "title:/url:/tag:" 前缀解析
util/LinkSearchScope.java       枚举: ALL/TITLE/URL/TAG
db/LinkDao.java                 新增 getLinksPage(offset, limit) + searchLinks(LinkSearchSpec)
                                searchLinks 重构: buildSearchQuery(spec) + buildSearchArgs(spec) 唯一拼装点
db/LinkDbHelper.java            数据库版本 16 -> 17,加 3 索引:
                                  idx_links_title / idx_links_timestamp / idx_tags_name
                                  onCreate + onUpgrade<17 双路径(IF NOT EXISTS 幂等)
ui/subject/SelectLinkAdapter.java  纯 RecyclerView.Adapter,接收 List<LinkItem> + Set<Long> disabledIds
ui/subject/SelectLinkDialog.java   DialogFragment 协调者:
                                  - 默认分页: 每页 100,到底自动 +5 触发下一页
                                  - 搜索: 一次性最多 200,搜索时无"加载更多"按钮
                                  - 已收录集合: onCreate + onResume 同步一次,用于置灰
                                  - 点击未收录: 回调 OnLinkSelectedListener 给上游
                                  - 点击已收录: dialog 内直接 SubjectDao.deleteSubjectItem(itemId)
                                    (删除的是 SubjectItem,不是 linkId,先遍历拿到 itemId)
res/layout/item_link_picker.xml    picker 专用极简: 标题 + URL + 标签条;不携带 click_count/收藏/置顶
res/layout/dialog_select_link.xml  搜索 EditText + RecyclerView + "加载更多" + 空状态
```

## 关键决策(用户确认)

| 项 | 选择 |
|---|---|
| 搜索框是否带历史下拉 | 否 |
| 已收录链接视觉 | 置灰(alpha 0.45),限定为当前 subjectId |
| 默认列表 | 前 100 条,滚动到底 +5 自动加载下一页 |
| 搜索范围 | 全库 SQL LIKE,不分页,最多 200 条 |
| 点击未收录 | 选这条 → 回调 `OnLinkSelectedListener` → 回 AddSubjectItemDialog |
| 点击已收录 | 取消收录(直接调 `SubjectDao.deleteSubjectItem(itemId)`)|
| 搜索语法 | `title:foo` / `tag:bar` / `url:baz` 限定维度;无前缀默认 ALL |

## 行为细节

1. 进入 `SelectLinkDialog` 立即拉前 100 条 + 同步已收录集合 → 置灰命中项。
2. EditText 改动 → 若 query 变化,重置 offset + hasMore → 空 query 走分页,非空走搜索。
3. 列表滚动至 `adapter.getItemCount() - 5` 触发下一页 IO;搜索态不分页。
4. 点击未收录 → 立即回调 + dismiss;点击已收录 → 异步取消收录 + 刷新置灰,留在 dialog。
5. `onResume` 重读已收录集合(用户在主页面可能改了),刷新置灰。

## 改动文件

**新增:**
- `app/src/main/java/person/notfresh/readingshare/util/LinkSearchSpec.java`
- `app/src/main/java/person/notfresh/readingshare/util/LinkSearchScope.java`
- `app/src/main/java/person/notfresh/readingshare/ui/subject/SelectLinkAdapter.java`
- `app/src/main/java/person/notfresh/readingshare/ui/subject/SelectLinkDialog.java`
- `app/src/main/res/layout/item_link_picker.xml`

**修改(过去文件,改动前已向用户报告):**
- `app/src/main/java/person/notfresh/readingshare/db/LinkDao.java` — 新增方法,旧方法零改动;`searchLinks` 重构为单一 builder
- `app/src/main/java/person/notfresh/readingshare/db/LinkDbHelper.java` — DB 版本 16→17,索引;`onCreate` + `onUpgrade<17`
- `app/src/main/java/person/notfresh/readingshare/ui/subject/AddSubjectItemDialog.java` — `showSelectLinkDialog()` 25 行替换,加 `setOnLinkSelectedListener` 桥接
- `app/src/main/res/layout/dialog_select_link.xml` — 整体重写(原文件是空占位)
- `AGENTS.md` — 加入 SE 原则 + 编译纪律 + 搜索/分页硬规则

## 验证

- 编译:`./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL in 12s`
- 测试用例(需在设备手测):
  - 默认进入 → 前 100 条按 timestamp DESC,可见
  - 滚动到底 → 自动加载下一页
  - 输 `foo` → 全字段 OR 搜索,搜索时禁用加载更多
  - 输 `title:foo` / `tag:bar` / `url:baz` → 限定维度
  - 已收录链接置灰(限当前 subjectId)
  - 点已收录 → 取消收录并 Toast 提示
  - 点未收录 → 回填到 AddSubjectItemDialog,继续填备注/图片,点"保存"统一入库

## 数据量与性能

- 用户当前 3000+ 链接,SQL LIKE + `idx_links_title` 命中索引;`idx_links_timestamp` 让默认排序走索引
- 默认列表 IO 走 `getLinksPage(offset, 100)`,单次 100 行;翻页最多 30 次才能拉满 3000 条
- 搜索 IO 走单次 SQL LIMIT 200,数据库侧完成,UI 不做 in-memory filter

## 未做

- 未 git commit / push(等你下一步指令)
- 未在设备实机验证(无法自测 APK)