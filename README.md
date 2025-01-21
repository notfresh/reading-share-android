# 读享

一个用于管理和组织链接的 Android 应用，支持标签管理、链接分类和快速分享功能。

## 下载安装

APK 文件位置：`app/release/app-release-版本号.apk`

## 主要功能

1. 链接管理

   - 自动捕获分享的链接
   - 按日期自动分组显示
   - 支持编辑和删除链接
   - 支持多选操作
2. 标签系统

   - 支持多标签筛选
   - 支持无标签内容查看
   - 标签状态自动保存
   - 快速添加新标签
3. 分享功能

   - 支持接收其他应用分享的链接
   - 智能解析分享内容（如小红书分享文本）
   - 支持打开原始应用查看内容

## 项目结构

    app/src/main/
    ├── java/person/notfresh/myapplication/
    │   ├── MainActivity.java              # 主活动
    │   ├── adapter/
    │   │   └── LinksAdapter.java         # 链接列表适配器
    │   ├── model/
    │   │   └── LinkItem.java             # 链接数据模型
    │   └── ui/gallery/
    │       ├── TagsFragment.java         # 标签页面
    │       ├── GalleryFragment.java      # 画廊页面
    │       └── GalleryViewModel.java     # 画廊视图模型
    └── res/
        └── layout/
            └── dialog_add_tag.xml        # 添加标签对话框布局

## 技术特点

### 架构设计

- 使用 Fragment 进行界面管理
- 采用 DAO 模式进行数据访问
- 使用 SQLite 数据库存储

### UI 组件

- RecyclerView 实现列表展示
- FlexboxLayout 实现标签流式布局
- Material Design 风格的界面元素

### 数据存储

- SQLite 数据库管理链接和标签
- SharedPreferences 保存用户选择

## 开发环境

- Android Studio
- minSdkVersion: 24
- targetSdkVersion: 33
- Java 版本: 1.8

## 数据库结构

### 表结构

1. links 表：存储链接信息

   - id: 主键
   - title: 链接标题
   - url: 链接地址
   - timestamp: 创建时间
   - source_app: 来源应用
   - original_intent: 原始意图
   - target_activity: 目标活动
2. tags 表：存储标签信息

   - tag_id: 主键
   - tag_name: 标签名称
3. link_tags 表：链接和标签的关联表

   - link_id: 链接ID
   - tag_id: 标签ID

## 使用说明

1. 添加链接

   - 从其他应用分享内容到本应用
   - 应用自动解析并保存链接信息
2. 标签管理

   - 点击顶部"+"按钮添加新标签
   - 点击标签进行筛选
   - 多选标签可查看多个标签下的内容
3. 链接操作

   - 长按链接进入多选模式
   - 点击链接打开原始内容
   - 使用菜单进行编辑或删除操作

## 注意事项

1. 数据备份

   - 定期备份数据库文件
   - 保持足够的存储空间
2. 权限说明

   - 需要网络访问权限
   - 需要读写存储权限

## 贡献指南

欢迎提交 Issue 和 Pull Request 来帮助改进项目。

## 许可证

MIT
