package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import android.util.Pair;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AppUpdatePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.ChannelFilterBridge;
import com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.KeywordFilterData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.PinHelper;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.List;

import io.github.hoanghaisol.blocktube.channelfilter.ChannelFilter;
import io.github.hoanghaisol.blocktube.channelfilter.store.ChannelStore.Suggestion;

/**
 * BlockTube: everything about blocking in one Settings tile: blocked channels, blocked
 * keywords (search and video titles), AI similar channels, PIN and app update.
 * The tile itself is behind the PIN (Settings grid).
 */
public class ContentFilterSettingsPresenter extends BasePresenter<Void> {
    private final BlockedChannelData mBlockedChannelData;
    private final KeywordFilterData mKeywordFilterData;
    private final GeneralData mGeneralData;

    private ContentFilterSettingsPresenter(Context context) {
        super(context);
        mBlockedChannelData = BlockedChannelData.instance(context);
        mKeywordFilterData = KeywordFilterData.instance(context);
        mGeneralData = GeneralData.instance(context);
    }

    public static ContentFilterSettingsPresenter instance(Context context) {
        return new ContentFilterSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendBlockedChannels(settingsPresenter);
        appendKeywords(settingsPresenter, R.string.content_filter_search_keywords, mKeywordFilterData.getSearchKeywords(),
                mKeywordFilterData::addSearchKeyword, mKeywordFilterData::removeSearchKeyword);
        appendKeywords(settingsPresenter, R.string.content_filter_video_keywords, mKeywordFilterData.getVideoKeywords(),
                mKeywordFilterData::addVideoKeyword, mKeywordFilterData::removeVideoKeyword);
        appendSimilarChannels(settingsPresenter);
        appendPin(settingsPresenter);
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.check_for_updates),
                option -> AppUpdatePresenter.instance(getContext()).start(true)));

        settingsPresenter.showDialog(getContext().getString(R.string.content_filter_title));
    }

    private void reopen() {
        AppDialogPresenter.instance(getContext()).closeDialog();
        show();
    }

    private void appendBlockedChannels(AppDialogPresenter settingsPresenter) {
        List<Pair<String, String>> channels = mBlockedChannelData.getChannelIdsWithNames();
        List<OptionItem> options = new ArrayList<>();

        for (Pair<String, String> channel : channels) {
            String name = channel.second != null ? channel.second : channel.first;
            options.add(UiOptionItem.from(getContext().getString(R.string.content_filter_unblock, name), option -> {
                unblock(channel.first, channel.second);
                reopen();
            }));
        }

        if (options.isEmpty()) {
            options.add(UiOptionItem.from(getContext().getString(R.string.content_filter_empty), option -> {}));
        }

        settingsPresenter.appendStringsCategory(
                getContext().getString(R.string.content_filter_blocked_channels, channels.size()), options);
    }

    private void unblock(String channelId, String channelName) {
        mBlockedChannelData.removeChannel(channelId, channelName);
        ChannelFilter filter = ChannelFilterBridge.instance();
        if (filter != null && channelId != null) {
            filter.onAutoBlockUndone(channelId); // no-op if the user blocked it
        }
        MessageHelpers.showMessage(getContext(), R.string.channel_unblocked);
    }

    private interface KeywordAction {
        void apply(String keyword);
    }

    private void appendKeywords(AppDialogPresenter settingsPresenter, int titleResId, List<String> keywords,
                                KeywordAction onAdd, KeywordAction onRemove) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.content_filter_add_keyword), option -> {
            // The edit dialog can't get focus over the settings panel (same as upstream password dialogs)
            settingsPresenter.closeDialog();
            SimpleEditDialog.show(getContext(), getContext().getString(R.string.content_filter_add_keyword), null,
                    newValue -> {
                        onAdd.apply(newValue);
                        show();
                        return true;
                    });
        }));

        for (String keyword : keywords) {
            options.add(UiOptionItem.from(getContext().getString(R.string.content_filter_remove_keyword, keyword), option -> {
                onRemove.apply(keyword);
                reopen();
            }));
        }

        settingsPresenter.appendStringsCategory(getContext().getString(titleResId, keywords.size()), options);
    }

    private void appendSimilarChannels(AppDialogPresenter settingsPresenter) {
        ChannelFilter filter = ChannelFilterBridge.instance();
        if (filter == null || !ChannelFilter.isSupported()) {
            return;
        }

        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.similar_channels_enable),
                getContext().getString(R.string.similar_channels_enable_desc),
                option -> {
                    if (option.isSelected()) {
                        AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.similar_channels_confirm_download),
                                () -> {
                                    filter.setEnabled(true);
                                    MessageHelpers.showMessage(getContext(), R.string.similar_channels_downloading);
                                },
                                settingsPresenter::closeDialog);
                    } else {
                        filter.setEnabled(false);
                    }
                },
                filter.isEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.similar_channels_auto_block),
                getContext().getString(R.string.similar_channels_auto_block_desc),
                option -> filter.setAutoBlock(option.isSelected()),
                filter.isAutoBlock()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.similar_channels_category), options);

        if (!filter.isEnabled()) {
            return;
        }

        List<Suggestion> suggestions = filter.getSuggestions();
        if (!suggestions.isEmpty()) {
            settingsPresenter.appendSingleButton(UiOptionItem.from(
                    getContext().getString(R.string.similar_channels_show, suggestions.size()),
                    option -> showSuggestions()));
        }

        List<Suggestion> autoBlocked = filter.getAutoBlocked();
        List<OptionItem> autoBlockedOptions = new ArrayList<>();
        for (int i = autoBlocked.size() - 1; i >= 0; i--) { // newest first
            Suggestion channel = autoBlocked.get(i);
            autoBlockedOptions.add(UiOptionItem.from(getContext().getString(R.string.content_filter_unblock, title(channel)), option -> {
                unblock(channel.channelId, channel.channelName);
                reopen();
            }));
        }
        if (autoBlockedOptions.isEmpty()) {
            autoBlockedOptions.add(UiOptionItem.from(getContext().getString(R.string.content_filter_empty), option -> {}));
        }
        settingsPresenter.appendStringsCategory(
                getContext().getString(R.string.similar_channels_auto_blocked_list, autoBlocked.size()), autoBlockedOptions);
    }

    private String title(Suggestion suggestion) {
        String name = suggestion.channelName != null ? suggestion.channelName : suggestion.channelId;
        return suggestion.closestBlockedName != null
                ? getContext().getString(R.string.similar_channels_like, name, suggestion.closestBlockedName) : name;
    }

    /**
     * Manual review, used when auto-block is off.
     */
    private void showSuggestions() {
        ChannelFilter filter = ChannelFilterBridge.instance();
        if (filter == null) {
            return;
        }

        List<Suggestion> suggestions = filter.getSuggestions();
        if (suggestions.isEmpty()) {
            MessageHelpers.showMessage(getContext(), filter.isModelReady() ? R.string.similar_channels_none : R.string.similar_channels_downloading);
            return;
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());

        for (Suggestion suggestion : suggestions) {
            List<OptionItem> actions = new ArrayList<>();

            actions.add(UiOptionItem.from(getContext().getString(R.string.dialog_block_channel), option -> {
                mBlockedChannelData.addChannel(suggestion.channelId, suggestion.channelName);
                filter.onSuggestionBlocked(suggestion.channelId);
                MessageHelpers.showMessage(getContext(), R.string.channel_blocked);
                reopenSuggestions(dialog, filter);
            }));

            actions.add(UiOptionItem.from(getContext().getString(R.string.similar_channels_ignore), option -> {
                filter.dismiss(suggestion.channelId);
                reopenSuggestions(dialog, filter);
            }));

            actions.add(UiOptionItem.from(getContext().getString(R.string.open_channel), option -> {
                dialog.closeDialog();
                ChannelPresenter.instance(getContext()).openChannel(suggestion.channelId);
            }));

            dialog.appendStringsCategory(title(suggestion), actions);
        }

        dialog.showDialog(getContext().getString(R.string.similar_channels_title));
    }

    private void reopenSuggestions(AppDialogPresenter dialog, ChannelFilter filter) {
        dialog.closeDialog();
        if (!filter.getSuggestions().isEmpty()) {
            showSuggestions();
        }
    }

    private void appendPin(AppDialogPresenter settingsPresenter) {
        boolean isSet = PinHelper.isSet(getContext());

        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(isSet ? R.string.pin_change : R.string.pin_set),
                option -> {
                    settingsPresenter.closeDialog();
                    PinHelper.showSetPin(getContext(), pin -> {
                        mGeneralData.setSettingsPassword(pin);
                        PinHelper.unlock();
                    });
                }));

        if (isSet) {
            settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.pin_remove), option -> {
                mGeneralData.setSettingsPassword(null);
                mGeneralData.persistNow();
                MessageHelpers.showMessage(getContext(), R.string.pin_removed);
                reopen();
            }));
        }
    }
}
