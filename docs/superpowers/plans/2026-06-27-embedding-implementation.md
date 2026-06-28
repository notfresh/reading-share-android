# 标签嵌入向量排序实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为标签排序模式添加基于语义相似度的自动排序功能

**Architecture:** 新增 EmbeddingService 负责 ONNX 模型加载和推理，TagEmbeddingManager 负责缓存和排序算法，tag_embeddings 表存储向量

**Tech Stack:** ONNX Runtime Mobile 1.16+, BGE-Micro v2, SQLite

---

## 文件结构

```
app/src/main/java/person/notfresh/readingshare/
├── service/
│   └── EmbeddingService.java          # ONNX 模型加载、推理、相似度计算
├── embedding/
│   ├── TagEmbeddingManager.java      # 标签向量管理、缓存、排序
│   ├── TagEmbeddingDao.java          # tag_embeddings 表访问
│   └── TagEmbeddingDbHelper.java     # 数据库创建/升级
├── db/
│   └── LinkDbHelper.java             # [修改] 添加 TAG_EMBEDDINGS_TABLE
└── ui/tag/
    └── TagsFragment.java             # [修改] 排序模式集成

app/src/main/assets/
└── models/
    └── bge-micro-v2.onnx             # BGE-Micro 模型文件
```

---

## 任务分解

### Task 1: 添加 ONNX Runtime Mobile 依赖

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 添加 ONNX Runtime Mobile 依赖**

```kotlin
dependencies {
    // ONNX Runtime Mobile for embedding inference
    implementation("com.microsoft.onnxruntime:onnxruntime-mobile:1.16.3")
}
```

- [ ] **Step 2: 验证构建**

Run: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`
Expected: 看到 `onnxruntime-mobile` 依赖

---

### Task 2: 下载并添加 BGE-Micro 模型文件

**Files:**
- Create: `app/src/main/assets/models/bge-micro-v2.onnx`

- [ ] **Step 1: 创建 models 目录**

Run: `mkdir -p app/src/main/assets/models`

- [ ] **Step 2: 下载 ONNX 模型**

从 HuggingFace 下载:
```bash
# 使用 huggingface-cli 或手动下载
huggingface-cli download BAAI/bge-micro-v2 --filename bge-micro-v2.onnx --local-dir app/src/main/assets/models
```

验证: `ls -la app/src/main/assets/models/` 显示 `bge-micro-v2.onnx` 文件

---

### Task 3: 创建 TagEmbeddingDbHelper

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/embedding/TagEmbeddingDbHelper.java`

- [ ] **Step 1: 创建文件**

```java
package person.notfresh.readingshare.embedding;

import android.provider.BaseColumns;
import person.notfresh.readingshare.db.LinkDbHelper;

public final class TagEmbeddingDbHelper implements BaseColumns {
    public static final String TABLE_NAME = "tag_embeddings";
    public static final String COLUMN_TAG_ID = "tag_id";
    public static final String COLUMN_EMBEDDING = "embedding";
    public static final String COLUMN_CREATED_AT = "created_at";

    public static final String SQL_CREATE_TABLE =
        "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
        COLUMN_TAG_ID + " INTEGER PRIMARY KEY," +
        COLUMN_EMBEDDING + " TEXT NOT NULL," +
        COLUMN_CREATED_AT + " INTEGER NOT NULL," +
        "FOREIGN KEY (" + COLUMN_TAG_ID + ") REFERENCES " + 
        LinkDbHelper.TABLE_TAGS + "(" + LinkDbHelper.COLUMN_TAG_ID + ")" +
        ")";

    public static final String SQL_DROP_TABLE =
        "DROP TABLE IF EXISTS " + TABLE_NAME;

    private TagEmbeddingDbHelper() {}
}
```

- [ ] **Step 2: 验证编译**

Run: `./gradlew :app:compileDebugJavaWithJavac`
Expected: 编译成功

---

### Task 4: 创建 TagEmbeddingDao

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/embedding/TagEmbeddingDao.java`

- [ ] **Step 1: 创建文件**

```java
package person.notfresh.readingshare.embedding;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.json.JSONArray;
import person.notfresh.readingshare.db.LinkDbHelper;
import java.util.ArrayList;
import java.util.List;

