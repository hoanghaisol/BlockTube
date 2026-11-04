package io.github.hoanghaisol.blocktube.channelfilter.tokenizer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Compares ids with the HuggingFace tokenizer (fixture from tools/export_tokenizer.py).
 * Needs the vocabulary, which is not committed:
 * <pre>
 * python3 channelfilter/tools/export_tokenizer.py tokenizer.json channelfilter/build/test-assets/vocab.bin
 * </pre>
 */
public class UnigramTokenizerTest {
    private static UnigramTokenizer sTokenizer;

    private static class Case {
        String text;
        int[] ids;
    }

    @BeforeClass
    public static void setUp() throws Exception {
        File vocab = new File(System.getProperty("channelfilter.vocab", ""));
        assumeTrue("Vocab not exported: " + vocab, vocab.isFile());

        try (InputStream in = new FileInputStream(vocab)) {
            sTokenizer = UnigramTokenizer.load(in);
        }
    }

    @Test
    public void matchesReferenceTokenizer() throws Exception {
        List<Case> cases;
        try (InputStream in = getClass().getResourceAsStream("/tokenizer_fixture.json")) {
            cases = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), new TypeToken<List<Case>>() {}.getType());
        }

        int failed = 0;
        StringBuilder report = new StringBuilder();
        for (Case c : cases) {
            int[] actual = sTokenizer.encode(c.text, 512);
            if (!Arrays.equals(c.ids, actual)) {
                failed++;
                report.append("\n").append(c.text)
                        .append("\n  expected ").append(Arrays.toString(c.ids))
                        .append("\n  actual   ").append(Arrays.toString(actual));
            }
        }

        assertEquals("Mismatches:" + report, 0, failed);
    }

    @Test
    public void truncatesKeepingSpecialTokens() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            text.append("word").append(i).append(' ');
        }

        int[] ids = sTokenizer.encode(text.toString(), 512);

        assertEquals(512, ids.length);
        assertEquals(UnigramTokenizer.BOS_ID, ids[0]);
        assertEquals(UnigramTokenizer.EOS_ID, ids[511]);
    }

    @Test
    public void emptyText() {
        assertArrayEquals(new int[] {UnigramTokenizer.BOS_ID, UnigramTokenizer.EOS_ID}, sTokenizer.encode("", 512));
    }
}
