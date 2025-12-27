# 主题需求

界面上显示的要求：一个新的标签页，主题标签，保持跟主页一样的list垂直显示，显示很多主题，点击进去，可以看到一个主题内部的东西，可以使用二级嵌套，主题的内部还是使用list垂直显示。

# 主题的内核

主题，作用把不同的东西汇聚起来，一个主题里包含很多东西

包括LinkItem, 多媒体内容，包括多张图片

抽象的声明一个类的模板，如下
class Subject{
    
    class SubJectItem{
        list<Image> images;
        StringText remark;
        LinkItem link;
        DateTime addTime;
        int orderIndex; // 满足未来可以拖来拽来调整顺序
    }

    List<SubJectItem> subItems;
    StringText title;
    DateTime createTime;
    String describe;
}

主题是什么意思呢？是为了把很多东西聚合起来，促进灵感的爆发，保证专注用的。

# 第一轮沟通

回答几个问题：

主题中的 LinkItem 是否与主页的链接共享数据？还是独立存储？可以直接从主页选链接加入，这是一个高级功能，帮助人更好的专注用的。

答：共享数据。

一个 LinkItem 能否属于多个主题？
答：可以。


图片存储，images 是本地文件路径，还是网络 URL，或两者都支持？
答：目前本地存储吧。

图片如何添加？从相册选择、截图、还是从网页保存？
从相册选择。

二级嵌套
是指主题内可再分组（如“子主题”），还是指 SubJectItem 可包含嵌套结构？

二级嵌套的意思是，主题的显示样式，使用list垂直显示，一个主题点进去，还是list垂直显示，只描述样式，主题本身不可嵌套。

交互细节
如何创建主题？如何向主题添加内容？
在主题标签页的右上角增加一个+号按钮，即创建主题，可以在主题内部增加内容，同样是右上角位置，增加主体项。

先完善内核，界面最后再写。

问：是否支持从主页/标签页直接“添加到主题”？
答：支持，支持双向选择，从主页/标签页直接“添加到主题（多选主题），也可以选择从主题内部收录链接。

问：拖拽排序是主题列表排序，还是主题内 SubJectItem 的排序？
答：主题内 SubJectItem 的排序，主题列表不支持排序，只有createTime。


数据持久化
使用 SQLite 还是其他存储方式？是否需要与现有 LinkDao 集成？
答：使用Sqlite存储，可以跟LinkDao交互，但是应该是另外一个Dao了,可以持久LinkDao的引用。



显示样式
主题列表项显示哪些信息（标题、描述、缩略图、项数量等）？
答：显示标题，描述即可，先不要缩略图。

SubJectItem 的列表项如何展示（图片、链接标题、备注等）？
答：有图片就显示图片，有链接就显示标题链接和链接本身，有备注显示备注。
这些项的内容是可选的。


整体要求，实现顺序，先完善内核，界面最后再写，不着急，让我确认内核完成后再写，内核与任何框架和平台无关。


# 第二轮沟通

需要进一步确认的设计细节
1. LinkItem 的引用方式
如果 LinkItem 被删除，主题中的引用如何处理？
选项A：级联删除（从所有主题中移除）
选项B：保留引用但标记为已删除
选项C：不允许删除被主题引用的 LinkItem

答：选择B。

2. SubJectItem 的完整性
SubJectItem 是否必须至少包含一项（图片、链接、备注）？
还是允许创建空的 SubJectItem？

答：至少一项。

3. 图片路径管理
图片文件存储在应用私有目录还是公共目录？
如果图片文件被删除，SubJectItem 如何处理？
答：私有目录，因此只能通过主题内部删除。

4. orderIndex 的实现
拖拽排序时，orderIndex 的更新策略：
选项A：重新计算所有项的 orderIndex（1, 2, 3...）
选项B：使用间隔值（10, 20, 30...），便于插入

答：选择间隔值。

