package io.github.hoanghaisol.blocktube.channelfilter.store;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plain-file storage, all in memory (a few hundred channels):
 * <ul>
 *     <li>seen channels: LRU of channelId → name, filled while browsing</li>
 *     <li>vectors: channelId → embedding + creation time</li>
 *     <li>dismissed: channels the user said "ignore" for (never suggested again)</li>
 *     <li>suggestions: last ranking result</li>
 * </ul>
 * Thread-safe; every method locks the store.
 */
public class ChannelStore {
    private static final int VERSION = 1;
    private static final int MAX_SEEN = 500;

    public static class Suggestion {
        public final String channelId;
        public final String channelName;
        public final float score;
        public final String closestBlockedName;

        public Suggestion(String channelId, String channelName, float score, String closestBlockedName) {
            this.channelId = channelId;
            this.channelName = channelName;
            this.score = score;
            this.closestBlockedName = closestBlockedName;
        }
    }

    private static class Vector {
        final long createdMs;
        final float[] values;

        Vector(long createdMs, float[] values) {
            this.createdMs = createdMs;
            this.values = values;
        }
    }

    private final File mDir;
    private final LinkedHashMap<String, String> mSeen = new LinkedHashMap<String, String>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_SEEN;
        }
    };
    private final Map<String, Vector> mVectors = new HashMap<>();
    private final Set<String> mDismissed = new HashSet<>();
    private final List<Suggestion> mSuggestions = new ArrayList<>();
    private boolean mSeenDirty;

    public ChannelStore(File dir) {
        mDir = dir;
        load();
    }

    // region Seen channels

    /**
     * @return true if the channel is new
     */
    public synchronized boolean addSeen(String channelId, String channelName) {
        String previous = mSeen.put(channelId, channelName);
        mSeenDirty |= previous == null || (channelName != null && !channelName.equals(previous));
        return previous == null;
    }

    public synchronized Map<String, String> getSeen() {
        return new LinkedHashMap<>(mSeen);
    }

    public synchronized String getName(String channelId) {
        return mSeen.get(channelId);
    }

    // endregion

    // region Vectors

    public synchronized float[] getVector(String channelId, long maxAgeMs) {
        Vector vector = mVectors.get(channelId);
        return vector != null && System.currentTimeMillis() - vector.createdMs < maxAgeMs ? vector.values : null;
    }

    public synchronized void putVector(String channelId, float[] values) {
        mVectors.put(channelId, new Vector(System.currentTimeMillis(), values));
    }

    public synchronized Map<String, float[]> getVectors(Set<String> ids) {
        Map<String, float[]> result = new HashMap<>();
        for (String id : ids) {
            Vector vector = mVectors.get(id);
            if (vector != null) {
                result.put(id, vector.values);
            }
        }
        return result;
    }

    /**
     * Drop vectors of channels we no longer care about.
     */
    public synchronized void retainVectors(Set<String> ids) {
        mVectors.keySet().retainAll(ids);
    }

    // endregion

    // region Dismissed and suggestions

    public synchronized void dismiss(String channelId) {
        mDismissed.add(channelId);
        removeSuggestion(channelId);
    }

    public synchronized Set<String> getDismissed() {
        return new HashSet<>(mDismissed);
    }

    public synchronized void setSuggestions(List<Suggestion> suggestions) {
        mSuggestions.clear();
        mSuggestions.addAll(suggestions);
    }

    public synchronized List<Suggestion> getSuggestions() {
        return new ArrayList<>(mSuggestions);
    }

    public synchronized void removeSuggestion(String channelId) {
        for (int i = mSuggestions.size() - 1; i >= 0; i--) {
            if (mSuggestions.get(i).channelId.equals(channelId)) {
                mSuggestions.remove(i);
            }
        }
    }

    public synchronized void clear() {
        mSeen.clear();
        mVectors.clear();
        mDismissed.clear();
        mSuggestions.clear();
        mSeenDirty = true;
    }

    // endregion

    // region Persistence

    public synchronized boolean isSeenDirty() {
        return mSeenDirty;
    }

    public synchronized void save() {
        if (!mDir.isDirectory() && !mDir.mkdirs()) {
            return;
        }

        File file = new File(mDir, "store.bin");
        File tmp = new File(mDir, "store.bin.tmp");

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            out.writeInt(VERSION);

            out.writeInt(mSeen.size());
            for (Map.Entry<String, String> entry : mSeen.entrySet()) {
                out.writeUTF(entry.getKey());
                writeNullableUTF(out, entry.getValue());
            }

            out.writeInt(mVectors.size());
            for (Map.Entry<String, Vector> entry : mVectors.entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeLong(entry.getValue().createdMs);
                float[] values = entry.getValue().values;
                out.writeInt(values.length);
                for (float value : values) {
                    out.writeFloat(value);
                }
            }

            out.writeInt(mDismissed.size());
            for (String id : mDismissed) {
                out.writeUTF(id);
            }

            out.writeInt(mSuggestions.size());
            for (Suggestion suggestion : mSuggestions) {
                out.writeUTF(suggestion.channelId);
                writeNullableUTF(out, suggestion.channelName);
                out.writeFloat(suggestion.score);
                writeNullableUTF(out, suggestion.closestBlockedName);
            }
        } catch (IOException e) {
            tmp.delete();
            return;
        }

        if (tmp.renameTo(file)) {
            mSeenDirty = false;
        }
    }

    private void load() {
        File file = new File(mDir, "store.bin");
        if (!file.isFile()) {
            return;
        }

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != VERSION) {
                return;
            }

            for (int i = in.readInt(); i > 0; i--) {
                mSeen.put(in.readUTF(), readNullableUTF(in));
            }

            for (int i = in.readInt(); i > 0; i--) {
                String id = in.readUTF();
                long createdMs = in.readLong();
                float[] values = new float[in.readInt()];
                for (int d = 0; d < values.length; d++) {
                    values[d] = in.readFloat();
                }
                mVectors.put(id, new Vector(createdMs, values));
            }

            for (int i = in.readInt(); i > 0; i--) {
                mDismissed.add(in.readUTF());
            }

            for (int i = in.readInt(); i > 0; i--) {
                mSuggestions.add(new Suggestion(in.readUTF(), readNullableUTF(in), in.readFloat(), readNullableUTF(in)));
            }
        } catch (IOException e) {
            // Corrupted: start over, it's only a cache
            clear();
        }
    }

    private static void writeNullableUTF(DataOutputStream out, String value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeUTF(value);
        }
    }

    private static String readNullableUTF(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    // endregion
}
