package person.notfresh.readingshare.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

/**
 * EmbeddingService handles loading the BGE-Micro ONNX model and generating embeddings.
 *
 * IMPORTANT: This implementation uses ONNX Runtime Mobile API.
 *
 * Model details:
 * - Path: models/bge-micro-v2.onnx in assets
 * - Input: input_ids (int64), attention_mask (int64)
 * - Output: pooled embedding (384 dimensions for bge-micro-v2)
 * - Uses mean pooling on last hidden state to get sentence embedding
 *
 * Tokenization: This implementation uses a simplified whitespace tokenizer as fallback.
 * For production, consider implementing proper SentencePiece/BPE tokenization.
 */
public class EmbeddingService {
    private static final String TAG = "EmbeddingService";
    private static final String MODEL_PATH = "models/bge-micro-v2.onnx";
    private static final int MAX_SEQ_LENGTH = 256; // BGE-Micro typical max length
    private static final long MODEL_TTL_MS = 5 * 60 * 1000; // 5 minutes

    // Singleton instance
    private static volatile EmbeddingService instance;
    private static volatile Context appContext;

    // ONNX Runtime Mobile components
    private OrtEnvironment ortEnvironment;
    private OrtSession ortSession;

    // Model state
    private volatile boolean modelLoaded = false;
    private volatile long lastUsedTimestamp = 0;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Input/output names - these may need adjustment based on the actual model
    private static final String INPUT_IDS_NAME = "input_ids";
    private static final String ATTENTION_MASK_NAME = "attention_mask";
    private static final String TOKEN_TYPE_IDS_NAME = "token_type_ids";
    private static final String OUTPUT_NAME = "output";

    private EmbeddingService() {
        // Private constructor for singleton
    }