5. 双向选择的具体交互
“从主页/标签页直接添加到主题（多选主题）”：
是否支持批量选择多个链接？答：支持。
是否支持同时添加到多个主题？答：不支持。

“从主题内部收录链接”：
是复制链接到主页，还是建立新的关联关系？答：关联关系，数据只存一份。

6. StringText 类型
StringText remark 中的 StringText 是普通 String，还是富文本/多行文本？
答：多行简单文本，可以多写东西。

# 第三轮沟通

LinkItem 删除标记：
如何标记 LinkItem 已删除？是在 SubjectItem 中增加 isLinkDeleted 字段，还是通过查询 LinkDao 判断？

答：如果LinkItem已经主页或者标签页删除，LinkItem加载的时候，没有查到，增加删除标记，以后就直接显示已经删除即可，只查一次即可。


orderIndex 初始值：
新主题的第一个 SubjectItem 的 orderIndex 从 10 开始？
间隔值固定为 10，还是可配置？
答：起始从0开始，默认间隔10，暂时不需要配置，如果间隔不够用了，把后面的 SubjectItem直接统一加10，然后再回到原来的SubjectItem继续拓展。

图片数量限制：
SubjectItem 的图片数量是否有上限？答：一个Iten限制10张。

主题删除策略：
删除主题时，是否同时删除所有关联的图片文件？答：是的。


设计建议

延迟加载删除标记：首次加载 SubjectItem 时检查 LinkItem 是否存在，之后使用缓存。答：如果加载不了LinkItem的Url，说明LinkItem已经被删除，到时候再查一次，并且提示找不到，是否删除。


orderIndex 调整算法：设计高效的批量调整算法，避免频繁全量更新，答：没明白。

图片文件管理：统一管理图片存储路径，便于清理和迁移。答：采纳。

事务处理：删除主题时使用事务，确保数据一致性。答：采纳。


# 对解释的回复 
orderIndex 调整算法，答：使用最简单的后续项暴力全增加10即可，因为数量不多。

关于删除标记的交互
提示时机：是在打开主题详情页时检测并提示，还是在点击某个 SubjectItem 的链接时提示？
答：点击的时候提示。


批量处理：如果主题中有多个 SubjectItem 的 LinkItem 都被删除了，是逐个提示，还是一次性提示所有？
答：逐一提示，点击的时候再说。


# 界面实现顺序
基于项目结构，界面实现顺序如下：

## 界面实现顺序和思路

### 第一阶段：数据库层（先于界面）
1. **SubjectDbHelper** - 数据库表结构
   - 创建 `subjects` 表
   - 创建 `subject_items` 表
   - 创建 `subject_item_images` 表（多对多关系）
   - 处理数据库版本升级

2. **SubjectDao** - 数据访问层
   - Subject 的 CRUD
   - SubjectItem 的 CRUD
   - 图片路径管理
   - 与 LinkDao 的交互（检查 LinkItem 是否存在）
   - 事务处理（删除主题时级联删除）

### 第二阶段：基础界面（核心功能）

#### 1. **主题列表页（SubjectFragment）**
   - 位置：`ui/subject/SubjectFragment.java`
   - 布局：`fragment_subject.xml`（类似 `fragment_home.xml`）
   - 功能：
     - RecyclerView 显示主题列表（标题+描述）
     - 右上角 + 按钮创建主题
     - 点击主题项进入详情页
     - 按 `createTime` 排序
   - Adapter：`SubjectAdapter.java`
   - 布局项：`item_subject.xml`（标题+描述）

#### 2. **主题详情页（SubjectDetailActivity 或 Fragment）**
   - 建议使用 Activity（便于独立管理）
   - 位置：`ui/subject/SubjectDetailActivity.java`
   - 布局：`activity_subject_detail.xml`
   - 功能：
     - Toolbar 显示主题标题
     - RecyclerView 显示 SubjectItem 列表
     - 右上角 + 按钮添加主题项
     - 支持拖拽排序（ItemTouchHelper）
     - 点击 SubjectItem 可编辑/查看详情
   - Adapter：`SubjectItemAdapter.java`
   - 布局项：`item_subject_item.xml`（图片+链接+备注，条件显示）

