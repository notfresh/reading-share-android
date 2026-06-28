package person.notfresh.readingshare.embedding;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.service.EmbeddingService;

/**
 * TagEmbeddingManager handles tag embedding operations including:
 * - In-memory cache of tag embeddings
 * - Loading/computing embeddings for tags
 * - Computing NxN similarity matrix
 * - Greedy hierarchical clustering sort algorithm
 */
public class TagEmbeddingManager {
    private static final String TAG = "TagEmbeddingManager";

    private final Context context;
    private final TagEmbeddingDao embeddingDao;
    private final LinkDao linkDao;
    private final EmbeddingService embeddingService;
    private final Handler mainHandler;
    private final ExecutorService executor;

    // In-memory cache: tagId -> embedding
    private final Map<Long, float[]> embeddingCache = new HashMap<>();
    // Custom proximity boosts loaded from assets config.
    private Map<String, Float> customPairBoosts = new HashMap<>();

    public interface SortCallback {
        void onSuccess(List<Long> sortedTagIds, List<String> sortedTagNames);
        void onError(String message);
    }

    public TagEmbeddingManager(Context context) {
        this.context = context.getApplicationContext();
        this.embeddingDao = new TagEmbeddingDao(context);
        this.linkDao = new LinkDao(context);
        this.embeddingService = EmbeddingService.getInstance(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
        this.customPairBoosts = loadCustomPairBoosts();
    }

    /**
     * Sort tags by similarity using greedy hierarchical clustering.
     * This method finds an optimal ordering of tags such that similar tags are placed together.
     *
     * @param callback Callback to receive sorted tag IDs and names, or error
     */
    public void sortTagsBySimilarity(SortCallback callback) {
        executor.execute(() -> {
            try {
                customPairBoosts = loadCustomPairBoosts();

                // 1. Get all tags with their IDs
                linkDao.open();
                Map<String, Integer> tagsWithCount = linkDao.getTagsWithCount();

                if (tagsWithCount.isEmpty()) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>(), new ArrayList<>()));
                    return;
                }

                List<Long> tagIds = new ArrayList<>();
                List<String> tagNames = new ArrayList<>();
                Map<Long, String> tagIdToName = new HashMap<>();

                // Get tag IDs from the tags with count (maintaining order)
                Set<String> tagNameSet = tagsWithCount.keySet();
                for (String tagName : tagNameSet) {
                    long tagId = linkDao.getTagIdByName(tagName);
                    if (tagId != -1) {
                        tagIds.add(tagId);
                        tagNames.add(tagName);
                        tagIdToName.put(tagId, tagName);
                    }
                }

                int n = tagIds.size();
                if (n == 0) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>(), new ArrayList<>()));
                    return;
                }

                // 2. Load or compute embeddings for all tags
                List<float[]> embeddings = new ArrayList<>(n);
                List<Long> missingTagIds = new ArrayList<>();

                for (int i = 0; i < n; i++) {
                    long tagId = tagIds.get(i);
                    float[] embedding = embeddingCache.get(tagId);

                    if (embedding == null) {
                        embedding = embeddingDao.getEmbedding(tagId);
                        if (embedding != null) {
                            embeddingCache.put(tagId, embedding);
                        }
                    }

                    if (embedding != null) {
                        embeddings.add(embedding);
                    } else {
                        missingTagIds.add(tagId);
                        embeddings.add(null);
                    }
                }

                // If tags are missing embeddings, compute them first
                if (!missingTagIds.isEmpty()) {
                    Log.d(TAG, "Missing embeddings for " + missingTagIds.size() + " tags, computing now...");

                    // Collect missing tag names in same order as missingTagIds
                    List<String> missingTagNames = new ArrayList<>();
                    for (Long tagId : missingTagIds) {
                        String name = tagIdToName.get(tagId);
                        if (name != null) {
                            missingTagNames.add(name);
                        }
                    }

                    // Load model and compute embeddings for missing tags
                    CountDownLatch latch = new CountDownLatch(1);
                    final boolean[] computeSuccess = {true};

                    // Get indices of missing tags in original arrays
                    Map<Long, Integer> tagIdToIndex = new HashMap<>();
                    for (int i = 0; i < n; i++) {
                        tagIdToIndex.put(tagIds.get(i), i);
                    }

                    embeddingService.loadModel(new EmbeddingService.LoadCallback() {
                        @Override
                        public void onSuccess() {
                            computeNextEmbedding(0, missingTagIds, missingTagNames, embeddings, tagIdToIndex, () -> {
                                latch.countDown();
                            });
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Failed to load model: " + error);
                            computeSuccess[0] = false;
                            latch.countDown();
                        }
                    });

                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        mainHandler.post(() -> callback.onError("Computing embeddings interrupted"));
                        return;
                    }

                    if (!computeSuccess[0]) {
                        mainHandler.post(() -> callback.onError("Failed to compute embeddings"));
                        return;
                    }
                }

                // Build final lists of tags with valid embeddings
                List<Long> validTagIds = new ArrayList<>();
                List<String> validTagNames = new ArrayList<>();
                List<float[]> validEmbeddings = new ArrayList<>();

                for (int i = 0; i < n; i++) {
                    if (embeddings.get(i) != null) {
                        validTagIds.add(tagIds.get(i));
                        validTagNames.add(tagNames.get(i));
                        validEmbeddings.add(embeddings.get(i));
                    }
                }

                if (validTagIds.isEmpty()) {
                    // No valid embeddings, return original order
                    Log.w(TAG, "No valid embeddings found for any tag, returning original order");
                    mainHandler.post(() -> callback.onSuccess(tagIds, tagNames));
                    return;
                }

                Log.d(TAG, "Tags with valid embeddings: " + validTagIds.size() + " out of " + n);

                // 3. Compute NxN similarity matrix
                int m = validTagIds.size();
                float[][] similarityMatrix = computeSimilarityMatrix(validEmbeddings, validTagNames);

                // 4. Run greedy hierarchical clustering sort
                List<Integer> sortedIndices = greedyClusterSort(similarityMatrix);

                // 5. Build result lists in sorted order
                List<Long> sortedTagIds = new ArrayList<>(m);
                List<String> sortedTagNames = new ArrayList<>(m);

                for (int idx : sortedIndices) {
                    sortedTagIds.add(validTagIds.get(idx));
                    sortedTagNames.add(validTagNames.get(idx));
                }

                Log.d(TAG, "Sorted " + m + " tags by similarity");
                mainHandler.post(() -> callback.onSuccess(sortedTagIds, sortedTagNames));

            } catch (Exception e) {
                Log.e(TAG, "Error sorting tags by similarity", e);
                mainHandler.post(() -> callback.onError("Failed to sort tags: " + e.getMessage()));
            } finally {
                linkDao.close();
            }
        });
    }

    /**
     * Compute NxN cosine similarity matrix for a list of embeddings.
     *
     * @param embeddings List of embedding vectors
     * @return NxN similarity matrix where matrix[i][j] is similarity between i and j
     */
    private float[][] computeSimilarityMatrix(List<float[]> embeddings, List<String> tagNames) {
        int n = embeddings.size();
        float[][] matrix = new float[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    matrix[i][j] = 1.0f; // Self-similarity is 1
                } else if (j > i) {
                    float embeddingSim = EmbeddingService.computeCosineSimilarity(embeddings.get(i), embeddings.get(j));
                    float lexicalSim = lexicalSimilarity(tagNames.get(i), tagNames.get(j));

                    // Current model uses simplified tokenization, so blend lexical signals to stabilize CN tags.
                    float lexicalWeight = hasCjk(tagNames.get(i)) || hasCjk(tagNames.get(j)) ? 0.65f : 0.35f;
                    float sim = clamp01((1.0f - lexicalWeight) * normalizeCosine(embeddingSim) + lexicalWeight * lexicalSim);
                    sim = clamp01(sim + getCustomPairBoost(tagNames.get(i), tagNames.get(j)));

                    matrix[i][j] = sim;
                    matrix[j][i] = sim; // Symmetric
                }
            }
        }

        return matrix;
    }

    /**
     * Greedy hierarchical clustering sort algorithm.
     *
     * Algorithm:
     * 1. Find centroid (mean vector) of all embeddings, select tag closest to centroid as first
    * 2. Greedily append the most similar remaining tag to the current cluster chain.
     *
     * @param similarityMatrix NxN similarity matrix
     * @return List of indices in sorted order
     */
    private List<Integer> greedyClusterSort(float[][] similarityMatrix) {
        int n = similarityMatrix.length;
        if (n == 0) return new ArrayList<>();
        if (n == 1) {
            List<Integer> result = new ArrayList<>();
            result.add(0);
            return result;
        }

        // Step 1: Compute centroid (mean vector across all tags)
        // Find the tag closest to centroid as the first element
        float[] centroidSimilarities = new float[n];
        for (int i = 0; i < n; i++) {
            float sum = 0;
            for (int j = 0; j < n; j++) {
                sum += similarityMatrix[i][j];
            }
            centroidSimilarities[i] = sum / (n - 1); // Average similarity to all others
        }

        // Find tag with highest average similarity (closest to centroid)
        int firstIdx = 0;
        float maxAvgSim = centroidSimilarities[0];
        for (int i = 1; i < n; i++) {
            if (centroidSimilarities[i] > maxAvgSim) {
                maxAvgSim = centroidSimilarities[i];
                firstIdx = i;
            }
        }

        // Step 2: Greedily add remaining tags
        List<Integer> selected = new ArrayList<>();
        List<Integer> remaining = new ArrayList<>();

        selected.add(firstIdx);
        for (int i = 0; i < n; i++) {
            if (i != firstIdx) {
                remaining.add(i);
            }
        }

        while (!remaining.isEmpty()) {
            int best = -1;
            float bestScore = -Float.MAX_VALUE;
            int lastSelected = selected.get(selected.size() - 1);

            for (int candidate : remaining) {
                float localSim = similarityMatrix[candidate][lastSelected];

                // Mild global term to avoid short-sighted jumps when local ties happen.
                float sumSim = 0f;
                for (int selectedIdx : selected) {
                    sumSim += similarityMatrix[candidate][selectedIdx];
                }
                float avgSelectedSim = sumSim / selected.size();
                float score = localSim * 0.8f + avgSelectedSim * 0.2f;

                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }

            if (best != -1) {
                selected.add(best);
                remaining.remove(Integer.valueOf(best));
            } else {
                // Fallback: shouldn't happen, but handle gracefully
                selected.add(remaining.remove(0));
            }
        }

        return selected;
    }

    private float normalizeCosine(float cosine) {
        return clamp01((cosine + 1.0f) * 0.5f);
    }

    private float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    private boolean hasCjk(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(s.charAt(i));
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }

    private float lexicalSimilarity(String a, String b) {
        if (a == null || b == null) {
            return 0f;
        }

        String na = normalizeTag(a);
        String nb = normalizeTag(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return 0f;
        }
        if (na.equals(nb)) {
            return 1f;
        }

        Set<String> ca = toCodepointTokens(na);
        Set<String> cb = toCodepointTokens(nb);
        float charJaccard = jaccard(ca, cb);

        Set<String> ba = toBigrams(na);
        Set<String> bb = toBigrams(nb);
        float bigramJaccard = jaccard(ba, bb);

        return clamp01(charJaccard * 0.35f + bigramJaccard * 0.65f);
    }

    private String normalizeTag(String s) {
        return s.toLowerCase(Locale.ROOT).trim();
    }

    private Map<String, Float> loadCustomPairBoosts() {
        List<TagSimilarityOverrideStore.Rule> rules = TagSimilarityOverrideStore.loadRules(context);
        Map<String, Float> boosts = TagSimilarityOverrideStore.toBoostMap(rules);
        Log.i(TAG, "Loaded custom tag pair boosts: " + boosts.size());
        return boosts;
    }

    private float getCustomPairBoost(String a, String b) {
        if (customPairBoosts.isEmpty()) {
            return 0f;
        }
        Float boost = customPairBoosts.get(TagSimilarityOverrideStore.pairKey(a, b));
        if (boost == null) {
            return 0f;
        }
        return Math.max(0f, boost);
    }

    private Set<String> toCodepointTokens(String s) {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            tokens.add(new String(Character.toChars(cp)));
            i += Character.charCount(cp);
        }
        return tokens;
    }

    private Set<String> toBigrams(String s) {
        List<String> cps = new ArrayList<>();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            cps.add(new String(Character.toChars(cp)));
            i += Character.charCount(cp);
        }

        if (cps.size() <= 1) {
            return new HashSet<>(cps);
        }

        Set<String> bigrams = new HashSet<>();
        for (int i = 0; i < cps.size() - 1; i++) {
            bigrams.add(cps.get(i) + cps.get(i + 1));
        }
        return bigrams;
    }

    private float jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1f;
        }

        int intersection = 0;
        Set<String> smaller = a.size() <= b.size() ? a : b;
        Set<String> larger = a.size() <= b.size() ? b : a;
        for (String token : smaller) {
            if (larger.contains(token)) {
                intersection++;
            }
        }

        int union = a.size() + b.size() - intersection;
        if (union <= 0) {
            return 0f;
        }
        return (float) intersection / union;
    }

    /**
     * Compute and save embedding for a new tag.
     * This should be called when a new tag is created.
     *
     * @param tagId   The tag ID
     * @param tagName The tag name to compute embedding from
     */
    public void computeEmbeddingForNewTag(long tagId, String tagName) {
        executor.execute(() -> {
            try {
                // Ensure model is loaded
                if (!embeddingService.isModelLoaded()) {
                    Log.d(TAG, "Model not loaded, loading now...");
                    embeddingService.loadModel(new EmbeddingService.LoadCallback() {
                        @Override
                        public void onSuccess() {
                            encodeTag(tagId, tagName);
                        }

                        @Override
                        public void onError(@androidx.annotation.NonNull String error) {
                            Log.e(TAG, "Failed to load model: " + error);
                        }
                    });
                } else {
                    encodeTag(tagId, tagName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error computing embedding for new tag", e);
            }
        });
    }

    /**
     * Recursively compute embeddings for missing tags.
     */
    private void computeNextEmbedding(int index, List<Long> missingTagIds, List<String> missingTagNames,
                                      List<float[]> embeddings, Map<Long, Integer> tagIdToIndex, Runnable onComplete) {
        if (index >= missingTagIds.size()) {
            onComplete.run();
            return;
        }

        long tagId = missingTagIds.get(index);
        String tagName = missingTagNames.get(index);

        embeddingService.encode(tagName, new EmbeddingService.EncodeCallback() {
            @Override
            public void onSuccess(@androidx.annotation.NonNull float[] embedding) {
                // Save to database
                embeddingDao.open();
                embeddingDao.saveEmbedding(tagId, embedding);
                embeddingDao.close();

                // Cache in memory
                embeddingCache.put(tagId, embedding);

                // Update embeddings array at correct index
                Integer idx = tagIdToIndex.get(tagId);
                if (idx != null && idx < embeddings.size()) {
                    embeddings.set(idx, embedding);
                }

                Log.d(TAG, "Computed embedding for tag: " + tagName + " (" + (index + 1) + "/" + missingTagIds.size() + ")");
                computeNextEmbedding(index + 1, missingTagIds, missingTagNames, embeddings, tagIdToIndex, onComplete);
            }

            @Override
            public void onError(@androidx.annotation.NonNull String error) {
                Log.e(TAG, "Failed to encode tag '" + tagName + "': " + error);
                computeNextEmbedding(index + 1, missingTagIds, missingTagNames, embeddings, tagIdToIndex, onComplete);
            }
        });
    }

    private void encodeTag(long tagId, String tagName) {
        embeddingService.encode(tagName, new EmbeddingService.EncodeCallback() {
            @Override
            public void onSuccess(@androidx.annotation.NonNull float[] embedding) {
                // Save to database
                embeddingDao.open();
                embeddingDao.saveEmbedding(tagId, embedding);
                embeddingDao.close();

                // Cache in memory
                embeddingCache.put(tagId, embedding);

                Log.d(TAG, "Computed and saved embedding for tag: " + tagName);
            }

            @Override
            public void onError(@androidx.annotation.NonNull String error) {
                Log.e(TAG, "Failed to encode tag: " + error);
            }
        });
    }

    /**
     * Check if the embedding model should be unloaded based on TTL.
     * Call this periodically or when the app goes to background.
     */
    public void checkAndUnload() {
        executor.execute(() -> {
            if (embeddingService.shouldUnload()) {
                Log.d(TAG, "Model TTL expired, unloading...");
                embeddingService.unloadModel();
            }
        });
    }

    /**
     * Clear the in-memory embedding cache.
     * This can be called to free memory when needed.
     */
    public void clearCache() {
        embeddingCache.clear();
        Log.d(TAG, "Cleared embedding cache");
    }

    /**
     * Get cached embedding for a tag (if available).
     *
     * @param tagId The tag ID
     * @return The embedding if cached, null otherwise
     */
    public float[] getCachedEmbedding(long tagId) {
        return embeddingCache.get(tagId);
    }

    /**
     * Load all embeddings into memory cache from database.
     * This can improve performance when many operations are expected.
     */
    public void preloadEmbeddings() {
        executor.execute(() -> {
            try {
                embeddingDao.open();
                List<Long> tagIds = embeddingDao.getAllTagIdsWithEmbeddings();

                for (long tagId : tagIds) {
                    float[] embedding = embeddingDao.getEmbedding(tagId);
                    if (embedding != null) {
                        embeddingCache.put(tagId, embedding);
                    }
                }

                embeddingDao.close();
                Log.d(TAG, "Preloaded " + embeddingCache.size() + " embeddings into cache");
            } catch (Exception e) {
                Log.e(TAG, "Error preloading embeddings", e);
            }
        });
    }

    /**
     * Shutdown the executor service. Call this when the manager is no longer needed.
     */
    public void shutdown() {
        executor.shutdown();
    }
}