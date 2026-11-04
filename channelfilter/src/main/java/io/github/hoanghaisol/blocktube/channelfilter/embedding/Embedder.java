package io.github.hoanghaisol.blocktube.channelfilter.embedding;

import java.io.Closeable;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import io.github.hoanghaisol.blocktube.channelfilter.tokenizer.UnigramTokenizer;

/**
 * multilingual-e5-small sentence embedding: "query: " prefix, mean pooling, L2 normalization.
 * Heavy (≈150-250 MB native memory while open): create in a background job, close when done.
 * Needs Android 7.0+ (ONNX Runtime minSdk 24), see {@link #isSupported()}.
 */
public class Embedder implements Closeable {
    public static final int DIMENSIONS = 384;
    public static final int MAX_TOKENS = 512;
    private static final String PREFIX = "query: ";

    private final OrtEnvironment mEnv;
    private final OrtSession mSession;
    private final UnigramTokenizer mTokenizer;

    public Embedder(File model, UnigramTokenizer tokenizer, int threads) throws OrtException {
        mTokenizer = tokenizer;
        mEnv = OrtEnvironment.getEnvironment();

        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            // Keep the TV responsive: don't take every core
            options.setIntraOpNumThreads(threads);
            options.setInterOpNumThreads(1);
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            mSession = mEnv.createSession(model.getAbsolutePath(), options);
        }
    }

    public static boolean isSupported() {
        return android.os.Build.VERSION.SDK_INT >= 24;
    }

    /**
     * @return unit-length vector of {@link #DIMENSIONS} floats
     */
    public float[] embed(String text) throws OrtException {
        int[] ids = mTokenizer.encode(PREFIX + text, MAX_TOKENS);
        int length = ids.length;

        long[][] inputIds = new long[1][length];
        long[][] attentionMask = new long[1][length];
        long[][] tokenTypeIds = new long[1][length];
        for (int i = 0; i < length; i++) {
            inputIds[0][i] = ids[i];
            attentionMask[0][i] = 1;
        }

        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            inputs.put("input_ids", OnnxTensor.createTensor(mEnv, inputIds));
            inputs.put("attention_mask", OnnxTensor.createTensor(mEnv, attentionMask));
            inputs.put("token_type_ids", OnnxTensor.createTensor(mEnv, tokenTypeIds));

            try (OrtSession.Result result = mSession.run(inputs)) {
                float[][] hidden = ((float[][][]) result.get(0).getValue())[0];
                return meanPoolNormalized(hidden);
            }
        } finally {
            for (OnnxTensor tensor : inputs.values()) {
                tensor.close();
            }
        }
    }

    private static float[] meanPoolNormalized(float[][] hidden) {
        float[] vector = new float[DIMENSIONS];
        for (float[] token : hidden) {
            for (int d = 0; d < DIMENSIONS; d++) {
                vector[d] += token[d];
            }
        }

        double norm = 0;
        for (int d = 0; d < DIMENSIONS; d++) {
            vector[d] /= hidden.length;
            norm += vector[d] * vector[d];
        }

        float scale = (float) (1 / Math.sqrt(norm));
        for (int d = 0; d < DIMENSIONS; d++) {
            vector[d] *= scale;
        }

        return vector;
    }

    @Override
    public void close() {
        try {
            mSession.close();
        } catch (OrtException e) {
            // Nothing to do
        }
    }
}
