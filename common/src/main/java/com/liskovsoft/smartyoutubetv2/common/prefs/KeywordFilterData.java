package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;

import java.util.ArrayList;
import java.util.List;

import io.github.hoanghaisol.blocktube.channelfilter.keyword.KeywordMatcher;

/**
 * BlockTube: blocked keywords for search queries (no results) and video titles (hidden).
 * Profile-scoped like {@link BlockedChannelData}, so it's part of the app backup.
 */
public class KeywordFilterData implements ProfileChangeListener {
    private static final String KEYWORD_FILTER_DATA = "keyword_filter_data";
    @SuppressLint("StaticFieldLeak")
    private static KeywordFilterData sInstance;
    private final AppPrefs mPrefs;
    private final List<String> mSearchKeywords = new ArrayList<>();
    private final List<String> mVideoKeywords = new ArrayList<>();
    private KeywordMatcher mSearchMatcher;
    private KeywordMatcher mVideoMatcher;

    private KeywordFilterData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    public static KeywordFilterData instance(Context context) {
        if (sInstance == null) {
            sInstance = new KeywordFilterData(context.getApplicationContext());
        }

        return sInstance;
    }

    public List<String> getSearchKeywords() {
        return new ArrayList<>(mSearchKeywords);
    }

    public List<String> getVideoKeywords() {
        return new ArrayList<>(mVideoKeywords);
    }

    public void addSearchKeyword(String keyword) {
        add(mSearchKeywords, keyword);
    }

    public void removeSearchKeyword(String keyword) {
        mSearchKeywords.remove(keyword);
        persistState();
    }

    public void addVideoKeyword(String keyword) {
        add(mVideoKeywords, keyword);
    }

    public void removeVideoKeyword(String keyword) {
        mVideoKeywords.remove(keyword);
        persistState();
    }

    public boolean isSearchBlocked(String query) {
        return mSearchMatcher.matches(query);
    }

    public boolean isVideoBlocked(String title) {
        return mVideoMatcher.matches(title);
    }

    private void add(List<String> keywords, String keyword) {
        if (keyword == null || keyword.trim().isEmpty() || keywords.contains(keyword.trim())) {
            return;
        }
        keywords.add(keyword.trim());
        persistState();
    }

    private void restoreState() {
        String[] split = Helpers.splitData(mPrefs.getProfileData(KEYWORD_FILTER_DATA));

        mSearchKeywords.clear();
        mSearchKeywords.addAll(Helpers.parseStrList(split, 0));
        mVideoKeywords.clear();
        mVideoKeywords.addAll(Helpers.parseStrList(split, 1));
        updateMatchers();
    }

    private void persistState() {
        updateMatchers();
        mPrefs.setProfileData(KEYWORD_FILTER_DATA, Helpers.mergeData(mSearchKeywords, mVideoKeywords));
    }

    private void updateMatchers() {
        mSearchMatcher = new KeywordMatcher(mSearchKeywords);
        mVideoMatcher = new KeywordMatcher(mVideoKeywords);
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }
}
