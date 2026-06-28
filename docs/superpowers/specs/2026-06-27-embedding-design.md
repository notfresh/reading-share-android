# 标签嵌入向量排序功能设计

> **状态：** 已批准实施

## 目标

为 ReadingShare Android 应用引入嵌入向量模型，实现：
1. **标签相似度排序**：在排序模式下，按标签语义相似度自动排序
2. **未来扩展**：基于链接标题的智能标签推荐

## 架构概览

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer                              │
│  TagsFragment (排序模式) / AddLinkDialog (推荐模式)       │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│                 EmbeddingService                         │
│  - loadModel(): 按需加载 ONNX 模型                       │
│  - encode(text): 生成嵌入向量                            │
│  - computeSimilarity(a, b): 余弦相似度                    │
│  - 缓存管理：TTL 5分钟，无访问时释放                      │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│              TagEmbeddingManager                         │
│  - getEmbedding(tag): 获取/计算标签向量                   │
│  - sortTagsBySimilarity(tags): 贪心层次聚类排序           │
│  - computePairwiseSimilarity(): N×N 相似度矩阵           │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│                 SQLite Database                          │
│  - tags 表（现有）                                       │
│  - tag_embeddings 表（新建）：tag_id, embedding, created_at │
└─────────────────────────────────────────────────────────┘
```

## 技术选型

| 组件 | 选择 | 理由 |
|------|------|------|
| 嵌入模型 | BGE-Micro (`bge-micro-v2`) | 轻量(~24MB)、高质量、HuggingFace 有 ONNX 版本 |
| 模型加载 | ONNX Runtime Mobile 1.16+ | Android 官方支持、内存管理良好 |
| 向量存储 | SQLite JSON | 复用现有数据库、简单可靠 |
| 向量维度 | 384 (原生) | 不降维，保持语义完整性 |

## 数据库设计

### 新建表：`tag_embeddings`

```sql
CREATE TABLE tag_embeddings (
    tag_id INTEGER PRIMARY KEY,
    embedding TEXT NOT NULL,          -- JSON 格式: "[0.123, -0.456, ...]"
    created_at INTEGER NOT NULL,       -- Unix timestamp (毫秒)
    FOREIGN KEY (tag_id) REFERENCES tags(_id)
);
```

## 缓存策略

### EmbeddingService 生命周期

```
状态机:
  UNLOADED → LOADING → READY → (UNLOADED)
                ↓         ↓
            LOADING    READY (缓存命中)
                          ↓
                      UNLOADED (5分钟无访问)
```

### 缓存规则
1. **按需加载**：首次使用时加载模型
2. **TTL 5分钟**：无任何调用后 5 分钟释放
3. **缓存命中**：模型就绪时直接使用，不重新加载
4. **新标签计算**：创建新标签时计算向量，存入数据库并缓存

## 排序算法

### 贪心层次聚类排序

```
输入: tags[] = [T1, T2, T3, T4, T5], embeddings[]

1. 计算 N×N 相似度矩阵 S[5][5]

2. 选择初始中心:
   - 计算所有向量的均值向量 C
   - 选择与 C 余弦距离最小的标签作为第一个

3. 贪心展开:
   已选: [T_center]
   剩余: [T1, T2, T3, T4, T5] - [T_center]

   迭代直到完成:
   - 对每个剩余标签，计算其与已选标签的最小相似度
   - 选择与其他已选标签相似度最低的那个（保持多样性）
   - 加入已选列表

输出: 排序后的标签列表
```

### 算法复杂度
- N×N 矩阵计算: O(N²) 相似度计算
- 贪心展开: O(N²) 查找
- **总计**: O(N²)，适合移动端（N ≤ 几百）

## 交互设计

### 标签排序模式

1. 用户点击菜单 **排序** → 进入排序模式
2. 显示 Toast: "正在计算标签相似度..."
3. 后台执行:
   - 检查模型是否加载，没有则加载
   - 检查标签向量是否完整，缺失则计算
   - 计算相似度矩阵，执行排序
4. 排序完成后，RecyclerView 更新显示顺序
5. 用户拖拽微调（保留现有功能）
6. 用户点击 **完成** → 保存排序到数据库

### 首次加载时机

排序模式下：
- 用户进入排序模式时触发
- 如果模型未加载，先显示加载提示

## 文件变更

### 新建文件

| 文件 | 职责 |
|------|------|
| `EmbeddingService.java` | ONNX 模型加载、推理、相似度计算 |
| `TagEmbeddingManager.java` | 标签向量管理、缓存、排序算法 |
| `TagEmbeddingDao.java` | tag_embeddings 表的数据访问 |
| `TagEmbeddingDbHelper.java` | 数据库升级、创建表 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `LinkDbHelper.java` | 添加 `TAG_EMBEDDINGS_TABLE` 常量和 `onUpgrade` 创建表 |
| `TagsFragment.java` | 排序模式下调用 TagEmbeddingManager |
| `LinkDao.java` | 添加 `addTag()` 后触发 embedding 计算（可选） |

## 模型文件

### 获取 BGE-Micro ONNX 模型

从 HuggingFace 下载：
```
https://huggingface.co/BAAI/bge-micro-v2/tree/main
```

需要文件：
- `onnx/model.onnx` 或 `bge-micro-v2.onnx`

放置位置：
```
app/src/main/assets/
└── models/
    └── bge-micro-v2.onnx
```

## 测试策略

1. **单元测试**：
   - EmbeddingService 加载/卸载
   - 相似度计算正确性
   - 缓存 TTL 行为

2. **集成测试**：
   - 标签排序输出验证
   - 新标签创建后排序更新

3. **手动测试**：
   - APK 大小增量
   - 首次加载延迟
   - 内存占用

## 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| 模型加载慢 | 后台线程 + loading indicator |
| APK 增量过大 (~24MB) | 仅在需要时下载/按需加载 |
| 低端设备内存不足 | 检测可用内存，必要时跳过 |

## 未来扩展

### 智能标签推荐（Phase 2）

```
用户添加链接 → 输入标题 → 
  → encode(title) → 向量 V
  → 从 tag_embeddings 查找与 V 最相似的 Top-K 标签
  → 显示推荐标签供选择
```

此设计为 Phase 1，Phase 2 可复用 EmbeddingService 和存储结构。
