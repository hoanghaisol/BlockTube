package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.ChannelFilterBridge;
import com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;

import java.util.ArrayList;
import java.util.List;

import io.github.hoanghaisol.blocktube.channelfilter.ChannelFilter;
import io.github.hoanghaisol.blocktube.channelfilter.store.ChannelStore.Suggestion;

/**
 * BlockTube: settings and review screen of the AI "similar channels" suggester.
 * The AI only suggests; every block is confirmed here by the user.
 */
public class SimilarChannelsPresenter extends BasePresenter<Void> {
    private SimilarChannelsPresenter(Context context) {
        super(context);
    }

    public static SimilarChannelsPresenter instance(Context context) {
        return new SimilarChannelsPresenter(context);
    }

    /**
     * Adds the feature category to the General settings dialog. Hidden below Android 7.
     */
    public void appendSettings(AppDialogPresenter settingsPresenter) {
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

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.similar_channels_category), options);

        if (filter.isEnabled()) {
            settingsPresenter.appendSingleButton(UiOptionItem.from(
                    getContext().getString(R.string.similar_channels_show, filter.getSuggestions().size()),
                    option -> showSuggestions()));
        }
    }

    public void showSuggestions() {
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
            String name = suggestion.channelName != null ? suggestion.channelName : suggestion.channelId;
            String title = suggestion.closestBlockedName != null
                    ? getContext().getString(R.string.similar_channels_like, name, suggestion.closestBlockedName) : name;

            List<OptionItem> actions = new ArrayList<>();

            actions.add(UiOptionItem.from(getContext().getString(R.string.dialog_block_channel), option -> {
                BlockedChannelData.instance(getContext()).addChannel(suggestion.channelId, suggestion.channelName);
                filter.onSuggestionBlocked(suggestion.channelId);
                MessageHelpers.showMessage(getContext(), R.string.channel_blocked);
                reopen(dialog, filter);
            }));

            actions.add(UiOptionItem.from(getContext().getString(R.string.similar_channels_ignore), option -> {
                filter.dismiss(suggestion.channelId);
                reopen(dialog, filter);
            }));

            actions.add(UiOptionItem.from(getContext().getString(R.string.open_channel), option -> {
                dialog.closeDialog();
                ChannelPresenter.instance(getContext()).openChannel(suggestion.channelId);
            }));

            dialog.appendStringsCategory(title, actions);
        }

        dialog.showDialog(getContext().getString(R.string.similar_channels_title));
    }

    private void reopen(AppDialogPresenter dialog, ChannelFilter filter) {
        dialog.closeDialog();
        if (!filter.getSuggestions().isEmpty()) {
            showSuggestions();
        }
    }
}
