package com.android.internal.telephony.datacallactive;

/**
 * XTC night-static-mode completion callback.
 *
 * @hide
 */
public interface NightStaticModeCheckCompletely {
    void enterNightStaticMode();

    void exitNightStaticMode();
}
