package io.github.hoanghaisol.blocktube.channelfilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import io.github.hoanghaisol.blocktube.channelfilter.embedding.Embedder;
import io.github.hoanghaisol.blocktube.channelfilter.model.ModelFiles;
import io.github.hoanghaisol.blocktube.channelfilter.store.ChannelStore;
import io.github.hoanghaisol.blocktube.channelfilter.store.ChannelStore.Suggestion;
import io.github.hoanghaisol.blocktube.channelfilter.work.SimilarityWorker;

/**
 * Entry point of the "similar channels" suggester. AI only suggests; blocking is always the
 * user's decision.
 * <p>
 * The app provides engine data through {@link Host}. Call {@link #onChannelSeen} for every
 * video shown (cheap, main thread) and {@link #onBlockedChanged} when the block list changes.
 */
public class ChannelFilter {
    private static final String PREFS = "channelfilter";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_AUTO_BLOCK = "auto_block";
    private static final String WORK_NAME = "channelfilter_similar";
    private static final long SAVE_DELAY_MS = 30_000;
    private static final long STARTUP_DELAY_MS = 60_000;

    public interface Host {
        /**
         * Blocking, called on a background thread.
         *
         * @return recent video titles of the channel, or null if unavailable
         */
        List<String> getRecentTitles(String channelId);

        /**
         * Blocking, called on a background thread.
         *
         * @return channel id of the video's author, or null
         */
        String resolveChannelId(String videoId);

        /**
         * Called on a background thread when auto-block is on.
         */
        void blockChannel(String channelId, String channelName);

        /**
         * @return channelId → name of blocked channels (entries without an id are skipped)
         */
        Map<String, String> getBlockedChannels();

        /**
         * Called on a background thread. Heavy work only runs when nothing is playing.
         */
        boolean isPlaybackActive();
    }

    public interface SuggestionsListener {
        void onSuggestionsChanged(List<Suggestion> suggestions);

        void onAutoBlocked(List<Suggestion> channels);
    }

    private static ChannelFilter sInstance;
    private final Context mContext;
    private final Host mHost;
    private final SharedPreferences mPrefs;
    private final ChannelStore mStore;
    private final ModelFiles mModelFiles;
    private final ExecutorService mIoExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mSaveSeen = () -> mIoExecutor.execute(this::saveIfDirty);
    private final List<SuggestionsListener> mListeners = new ArrayList<>();
    private boolean mEnabled;

    private ChannelFilter(Context context, Host host) {
        mContext = context.getApplicationContext();
        mHost = host;
        mPrefs = mContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        File dir = new File(mContext.getFilesDir(), "channelfilter");
        mStore = new ChannelStore(dir);
        mModelFiles = new ModelFiles(new File(dir, "model"));
        mEnabled = isSupported() && mPrefs.getBoolean(KEY_ENABLED, false);

        if (mEnabled) {
            // Finish interrupted runs, refresh old vectors
            mHandler.postDelayed(() -> scheduleWork(0, ExistingWorkPolicy.KEEP), STARTUP_DELAY_MS);
        }
    }

    public static synchronized ChannelFilter instance(Context context, Host host) {
        if (sInstance == null) {
            sInstance = new ChannelFilter(context, host);
        }
        return sInstance;
    }

    /**
     * @return the instance if the app already created it (e.g. null when WorkManager starts a fresh process)
     */
    public static synchronized ChannelFilter peek() {
        return sInstance;
    }

