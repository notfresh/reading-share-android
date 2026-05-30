# 洗牌功能实现 (2026-05-30)

## 发现 (What we discovered)
- HomeFragment 有两条数据加载路径：无标签筛选走 `refreshLinksList()`，有标签筛选走 `updateContentBySelectedTags()`
- `LinksAdapter.setGroupedLinks()` 使用 `TreeMap` 按日期分组，日期顺序被固定
- Android 菜单按钮需要在 `onCreateOptionsMenu` 中用 `findItem` 获取引用

## 学到 (What we learned)
- 洗牌后如果还用 `TreeMap` 按日期分组，变相又按日期排序，效果不明显
- 扁平化显示（无日期分组）更适合洗牌模式
- `groupedLinks = null` 可以作为洗牌模式的标记，但需要注意 NPE 问题
- `Collections.shuffle()` 直接修改列表顺序，洗牌后无需再次排序

## 完成 (What we accomplished)
- 实现了洗牌模式：点击洗牌按钮进入，链接混排显示
- 实现了重新洗牌：在洗牌模式下再次点击洗牌按钮
- 实现了退出洗牌：点击退出洗牌按钮或添加新链接后自动退出
- 置顶链接始终固定在最前面，不参与洗牌
- 洗牌模式状态持久化到 SharedPreferences