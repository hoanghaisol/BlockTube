package com.liskovsoft.smartyoutubetv2.common.app.models.errors;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.PinHelper;

public class BlockedChannelsLockedError implements ErrorFragmentData {
    private final Context mContext;

    public BlockedChannelsLockedError(Context context) {
        mContext = context;
    }

    @Override
    public void onAction() {
        PinHelper.check(mContext, () -> BrowsePresenter.instance(mContext).refresh());
    }

    @Override
    public String getMessage() {
        return null;
    }

    @Override
    public String getActionText() {
        return mContext.getString(R.string.pin_enter);
    }
}
