package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Pair;

import androidx.appcompat.app.AlertDialog;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AppUpdatePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ContentFilterView;
import com.liskovsoft.smartyoutubetv2.common.misc.ChannelFilterBridge;
import com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.KeywordFilterData;
import com.liskovsoft.smartyoutubetv2.common.utils.PinHelper;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.hoanghaisol.blocktube.channelfilter.ChannelFilter;
import io.github.hoanghaisol.blocktube.channelfilter.store.ChannelStore.Suggestion;

/**
 * BlockTube: data and actions of the full screen Content filter. Actions change the data and
 * call {@link ContentFilterView#update()}, so the screen stays open (unblock many in a row).
 */
public class ContentFilterPresenter extends BasePresenter<ContentFilterView> {
    public static final int SECTION_BLOCKED = 0;
    public static final int SECTION_AI_BLOCKED = 1;
    public static final int SECTION_AI_REVIEW = 2;
    public static final int SECTION_SEARCH_KEYWORDS = 3;
    public static final int SECTION_VIDEO_KEYWORDS = 4;
    public static final int SECTION_AI_SETTINGS = 5;
    public static final int SECTION_PIN = 6;

    @SuppressLint("StaticFieldLeak")
    private static ContentFilterPresenter sInstance;
    private int mStartSection = SECTION_BLOCKED;

    public static class MenuEntry {
        public final int section;
        public final String title;
        public final boolean hasUnread;

        MenuEntry(int section, String title, boolean hasUnread) {
            this.section = section;
            this.title = title;
            this.hasUnread = hasUnread;
        }
    }

    public static class Item {
        public final String title;
        public final String subtitle;
        /** Shown on the right, e.g. "Unblock" */
        public final String action;
        public final boolean unread;
        /** A channel row (letter icon), not a setting or keyword */
        public final boolean isChannel;
        public final Runnable onClick;

        Item(String title, String subtitle, String action, boolean unread, Runnable onClick) {
            this(title, subtitle, action, unread, false, onClick);
        }

        Item(String title, String subtitle, String action, boolean unread, boolean isChannel, Runnable onClick) {
            this.title = title;
            this.subtitle = subtitle;
            this.action = action;
            this.unread = unread;
            this.isChannel = isChannel;
            this.onClick = onClick;
        }
    }

    private ContentFilterPresenter(Context context) {
        super(context);
    }

    public static ContentFilterPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new ContentFilterPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    /**
     * Asks for the PIN (if set and not recently entered), then opens the screen.
     */
    public void open(int section) {
        PinHelper.check(getContext(), () -> {
            mStartSection = section;
            if (getView() != null) {
                getView().selectSection(section);
            }
            getViewManager().startView(ContentFilterView.class);
        });
    }

