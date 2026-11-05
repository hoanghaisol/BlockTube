package io.github.hoanghaisol.blocktube.channelfilter.scoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Ranks candidate channels by similarity to blocked channels.
 * <p>
 * Raw e5 cosine is high for any pair (0.78-0.85 for unrelated channels), so vectors are
 * centered on the mean of every known channel before comparing. A candidate's score is the
 * max similarity to any blocked channel (not to their average, which would blur different
 * topics). A dismissed channel that is closer than every blocked one vetoes the candidate.
 */
public class SimilarityScorer {
    /**
     * Centered cosine. Emulator data: same-topic channels 0.11-0.62, wrong matches up to 0.09.
     * Suggestions may be auto-blocked, so err on the side of missing a channel.
     */
    public static final float MIN_SCORE = 0.12f;
    public static final int MAX_RESULTS = 10;

    public static class Result {
        public final String channelId;
        public final float score;
        public final String closestBlockedId;

        Result(String channelId, float score, String closestBlockedId) {
            this.channelId = channelId;
            this.score = score;
            this.closestBlockedId = closestBlockedId;
        }
    }

    /**
     * @param vectors   unit vectors of every known channel (blocked, dismissed and candidates)
     * @param blocked   ids of blocked channels
     * @param dismissed ids the user chose to ignore (used as counterexamples)
     * @param candidates ids to rank
     */
    public static List<Result> rank(Map<String, float[]> vectors, List<String> blocked, List<String> dismissed,
                                    List<String> candidates) {
        List<Result> results = new ArrayList<>();
        if (vectors.size() < 2 || blocked.isEmpty()) {
            return results;
        }

        float[] mean = mean(vectors.values());

        List<float[]> blockedVectors = centered(vectors, blocked, mean);
        List<float[]> dismissedVectors = centered(vectors, dismissed, mean);
        List<String> blockedIds = new ArrayList<>();
        for (String id : blocked) {
            if (vectors.containsKey(id)) {
                blockedIds.add(id);
            }
        }

        for (String candidate : candidates) {
            float[] vector = vectors.get(candidate);
            if (vector == null || blocked.contains(candidate) || dismissed.contains(candidate)) {
                continue;
            }
            float[] centered = center(vector, mean);

            float best = -1;
            int bestIndex = -1;
            for (int i = 0; i < blockedVectors.size(); i++) {
                float similarity = dot(centered, blockedVectors.get(i));
                if (similarity > best) {
                    best = similarity;
                    bestIndex = i;
                }
            }

            float closestDismissed = -1;
            for (float[] dismissedVector : dismissedVectors) {
                closestDismissed = Math.max(closestDismissed, dot(centered, dismissedVector));
            }

            if (best >= MIN_SCORE && best > closestDismissed) {
                results.add(new Result(candidate, best, blockedIds.get(bestIndex)));
            }
        }

        Collections.sort(results, (a, b) -> Float.compare(b.score, a.score));
        return results.size() > MAX_RESULTS ? new ArrayList<>(results.subList(0, MAX_RESULTS)) : results;
    }

    private static float[] mean(Iterable<float[]> vectors) {
        float[] mean = null;
        int count = 0;
        for (float[] vector : vectors) {
            if (mean == null) {
                mean = new float[vector.length];
            }
            for (int d = 0; d < vector.length; d++) {
                mean[d] += vector[d];
            }
            count++;
        }
        for (int d = 0; d < mean.length; d++) {
            mean[d] /= count;
        }
        return mean;
    }

    private static List<float[]> centered(Map<String, float[]> vectors, List<String> ids, float[] mean) {
        List<float[]> result = new ArrayList<>();
        for (String id : ids) {
            float[] vector = vectors.get(id);
            if (vector != null) {
                result.add(center(vector, mean));
            }
        }
        return result;
    }

    private static float[] center(float[] vector, float[] mean) {
        float[] result = new float[vector.length];
        double norm = 0;
        for (int d = 0; d < vector.length; d++) {
            result[d] = vector[d] - mean[d];
            norm += result[d] * result[d];
        }
        float scale = norm > 0 ? (float) (1 / Math.sqrt(norm)) : 0;
        for (int d = 0; d < vector.length; d++) {
            result[d] *= scale;
        }
        return result;
    }

    private static float dot(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}
