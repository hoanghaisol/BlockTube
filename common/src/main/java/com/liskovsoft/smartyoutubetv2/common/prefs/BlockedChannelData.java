package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;

public class BlockedChannelData implements ProfileChangeListener {
    private static final String BLOCKED_CHANNEL_DATA = "blocked_channel_data";
    @SuppressLint("StaticFieldLeak")
    private static BlockedChannelData sInstance;
    private final AppPrefs mPrefs;
    private final TreeSet<Channel> mChannels =
            new TreeSet<>((channel1, channel2) -> {
                // Sort by name, but keep different channels with the same name apart
                int result = Helpers.compareToIgnoreCase(channel1.channelName, channel2.channelName);
                return result != 0 ? result : Helpers.compareToIgnoreCase(channel1.channelId, channel2.channelId);
            });
    private final Runnable mPersistStateInt = this::persistStateInt;
    private final List<BlockedChannelListener> mListeners = new ArrayList<>();

    private static class Channel {
        public String channelId;
        public String channelName;

        public Channel(String channelId, String channelName) {
            this.channelId = channelId;
            this.channelName = channelName;
        }

        public static Channel fromString(String specs) {
            String[] split = Helpers.splitObj(specs);

            if (split == null || split.length != 2) {
                return null;
            }

            return new Channel(Helpers.parseStr(split[0]), Helpers.parseStr(split[1]));
        }

        /**
         * Channel id is stable, name may change. Fall back to the name only when an id is missing.
         */
        public boolean matches(String channelId, String channelName) {
            if (this.channelId != null && channelId != null) {
                return this.channelId.equals(channelId);
            }

            return this.channelName != null && channelName != null && this.channelName.equalsIgnoreCase(channelName);
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj instanceof Channel) {
                Channel channel = (Channel) obj;
                return matches(channel.channelId, channel.channelName);
            }

            return super.equals(obj);
        }

        @NonNull
        @Override
        public String toString() {
            return Helpers.mergeObj(channelId, channelName);
        }
    }

    public interface BlockedChannelListener {
        void onChanged();
    }

    private BlockedChannelData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    public static BlockedChannelData instance(Context context) {
        if (sInstance == null) {
            sInstance = new BlockedChannelData(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * Add a channel to the block list
     */
    public void addChannel(String channelId, String channelName) {
        if (Helpers.allNulls(channelId, channelName)) {
            return;
        }

        removeMatches(channelId, channelName);
        mChannels.add(new Channel(channelId, channelName));

        persistState();
        notifyListeners();
    }

    /**
     * Remove a channel from the list
     */
    public void removeChannel(String channelId, String channelName) {
        if (Helpers.allNulls(channelId, channelName) || mChannels.isEmpty()) {
            return;
        }

        removeMatches(channelId, channelName);

        persistState();
        notifyListeners();
    }

    /**
     * Check if a channel is blocked
     */
    public boolean containsChannel(String channelId, String channelName) {
        if (Helpers.allNulls(channelId, channelName) || mChannels.isEmpty()) {
            return false;
        }

        return findChannel(channelId, channelName) != null;
    }

    private Channel findChannel(String channelId, String channelName) {
        for (Channel channel : mChannels) {
            if (channel.matches(channelId, channelName)) {
                return channel;
            }
        }

        return null;
    }

    private void removeMatches(String channelId, String channelName) {
        Iterator<Channel> iterator = mChannels.iterator();

        while (iterator.hasNext()) {
            if (iterator.next().matches(channelId, channelName)) {
                iterator.remove();
            }
        }
    }

    /**
     * Get all blocked channels with their names
     *
     * @return List of channelId -> channel name
     */
    public List<Pair<String, String>> getChannelIdsWithNames() {
        List<Pair<String, String>> result = new ArrayList<>();

        for (Channel item : mChannels) {
            result.add(new Pair<>(item.channelId, item.channelName));
        }

        return result;
    }

    /**
     * Get the count of blacklisted channels
     *
     * @return Number of blacklisted channels
     */
    public int getChannelCount() {
        return mChannels.size();
    }

    public boolean isEmpty() {
        return mChannels.isEmpty();
    }

    /**
     * Clear all blacklisted channels
     */
    public void clear() {
        mChannels.clear();
        persistState();
        notifyListeners();
    }

    private synchronized void restoreState() {
        String data = mPrefs.getProfileData(BLOCKED_CHANNEL_DATA);

        String[] split = Helpers.splitData(data);

        mChannels.clear();
        mChannels.addAll(Helpers.parseList(split, 0, Channel::fromString));
        // null

        restoreOldData(split);
    }

    private void restoreOldData(String[] split) {
        Map<String, String> channelIdsWithNames = Helpers.parseMap(split, 1, Helpers::parseStr, Helpers::parseStr);

        if (!channelIdsWithNames.isEmpty()) {
            for (Entry<String, String> entry : channelIdsWithNames.entrySet()) {
                if (findChannel(entry.getKey(), entry.getValue()) == null) {
                    mChannels.add(new Channel(entry.getKey(), entry.getValue()));
                }
            }
        }
    }

    public void persistNow() {
        Utils.post(mPersistStateInt);
    }

    private void persistState() {
        Utils.postDelayed(mPersistStateInt, 10_000);
    }

    private void persistStateInt() {
        // Convert Set to List for persistence
        mPrefs.setProfileData(BLOCKED_CHANNEL_DATA, Helpers.mergeData(
                mChannels, null));
    }

    public void addListener(BlockedChannelListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(BlockedChannelListener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners() {
        for (BlockedChannelListener listener : mListeners) {
            listener.onChanged();
        }
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }
}