package io.github.hoanghaisol.blocktube.channelfilter.tokenizer;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SentencePiece Unigram tokenizer compatible with the HuggingFace tokenizer of
 * multilingual-e5 (XLM-R vocabulary).
 * <p>
 * Pipeline: NFKC (approximates the precompiled nmt_nfkc charsmap) → collapse spaces →
 * Metaspace (prefix "▁", split words) → Viterbi per word → {@code <s> ... </s>}.
 * Vocabulary comes from {@code tools/export_tokenizer.py}.
 */
public class UnigramTokenizer {
    private static final int MAGIC = 0x45355643;
    private static final char META = '▁';
    private static final float UNK_PENALTY = 10f;
    public static final int BOS_ID = 0;
    public static final int EOS_ID = 2;

    private final Map<String, Integer> mPieces;
    private final float[] mScores;
    private final int mUnkId;
    private final float mUnkScore;
    private final int mMaxPieceLength;

    private UnigramTokenizer(Map<String, Integer> pieces, float[] scores, int unkId, int maxPieceLength) {
        mPieces = pieces;
        mScores = scores;
        mUnkId = unkId;
        mMaxPieceLength = maxPieceLength;

        float min = 0;
        for (float score : scores) {
            min = Math.min(min, score);
        }
        mUnkScore = min - UNK_PENALTY;
    }

    public static UnigramTokenizer load(InputStream stream) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(stream, 1 << 16));

        if (in.readInt() != MAGIC) {
            throw new IOException("Not a vocab file");
        }
        in.readInt(); // version
        int unkId = in.readInt();
        int count = in.readInt();

        Map<String, Integer> pieces = new HashMap<>(count * 4 / 3 + 1);
        float[] scores = new float[count];
        int maxLength = 0;
        byte[] buffer = new byte[256];

        for (int id = 0; id < count; id++) {
            scores[id] = in.readFloat();
            int length = in.readShort();
            if (length > buffer.length) {
                buffer = new byte[length];
            }
            in.readFully(buffer, 0, length);
            String piece = new String(buffer, 0, length, StandardCharsets.UTF_8);
            pieces.put(piece, id);
            maxLength = Math.max(maxLength, piece.length());
        }

        return new UnigramTokenizer(pieces, scores, unkId, maxLength);
    }

    /**
     * @param maxLength total ids including {@code <s>} and {@code </s>} (e5: 512)
     */
    public int[] encode(String text, int maxLength) {
        List<Integer> ids = new ArrayList<>();
        ids.add(BOS_ID);

        for (String word : preTokenize(normalize(text))) {
            tokenizeWord(word, ids);
            if (ids.size() >= maxLength - 1) {
                break;
            }
        }

        List<Integer> result = ids.size() > maxLength - 1 ? ids.subList(0, maxLength - 1) : ids;
        int[] out = new int[result.size() + 1];
        for (int i = 0; i < result.size(); i++) {
            out[i] = result.get(i);
        }
        out[out.length - 1] = EOS_ID;
        return out;
    }

    static String normalize(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(normalized.length());

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                c = ' ';
            } else if (Character.isISOControl(c) || c == '​' || c == '﻿') {
                continue;
            }
            if (c == ' ' && sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
                continue; // Replace " {2,}" → " "
            }
            sb.append(c);
        }

        return sb.toString();
    }

    /**
     * Metaspace with add_prefix_space: spaces become "▁", a leading "▁" is added and
     * the text is split before every "▁".
     */
    static List<String> preTokenize(String text) {
        List<String> words = new ArrayList<>();
        if (text.isEmpty()) {
            return words;
        }

        String replaced = text.replace(' ', META);
        if (replaced.charAt(0) != META) {
            replaced = META + replaced;
        }

        int start = 0;
        for (int i = 1; i < replaced.length(); i++) {
            if (replaced.charAt(i) == META) {
                words.add(replaced.substring(start, i));
                start = i;
            }
        }
        words.add(replaced.substring(start));

        return words;
    }

    /**
     * Viterbi: best segmentation by summed piece scores. Unknown characters become one
     * unk piece; consecutive unks are fused (HF fuse_unk).
     */
    private void tokenizeWord(String word, List<Integer> out) {
        int n = word.length();
        float[] best = new float[n + 1];
        int[] prev = new int[n + 1];
        int[] pieceId = new int[n + 1];

        for (int i = 1; i <= n; i++) {
            best[i] = Float.NEGATIVE_INFINITY;
        }

        for (int start = 0; start < n; start++) {
            if (best[start] == Float.NEGATIVE_INFINITY) {
                continue;
            }

            boolean singleCharFound = false;
            int charEnd = start + Character.charCount(word.codePointAt(start));
            int maxEnd = Math.min(n, start + mMaxPieceLength);

            for (int end = charEnd; end <= maxEnd; end++) {
                if (end < n && Character.isLowSurrogate(word.charAt(end))) {
                    continue;
                }
                Integer id = mPieces.get(word.substring(start, end));
                if (id == null) {
                    continue;
                }
                if (end == charEnd) {
                    singleCharFound = true;
                }
                float score = best[start] + mScores[id];
                if (score > best[end]) {
                    best[end] = score;
                    prev[end] = start;
                    pieceId[end] = id;
                }
            }

            if (!singleCharFound) {
                float score = best[start] + mUnkScore;
                if (score > best[charEnd]) {
                    best[charEnd] = score;
                    prev[charEnd] = start;
                    pieceId[charEnd] = mUnkId;
                }
            }
        }

        List<Integer> reversed = new ArrayList<>();
        for (int end = n; end > 0; end = prev[end]) {
            int id = pieceId[end];
            if (id == mUnkId && !reversed.isEmpty() && reversed.get(reversed.size() - 1) == mUnkId) {
                continue;
            }
            reversed.add(id);
        }
        for (int i = reversed.size() - 1; i >= 0; i--) {
            out.add(reversed.get(i));
        }
    }
}
