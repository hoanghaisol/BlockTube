package com.liskovsoft.smartyoutubetv2.common.app.models.errors;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

public class BlockedChannelsLockedError implements ErrorFragmentData {
    private final Context mContext;

    public BlockedChannelsLockedError(Context context) {
        mContext = context;
    }

    @Override
    public void onAction() {
        String password = GeneralData.instance(mContext).getSettingsPassword();

        SimpleEditDialog.showPassword(
                mContext,
                mContext.getString(R.string.enter_settings_password),
                null,
                newValue -> {
                    if (Utils.passwordMatch(password, newValue)) {
                        BlockedChannelData.instance(mContext).unlock();
                        BrowsePresenter.instance(mContext).refresh();
                        return true;
                    }
                    return false;
                });
    }

    @Override
    public String getMessage() {
        return null;
    }

    @Override
    public String getActionText() {
        return mContext.getString(R.string.enter_settings_password);
    }
}
