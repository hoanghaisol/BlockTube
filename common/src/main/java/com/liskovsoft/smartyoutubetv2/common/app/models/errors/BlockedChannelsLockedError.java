package com.liskovsoft.smartyoutubetv2.common.app.models.errors;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ContentFilterPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData;

/**
 * BlockTube: the Blocked channels section shows a button to the Content filter screen.
 */
public class BlockedChannelsLockedError implements ErrorFragmentData {
    private final Context mContext;

    public BlockedChannelsLockedError(Context context) {
        mContext = context;
    }

    @Override
    public void onAction() {
        ContentFilterPresenter.instance(mContext).open(ContentFilterPresenter.SECTION_BLOCKED);
    }

    @Override
    public String getMessage() {
        return mContext.getString(R.string.content_filter_blocked_channels, BlockedChannelData.instance(mContext).getChannelCount());
    }

    @Override
    public String getActionText() {
        return mContext.getString(R.string.content_filter_open);
    }
}