### 第三阶段：对话框和交互

#### 3. **创建/编辑主题对话框**
   - 位置：`ui/subject/CreateSubjectDialog.java` 或使用 DialogFragment
   - 功能：
     - 输入标题
     - 输入描述（多行）
     - 保存/取消

#### 4. **添加主题项对话框**
   - 位置：`ui/subject/AddSubjectItemDialog.java`
   - 功能：
     - 选择链接（可选，从 LinkDao 选择）
     - 添加图片（从相册选择，最多10张）
     - 输入备注（多行文本）
     - 验证至少包含一项
     - 保存时计算 orderIndex

#### 5. **编辑主题项对话框**
   - 复用添加对话框，支持编辑模式
   - 预填充现有数据

### 第四阶段：导航和菜单集成

#### 6. **导航配置**
   - 在 `mobile_navigation.xml` 添加主题 Fragment
   - 在 `activity_main_drawer.xml` 添加主题菜单项
   - 在 `MainActivity.java` 添加导航处理
   - 在 `AppBarConfiguration` 添加主题 ID
   - 在 `strings.xml` 添加主题相关字符串

### 第五阶段：高级功能

#### 7. **从主页/标签页添加到主题**
   - 在 `LinksAdapter` 的选择模式中添加“添加到主题”选项
   - 创建 `SelectSubjectDialog.java`（选择目标主题）
   - 批量创建 SubjectItem

#### 8. **从主题收录链接**
   - 在 `SubjectItemAdapter` 中添加“收录链接”选项
   - 如果 LinkItem 不存在，先创建再关联

#### 9. **LinkItem 删除检测**
   - 在点击链接时检测（SubjectItemAdapter 中处理）
   - 如果 LinkItem 不存在，标记 `isLinkDeleted = true`
   - 提示用户是否删除该 SubjectItem

#### 10. **拖拽排序**
   - 使用 `ItemTouchHelper` 实现拖拽
   - 拖拽完成后调用 `SubjectUtil.calculateDragOrderIndex()`
   - 如果间隔耗尽，调用 `SubjectUtil.adjustOrderIndexInterval()`
   - 更新数据库

### 第六阶段：优化和细节

#### 11. **图片管理**
   - 图片保存到私有目录（统一路径管理）
   - 删除 SubjectItem 时删除图片文件
   - 删除主题时批量删除所有图片

#### 12. **搜索功能**（可选）
   - 在主题列表页添加搜索框
   - 按标题/描述搜索

#### 13. **空状态提示**
   - 主题列表为空时的提示
   - 主题详情为空时的提示

## 实现优先级建议

**必须实现（MVP）：**
1. 数据库层（SubjectDbHelper + SubjectDao）
2. 主题列表页（SubjectFragment）
3. 主题详情页（SubjectDetailActivity）
4. 创建主题对话框
5. 添加主题项对话框
6. 导航集成

**重要功能：**
7. 拖拽排序
8. 从主页添加到主题
9. LinkItem 删除检测

**优化功能：**
10. 搜索功能
11. 编辑主题/主题项
12. 空状态提示

## 技术要点

1. 数据绑定：使用 DataBinding（如 `FragmentHomeBinding`）
2. RecyclerView：使用 Adapter + ViewHolder
3. 拖拽排序：使用 `ItemTouchHelper`
4. 图片选择：使用系统 Intent 或第三方库
5. 数据库事务：删除主题时使用事务
6. 异步处理：数据库操作在后台线程

按此顺序实现，先完成核心功能，再逐步完善。

# URIScheme TODO

是的，这个设计确实类似于 App URI Scheme / Deep Link。

## 当前实现

通过 Intent 可以直接定位到特定主题：
```java
Intent intent = new Intent(context, SubjectDetailActivity.class);
intent.putExtra(SubjectDetailActivity.EXTRA_SUBJECT_ID, subjectId);
```

