package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.hoanghaisol.blocktube.channelfilter.ChannelFilter;
import io.github.hoanghaisol.blocktube.channelfilter.store.ChannelStore.Suggestion;

/**
 * BlockTube: the only place where upstream code talks to the :channelfilter module.
 * Provides engine data (RSS titles, block list, playback state) to the suggester.
 */
public class ChannelFilterBridge implements ChannelFilter.Host, BlockedChannelData.BlockedChannelListener,
        ChannelFilter.SuggestionsListener {
    private static final String TAG = ChannelFilterBridge.class.getSimpleName();
    private final Context mContext;
    private final Set<String> mKnownSuggestions = new HashSet<>();

    private ChannelFilterBridge(Context context) {
        mContext = context.getApplicationContext();
    }

    /**
     * @return null when the context is not ready yet
     */
    public static ChannelFilter instance() {
        ChannelFilter filter = ChannelFilter.peek();
        if (filter != null) {
            return filter;
        }

        Context context = GlobalPreferences.context();
        if (context == null) {
            return null;
        }

        ChannelFilterBridge bridge = new ChannelFilterBridge(context);
        filter = ChannelFilter.instance(context, bridge);
        BlockedChannelData.instance(context).addListener(bridge);
        bridge.rememberSuggestions(filter.getSuggestions());
        filter.addListener(bridge);
        return filter;
    }

    /**
     * Called for every video added to the UI (main thread, must stay cheap).
     */
    public static void onVideoSeen(Video video) {
        if (video.isChapter) {
            return;
        }

        ChannelFilter filter = instance();
        if (filter == null) {
            return;
        }

        // Channel ids start with "UC"; playlists ("VL...") also show up as authors
        if (video.channelId != null && video.channelId.startsWith("UC")) {
            filter.onChannelSeen(video.channelId, video.getAuthor());
        } else if (video.channelId == null && video.videoId != null) {
            // Search results have no channel id
            filter.onUnresolvedChannelSeen(video.getAuthor(), video.videoId);
        }
    }

    @Override
    public void onChanged() {
        ChannelFilter.peek().onBlockedChanged();
    }

    /**
     * Tell the user when new suggestions are waiting (main thread).
     */
    @Override
    public void onSuggestionsChanged(List<Suggestion> suggestions) {
        if (rememberSuggestions(suggestions)) {
            MessageHelpers.showMessage(mContext, mContext.getString(R.string.similar_channels_found, suggestions.size()));
        }
    }

    /**
     * @return true if there is a suggestion we haven't seen before
     */
    private boolean rememberSuggestions(List<Suggestion> suggestions) {
        boolean hasNew = false;
        for (Suggestion suggestion : suggestions) {
            hasNew |= mKnownSuggestions.add(suggestion.channelId);
        }
        return hasNew;
    }

    @Override
    public List<String> getRecentTitles(String channelId) {
        List<String> titles = new ArrayList<>();

        try {
            MediaGroup feed = YouTubeServiceManager.instance().getContentService().getRssFeed(channelId);
            List<MediaItem> items = feed != null ? feed.getMediaItems() : null;
            if (items != null) {
                for (MediaItem item : items) {
                    if (item.getTitle() != null) {
                        titles.add(item.getTitle());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "RSS failed for %s: %s", channelId, e.getMessage());
            return null;
        }

        return titles;
    }

    @Override
    public String resolveChannelId(String videoId) {
        try {
            MediaItemMetadata metadata = YouTubeServiceManager.instance().getMediaItemService().getMetadata(videoId);
            return metadata != null ? metadata.getChannelId() : null;
        } catch (Exception e) {
            Log.e(TAG, "Metadata failed for %s: %s", videoId, e.getMessage());
            return null;
        }
    }

    @Override
    public Map<String, String> getBlockedChannels() {
        Map<String, String> result = new HashMap<>();
        for (Pair<String, String> channel : BlockedChannelData.instance(mContext).getChannelIdsWithNames()) {
            if (channel.first != null) {
                result.put(channel.first, channel.second);
            }
        }
        return result;
    }

    @Override
    public boolean isPlaybackActive() {
        // Player state lives on the main thread
        AtomicBoolean playing = new AtomicBoolean(true);
        CountDownLatch latch = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(() -> {
            playing.set(PlaybackPresenter.instance(mContext).isPlaying());
            latch.countDown();
        });

        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return true;
        }

        return playing.get();
    }
}
