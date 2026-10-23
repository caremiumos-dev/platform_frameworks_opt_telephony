/*
 * Copyright (c) 2017, The Linux Foundation. All rights reserved.
 *
 * Not a Contribution.
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codeaurora.ims.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import org.codeaurora.ims.QtiCallConstants;
import org.codeaurora.ims.QtiCarrierConfigs;
import org.codeaurora.ims.QtiImsException;

import java.io.File;

public class QtiImsExtUtils {
    public static final String ACTION_VOPS_SSAC_STATUS = "org.codeaurora.VOIP_VOPS_SSAC_STATUS";
    public static final String CARRIER_ONE_DEFAULT_MCC_MNC = "405854";
    public static final String EXTRA_SSAC = "Ssac";
    public static final String EXTRA_VOPS = "Vops";
    private static String LOG_TAG = "QtiImsExtUtils";
    public static final String PROPERTY_RADIO_ATEL_CARRIER = "persist.radio.atel.carrier";
    public static final int QTI_IMS_ASSURED_TRANSFER = 2;
    public static final int QTI_IMS_BLIND_TRANSFER = 1;
    public static final String QTI_IMS_CALL_DEFLECT_NUMBER = "ims_call_deflect_number";
    public static final int QTI_IMS_CONSULTATIVE_TRANSFER = 4;
    public static final int QTI_IMS_HO_DISABLE_ALL = 2;
    public static final int QTI_IMS_HO_ENABLED_WLAN_TO_WWAN_ONLY = 3;
    public static final int QTI_IMS_HO_ENABLED_WWAN_TO_WLAN_ONLY = 4;
    public static final int QTI_IMS_HO_ENABLE_ALL = 1;
    public static final int QTI_IMS_HO_INVALID = 0;
    public static final String QTI_IMS_INCOMING_CONF_EXTRA_KEY = "incomingConference";
    public static final String QTI_IMS_PHONE_ID_EXTRA_KEY = "phoneId";
    public static final int QTI_IMS_REQUEST_ERROR = 1;
    public static final int QTI_IMS_REQUEST_SUCCESS = 0;
    public static final int QTI_IMS_SMS_APP_INVALID = -1;
    public static final int QTI_IMS_SMS_APP_NOT_RCS = 2;
    public static final int QTI_IMS_SMS_APP_RCS = 1;
    public static final int QTI_IMS_SMS_APP_SELECTION_NOT_ALLOWED = 0;
    public static final String QTI_IMS_STATIC_IMAGE_SETTING = "ims_vt_call_static_image";
    public static final String QTI_IMS_TRANSFER_EXTRA_KEY = "transferType";
    public static final int QTI_IMS_VOLTE_PREF_OFF = 0;
    public static final int QTI_IMS_VOLTE_PREF_ON = 1;
    public static final int QTI_IMS_VOLTE_PREF_UNKNOWN = 2;
    public static final int QTI_IMS_VVM_APP_INVALID = -1;
    public static final int QTI_IMS_VVM_APP_NOT_RCS = 0;
    public static final int QTI_IMS_VVM_APP_RCS = 1;

    public static class VideoQualityFeatureValuesConstants {
        public static final int HIGH = 2;
        public static final int LOW = 0;
        public static final int MEDIUM = 1;
    }

    private QtiImsExtUtils() {
    }

    public static boolean allowVideoCallsInLowBattery(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.ALLOW_VIDEO_CALL_IN_LOW_BATTERY);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth,
            int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        Log.d(LOG_TAG, "calculateInSampleSize: reqWidth = " + reqWidth + " reqHeight = " + reqHeight
                + " raw width = " + width + " raw height = " + height);
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while (halfHeight / inSampleSize > reqHeight && halfWidth / inSampleSize > reqWidth) {
                inSampleSize *= 2;
            }
        }
        Log.d(LOG_TAG, "calculateInSampleSize: inSampleSize = " + inSampleSize);
        return inSampleSize;
    }

    public static boolean canHoldVideoCall(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.ALLOW_HOLD_IN_VIDEO_CALL);
    }

    public static Bitmap decodeImage(Resources resources, int resId, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(resources, resId, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return scaleImage(BitmapFactory.decodeResource(resources, resId, options), reqWidth,
                reqHeight);
    }

    public static Bitmap decodeImage(String path, int reqWidth, int reqHeight) {
        if (path == null) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return scaleImage(BitmapFactory.decodeFile(path, options), reqWidth, reqHeight);
    }

    public static String getCallDeflectNumber(ContentResolver resolver) {
        String number = Settings.Global.getString(resolver, QTI_IMS_CALL_DEFLECT_NUMBER);
        if (number == null || !number.isEmpty()) {
            return number;
        }
        return null;
    }

    private static PersistableBundle getConfigForPhoneId(Context context, int phoneId) {
        if (context == null) {
            Log.e(LOG_TAG, "getConfigForPhoneId context is null");
            return null;
        }
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        if (configManager == null) {
            Log.e(LOG_TAG, "getConfigForPhoneId configManager is null");
            return null;
        }
        if (phoneId == -1) {
            Log.e(LOG_TAG, "getConfigForPhoneId phoneId is invalid");
            return null;
        }
        int subId = getSubscriptionIdFromPhoneId(context, phoneId);
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            return configManager.getConfigForSubId(subId);
        }
        Log.e(LOG_TAG, "getConfigForPhoneId subId is invalid");
        return null;
    }

    public static int getRttMode(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                QtiCallConstants.QTI_IMS_RTT_MODE, 0);
    }

    public static int getRttOperatingMode(Context context) {
        return SystemProperties.getInt(QtiCallConstants.PROPERTY_RTT_OPERATING_MODE, 0);
    }

    public static Bitmap getStaticImage(Context context, int reqWidth, int reqHeight)
            throws QtiImsException {
        String uriStr = getStaticImageUriStr(context.getContentResolver());
        Log.d(LOG_TAG, "getStaticImage: uriStr = " + uriStr + " reqWidth = " + reqWidth
                + " reqHeight = " + reqHeight);
        if (!isValidUriStr(uriStr)) {
            throw new QtiImsException("invalid file path");
        }
        Bitmap bitmap = decodeImage(uriStr, reqWidth, reqHeight);
        if (bitmap == null) {
            throw new QtiImsException("image decoding error");
        }
        return bitmap;
    }

    public static String getStaticImageUriStr(ContentResolver resolver) {
        return Settings.Global.getString(resolver, QTI_IMS_STATIC_IMAGE_SETTING);
    }

    private static int getSubscriptionIdFromPhoneId(Context context, int phoneId) {
        SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
        if (subscriptionManager == null) {
            return -1;
        }
        SubscriptionInfo info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(
                phoneId);
        if (info == null) {
            return -1;
        }
        return info.getSubscriptionId();
    }

    public static boolean isCallDeflectionSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.KEY_CARRIER_IMS_CALL_DEFLECT_SUPPORTED);
    }

    public static boolean isCallTransferEnabled(Context context) {
        return SystemProperties.getBoolean("persist.radio.ims_call_transfer", false);
    }

    public static boolean isCancelModifyCallSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.KEY_CARRIER_CANCEL_MODIFY_CALL_SUPPORTED);
    }

    public static boolean isCarrierConfigEnabled(int phoneId, Context context, String key) {
        return QtiCarrierConfigHelper.getInstance().getBoolean(context, phoneId, key);
    }

    public static boolean isCarrierOneSupported() {
        return CARRIER_ONE_DEFAULT_MCC_MNC.equals(SystemProperties.get(
                PROPERTY_RADIO_ATEL_CARRIER));
    }

    public static boolean isCsRetryConfigEnabled(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context, QtiCarrierConfigs.CONFIG_CS_RETRY);
    }

    public static boolean isRttDowngradeSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.KEY_CARRIER_RTT_DOWNGRADE_SUPPORTED);
    }

    public static boolean isRttOn(Context context) {
        return getRttMode(context) != 0;
    }

    public static boolean isRttSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.KEY_CARRIER_RTT_SUPPORTED);
    }

    public static boolean isRttSupportedOnVtCalls(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.KEY_CARRIER_RTT_SUPPORTED_ON_VTCALLS);
    }

    public static boolean isRttUpgradeSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.KEY_CARRIER_RTT_UPGRADE_SUPPORTED);
    }

    private static boolean isValidUriStr(String uriStr) {
        if (uriStr == null || !(!uriStr.isEmpty())) {
            return false;
        }
        return new File(uriStr).exists();
    }

    public static boolean isVoWiFiCallQualityEnabled(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context, QtiCarrierConfigs.VOWIFI_CALL_QUALITY);
    }

    private static Bitmap scaleImage(Bitmap bitmap, int reqWidth, int reqHeight) {
        if (bitmap == null) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Log.d(LOG_TAG, "scaleImage bitmap w = " + width + " bitmap h = " + height);
        Matrix matrix = new Matrix();
        matrix.postScale(reqWidth / width, reqHeight / height);
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);
    }

    public static void setCallDeflectNumber(ContentResolver resolver, String number) {
        if (number == null || number.isEmpty()) {
            number = "";
        }
        Settings.Global.putString(resolver, QTI_IMS_CALL_DEFLECT_NUMBER, number);
    }

    public static void setRttMode(boolean enable, Context context) {
        Settings.Global.putInt(context.getContentResolver(), QtiCallConstants.QTI_IMS_RTT_MODE,
                enable ? 1 : 0);
    }

    public static boolean shallHidePreviewInVtConference(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.HIDE_PREVIEW_IN_VT_CONFERENCE);
    }

    public static boolean shallRemoveModifyCallCapability(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.REMOVE_MODIFY_CALL_CAPABILITY);
    }

    public static boolean shallRemoveModifyCallCapability(Context context) {
        return shallRemoveModifyCallCapability(-1, context);
    }

    public static boolean shallShowStaticImageUi(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context, QtiCarrierConfigs.SHOW_STATIC_IMAGE_UI);
    }

    public static boolean shallShowVideoQuality(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context, QtiCarrierConfigs.SHOW_VIDEO_QUALITY_UI);
    }

    public static boolean shallTransmitStaticImage(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context, QtiCarrierConfigs.TRANSMIT_STATIC_IMAGE);
    }

    public static boolean useCustomVideoUi(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context, QtiCarrierConfigs.USE_CUSTOM_VIDEO_UI);
    }

    public static boolean useExt(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.USE_VIDEO_UI_EXTENSIONS);
    }
}
