package person.notfresh.readingshare.util;

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
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;

import person.notfresh.readingshare.db.DbConnection;
import person.notfresh.readingshare.db.LinkDao;
import person.notfresh.readingshare.embedding.TagEmbeddingDao;
import person.notfresh.readingshare.service.EmbeddingService;

/**
 * 标签推荐模块：按页面标题与已有标签的语义相似度推荐标签（加标签弹窗的「推荐」区）。
 *
 * <p>复用 {@link EmbeddingService}(bge-micro-v2 本地 ONNX)与
 * {@link TagEmbeddingDao} 已存的标签向量；混合打分公式与标签排序
 * (TagEmbeddingManager)同口径——中文对掺字符级相似度(权重 0.65)、英文对 0.35。</p>
 *
 * <p><b>边界</b>：只从已有标签里找，绝不新建标签；标题为空 / 没有带向量的标签 /
 * 模型失败 → 回调空列表（UI 整区不显示），不抛错。缺向量的老标签直接跳过，
 * 不现场补算（补算归 TagEmbeddingManager 管，这里不做）。</p>
 *
 * <p>唯一入口 {@link #recommend(Context, String, Set, RecommendCallback)}，
 * 结果回调在主线程。</p>
 */
public final class TagRecommender {

    private static final String TAG = "TagRecommender";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tag-recommender");
        t.setDaemon(true);
        return t;
    });

    /** 低于此分不推（避免完全不相干的硬凑）。 */
    private static final float MIN_SCORE = 0.30f;
    /** 最高分达到此值时，推荐数从 3 放宽到 5。 */
    private static final float EXPANDED_SCORE = 0.60f;
    private static final int TOP_N = 3;
    private static final int TOP_N_EXPANDED = 5;

    private TagRecommender() {
    }

    /** 推荐结果回调（主线程）：tags 为空列表 = 没有够格的推荐。 */
    public interface RecommendCallback {
        void onRecommended(List<String> tags);
    }

    /**
     * 按标题推荐标签。
     *
     * @param excluded 已在该链接上的标签，不参与推荐（可为 null）
     */
    public static void recommend(Context ctx, String title, Set<String> excluded,
                                 RecommendCallback callback) {
        final Context app = ctx.getApplicationContext();
        final String t = title == null ? "" : title.trim();
        final Set<String> excludedNames = excluded == null ? new HashSet<>() : excluded;
        if (t.isEmpty()) {
            MAIN.post(() -> callback.onRecommended(new ArrayList<>()));
            return;
        }
        IO.execute(() -> {
            List<String> result = new ArrayList<>();
            try {
                result = computeRecommendations(app, t, excludedNames);
            } catch (Exception e) {
                Log.e(TAG, "recommend failed", e);
            }
            final List<String> finalResult = result;
            MAIN.post(() -> callback.onRecommended(finalResult));
        });
    }

    private static List<String> computeRecommendations(Context app, String title,
                                                       Set<String> excludedNames) {
        LinkDao linkDao = new LinkDao(DbConnection.get(app).writable());
        Map<String, Integer> tagsWithCount = linkDao.getTagsWithCount();
        if (tagsWithCount.isEmpty()) {
            return new ArrayList<>();
        }

        // 标签名 -> 已存向量；缺向量的标签跳过
        TagEmbeddingDao embeddingDao = new TagEmbeddingDao(app);
        embeddingDao.open();
        Map<String, float[]> tagVectors = new HashMap<>();
        try {
            for (String name : tagsWithCount.keySet()) {
                if (excludedNames.contains(name)) {
                    continue;
                }
                long tagId = linkDao.getTagIdByName(name);
                if (tagId == -1) {
                    continue;
                }
                float[] vector = embeddingDao.getEmbedding(tagId);
                if (vector != null) {
                    tagVectors.put(name, vector);
                }
            }
        } finally {
            embeddingDao.close();
        }
        if (tagVectors.isEmpty()) {
            return new ArrayList<>();
        }

        float[] titleVector = encodeTitle(app, title);
        if (titleVector == null) {
            return new ArrayList<>();
        }

        // 打分：与 TagEmbeddingManager 同口径的混合公式
        List<Scored> scored = new ArrayList<>();
        for (Map.Entry<String, float[]> entry : tagVectors.entrySet()) {
            String name = entry.getKey();
            float cos = EmbeddingService.computeCosineSimilarity(titleVector, entry.getValue());
            float lexicalWeight = hasCjk(title) || hasCjk(name) ? 0.65f : 0.35f;
            float sim = clamp01((1f - lexicalWeight) * normalizeCosine(cos)
                    + lexicalWeight * lexicalSimilarity(title, name));
            if (sim >= MIN_SCORE) {
                scored.add(new Scored(name, sim));
            }
        }
        scored.sort((a, b) -> Float.compare(b.score, a.score));

        int limit = !scored.isEmpty() && scored.get(0).score >= EXPANDED_SCORE
                ? TOP_N_EXPANDED : TOP_N;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < scored.size() && out.size() < limit; i++) {
            out.add(scored.get(i).name);
        }
        Log.d(TAG, "recommend \"" + title + "\": " + out.size() + " hits from "
                + tagVectors.size() + " tagged candidates");
        return out;
    }

    /** 编码标题；模型加载/编码失败返回 null（推荐区不显示，不影响弹窗）。 */
    private static float[] encodeTitle(Context app, String title) {
        EmbeddingService service = EmbeddingService.getInstance(app);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        final float[][] vector = new float[1][];
        service.loadModel(new EmbeddingService.LoadCallback() {
            @Override
            public void onSuccess() {
                service.encode(title, new EmbeddingService.EncodeCallback() {
                    @Override
                    public void onSuccess(@NonNull float[] embedding) {
                        vector[0] = embedding;
                        ok.set(true);
                        latch.countDown();
                    }

                    @Override
                    public void onError(@NonNull String error) {
                        Log.e(TAG, "encode title failed: " + error);
                        latch.countDown();
                    }
                });
            }

            @Override
            public void onError(@NonNull String error) {
                Log.e(TAG, "load model failed: " + error);
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return ok.get() ? vector[0] : null;
    }

    private static final class Scored {
        final String name;
        final float score;

        Scored(String name, float score) {
            this.name = name;
            this.score = score;
        }
    }

    // ---- 以下打分辅助与 TagEmbeddingManager 同口径（为保推荐模块独立而重复，
    // ---- 两处公式改动需同步）。 ----

    private static float normalizeCosine(float cosine) {
        return clamp01((cosine + 1.0f) * 0.5f);
    }

    private static float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    private static boolean hasCjk(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    private static float lexicalSimilarity(String a, String b) {
        if (a == null || b == null) {
            return 0f;
        }
        String na = a.toLowerCase(Locale.ROOT).trim();
        String nb = b.toLowerCase(Locale.ROOT).trim();
        if (na.isEmpty() || nb.isEmpty()) {
            return 0f;
        }
        if (na.equals(nb)) {
            return 1f;
        }
        float charJaccard = jaccard(toCodepointTokens(na), toCodepointTokens(nb));
        float bigramJaccard = jaccard(toBigrams(na), toBigrams(nb));
        return clamp01(charJaccard * 0.35f + bigramJaccard * 0.65f);
    }

    private static Set<String> toCodepointTokens(String s) {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            tokens.add(new String(Character.toChars(cp)));
            i += Character.charCount(cp);
        }
        return tokens;
    }

    private static Set<String> toBigrams(String s) {
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

    private static float jaccard(Set<String> a, Set<String> b) {
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
}