    /**
     * Get the singleton instance of EmbeddingService.
     * @param context Application context (will be used for assets access)
     * @return EmbeddingService instance
     */
    public static EmbeddingService getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (EmbeddingService.class) {
                if (instance == null) {
                    instance = new EmbeddingService();
                    appContext = context.getApplicationContext();
                }
            }
        }
        return instance;
    }

    /**
     * Load the ONNX model from assets.
     * @param callback Callback to receive load result
     */
    public void loadModel(@NonNull LoadCallback callback) {
        executor.execute(() -> {
            if (modelLoaded) {
                Log.i(TAG, "Model already loaded");
                callback.onSuccess();
                return;
            }

            try {
                Log.i(TAG, "Loading ONNX model from assets: " + MODEL_PATH);

                // Initialize OrtEnvironment (ONNX Runtime Mobile API)
                ortEnvironment = OrtEnvironment.getEnvironment();
                Log.d(TAG, "OrtEnvironment initialized");

                // Create session options
                OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();

                // Load model bytes from assets (ONNX Runtime Java API expects path or byte[])
                byte[] modelBytes;
                try (InputStream modelStream = appContext.getAssets().open(MODEL_PATH)) {
                    modelBytes = readAllBytes(modelStream);
                }

                // Create the session - this loads the model
                ortSession = ortEnvironment.createSession(modelBytes, sessionOptions);
                Log.i(TAG, "Model loaded successfully. Session created.");

                // Log input/output info for debugging
                for (String inputName : ortSession.getInputNames()) {
                    Log.d(TAG, "Model input: " + inputName);
                }
                for (String outputName : ortSession.getOutputNames()) {
                    Log.d(TAG, "Model output: " + outputName);
                }

                modelLoaded = true;
                lastUsedTimestamp = System.currentTimeMillis();
                callback.onSuccess();

            } catch (IOException e) {
                Log.e(TAG, "Failed to load model from assets", e);
                callback.onError("Failed to load model: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize ONNX session", e);
                callback.onError("Failed to initialize ONNX session: " + e.getMessage());
            }
        });
    }

    /**
     * Unload the model and release resources.
     */
    public void unloadModel() {
        executor.execute(() -> {
            try {
                if (ortSession != null) {
                    ortSession.close();
                    ortSession = null;
                    Log.i(TAG, "OrtSession closed");
                }
                if (ortEnvironment != null) {
                    ortEnvironment.close();
                    ortEnvironment = null;
                    Log.i(TAG, "OrtEnvironment closed");
                }
                modelLoaded = false;
                Log.i(TAG, "Model unloaded successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error unloading model", e);
            }
        });
    }

    /**
     * Generate embedding for the given text.
     * @param text Input text to encode
     * @param callback Callback to receive encoding result
     */
    public void encode(@NonNull String text, @NonNull EncodeCallback callback) {
        executor.execute(() -> {
            if (!modelLoaded || ortSession == null || ortEnvironment == null) {
                callback.onError("Model not loaded. Call loadModel() first.");
                return;
            }

            try {
                lastUsedTimestamp = System.currentTimeMillis();

                // Tokenize input text
                long[] tokenIds = tokenize(text);
                long[] attentionMask = new long[tokenIds.length];
                for (int i = 0; i < attentionMask.length; i++) {
                    attentionMask[i] = 1L;
                }

                // Pad or truncate to MAX_SEQ_LENGTH
                long[] inputIdsPadded = padOrTruncate(tokenIds, MAX_SEQ_LENGTH);
                long[] attentionMaskPadded = padOrTruncate(attentionMask, MAX_SEQ_LENGTH);
                long[] tokenTypeIdsPadded = new long[MAX_SEQ_LENGTH];

                // Create input tensors (int64)
                long[] inputIdsShape = {1, MAX_SEQ_LENGTH};
                long[] attentionMaskShape = {1, MAX_SEQ_LENGTH};

                OnnxTensor inputIdsTensor = OnnxTensor.createTensor(
                        ortEnvironment, LongBuffer.wrap(inputIdsPadded), inputIdsShape);
                OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(
                        ortEnvironment, LongBuffer.wrap(attentionMaskPadded), attentionMaskShape);
                OnnxTensor tokenTypeIdsTensor = null;

                // Prepare inputs map
                Map<String, OnnxTensor> inputs = new HashMap<>();
                inputs.put(INPUT_IDS_NAME, inputIdsTensor);
                inputs.put(ATTENTION_MASK_NAME, attentionMaskTensor);
                if (ortSession.getInputNames().contains(TOKEN_TYPE_IDS_NAME)) {
                    tokenTypeIdsTensor = OnnxTensor.createTensor(
                        ortEnvironment, LongBuffer.wrap(tokenTypeIdsPadded), attentionMaskShape);
                    inputs.put(TOKEN_TYPE_IDS_NAME, tokenTypeIdsTensor);
                }

                // Run inference
                OrtSession.RunOptions runOptions = new OrtSession.RunOptions();
                OrtSession.Result outputs = null;

                try {
                    outputs = ortSession.run(inputs, runOptions);

                    // Extract embedding from output
                    OnnxValue outputValue = outputs.get(OUTPUT_NAME).orElse(null);
                    if (outputValue == null && outputs.size() > 0) {
                        // Fallback to first output if configured output name is unavailable
                        outputValue = outputs.get(0);
                    }
                    if (outputValue == null) {
                        throw new IllegalStateException("Model returned no outputs");
                    }

                    float[] embedding = extractEmbedding(outputValue);

                    // Apply mean pooling
                    float[] pooledEmbedding = meanPooling(embedding, attentionMaskPadded);

                    callback.onSuccess(pooledEmbedding);
                    Log.d(TAG, "Encoding completed successfully. Embedding dimension: " + pooledEmbedding.length);
                } finally {
                    if (outputs != null) {
                        outputs.close();
                    }
                    inputIdsTensor.close();
                    attentionMaskTensor.close();
                    if (tokenTypeIdsTensor != null) {
                        tokenTypeIdsTensor.close();
                    }
                    runOptions.close();
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to encode text", e);
                callback.onError("Encoding failed: " + e.getMessage());
            }
        });
    }

    /**
     * Check if the model should be unloaded based on TTL expiration.
     * @return true if model should be unloaded, false otherwise
     */
    public boolean shouldUnload() {
        if (!modelLoaded) {
            return true;
        }
        long elapsed = System.currentTimeMillis() - lastUsedTimestamp;
        return elapsed > MODEL_TTL_MS;
    }

    /**
     * Check if the model is currently loaded.
     * @return true if model is loaded, false otherwise
     */
    public boolean isModelLoaded() {
        return modelLoaded;
    }

    /**
     * Compute cosine similarity between two embeddings.
     * @param a First embedding vector
     * @param b Second embedding vector
     * @return Cosine similarity score (between -1 and 1)
     */
    public static float computeCosineSimilarity(@NonNull float[] a, @NonNull float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Embedding vectors must have the same dimension");
        }

        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        normA = (float) Math.sqrt(normA);
        normB = (float) Math.sqrt(normB);

        if (normA == 0 || normB == 0) {
            return 0.0f;
        }

        return dotProduct / (normA * normB);
    }

    // ==================== Tokenization (Simplified) ====================

    /**
     * Simple whitespace tokenizer - converts text to token IDs.
     *
     * IMPORTANT: This is a simplified fallback implementation.
     * For production use, consider implementing proper tokenization:
     * - SentencePiece tokenizer (used by BGE-Micro)
     * - Or include pre-trained tokenizer files in assets
     *
     * This simplified version maps each character to a token ID based on
     * a basic vocabulary. It will not produce accurate embeddings but
     * allows the service to function for testing purposes.
     *
     * @param text Input text
     * @return Token IDs array
     */
    private long[] tokenize(String text) {
        // Simplified tokenization: map characters to IDs
        // This is a placeholder - real BGE uses SentencePiece

        // Truncate text if too long (rough approximation: 1 token per character)
        if (text.length() > MAX_SEQ_LENGTH - 2) { // -2 for [CLS] and [SEP]
            text = text.substring(0, MAX_SEQ_LENGTH - 2);
        }

        // Use a simple character-based approach
        // In production, this should use proper SentencePiece tokenization
        int length = text.length();
        long[] tokenIds = new long[length];

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            // Map character to a pseudo token ID (simplified)
            // Real implementation would use proper vocabulary lookup
            tokenIds[i] = mapCharToTokenId(c);
        }

        return tokenIds;
    }

    /**
     * Map a character to a token ID.
     * This is a placeholder for proper vocabulary lookup.
     */
    private long mapCharToTokenId(char c) {
        // Simplified mapping - returns a pseudo token ID based on character code
        // For production, implement proper vocabulary lookup
        if (c == ' ' || c == '\t' || c == '\n') {
            return 3; // Common whitespace token ID in many tokenizers
        }
        if (c >= 'a' && c <= 'z') {
            return 100 + (c - 'a'); // Simple mapping for lowercase
        }
        if (c >= 'A' && c <= 'Z') {
            return 200 + (c - 'A'); // Simple mapping for uppercase
        }
        if (c >= '0' && c <= '9') {
            return 300 + (c - '0'); // Simple mapping for digits
        }
        // Default mapping for other characters
        return (long) (c % 500) + 400;
    }

    /**
     * Pad or truncate array to specified length.
     */
    private long[] padOrTruncate(long[] input, int targetLength) {
        long[] result = new long[targetLength];
        int copyLength = Math.min(input.length, targetLength);
        System.arraycopy(input, 0, result, 0, copyLength);
        // Padding is already zeros
        return result;
    }

    /**
     * Read all bytes from an InputStream (compatible with Java 8 source level).
     */
    private byte[] readAllBytes(@NonNull InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        return outputStream.toByteArray();
    }

    /**
     * Extract embedding tensor from model output.
     */
    private float[] extractEmbedding(OnnxValue outputValue) throws Exception {
        // The output is typically [1, seq_len, hidden_size] or [1, hidden_size] for pooled
        // This extracts and flattens the tensor data
        if (!(outputValue instanceof OnnxTensor)) {
            throw new Exception("Expected OnnxTensor output but got: " + outputValue.getClass().getName());
        }

        OnnxTensor outputTensor = (OnnxTensor) outputValue;
        TensorInfo info = outputTensor.getInfo();
        long[] shape = info.getShape();
        long numElements = 1;
        for (long dim : shape) {
            numElements *= dim;
        }

        // Copy data from FloatBuffer safely (may be direct buffer without backing array)
        FloatBuffer floatBuffer = outputTensor.getFloatBuffer();
        float[] data = new float[floatBuffer.remaining()];
        floatBuffer.get(data);

        // Flatten if multi-dimensional
        float[] result = new float[(int) numElements];
        System.arraycopy(data, 0, result, 0, (int) numElements);
        return result;
    }

    /**
     * Apply mean pooling to sequence embeddings.
     * Takes the mean of all token embeddings weighted by attention mask.
     *
     * @param embeddings Flattened embeddings [seq_len * hidden_size]
     * @param attentionMask Attention mask [seq_len]
     * @return Pooled embedding [hidden_size]
     */
    private float[] meanPooling(float[] embeddings, long[] attentionMask) {
        // Embeddings shape is [seq_len, hidden_size]
        int hiddenSize = 384; // BGE-Micro v2 output dimension
        int seqLen = embeddings.length / hiddenSize;

        float[] pooled = new float[hiddenSize];
        float sumWeight = 0;

        for (int i = 0; i < seqLen && i < attentionMask.length; i++) {
            if (attentionMask[i] == 1) {
                for (int j = 0; j < hiddenSize; j++) {
                    pooled[j] += embeddings[i * hiddenSize + j];
                }
                sumWeight += 1;
            }
        }

        if (sumWeight > 0) {
            for (int i = 0; i < hiddenSize; i++) {
                pooled[i] /= sumWeight;
            }
        }

        return pooled;
    }

    // ==================== Inner Interfaces ====================

    /**
     * Callback interface for model load operations.
     */
    public interface LoadCallback {
        void onSuccess();
        void onError(@NonNull String error);
    }

    /**
     * Callback interface for encoding operations.
     */
    public interface EncodeCallback {
        void onSuccess(@NonNull float[] embedding);
        void onError(@NonNull String error);
    }
}
