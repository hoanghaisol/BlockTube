package io.github.hoanghaisol.blocktube.channelfilter.keyword;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class KeywordMatcherTest {
    @Test
    public void ignoresCaseAndDiacritics() {
        KeywordMatcher matcher = new KeywordMatcher(Collections.singletonList("tro choi"));

        assertTrue(matcher.matches("Top 10 TRÒ CHƠI kinh dị"));
        assertTrue(matcher.matches("Trò Chơi Con Mực"));
    }

    @Test
    public void typedWithDiacriticsMatchesPlainText() {
        KeywordMatcher matcher = new KeywordMatcher(Collections.singletonList("Đánh nhau"));

        assertTrue(matcher.matches("danh nhau o truong hoc"));
    }

    @Test
    public void wholeWordsOnly() {
        KeywordMatcher matcher = new KeywordMatcher(Collections.singletonList("ma"));

        assertTrue(matcher.matches("Phim ma hay nhất"));
        assertTrue(matcher.matches("Chuyện má kể"));
        assertFalse(matcher.matches("Mang về nhà"));
        assertFalse(matcher.matches("Manga review"));
    }

    @Test
    public void punctuationSeparatesWords() {
        KeywordMatcher matcher = new KeywordMatcher(Collections.singletonList("prank"));

        assertTrue(matcher.matches("Subscribe! #funny #prank #troll"));
        assertTrue(matcher.matches("PRANK: gone wrong"));
        assertFalse(matcher.matches("pranking my friend"));
    }

    @Test
    public void phraseMustBeContiguous() {
        KeywordMatcher matcher = new KeywordMatcher(Collections.singletonList("phim kinh di"));

        assertTrue(matcher.matches("Top phim kinh dị 2025"));
        assertFalse(matcher.matches("Phim hài, không kinh dị"));
    }

    @Test
    public void reportsMatchingKeyword() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList("troll", "prank"));

        assertEquals("prank", matcher.findMatch("Best prank ever"));
        assertNull(matcher.findMatch("Cooking show"));
    }

    @Test
    public void blankKeywordsIgnored() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList("", "  ", "!!"));

        assertTrue(matcher.isEmpty());
        assertFalse(matcher.matches("anything"));
    }
}
