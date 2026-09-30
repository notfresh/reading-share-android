# Project Knowledge Index

> 最后更新: 2026-09-23 · 共 3 项沉淀 · 基于 commit `055dfc9` (新增事件日志模块)

## 杂项 (Misc)
- [SubjectFragment 加载流程](subject-fragment-load-flow.md) — 进入主题标签时的视图创建、数据加载、记忆恢复与销毁释放全过程
- [DbConnection 单例优化](db-connection-singleton.md) — 通过进程级单例 + Application 预热,把启动期 SQLite getWritableDatabase 从 3 次压到 1 次,含完整发现过程和原理
- [Event Log 模块](event-log-module.md) — 新增独立插件式事件日志模块 `eventlog`,含 10 阶段讨论框架、关键决策清单、反复出现的元模式与具体错误教训;设计约定以 `PROTOCOL.md` §2.5 / §3.3 / §10.x 为准

---
*新增探索时,挑已有分类追加,或新建一个分类 section。保持分类少而稳。*