    public int getStartSection() {
        return mStartSection;
    }

    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();
        // Leaving the screen = the user has seen what the AI blocked
        ChannelFilter filter = filter();
        if (filter != null) {
            filter.markAutoBlockedRead();
        }
    }

    private ChannelFilter filter() {
        ChannelFilter filter = ChannelFilterBridge.instance();
        return filter != null && ChannelFilter.isSupported() ? filter : null;
    }

    private void update() {
        if (getView() != null) {
            getView().update();
        }
    }

    private String str(int resId, Object... args) {
        return getContext().getString(resId, args);
    }

    // region Menu

    public List<MenuEntry> getMenu() {
        List<MenuEntry> menu = new ArrayList<>();
        KeywordFilterData keywords = KeywordFilterData.instance(getContext());
        ChannelFilter filter = filter();

        menu.add(new MenuEntry(SECTION_BLOCKED, str(R.string.content_filter_blocked_channels,
                BlockedChannelData.instance(getContext()).getChannelCount()), false));

        if (filter != null && filter.isEnabled()) {
            int unread = filter.getUnreadAutoBlockedCount();
            int total = filter.getAutoBlocked().size();
            menu.add(new MenuEntry(SECTION_AI_BLOCKED, unread > 0
                    ? str(R.string.content_filter_ai_blocked_unread, total, unread)
                    : str(R.string.similar_channels_auto_blocked_list, total), unread > 0));
            menu.add(new MenuEntry(SECTION_AI_REVIEW, str(R.string.similar_channels_show, filter.getSuggestions().size()),
                    !filter.getSuggestions().isEmpty()));
        }

        menu.add(new MenuEntry(SECTION_SEARCH_KEYWORDS, str(R.string.content_filter_search_keywords, keywords.getSearchKeywords().size()), false));
        menu.add(new MenuEntry(SECTION_VIDEO_KEYWORDS, str(R.string.content_filter_video_keywords, keywords.getVideoKeywords().size()), false));

        if (filter != null) {
            menu.add(new MenuEntry(SECTION_AI_SETTINGS, str(R.string.similar_channels_category), false));
        }

        menu.add(new MenuEntry(SECTION_PIN, str(R.string.content_filter_pin_and_update), false));

        return menu;
    }

    public String getHint(int section) {
        switch (section) {
            case SECTION_BLOCKED:
                return str(R.string.content_filter_hint_blocked);
            case SECTION_AI_BLOCKED:
                return str(R.string.content_filter_hint_ai_blocked);
            case SECTION_AI_REVIEW:
                return str(R.string.content_filter_hint_ai_review);
            case SECTION_SEARCH_KEYWORDS:
                return str(R.string.content_filter_hint_search_keywords);
            case SECTION_VIDEO_KEYWORDS:
                return str(R.string.content_filter_hint_video_keywords);
            default:
                return null;
        }
    }

    // endregion

    // region Items

    public List<Item> getItems(int section) {
        switch (section) {
            case SECTION_BLOCKED:
                return getBlockedItems();
            case SECTION_AI_BLOCKED:
                return getAutoBlockedItems();
            case SECTION_AI_REVIEW:
                return getReviewItems();
            case SECTION_SEARCH_KEYWORDS:
                KeywordFilterData searchData = KeywordFilterData.instance(getContext());
                return getKeywordItems(searchData.getSearchKeywords(), searchData::addSearchKeyword, searchData::removeSearchKeyword);
            case SECTION_VIDEO_KEYWORDS:
                KeywordFilterData videoData = KeywordFilterData.instance(getContext());
                return getKeywordItems(videoData.getVideoKeywords(), videoData::addVideoKeyword, videoData::removeVideoKeyword);
            case SECTION_AI_SETTINGS:
                return getAiSettingsItems();
            case SECTION_PIN:
                return getPinItems();
            default:
                return new ArrayList<>();
        }
    }

    private List<Item> getBlockedItems() {
        BlockedChannelData blockedData = BlockedChannelData.instance(getContext());
        ChannelFilter filter = filter();

        Map<String, Suggestion> byAi = new HashMap<>();
        if (filter != null) {
            for (Suggestion channel : filter.getAutoBlocked()) {
                byAi.put(channel.channelId, channel);
            }
        }

        List<Item> items = new ArrayList<>();
        for (Pair<String, String> channel : blockedData.getChannelIdsWithNames()) {
            Suggestion aiBlock = channel.first != null ? byAi.get(channel.first) : null;
            String subtitle = aiBlock != null
                    ? str(R.string.content_filter_blocked_by_ai, aiBlock.closestBlockedName)
                    : str(R.string.content_filter_blocked_by_you);
            items.add(new Item(name(channel.first, channel.second), subtitle, str(R.string.content_filter_unblock_action), false, true,
                    () -> unblock(channel.first, channel.second)));
        }
        return items;
    }

    private List<Item> getAutoBlockedItems() {
        ChannelFilter filter = filter();
        List<Item> items = new ArrayList<>();
        if (filter == null) {
            return items;
        }

        List<Suggestion> autoBlocked = filter.getAutoBlocked();
        for (int i = autoBlocked.size() - 1; i >= 0; i--) { // newest first
            Suggestion channel = autoBlocked.get(i);
            items.add(new Item(name(channel.channelId, channel.channelName), similarity(channel), str(R.string.content_filter_unblock_action),
                    !channel.read, true, () -> unblock(channel.channelId, channel.channelName)));
        }
        return items;
    }

    private List<Item> getReviewItems() {
        ChannelFilter filter = filter();
        List<Item> items = new ArrayList<>();
        if (filter == null) {
            return items;
        }

        for (Suggestion suggestion : filter.getSuggestions()) {
            items.add(new Item(name(suggestion.channelId, suggestion.channelName), similarity(suggestion), str(R.string.content_filter_choose_action), true, true,
                    () -> showReviewChoice(filter, suggestion)));
        }
        return items;
    }

    private void showReviewChoice(ChannelFilter filter, Suggestion suggestion) {
        String[] choices = {
                str(R.string.dialog_block_channel),
                str(R.string.similar_channels_ignore),
                str(R.string.open_channel)
        };

        new AlertDialog.Builder(getContext(), R.style.AppDialog)
                .setTitle(name(suggestion.channelId, suggestion.channelName))
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        BlockedChannelData.instance(getContext()).addChannel(suggestion.channelId, suggestion.channelName);
                        filter.onSuggestionBlocked(suggestion.channelId);
                        MessageHelpers.showMessage(getContext(), R.string.channel_blocked);
                    } else if (which == 1) {
                        filter.dismiss(suggestion.channelId);
                    } else {
                        ChannelPresenter.instance(getContext()).openChannel(suggestion.channelId);
                    }
                    update();
                })
                .show();
    }

    private interface KeywordAction {
        void apply(String keyword);
    }

    private List<Item> getKeywordItems(List<String> keywords, KeywordAction onAdd, KeywordAction onRemove) {
        List<Item> items = new ArrayList<>();

        items.add(new Item(str(R.string.content_filter_add_keyword), null, null, false, () ->
                SimpleEditDialog.show(getContext(), str(R.string.content_filter_add_keyword), null, newValue -> {
                    onAdd.apply(newValue);
                    update();
                    return true;
                })));

        for (String keyword : keywords) {
            items.add(new Item(keyword, null, str(R.string.content_filter_remove_action), false, () -> {
                onRemove.apply(keyword);
                update();
            }));
        }
        return items;
    }

    private List<Item> getAiSettingsItems() {
        ChannelFilter filter = filter();
        List<Item> items = new ArrayList<>();
        if (filter == null) {
            return items;
        }

        items.add(new Item(str(R.string.similar_channels_enable), str(R.string.similar_channels_enable_desc), onOff(filter.isEnabled()), false, () -> {
            if (filter.isEnabled()) {
                filter.setEnabled(false);
                update();
            } else {
                new AlertDialog.Builder(getContext(), R.style.AppDialog)
                        .setTitle(str(R.string.similar_channels_confirm_download))
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            filter.setEnabled(true);
                            MessageHelpers.showMessage(getContext(), R.string.similar_channels_downloading);
                            update();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        }));

        if (!filter.isEnabled()) {
            return items;
        }

        items.add(new Item(str(R.string.similar_channels_model),
                str(filter.isModelReady() ? R.string.similar_channels_model_ready : R.string.similar_channels_model_downloading), null, false, () -> {}));

        items.add(new Item(str(R.string.similar_channels_auto_block), str(R.string.similar_channels_auto_block_desc), onOff(filter.isAutoBlock()), false, () -> {
            filter.setAutoBlock(!filter.isAutoBlock());
            update();
        }));

        if (filter.isAutoBlock()) {
            int[] levelNames = {R.string.similar_channels_level_strict, R.string.similar_channels_level_medium, R.string.similar_channels_level_broad};
            int[] levelDescs = {R.string.similar_channels_level_strict_desc, R.string.similar_channels_level_medium_desc, R.string.similar_channels_level_broad_desc};
            int level = filter.getLevel();
            items.add(new Item(str(R.string.similar_channels_level), str(levelDescs[level]), str(levelNames[level]), false, () -> {
                filter.setLevel((filter.getLevel() + 1) % levelNames.length);
                update();
            }));
        }

        return items;
    }

    private List<Item> getPinItems() {
        GeneralData generalData = GeneralData.instance(getContext());
        boolean isSet = PinHelper.isSet(getContext());
        List<Item> items = new ArrayList<>();

        items.add(new Item(str(isSet ? R.string.pin_change : R.string.pin_set), str(R.string.content_filter_pin_desc), null, false, () ->
                PinHelper.showSetPin(getContext(), pin -> {
                    generalData.setSettingsPassword(pin);
                    PinHelper.unlock();
                    update();
                })));

        if (isSet) {
            items.add(new Item(str(R.string.pin_remove), null, null, false, () -> {
                generalData.setSettingsPassword(null);
                generalData.persistNow();
                MessageHelpers.showMessage(getContext(), R.string.pin_removed);
                update();
            }));
        }

        items.add(new Item(str(R.string.check_for_updates), null, null, false,
                () -> AppUpdatePresenter.instance(getContext()).start(true)));

        return items;
    }

    // endregion

    private void unblock(String channelId, String channelName) {
        BlockedChannelData.instance(getContext()).removeChannel(channelId, channelName);
        ChannelFilter filter = filter();
        if (filter != null && channelId != null) {
            filter.onAutoBlockUndone(channelId); // no-op if the user blocked it
        }
        MessageHelpers.showMessage(getContext(), str(R.string.content_filter_unblocked, name(channelId, channelName)));
        update();
    }

    private String similarity(Suggestion channel) {
        int percent = Math.round(channel.score * 100);
        return channel.closestBlockedName != null
                ? str(R.string.content_filter_like, channel.closestBlockedName, percent)
                : str(R.string.content_filter_score, percent);
    }

    private String onOff(boolean on) {
        return str(on ? R.string.content_filter_on : R.string.content_filter_off);
    }

    private static String name(String channelId, String channelName) {
        return channelName != null ? channelName : channelId;
    }
}