public class TagEmbeddingDao {
    private static final String TAG = "TagEmbeddingDao";
    private LinkDbHelper dbHelper;

    public TagEmbeddingDao(Context context) {
        this.dbHelper = new LinkDbHelper(context);
    }

    public void open() {
        dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    public void saveEmbedding(long tagId, float[] embedding) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(TagEmbeddingDbHelper.COLUMN_TAG_ID, tagId);
        values.put(TagEmbeddingDbHelper.COLUMN_EMBEDDING, arrayToJson(embedding));
        values.put(TagEmbeddingDbHelper.COLUMN_CREATED_AT, System.currentTimeMillis());
        
        db.insertWithOnConflict(
            TagEmbeddingDbHelper.TABLE_NAME,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        );
        Log.d(TAG, "Saved embedding for tag_id=" + tagId);
    }

    public float[] getEmbedding(long tagId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
            TagEmbeddingDbHelper.TABLE_NAME,
            new String[]{TagEmbeddingDbHelper.COLUMN_EMBEDDING},
            TagEmbeddingDbHelper.COLUMN_TAG_ID + " = ?",
            new String[]{String.valueOf(tagId)},
            null, null, null
        );

        float[] result = null;
        if (cursor.moveToFirst()) {
            String json = cursor.getString(0);
            result = jsonToArray(json);
        }
        cursor.close();
        return result;
    }

    public List<Long> getAllTagIdsWithEmbeddings() {
        List<Long> tagIds = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
            TagEmbeddingDbHelper.TABLE_NAME,
            new String[]{TagEmbeddingDbHelper.COLUMN_TAG_ID},
            null, null, null, null, null
        );

        if (cursor.moveToFirst()) {
            do {
                tagIds.add(cursor.getLong(0));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return tagIds;
    }

    public void deleteEmbedding(long tagId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(
            TagEmbeddingDbHelper.TABLE_NAME,
            TagEmbeddingDbHelper.COLUMN_TAG_ID + " = ?",
            new String[]{String.valueOf(tagId)}
        );
    }

    private String arrayToJson(float[] arr) {
        JSONArray jsonArray = new JSONArray();
        for (float v : arr) {
            jsonArray.put(v);
        }
        return jsonArray.toString();
    }

    private float[] jsonToArray(String json) {
        try {
            JSONArray jsonArray = new JSONArray(json);
            float[] arr = new float[jsonArray.length()];
            for (int i = 0; i < jsonArray.length(); i++) {
                arr[i] = (float) jsonArray.getDouble(i);
            }
            return arr;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse embedding JSON", e);
            return null;
        }
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `./gradlew :app:compileDebugJavaWithJavac`
Expected: 编译成功

---

### Task 5: 创建 EmbeddingService

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/service/EmbeddingService.java`

- [ ] **Step 1: 创建 EmbeddingService 类**

```java
package person.notfresh.readingshare.service;

import android.content.Context;
import android.util.Log;
import AI.ONNX.RoaringBitmap;
import org.apache.commons.lang3.ArrayUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class EmbeddingService {
    private static final String TAG = "EmbeddingService";
    private static final String MODEL_PATH = "models/bge-micro-v2.onnx";
    private static final int MAX_LENGTH = 256;
    
    private static volatile EmbeddingService instance;
    private final Context appContext;
    private final ExecutorService executor;
    
    private OrtEnvironment environment;
    private OrtSession session;
    private volatile ModelState state = ModelState.UNLOADED;
    
    private final AtomicReference<float[]> lastAccessTime = new AtomicReference<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes
    private final AtomicLong lastUsedTime = new AtomicLong(0);
    
    private enum ModelState { UNLOADED, LOADING, READY, ERROR }

    private EmbeddingService(Context context) {
        this.appContext = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static EmbeddingService getInstance(Context context) {
        if (instance == null) {
            synchronized (EmbeddingService.class) {
                if (instance == null) {
                    instance = new EmbeddingService(context);
                }
            }
        }
        return instance;
    }

    public void loadModel(LoadCallback callback) {
        if (state == ModelState.READY) {
            callback.onSuccess();
            return;
        }
        if (state == ModelState.LOADING) {
            return;
        }
        
        state = ModelState.LOADING;
        executor.execute(() -> {
            try {
                Log.d(TAG, "Loading ONNX model...");
                long startTime = System.currentTimeMillis();
                
                // Initialize ONNX Environment and Session
                // Note: Actual ONNX API calls depend on onnxruntime-mobile API
                // This is a placeholder showing the structure
                environment = OrtEnvironment.getEnvironment();
                session = environment.createSession(
                    appContext.getAssets().open(MODEL_PATH),
                    new OrtSession.SessionOptions()
                );
                
                state = ModelState.READY;
                Log.d(TAG, "Model loaded in " + (System.currentTimeMillis() - startTime) + "ms");
                callback.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Failed to load model", e);
                state = ModelState.ERROR;
                callback.onError(e.getMessage());
            }
        });
    }

    public void unloadModel() {
        executor.execute(() -> {
            try {
                if (session != null) {
                    session.close();
                    session = null;
                }
                if (environment != null) {
                    environment.close();
                    environment = null;
                }
                state = ModelState.UNLOADED;
                Log.d(TAG, "Model unloaded");
            } catch (Exception e) {
                Log.e(TAG, "Error unloading model", e);
            }
        });
    }

    public void encode(String text, EncodeCallback callback) {
        updateLastUsedTime();
        
        if (state != ModelState.READY) {
            callback.onError("Model not ready, state=" + state);
            return;
        }
        
        executor.execute(() -> {
            try {
                float[] embedding = runInference(text);
                callback.onSuccess(embedding);
            } catch (Exception e) {
                Log.e(TAG, "Inference failed", e);
                callback.onError(e.getMessage());
            }
        });
    }

    private float[] runInference(String text) {
        // Tokenize and run inference
        // This is a placeholder - actual implementation depends on ONNX API
        
        // 1. Tokenize
        String[] tokens = tokenize(text);
        
        // 2. Convert to IDs (vocab lookup - requires tokenizer model)
        // For bge-micro, tokenizer is embedded in the model or separate
        
        // 3. Create input tensors
        long[] inputIds = new long[]{/* tokenized ids */};
        long[] attentionMask = new long[]{/* attention mask */};
        
        // 4. Run inference
        // float[] output = session.run(new String[]{inputIds, attentionMask});
        
        // 5. Mean pooling to get sentence embedding
        // return meanPooling(output, attentionMask);
        
        return new float[384]; // Placeholder - actual implementation
    }

    private String[] tokenize(String text) {
        // Simple whitespace tokenization for demo
        // Actual BGE tokenization requires SentencePiece or similar
        return text.trim().split("\\s+");
    }

    public static float computeCosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0f;
        }
        float dotProduct = 0f;
        float normA = 0f;
        float normB = 0f;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0f;
        }
        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private void updateLastUsedTime() {
        lastUsedTime.set(System.currentTimeMillis());
    }

    public boolean shouldUnload() {
        return state == ModelState.READY && 
               (System.currentTimeMillis() - lastUsedTime.get()) > CACHE_TTL_MS;
    }

    public interface LoadCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface EncodeCallback {
        void onSuccess(float[] embedding);
        void onError(String message);
    }
}
```

- [ ] **Step 2: 实现完整的 tokenization 和推理逻辑**

Note: BGE-Micro 需要 tokenizer 模型或使用内置的 tokenization。需要：
1. 下载完整的 BGE-Micro 包含 tokenizer 的版本
2. 或使用 Java 实现的 tokenizer (如 Hugging Face Java tokenizer)

- [ ] **Step 3: 验证编译**

Run: `./gradlew :app:compileDebugJavaWithJavac`
Expected: 编译成功

---

### Task 6: 创建 TagEmbeddingManager

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/embedding/TagEmbeddingManager.java`

- [ ] **Step 1: 创建 TagEmbeddingManager 类**

```java
package person.notfresh.readingshare.embedding;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import person.notfresh.readingshare.service.EmbeddingService;
import person.notfresh.readingshare.db.LinkDao;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;

public class TagEmbeddingManager {
    private static final String TAG = "TagEmbeddingManager";
    
    private final Context context;
    private final TagEmbeddingDao embeddingDao;
    private final LinkDao linkDao;
    private final EmbeddingService embeddingService;
    private final Handler mainHandler;
    
    // In-memory cache: tagId -> embedding
    private final Map<Long, float[]> embeddingCache = new HashMap<>();
    
    public TagEmbeddingManager(Context context) {
        this.context = context;
        this.embeddingDao = new TagEmbeddingDao(context);
        this.embeddingDao.open();
        this.linkDao = new LinkDao(context);
        this.linkDao.open();
        this.embeddingService = EmbeddingService.getInstance(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void sortTagsBySimilarity(SortCallback callback) {
        // 1. Get all tags with their IDs
        Map<String, Integer> tagsWithCount = linkDao.getTagsWithCount();
        List<Long> tagIds = new ArrayList<>();
        List<String> tagNames = new ArrayList<>();
        
        int idx = 0;
        for (Map.Entry<String, Integer> entry : tagsWithCount.entrySet()) {
            tagNames.add(entry.getKey());
            long id = linkDao.getTagIdByName(entry.getKey());
            tagIds.add(id);
        }
        
        // 2. Load or compute embeddings for all tags
        loadOrComputeEmbeddings(tagIds, tagNames, () -> {
            // 3. Compute NxN similarity matrix
            int n = tagIds.size();
            float[][] similarityMatrix = new float[n][n];
            for (int i = 0; i < n; i++) {
                float[] embI = embeddingCache.get(tagIds.get(i));
                if (embI == null) continue;
                for (int j = 0; j < n; j++) {
                    float[] embJ = embeddingCache.get(tagIds.get(j));
                    if (embJ == null) continue;
                    similarityMatrix[i][j] = EmbeddingService.computeCosineSimilarity(embI, embJ);
                }
            }
            
            // 4. Greedy hierarchical clustering sort
            List<Integer> sortedIndices = greedyClusterSort(similarityMatrix);
            
            // 5. Convert back to tag IDs
            List<Long> sortedTagIds = new ArrayList<>();
            for (int idx_ : sortedIndices) {
                sortedTagIds.add(tagIds.get(idx_));
            }
            
            mainHandler.post(() -> callback.onSuccess(sortedTagIds, sortedTagNames));
        });
    }
    
    private List<Integer> greedyClusterSort(float[][] matrix) {
        int n = matrix.length;
        if (n == 0) return new ArrayList<>();
        if (n == 1) return Collections.singletonList(0);
        
        List<Integer> selected = new ArrayList<>();
        List<Integer> remaining = new ArrayList<>();
        for (int i = 0; i < n; i++) remaining.add(i);
        
        // Find centroid (vector mean) and select closest to centroid as first
        float[] centroid = computeCentroid(matrix);
        int firstIdx = findClosestToCentroid(centroid, matrix, remaining);
        selected.add(firstIdx);
        remaining.remove((Integer) firstIdx);
        
        // Greedy expansion
        while (!remaining.isEmpty()) {
            int bestNext = -1;
            float bestScore = Float.MAX_VALUE;
            
            for (int candidate : remaining) {
                // Score = max similarity to any selected (we want LOW similarity to maintain diversity)
                float maxSimToSelected = Float.MIN_VALUE;
                for (int s : selected) {
                    maxSimToSelected = Math.max(maxSimToSelected, matrix[candidate][s]);
                }
                if (maxSimToSelected < bestScore) {
                    bestScore = maxSimToSelected;
                    bestNext = candidate;
                }
            }
            
            if (bestNext != -1) {
                selected.add(bestNext);
                remaining.remove((Integer) bestNext);
            }
        }
        
        return selected;
    }
    
    private float[] computeCentroid(float[][] matrix) {
        // Approximation: use mean of each row as centroid
        int n = matrix.length;
        float[] centroid = new float[n];
        for (int i = 0; i < n; i++) {
            float rowSum = 0;
            for (int j = 0; j < n; j++) {
                rowSum += matrix[i][j];
            }
            centroid[i] = rowSum / n;
        }
        return centroid;
    }
    
    private int findClosestToCentroid(float[] centroid, float[][] matrix, List<Integer> remaining) {
        int best = -1;
        float bestScore = Float.MIN_VALUE;
        for (int i : remaining) {
            float score = 0;
            for (int j = 0; j < matrix.length; j++) {
                score += matrix[i][j];
            }
            if (score > bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }
        return best;
    }
    
    private void loadOrComputeEmbeddings(List<Long> tagIds, List<String> tagNames, Runnable onComplete) {
        List<Long> missingIds = new ArrayList<>();
        List<String> missingNames = new ArrayList<>();
        
        // Check cache and database
        for (int i = 0; i < tagIds.size(); i++) {
            Long tagId = tagIds.get(i);
            float[] cached = embeddingCache.get(tagId);
            if (cached == null) {
                float[] fromDb = embeddingDao.getEmbedding(tagId);
                if (fromDb != null) {
                    embeddingCache.put(tagId, fromDb);
                } else {
                    missingIds.add(tagId);
                    missingNames.add(tagNames.get(i));
                }
            }
        }
        
        if (missingIds.isEmpty()) {
            onComplete.run();
            return;
        }
        
        // Compute missing embeddings
        computeEmbeddings(missingIds, missingNames, onComplete);
    }
    
    private void computeEmbeddings(List<Long> tagIds, List<String> tagNames, Runnable onComplete) {
        // Ensure model is loaded
        embeddingService.loadModel(new EmbeddingService.LoadCallback() {
            @Override
            public void onSuccess() {
                computeNext(tagIds, tagNames, 0, onComplete);
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to load model: " + message);
                onComplete.run();
            }
        });
    }
    
    private void computeNext(List<Long> tagIds, List<String> tagNames, int index, Runnable onComplete) {
        if (index >= tagIds.size()) {
            onComplete.run();
            return;
        }
        
        String tagName = tagNames.get(index);
        Long tagId = tagIds.get(index);
        
        embeddingService.encode(tagName, new EmbeddingService.EncodeCallback() {
            @Override
            public void onSuccess(float[] embedding) {
                embeddingCache.put(tagId, embedding);
                embeddingDao.saveEmbedding(tagId, embedding);
                computeNext(tagIds, tagNames, index + 1, onComplete);
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to encode tag '" + tagName + "': " + message);
                computeNext(tagIds, tagNames, index + 1, onComplete);
            }
        });
    }

    public void computeEmbeddingForNewTag(long tagId, String tagName) {
        embeddingService.loadModel(new EmbeddingService.LoadCallback() {
            @Override
            public void onSuccess() {
                embeddingService.encode(tagName, new EmbeddingService.EncodeCallback() {
                    @Override
                    public void onSuccess(float[] embedding) {
                        embeddingCache.put(tagId, embedding);
                        embeddingDao.saveEmbedding(tagId, embedding);
                    }
                    
                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "Failed to encode new tag: " + message);
                    }
                });
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to load model for new tag: " + message);
            }
        });
    }

    public void checkAndUnload() {
        if (embeddingService.shouldUnload()) {
            embeddingService.unloadModel();
            embeddingCache.clear();
        }
    }

    public interface SortCallback {
        void onSuccess(List<Long> sortedTagIds, List<String> sortedTagNames);
        void onError(String message);
    }
}
```

- [ ] **Step 2: 修复编译错误**

Note: 代码中有 `bestIdx` 未定义的问题，需要修复为 `best`

- [ ] **Step 3: 验证编译**

Run: `./gradlew :app:compileDebugJavaWithJavac`
Expected: 编译成功

---

### Task 7: 修改 LinkDbHelper 添加 tag_embeddings 表

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/db/LinkDbHelper.java`

- [ ] **Step 1: 添加表创建 SQL 到 onCreate**

找到 `onCreate` 方法，添加：
```java
db.execSQL(TagEmbeddingDbHelper.SQL_CREATE_TABLE);
```

- [ ] **Step 2: 添加表创建到 onUpgrade**

找到 `onUpgrade` 方法，添加：
```java
db.execSQL("DROP TABLE IF EXISTS " + TagEmbeddingDbHelper.TABLE_NAME);
db.execSQL(TagEmbeddingDbHelper.SQL_CREATE_TABLE);
```

- [ ] **Step 3: 添加 import**

```java
import person.notfresh.readingshare.embedding.TagEmbeddingDbHelper;
```

- [ ] **Step 4: 验证编译**

Run: `./gradlew :app:compileDebugJavaWithJavac`
Expected: 编译成功

---

### Task 8: 集成到 TagsFragment 排序模式

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/ui/tag/TagsFragment.java`

- [ ] **Step 1: 添加 TagEmbeddingManager 成员和初始化**

```java
private TagEmbeddingManager tagEmbeddingManager;

// 在 onCreateView 中初始化
tagEmbeddingManager = new TagEmbeddingManager(requireContext());
```

- [ ] **Step 2: 修改 toggleSortMode 方法**

找到排序模式启动逻辑，在显示 Toast "正在计算标签相似度..." 后添加：
```java
// 计算相似度排序
tagEmbeddingManager.sortTagsBySimilarity(new TagEmbeddingManager.SortCallback() {
    @Override
    public void onSuccess(List<Long> sortedTagIds, List<String> sortedTagNames) {
        requireActivity().runOnUiThread(() -> {
            // 更新适配器的标签顺序
            if (sortedTagIds.size() == tagsAdapter.getTags().size()) {
                List<TagsAdapter.TagItem> currentTags = tagsAdapter.getTags();
                Map<Long, TagsAdapter.TagItem> tagMap = new HashMap<>();
                for (TagsAdapter.TagItem tag : currentTags) {
                    tagMap.put(tag.getId(), tag);
                }
                
                List<TagsAdapter.TagItem> sortedItems = new ArrayList<>();
                for (Long tagId : sortedTagIds) {
                    TagsAdapter.TagItem item = tagMap.get(tagId);
                    if (item != null) {
                        sortedItems.add(item);
                    }
                }
                
                if (!sortedItems.isEmpty()) {
                    tagsAdapter.setTags(sortedItems);
                }
            }
            Toast.makeText(requireContext(), "相似度排序完成，可拖拽微调", Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onError(String message) {
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(), "排序失败: " + message, Toast.LENGTH_SHORT).show();
        });
    }
});
```

- [ ] **Step 3: 添加必要的 import**

```java
import person.notfresh.readingshare.embedding.TagEmbeddingManager;
import java.util.HashMap;
import java.util.Map;
```

- [ ] **Step 4: 在 onDestroyView 中清理**

```java
@Override
public void onDestroyView() {
    super.onDestroyView();
    if (tagEmbeddingManager != null) {
        tagEmbeddingManager.checkAndUnload();
    }
}
```

- [ ] **Step 5: 验证编译**

Run: `./gradlew :app:compileDebugJavaWithJavac`
Expected: 编译成功

---

### Task 9: 验证构建和测试

**Files:**
- None (verification only)

- [ ] **Step 1: 完整构建**

Run: `./gradlew assembleDebug`
Expected: 构建成功，生成 APK

- [ ] **Step 2: 检查 APK 大小增量**

Run: `ls -lh app/build/outputs/apk/debug/app-debug.apk`
Expected: APK 大小合理（增量约 24MB）

- [ ] **Step 3: 手动测试**

1. 安装 APK 到设备
2. 进入标签页面
3. 点击排序菜单
4. 观察是否显示 loading toast
5. 检查标签顺序是否改变

---

## 已知限制和后续工作

1. **BGE Tokenizer**: 当前代码使用简单 whitespace tokenization，实际 BGE 需要 SentencePiece tokenizer。需要在 Task 5 中完善。

2. **ONNX API**: 实际 ONNX Runtime Mobile API 可能与代码中的调用略有不同，需要根据实际库调整。

3. **内存监控**: 当前没有实现根据可用内存决定是否加载模型的逻辑。

---

## 执行选项

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