这相当于一个内部的 Deep Link 机制。

## 优势

1. 快捷方式支持：桌面快捷方式可直接打开特定主题
2. 应用内跳转：可以从任何地方通过 Intent 跳转到主题详情
3. 可扩展性：未来可以扩展为完整的 URI Scheme

## 未来扩展方向

### 1. 完整的 URI Scheme
```
readingshare://subject/123
readingshare://link/456
readingshare://tag/技术
```
- 支持从浏览器、其他应用打开
- 支持分享链接直接打开主题

### 2. 通用 Intent Filter
在 AndroidManifest.xml 中注册：
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="readingshare" />
</intent-filter>
```

### 3. 分享功能增强
- 分享主题链接给其他用户
- 通过链接直接打开主题（如果实现 URI Scheme）

### 4. 跨应用集成
- 其他应用可以通过 Intent 打开你的主题
- 支持从笔记应用、浏览器等跳转

## 当前架构的优势

1. 已有基础：`SubjectDetailActivity` 已支持通过 Intent 接收 subjectId
2. 数据层完善：`SubjectDao` 可以查询主题
3. 扩展容易：只需添加 Intent Filter 和 URI 解析逻辑

## 建议

1. 保持当前设计：通过 Intent Extra 传递 ID 的方式简单有效
2. 预留扩展接口：未来可以添加 URI 解析层，将 URI 转换为 Intent
3. 统一入口：所有通过 Intent 打开的主题都走同一个 Activity

这是一个很好的架构设计，为未来的开放和扩展打下了基础。

URI Scheme 设计确实很有潜力。以下是一些思路和方向：

## URI Scheme 设计思路

### 1. **统一的 URI 格式**
```
readingshare://subject/{subjectId}
readingshare://link/{linkId}
readingshare://tag/{tagName}
readingshare://search?q={query}
readingshare://rss/{rssId}
```

### 2. **支持多种打开方式**
- 浏览器：`readingshare://subject/123`
- 其他应用：通过 Intent 调用
- 分享链接：生成可分享的 URI
- 二维码：扫描后直接打开

### 3. **参数扩展**
```
readingshare://subject/123?highlight=item456
readingshare://link/789?openInBrowser=true
readingshare://search?q=Android&tag=技术
```

## 未来应用场景

### 1. **分享功能**
- 分享主题链接给朋友
- 朋友点击链接直接打开主题详情
- 支持跨设备同步

### 2. **Web 集成**
- 网站可以链接到应用内的主题
- 从网页直接跳转到应用

### 3. **自动化脚本**
- 通过 URI 批量操作
- 支持外部工具调用

### 4. **插件系统**
- 第三方应用可以集成
- 扩展应用功能

## 设计建议

### 1. **保持向后兼容**
- 现有的 Intent Extra 方式继续支持
- URI Scheme 作为新的入口，不破坏现有功能

### 2. **统一的路由层**
- 创建一个 `UriRouter` 或 `DeepLinkHandler`
- 统一处理所有 URI 请求
- 便于维护和扩展

### 3. **安全性考虑**
- 验证 URI 参数的有效性
- 防止恶意 URI 攻击
- 权限控制（某些 URI 可能需要权限）

### 4. **用户体验**
- 优雅的错误处理
- 如果资源不存在，提供友好的提示
- 支持降级处理（如主题不存在时跳转到列表页）

## 实现优先级建议

1. 第一阶段：基础 URI Scheme
   - 支持 `readingshare://subject/{id}`
   - 支持 `readingshare://link/{id}`

2. 第二阶段：参数和查询
   - 支持查询参数
   - 支持复杂场景

3. 第三阶段：分享和开放
   - 生成可分享的 URI
   - Web 集成
   - 文档和 API

这个设计方向很好，为应用提供了更多开放性和扩展性。需要我帮你规划具体的实现方案吗？