    public static boolean isSupported() {
        return Embedder.isSupported();
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Enabling downloads the model (~120 MB) in the background on first use.
     * Disabling deletes the model and all collected data.
     */
    public void setEnabled(boolean enabled) {
        if (!isSupported()) {
            return;
        }
        mEnabled = enabled;
        mPrefs.edit().putBoolean(KEY_ENABLED, enabled).apply();

        if (enabled) {
            scheduleWork(0, ExistingWorkPolicy.REPLACE);
        } else {
            WorkManager.getInstance(mContext).cancelUniqueWork(WORK_NAME);
            mIoExecutor.execute(() -> {
                mModelFiles.delete();
                mStore.clear();
                mStore.save();
            });
            notifySuggestions();
        }
    }

    /**
     * Block every suggestion without asking (default). Only channels the user blocked serve as
     * examples, so auto-blocks don't chain.
     */
    public boolean isAutoBlock() {
        return mPrefs.getBoolean(KEY_AUTO_BLOCK, true);
    }

    public void setAutoBlock(boolean autoBlock) {
        mPrefs.edit().putBoolean(KEY_AUTO_BLOCK, autoBlock).apply();
        if (mEnabled && autoBlock) {
            scheduleWork(0, ExistingWorkPolicy.REPLACE); // Handle pending suggestions
        }
    }

    public List<Suggestion> getAutoBlocked() {
        return mEnabled ? mStore.getAutoBlocked() : new ArrayList<>();
    }

    /**
     * The app unblocked an auto-blocked channel: remember it so it's never auto-blocked again.
     */
    public void onAutoBlockUndone(String channelId) {
        mStore.undoAutoBlock(channelId);
        mIoExecutor.execute(mStore::save);
    }

    public boolean isModelReady() {
        return mModelFiles.isReady();
    }

    public void onChannelSeen(String channelId, String channelName) {
        if (!mEnabled || channelId == null) {
            return;
        }

        if (mStore.addSeen(channelId, channelName)) {
            mHandler.removeCallbacks(mSaveSeen);
            mHandler.postDelayed(mSaveSeen, SAVE_DELAY_MS);
        }
    }

    /**
     * For videos without a channel id (search results). Resolved later in the background.
     */
    public void onUnresolvedChannelSeen(String channelName, String videoId) {
        if (!mEnabled || channelName == null || videoId == null) {
            return;
        }

        if (mStore.addPending(channelName, videoId)) {
            mHandler.removeCallbacks(mSaveSeen);
            mHandler.postDelayed(mSaveSeen, SAVE_DELAY_MS);
        }
    }

    public void onBlockedChanged() {
        if (mEnabled) {
            // Give the user time to block a few channels in a row
            scheduleWork(30, ExistingWorkPolicy.REPLACE);
        }
    }

    public List<Suggestion> getSuggestions() {
        return mEnabled ? mStore.getSuggestions() : new ArrayList<>();
    }

    /**
     * "Ignore": never suggest this channel again, and treat it as a counterexample.
     */
    public void dismiss(String channelId) {
        mStore.dismiss(channelId);
        mIoExecutor.execute(mStore::save);
        notifySuggestions();
    }

    /**
     * The user blocked a suggested channel.
     */
    public void onSuggestionBlocked(String channelId) {
        mStore.removeSuggestion(channelId);
        mIoExecutor.execute(mStore::save);
        notifySuggestions();
    }

    public void addListener(SuggestionsListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(SuggestionsListener listener) {
        mListeners.remove(listener);
    }

    // region Used by SimilarityWorker

    public Host getHost() {
        return mHost;
    }

    public ChannelStore getStore() {
        return mStore;
    }

    public ModelFiles getModelFiles() {
        return mModelFiles;
    }

    public void notifySuggestions() {
        List<Suggestion> suggestions = getSuggestions();
        mHandler.post(() -> {
            for (SuggestionsListener listener : new ArrayList<>(mListeners)) {
                listener.onSuggestionsChanged(suggestions);
            }
        });
    }

    public void notifyAutoBlocked(List<Suggestion> channels) {
        mHandler.post(() -> {
            for (SuggestionsListener listener : new ArrayList<>(mListeners)) {
                listener.onAutoBlocked(channels);
            }
        });
    }

    // endregion

    private void scheduleWork(long delaySeconds, ExistingWorkPolicy policy) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SimilarityWorker.class)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 2, TimeUnit.MINUTES)
                .build();

        try {
            WorkManager.getInstance(mContext).enqueueUniqueWork(WORK_NAME, policy, request);
        } catch (IllegalStateException e) {
            // WorkManager not initialized (see SafeWorkManager in the app)
        }
    }

    private void saveIfDirty() {
        if (mStore.isSeenDirty()) {
            mStore.save();
        }
    }
}
