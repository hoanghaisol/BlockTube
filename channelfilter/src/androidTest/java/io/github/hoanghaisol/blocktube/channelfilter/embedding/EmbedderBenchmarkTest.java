package io.github.hoanghaisol.blocktube.channelfilter.embedding;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.Debug;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.hoanghaisol.blocktube.channelfilter.tokenizer.UnigramTokenizer;

/**
 * Speed/memory check on a real device. Push the model first:
 * <pre>
 * adb shell mkdir -p /data/local/tmp/blocktube
 * adb push model_quantized.onnx vocab.bin /data/local/tmp/blocktube/
 * ./gradlew :channelfilter:connectedDebugAndroidTest
 * adb logcat -s ChannelFilterBench
 * </pre>
 */
@RunWith(AndroidJUnit4.class)
public class EmbedderBenchmarkTest {
    private static final String TAG = "ChannelFilterBench";
    private static final File DIR = new File("/data/local/tmp/blocktube");

    private static final Map<String, String> CHANNELS = new LinkedHashMap<>();

    static {
        CHANNELS.put("prank_vi", "Kênh Troll Vui. Troll người yêu cực gắt; Prank bạn thân bằng gián giả; Thử thách 24h ăn mì cay; Troll mẹ và cái kết bất ngờ; Prank ăn kem ớt");
        CHANNELS.put("prank_vi2", "Hài Lầy TV. Thử thách 100 tầng mì cay khổng lồ; Troll em gái khóc thét; Prank bạn cùng phòng nửa đêm; Giả làm ma dọa bạn");
        CHANNELS.put("cooking_vi", "Bếp Nhà Mình. Cách nấu phở bò chuẩn vị Hà Nội; Làm bánh flan mềm mịn; Canh chua cá lóc miền Tây; Thịt kho tàu ngày Tết");
        CHANNELS.put("science_vi", "Khoa Học Vui. Vì sao bầu trời màu xanh; Hố đen vũ trụ là gì; Thí nghiệm núi lửa baking soda; Lịch sử kính thiên văn");
    }

    @Test
    public void benchmark() throws Exception {
        File model = new File(DIR, "model_quantized.onnx");
        File vocab = new File(DIR, "vocab.bin");
        assumeTrue("Push model and vocab to " + DIR, model.canRead() && vocab.canRead());

        long nativeBefore = Debug.getNativeHeapAllocatedSize();
        long t0 = SystemClock.elapsedRealtime();

        UnigramTokenizer tokenizer;
        try (InputStream in = new FileInputStream(vocab)) {
            tokenizer = UnigramTokenizer.load(in);
        }
        long t1 = SystemClock.elapsedRealtime();

        try (Embedder embedder = new Embedder(model, tokenizer, 2)) {
            long t2 = SystemClock.elapsedRealtime();

            Map<String, float[]> vectors = new LinkedHashMap<>();
            for (Map.Entry<String, String> channel : CHANNELS.entrySet()) {
                vectors.put(channel.getKey(), embedder.embed(channel.getValue()));
            }
            long t3 = SystemClock.elapsedRealtime();

            // Worst case: a full 512-token profile
            StringBuilder longText = new StringBuilder();
            while (longText.length() < 4000) {
                longText.append("Troll người yêu cực gắt và cái kết bất ngờ; ");
            }
            embedder.embed(longText.toString());
            long t4 = SystemClock.elapsedRealtime();

            Debug.MemoryInfo memory = new Debug.MemoryInfo();
            Debug.getMemoryInfo(memory);

            Log.i(TAG, String.format("vocab load %d ms, model load %d ms, short profile %d ms avg, 512 tokens %d ms",
                    t1 - t0, t2 - t1, (t3 - t2) / CHANNELS.size(), t4 - t3));
            Log.i(TAG, String.format("native heap +%d MB, total PSS %d MB",
                    (Debug.getNativeHeapAllocatedSize() - nativeBefore) >> 20, memory.getTotalPss() >> 10));

            float related = dot(vectors.get("prank_vi"), vectors.get("prank_vi2"));
            float unrelated = dot(vectors.get("prank_vi"), vectors.get("cooking_vi"));
            Log.i(TAG, String.format("cosine prank/prank %.3f, prank/cooking %.3f (Python: 0.925, 0.849)", related, unrelated));

            assertTrue("Embeddings differ from the Python reference", Math.abs(related - 0.925f) < 0.01f && Math.abs(unrelated - 0.849f) < 0.01f);
        }
    }

    private static float dot(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}
