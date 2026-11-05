package io.github.hoanghaisol.blocktube.channelfilter.keyword;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Whole-word keyword matching that ignores case and diacritics, so a keyword typed without
 * accents on a TV keyboard ("tro choi") still matches "Trò Chơi". Multi-word keywords match
 * as a phrase. "ma" matches "Ma", "má", "mã" but not "mang".
 */
public class KeywordMatcher {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final List<String> mKeywords = new ArrayList<>();

    public KeywordMatcher(Collection<String> keywords) {
        for (String keyword : keywords) {
            String normalized = normalize(keyword);
            if (!normalized.trim().isEmpty()) {
                mKeywords.add(normalized);
            }
        }
    }

    public boolean isEmpty() {
        return mKeywords.isEmpty();
    }

    /**
     * @return the first matching keyword (normalized), or null
     */
    public String findMatch(String text) {
        if (text == null || mKeywords.isEmpty()) {
            return null;
        }

        String normalized = normalize(text);
        for (String keyword : mKeywords) {
            if (normalized.contains(keyword)) {
                return keyword.trim();
            }
        }
        return null;
    }

    public boolean matches(String text) {
        return findMatch(text) != null;
    }

    /**
     * Lower case, no diacritics ("đ" → "d"), words separated by single spaces and padded with
     * spaces on both ends so {@code contains(" word ")} is a whole-word test.
     */
    public static String normalize(String text) {
        String result = text.toLowerCase().replace('đ', 'd');
        result = MARKS.matcher(Normalizer.normalize(result, Normalizer.Form.NFD)).replaceAll("");
        result = NON_WORD.matcher(result).replaceAll(" ").trim();
        return " " + result + " ";
    }
}
