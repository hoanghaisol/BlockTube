package io.github.hoanghaisol.blocktube.channelfilter.scoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimilarityScorerTest {
    /** Mimics e5: every vector shares a big common component, topics differ slightly */
    private static float[] vector(float common, float prank, float cooking, float science) {
        float[] v = {common, prank, cooking, science};
        float norm = (float) Math.sqrt(common * common + prank * prank + cooking * cooking + science * science);
        for (int i = 0; i < v.length; i++) {
            v[i] /= norm;
        }
        return v;
    }

    private static Map<String, float[]> vectors() {
        Map<String, float[]> vectors = new HashMap<>();
        vectors.put("blockedPrank", vector(1, 0.3f, 0, 0));
        vectors.put("prank2", vector(1, 0.25f, 0.02f, 0));
        vectors.put("cooking", vector(1, 0, 0.3f, 0));
        vectors.put("science", vector(1, 0, 0, 0.3f));
        vectors.put("science2", vector(1, 0.02f, 0, 0.28f));
        return vectors;
    }

    @Test
    public void ranksSameTopicFirstAndDropsUnrelated() {
        List<SimilarityScorer.Result> results = SimilarityScorer.rank(vectors(),
                Collections.singletonList("blockedPrank"), Collections.emptyList(),
                Arrays.asList("prank2", "cooking", "science", "science2"));

        assertEquals(1, results.size());
        assertEquals("prank2", results.get(0).channelId);
        assertEquals("blockedPrank", results.get(0).closestBlockedId);
    }

    @Test
    public void dismissedChannelVetoesCloserCandidates() {
        Map<String, float[]> vectors = vectors();
        vectors.put("dismissedPrank", vector(1, 0.26f, 0.02f, 0));

        List<SimilarityScorer.Result> results = SimilarityScorer.rank(vectors,
                Collections.singletonList("blockedPrank"), Collections.singletonList("dismissedPrank"),
                Arrays.asList("prank2", "cooking", "science"));

        assertTrue(results.isEmpty());
    }

    @Test
    public void nothingBlockedNothingSuggested() {
        assertTrue(SimilarityScorer.rank(vectors(), Collections.emptyList(), Collections.emptyList(),
                Arrays.asList("prank2")).isEmpty());
    }
}
