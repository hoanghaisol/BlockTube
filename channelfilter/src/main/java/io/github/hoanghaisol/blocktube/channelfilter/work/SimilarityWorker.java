package io.github.hoanghaisol.blocktube.channelfilter.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.github.hoanghaisol.blocktube.channelfilter.ChannelFilter;
import io.github.hoanghaisol.blocktube.channelfilter.embedding.Embedder;
import io.github.hoanghaisol.blocktube.channelfilter.model.ModelFiles;
import io.github.hoanghaisol.blocktube.channelfilter.scoring.SimilarityScorer;
import io.github.hoanghaisol.blocktube.channelfilter.store.ChannelStore;
import io.github.hoanghaisol.blocktube.channelfilter.store.ChannelStore.Suggestion;
import io.github.hoanghaisol.blocktube.channelfilter.tokenizer.UnigramTokenizer;

/**
 * Downloads the model if needed, embeds channels that have no fresh vector (blocked first),
 * then ranks seen channels against blocked ones. Stops and retries later as soon as playback
 * starts, so weak TV boxes don't stutter. Progress is saved, so retries continue.
 */
public class SimilarityWorker extends Worker {
    private static final String TAG = "ChannelFilter";
    private static final long VECTOR_MAX_AGE_MS = TimeUnit.DAYS.toMillis(30);
    private static final int MAX_EMBEDDINGS_PER_RUN = 80;
    private static final int MAX_TITLES = 15;
    private static final int THREADS = 2;
    private static final long FETCH_PAUSE_MS = 300;
    private static final int MAX_RESOLVES_PER_RUN = 30;

    public SimilarityWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        ChannelFilter filter = ChannelFilter.peek();
        if (filter == null) {
            return Result.retry(); // App process not started yet
        }
        if (!filter.isEnabled()) {
            return Result.success();
        }

        ModelFiles modelFiles = filter.getModelFiles();
        if (!modelFiles.isReady()) {
            try {
                modelFiles.download(null);
            } catch (IOException e) {
                Log.w(TAG, "Model download failed", e);
                return Result.retry();
            }
        }

        ChannelFilter.Host host = filter.getHost();
        if (host.isPlaybackActive()) {
            return Result.retry();
        }

        ChannelStore store = filter.getStore();
        Map<String, String> allBlocked = host.getBlockedChannels();
        store.reconcileAutoBlocked(allBlocked.keySet());

        // Examples are the channels the user blocked; auto-blocked ones would make blocks chain
        Map<String, String> blocked = new LinkedHashMap<>(allBlocked);
        blocked.keySet().removeAll(store.getAutoBlockedIds());
        if (blocked.isEmpty()) {
            store.setSuggestions(new ArrayList<>());
            store.save();
            filter.notifySuggestions();
            return Result.success();
        }

        resolvePending(filter);

        Map<String, String> seen = store.getSeen();
        Set<String> dismissed = store.getDismissed();

        Set<String> candidates = new LinkedHashSet<>(seen.keySet());
        candidates.removeAll(allBlocked.keySet());
        candidates.removeAll(dismissed);

        // Blocked first: without them nothing can be scored
        Set<String> all = new LinkedHashSet<>(blocked.keySet());
        all.addAll(dismissed);
        all.addAll(candidates);

        List<String> missing = new ArrayList<>();
        for (String id : all) {
            if (store.getVector(id, VECTOR_MAX_AGE_MS) == null) {
                missing.add(id);
            }
        }

        boolean interrupted = embedMissing(filter, missing, blocked, seen);
        Log.i(TAG, String.format("Embedded up to %d of %d missing vectors, interrupted: %s", MAX_EMBEDDINGS_PER_RUN, missing.size(), interrupted));

        store.retainVectors(all);
        rank(store, blocked, dismissed, candidates, seen);
        if (filter.isAutoBlock()) {
            autoBlock(filter);
        }
        store.save();
        filter.notifySuggestions();

