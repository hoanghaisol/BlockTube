package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.hoanghaisol.blocktube.channelfilter.ChannelFilter;

/**
 * BlockTube: the only place where upstream code talks to the :channelfilter module.
 * Provides engine data (RSS titles, block list, playback state) to the suggester.
 */
public class ChannelFilterBridge implements ChannelFilter.Host, BlockedChannelData.BlockedChannelListener {
    private static final String TAG = ChannelFilterBridge.class.getSimpleName();
    private final Context mContext;

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
        return filter;
    }

    /**
     * Called for every video added to the UI (main thread, must stay cheap).
     */
    public static void onVideoSeen(Video video) {
        // Channel ids start with "UC"; playlists ("VL...") also show up as authors
        if (video.isChapter || video.channelId == null || !video.channelId.startsWith("UC")) {
            return;
        }

        ChannelFilter filter = instance();
        if (filter != null) {
            filter.onChannelSeen(video.channelId, video.getAuthor());
        }
    }

    @Override
    public void onChanged() {
        ChannelFilter.peek().onBlockedChanged();
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
