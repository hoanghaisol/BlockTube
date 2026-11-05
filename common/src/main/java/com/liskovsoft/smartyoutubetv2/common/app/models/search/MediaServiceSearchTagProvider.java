package com.liskovsoft.smartyoutubetv2.common.app.models.search;

import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.prefs.KeywordFilterData;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;

public class MediaServiceSearchTagProvider implements SearchTagsProvider {
    private static final String TAG = MediaServiceSearchTagProvider.class.getSimpleName();
    private final ContentService mContentService;
    private final boolean mIgnoreEmptyQuery;
    private Disposable mTagsAction;

    public MediaServiceSearchTagProvider(boolean ignoreEmptyQuery) {
        mIgnoreEmptyQuery = ignoreEmptyQuery;
        ServiceManager service = YouTubeServiceManager.instance();
        mContentService = service.getContentService();
    }

    /**
     * BlockTube: don't suggest searches that contain a blocked keyword
     */
    private static List<String> filterBlocked(List<String> tags) {
        if (tags == null) {
            return null;
        }
        KeywordFilterData filter = KeywordFilterData.instance(GlobalPreferences.context());
        List<String> result = new ArrayList<>();
        for (String tag : tags) {
            if (!filter.isSearchBlocked(tag)) {
                result.add(tag);
            }
        }
        return result;
    }

    @Override
    public void search(String query, ResultsCallback callback) {
        RxHelper.disposeActions(mTagsAction);

        if (mIgnoreEmptyQuery && TextUtils.isEmpty(query)) {
            callback.onResults(null);
            return;
        }

        mTagsAction = mContentService.getSearchTagsObserve(query)
                .subscribe(
                        tags -> callback.onResults(Tag.from(filterBlocked(tags))),
                        error -> Log.e(TAG, "Result is empty. Just ignore it. Error msg: %s", error.getMessage())
                );
    }
}
