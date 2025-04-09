# 奇怪的问题
2025-04-09 07:17:43.495 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  onSwiped: position=3
2025-04-09 07:17:43.495 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  swap item id is 485
2025-04-09 07:17:47.522 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  onSwiped: position=3
2025-04-09 07:17:47.523 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  swap item id is 485
2025-04-09 07:17:47.785 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  正在归档项目ID: 485, 标题: 西部大道小区比较
2025-04-09 07:17:47.798 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  已添加到归档数据库, 现在从主数据库删除
2025-04-09 07:17:47.798 26265-26265 HomeFragment            person.notfresh.readingshare         D  onDeleteLink: 西部大道小区比较, link id 1
2025-04-09 07:17:47.901 26265-26265 HomeFragment            person.notfresh.readingshare         D  链接删除完成，UI已更新
2025-04-09 07:17:47.901 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  已从主数据库删除，操作完成
2025-04-09 07:17:47.907 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  归档成功: 西部大道小区比较
2025-04-09 07:18:32.765 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  onSwiped: position=3
2025-04-09 07:18:32.766 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  swap item id is 485
2025-04-09 07:18:32.970 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  正在归档项目ID: 485, 标题: 西部大道小区比较
2025-04-09 07:18:32.981 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  已添加到归档数据库, 现在从主数据库删除
2025-04-09 07:18:32.981 26265-26265 HomeFragment            person.notfresh.readingshare         D  onDeleteLink: 西部大道小区比较, link id 2
2025-04-09 07:18:33.081 26265-26265 HomeFragment            person.notfresh.readingshare         D  链接删除完成，UI已更新
2025-04-09 07:18:33.081 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  已从主数据库删除，操作完成
2025-04-09 07:18:33.086 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  归档成功: 西部大道小区比较
2025-04-09 07:18:42.180 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  onSwiped: position=3
2025-04-09 07:18:42.181 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  swap item id is 485
2025-04-09 07:18:42.857 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  正在归档项目ID: 485, 标题: 西部大道小区比较
2025-04-09 07:18:42.874 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  已添加到归档数据库, 现在从主数据库删除
2025-04-09 07:18:42.874 26265-26265 HomeFragment            person.notfresh.readingshare         D  onDeleteLink: 西部大道小区比较, link id 3
2025-04-09 07:18:43.556 26265-26265 HomeFragment            person.notfresh.readingshare         D  链接删除完成，UI已更新
2025-04-09 07:18:43.556 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  已从主数据库删除，操作完成
2025-04-09 07:18:43.573 26265-26265 SwipeActionsHelperX     person.notfresh.readingshare         D  归档成功: 西部大道小区比较

问题出在哪？
setCurrentSwipedItem((LinkItem) item); 可能因为强转而丢失了信息。

public void setCurrentSwipedItem(LinkItem item) {
this.currentSwipedItem = item;
Log.d(TAG+"X", "Item: " + (item != null ? item.getId() + ", " +item.getTitle() : "null"));
}

其实setCurrentSwipedItem((LinkItem) item); 这一行也没问题。不知道那个onDelete调用的怎么就不对劲了。

void onDeleteLink(LinkItem link); 有可能是这个方法，传参的时候，id被改变了。

2025-04-09 07:39:48.630  1824-1824  SwipeActionsHelperX     person.notfresh.readingshare         D  正在归档项目ID: 485, 标题: 西部大道小区比较
2025-04-09 07:39:48.651  1824-1824  SwipeActionsHelperX     person.notfresh.readingshare         D  已添加到归档数据库, 现在从主数据库删除
2025-04-09 07:39:48.652  1824-1824  HomeFragment            person.notfresh.readingshare         D  deleteLink: + link id 10
2025-04-09 07:39:49.426  1824-1824  SwipeActionsHelperX     person.notfresh.readingshare         D  已从主数据库删除，操作完成
2025-04-09 07:39:49.443  1824-1824  SwipeActionsHelperX     person.notfresh.readingshare         D  归档成功: 西部大道小区比较

传参也发生了变化？？

我发现了其中一个问题，就是

// 添加到归档数据库
adapter.archiveOneItem(currentSwipedItem);
Log.d("SwipeActionsHelperX", "已添加到归档数据库, 现在从主数据库删除");

adapter.archiveOneItem(currentSwipedItem);这个方法改变了传入参数的id