        // Unfinished: continue later
        boolean unfinished = interrupted || missing.size() > MAX_EMBEDDINGS_PER_RUN || !store.getPending().isEmpty();
        return unfinished ? Result.retry() : Result.success();
    }

    /**
     * Channels seen without an id (search results): look the id up from one of their videos.
     */
    private void resolvePending(ChannelFilter filter) {
        ChannelFilter.Host host = filter.getHost();
        ChannelStore store = filter.getStore();
        int count = 0;

        for (Map.Entry<String, String> pending : store.getPending().entrySet()) {
            if (isStopped() || count++ >= MAX_RESOLVES_PER_RUN || host.isPlaybackActive()) {
                return;
            }
            store.resolvePending(pending.getKey(), host.resolveChannelId(pending.getValue()));

            try {
                Thread.sleep(FETCH_PAUSE_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /**
     * @return true if stopped early (playback started, work cancelled)
     */
    private boolean embedMissing(ChannelFilter filter, List<String> missing, Map<String, String> blocked, Map<String, String> seen) {
        if (missing.isEmpty()) {
            return false;
        }

        ChannelFilter.Host host = filter.getHost();
        ChannelStore store = filter.getStore();
        ModelFiles files = filter.getModelFiles();

        UnigramTokenizer tokenizer;
        try (InputStream in = new FileInputStream(files.get(ModelFiles.VOCAB))) {
            tokenizer = UnigramTokenizer.load(in);
        } catch (IOException e) {
            Log.w(TAG, "Vocab load failed", e);
            files.delete(); // Re-download next time
            return true;
        }

        int count = 0;
        try (Embedder embedder = new Embedder(files.get(ModelFiles.MODEL), tokenizer, THREADS)) {
            for (String id : missing) {
                if (isStopped() || host.isPlaybackActive()) {
                    return true;
                }
                if (count++ >= MAX_EMBEDDINGS_PER_RUN) {
                    break;
                }

                List<String> titles = host.getRecentTitles(id);
                if (titles == null || titles.isEmpty()) {
                    continue;
                }

                String name = blocked.containsKey(id) ? blocked.get(id) : seen.get(id);
                store.putVector(id, embedder.embed(profile(name, titles)));

                Thread.sleep(FETCH_PAUSE_MS);
            }
        } catch (Exception e) { // OrtException, InterruptedException
            Log.w(TAG, "Embedding failed", e);
            return true;
        }

        return false;
    }

    private static void autoBlock(ChannelFilter filter) {
        ChannelStore store = filter.getStore();
        List<Suggestion> suggestions = store.getSuggestions();
        if (suggestions.isEmpty()) {
            return;
        }

        for (Suggestion suggestion : suggestions) {
            filter.getHost().blockChannel(suggestion.channelId, suggestion.channelName);
        }
        store.addAutoBlocked(suggestions);
        store.setSuggestions(new ArrayList<>());
        filter.notifyAutoBlocked(suggestions);
        Log.i(TAG, "Auto-blocked " + suggestions.size() + " channels");
    }

    private static String profile(String name, List<String> titles) {
        StringBuilder sb = new StringBuilder();
        if (name != null) {
            sb.append(name).append(". ");
        }
        for (int i = 0; i < titles.size() && i < MAX_TITLES; i++) {
            sb.append(titles.get(i)).append("; ");
        }
        return sb.toString();
    }

    private static void rank(ChannelStore store, Map<String, String> blocked, Set<String> dismissed,
                             Set<String> candidates, Map<String, String> seen) {
        Set<String> all = new HashSet<>(blocked.keySet());
        all.addAll(dismissed);
        all.addAll(candidates);

        List<SimilarityScorer.Result> results = SimilarityScorer.rank(store.getVectors(all),
                new ArrayList<>(blocked.keySet()), new ArrayList<>(dismissed), new ArrayList<>(candidates));

        List<Suggestion> suggestions = new ArrayList<>();
        for (SimilarityScorer.Result result : results) {
            suggestions.add(new Suggestion(result.channelId, seen.get(result.channelId), result.score,
                    blocked.get(result.closestBlockedId)));
        }
        store.setSuggestions(suggestions);

        Log.i(TAG, String.format("Ranked %d candidates against %d blocked (%d vectors): %d suggestions%s",
                candidates.size(), blocked.size(), all.size(), suggestions.size(),
                suggestions.isEmpty() ? "" : ", top " + suggestions.get(0).channelName + " " + suggestions.get(0).score));
    }
}
