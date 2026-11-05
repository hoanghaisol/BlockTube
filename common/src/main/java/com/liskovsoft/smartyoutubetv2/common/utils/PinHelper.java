package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

/**
 * BlockTube: the settings password is a 4-digit PIN (number pad on the remote).
 * A password set before this change (not 4 digits) is still asked with the text dialog.
 */
public class PinHelper {
    private static final int PIN_LENGTH = 4;
    private static final long UNLOCK_TIMEOUT_MS = 5 * 60 * 1_000;
    private static long sUnlockedUntilMs;

    public static boolean isPin(String value) {
        return value != null && value.matches("\\d{" + PIN_LENGTH + "}");
    }

    public static boolean isSet(Context context) {
        return GeneralData.instance(context).getSettingsPassword() != null;
    }

    /**
     * A correct PIN unlocks Settings, the block list and unblocking for a few minutes (memory only).
     */
    public static boolean isLocked(Context context) {
        return isSet(context) && System.currentTimeMillis() > sUnlockedUntilMs;
    }

    public static void unlock() {
        sUnlockedUntilMs = System.currentTimeMillis() + UNLOCK_TIMEOUT_MS;
    }

    /**
     * Runs {@code onSuccess} right away when no PIN is set or it was entered recently.
     */
    public static void check(Context context, Runnable onSuccess) {
        String password = GeneralData.instance(context).getSettingsPassword();
        if (!isLocked(context)) {
            onSuccess.run();
            return;
        }

        SimpleEditDialog.OnChange onChange = newValue -> {
            if (Utils.passwordMatch(password, newValue)) {
                unlock();
                onSuccess.run();
                return true;
            }
            MessageHelpers.showMessage(context, R.string.pin_wrong);
            return false;
        };

        if (isPin(password)) {
            SimpleEditDialog.showPin(context, context.getString(R.string.pin_enter), onChange);
        } else {
            SimpleEditDialog.showPassword(context, context.getString(R.string.enter_settings_password), null, onChange);
        }
    }

    /**
     * Asks for a new PIN twice. {@code onSet} receives the PIN.
     */
    public static void showSetPin(Context context, SetListener onSet) {
        SimpleEditDialog.showPin(context, context.getString(R.string.pin_new), first -> {
            if (!isPin(first)) {
                MessageHelpers.showMessage(context, R.string.pin_must_be_4_digits);
                return false;
            }

            SimpleEditDialog.showPin(context, context.getString(R.string.pin_confirm), second -> {
                if (!first.equals(second)) {
                    MessageHelpers.showMessage(context, R.string.pin_mismatch);
                    return false;
                }
                onSet.onSet(first);
                GeneralData.instance(context).persistNow(); // don't lose the PIN to the delayed save
                MessageHelpers.showMessage(context, R.string.pin_saved);
                return true;
            });
            return true;
        });
    }

    public interface SetListener {
        void onSet(String pin);
    }
}
