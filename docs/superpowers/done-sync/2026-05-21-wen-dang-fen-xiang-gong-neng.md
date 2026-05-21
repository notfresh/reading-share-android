# 文档分享功能 (2026-05-21)

## 发现 (What we discovered)
- DocumentFragment 是文档列表，与 HomeFragment 结构类似
- DocumentAdapter 已有长按菜单机制，使用 PopupMenu 实现
- ShareUtil 是为 LinkItem（链接）设计的，不适合 DocumentItem（文档文件）
- 文档存储在 `getFilesDir()/documents/` 目录

## 学到 (What we learned)
- FileProvider 需要在 file_paths.xml 中配置路径才能访问文件
- documents 目录需要用 `files-path` 配置（不是 external-files-path）
- DocumentType 枚举需要 getMimeType() 方法来设置正确的 MIME 类型
- 文档分享直接调起系统分享面板即可，无需复杂逻辑

## 完成 (What we accomplished)
- 在 document_item_menu.xml 添加了分享菜单项
- 在 DocumentAdapter 添加了 shareDocument() 方法
- 在 DocumentType 添加了 getMimeType() 方法
- 修复了 file_paths.xml 配置，解决了 FileProvider 路径问题
- 编译通过，功能验证可用

