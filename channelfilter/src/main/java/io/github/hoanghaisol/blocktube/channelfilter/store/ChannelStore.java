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
    private static final int VERSION = 3;
    private static final int MAX_SEEN = 500;
    private static final int MAX_PENDING = 100;

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
    /** Channels shown without an id (e.g. search results): name → one of their video ids */
    private final LinkedHashMap<String, String> mPending = new LinkedHashMap<String, String>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_PENDING;
        }
    };
    private final Set<String> mSeenNames = new HashSet<>();
    private final Map<String, Vector> mVectors = new HashMap<>();
    private final Set<String> mDismissed = new HashSet<>();
    private final List<Suggestion> mSuggestions = new ArrayList<>();
    /** Channels the AI blocked by itself (newest last) */
    private final List<Suggestion> mAutoBlocked = new ArrayList<>();
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
        if (channelName != null) {
            mSeenNames.add(channelName);
            mPending.remove(channelName);
        }
        return previous == null;
    }

    /**
     * @return true if the channel is new
     */
    public synchronized boolean addPending(String channelName, String videoId) {
        if (mSeenNames.contains(channelName) || mPending.containsKey(channelName)) {
            return false;
        }
        mPending.put(channelName, videoId);
        mSeenDirty = true;
        return true;
    }

    public synchronized Map<String, String> getPending() {
        return new LinkedHashMap<>(mPending);
    }

    /**
     * @param channelId null if it couldn't be resolved (dropped anyway)
     */
    public synchronized void resolvePending(String channelName, String channelId) {
        mPending.remove(channelName);
        mSeenDirty = true;
        if (channelId != null && channelId.startsWith("UC")) {
            addSeen(channelId, channelName);
        }
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

    public synchronized void addAutoBlocked(List<Suggestion> channels) {
        mAutoBlocked.addAll(channels);
    }

    public synchronized List<Suggestion> getAutoBlocked() {
        return new ArrayList<>(mAutoBlocked);
    }

    public synchronized Set<String> getAutoBlockedIds() {
        Set<String> result = new HashSet<>();
        for (Suggestion channel : mAutoBlocked) {
            result.add(channel.channelId);
        }
        return result;
    }

    /**
     * The user unblocked an auto-blocked channel: never auto-block it again.
     */
    public synchronized void undoAutoBlock(String channelId) {
        for (int i = mAutoBlocked.size() - 1; i >= 0; i--) {
            if (mAutoBlocked.get(i).channelId.equals(channelId)) {
                mAutoBlocked.remove(i);
            }
        }
        mDismissed.add(channelId);
    }

    /**
     * Auto-blocked channels that were unblocked elsewhere (e.g. Blocked channels section) count as undone.
     */
    public synchronized void reconcileAutoBlocked(Set<String> blockedIds) {
        for (String id : getAutoBlockedIds()) {
            if (!blockedIds.contains(id)) {
                undoAutoBlock(id);
            }
        }
    }

    public synchronized void clear() {
        mSeen.clear();
        mPending.clear();
        mSeenNames.clear();
        mVectors.clear();
        mDismissed.clear();
        mSuggestions.clear();
        mAutoBlocked.clear();
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

            out.writeInt(mPending.size());
            for (Map.Entry<String, String> entry : mPending.entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeUTF(entry.getValue());
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

            writeSuggestions(out, mSuggestions);
            writeSuggestions(out, mAutoBlocked);
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
                String id = in.readUTF();
                String name = readNullableUTF(in);
                mSeen.put(id, name);
                if (name != null) {
                    mSeenNames.add(name);
                }
            }

            for (int i = in.readInt(); i > 0; i--) {
                mPending.put(in.readUTF(), in.readUTF());
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

            readSuggestions(in, mSuggestions);
            readSuggestions(in, mAutoBlocked);
        } catch (IOException e) {
            // Corrupted: start over, it's only a cache
            clear();
        }
    }

    private static void writeSuggestions(DataOutputStream out, List<Suggestion> suggestions) throws IOException {
        out.writeInt(suggestions.size());
        for (Suggestion suggestion : suggestions) {
            out.writeUTF(suggestion.channelId);
            writeNullableUTF(out, suggestion.channelName);
            out.writeFloat(suggestion.score);
            writeNullableUTF(out, suggestion.closestBlockedName);
        }
    }

    private static void readSuggestions(DataInputStream in, List<Suggestion> suggestions) throws IOException {
        for (int i = in.readInt(); i > 0; i--) {
            suggestions.add(new Suggestion(in.readUTF(), readNullableUTF(in), in.readFloat(), readNullableUTF(in)));
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
