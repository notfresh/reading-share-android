# SubjectFragment 加载流程 (2026-08-19)

> 一句话总结: 进入主题标签时,通过 SharedPreferences 记忆上次查看的主题,从 SubjectDao 读取数据并更新 RecyclerView / toolbar / 空状态。

## 关键文件

- `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectFragment.java:48` — `onCreate`,初始化 `SubjectEntryManager`
- `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectFragment.java:55` — `onCreateView`,加载 XML、打开 DAO、绑定 Adapter、设置拖拽
- `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectFragment.java:73` — `onResume`,真正触发数据加载的入口
- `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectFragment.java:105` — `openRememberedSubjectOrList`,记忆恢复 + 弹列表回退
- `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectFragment.java:110` — `loadSubject`,核心数据加载收敛点
- `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectFragment.java:95` — `onDestroyView`,释放 DAO 与 Adapter
- `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectEntryManager.java:36` — `getDefaultEntryTarget` / `getLastViewedSubjectId`,记忆读取
- `app/src/main/java/person/notfresh/readingshare/ui/subject/SubjectEntryManager.java:60` — `saveLastViewedSubject`,记忆写入
- `app/src/main/res/layout/fragment_subject.xml:1` — 布局,FrameLayout 套 RecyclerView + 空态 TextView

## 流程

1. 用户在 `MainActivity` 底部导航选中"主题"项,系统取回或新建 `SubjectFragment` 实例。
2. `onCreate`: `setHasOptionsMenu(true)`;用 `SharedPreferencesStorage` 包装 `SharedPreferences("subject_entry_prefs")`,新建 `SubjectEntryManager`。
3. `onCreateView`: 加载 `fragment_subject.xml`;`findViewById` 拿 `recyclerView` / `textEmpty`;`SubjectDao.open()` + `LinkDao.open()` 打开数据库;创建 `SubjectItemAdapter` 并注册三个回调接口(点击/编辑/操作);设置 `LinearLayoutManager`;`setupDragAndDrop` 挂 `ItemTouchHelper`(UP|DOWN,完成时重写 `orderIndex` 落库)。
4. `onResume`: 若 `subject != null` 重读该主题(用于编辑对话框返回后同步);否则调用 `openRememberedSubjectOrList`。
5. `openRememberedSubjectOrList`: 从 `entryManager.getLastViewedSubjectId()` 拿 ID;若为空或 `loadSubject` 返回 false,弹 `SubjectListDialog` 让用户选。
6. `loadSubject(id)`: `subjectDao.getSubjectById` 读主题及嵌套 `SubjectItem`;`adapter.setItems` + `updateEmptyState` + `updateToolbarTitle` + `entryManager.saveLastViewedSubject(id)`;读不到则清空状态返回 false。
7. `onCreateOptionsMenu` / `onOptionsItemSelected`: 灌入 `subject_detail_menu`(返回主页 / 新增主题项 / 主题列表 / 设置),分发到对应弹窗或 `MainActivity.navigateTo`。
8. `onDestroyView`: `adapter.close()` + `subjectDao.close()` + `linkDao.close()`,置空防内存泄漏。

## 坑

- `SubjectFragment.java:59` — DAO 在 `onCreateView` 才打开,意味着每次视图重建都会重复 open/close,Fragment 频繁重建场景下有连接开销。
- `SubjectFragment.java:149` — `updateToolbarTitle` 直接 `requireActivity().findViewById(R.id.toolbar)`,假设宿主 Activity 暴露了此 view;若改用 `ActionBar` 或换宿主会失效。
- `SubjectFragment.java:75` — `onResume` 中 `subject != null` 分支总会重读一遍,如果对话框保存回调里已经 reload 过,会重复查一次 DB(数据量小可忽略)。
- `SubjectFragment.java:107` — 记忆恢复失败时立即弹 `SubjectListDialog`,但 `listDialogShown` 仅在同一 `onResume` 内保护,如果 DialogFragment 的 dismiss 回调异常会卡住状态,导致后续无法再弹。
- `SubjectFragment.java:217` — `clearView` 全表重写 `orderIndex` 并逐条 updateSubjectItem,主题项多时是 N 次写库,后续优化空间在批更新。
- `SubjectFragment.java:227` — `onCollectLink` 用 `linkDao.getAllLinks()` 全表扫描去重,链接多时性能下降,应改用按 URL 查询。

## 关联

- 相关: 主题项编辑/新增弹窗 (`AddSubjectItemDialog`)、归档项管理 (`SubjectArchivedItemsDialog`)、主题列表 (`SubjectListDialog`)
- 待探索: `SubjectDao` 的事务模型与批量更新接口、`WebViewActivity` 的上下文传递消费方式