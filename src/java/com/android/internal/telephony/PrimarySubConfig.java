
package com.android.internal.telephony;

import android.content.Context;
import android.content.res.Resources;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UiccProfile;
import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PrimarySubConfig {
    private static final boolean DBG = true;
    private static final String TAG = "PrimarySubConfig";

    private static PrimarySubConfig mInstance;

    private int mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
    private List<PrimarySubConfigInfo> mAllConfigInfos = new ArrayList<>();
    private PrimarySubConfigInfo[] mIccConfigs = new PrimarySubConfigInfo[mPhoneCount];

    private Context mContext;

    private PrimarySubConfig(Context context) {
        mContext = context;
        loadPrimarySubConfigInfo();
    }

    static PrimarySubConfig init(Context context) {
        if (mInstance == null) {
            mInstance = new PrimarySubConfig(context);
        }
        return mInstance;
    }

    public static PrimarySubConfig getInstance() {
        return mInstance;
    }

    private void loadPrimarySubConfigInfo() {
        try {
            Resources resource = mContext.getApplicationContext().getResources();
            int resId = resource.getIdentifier("primary_sub_conf", "xml",
                    mContext.getPackageName());
            XmlPullParser parser = resource.getXml(resId);

            XmlUtils.beginDocument(parser, "iccConfigs");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"iccConfig".equals(name)) {
                    break;
                }

                mAllConfigInfos.add(new PrimarySubConfigInfo(parser));
            }
        } catch (XmlPullParserException e) {
            logw("Exception in primary_sub_conf parser " + e);
        } catch (IOException e) {
            logw("Exception in primary_sub_conf parser " + e);
        }

        Rlog.d(TAG, "loadPrimarySubConfigInfo done: " + mAllConfigInfos);
    }

    public void update() {
        int phoneCount = TelephonyManager.getDefault().getPhoneCount();
        SubscriptionManager subManager = SubscriptionManager.from(mContext);
        mIccConfigs = new PrimarySubConfigInfo[phoneCount];
        for (int i = 0; i < phoneCount; i++) {
            SubscriptionInfo subInfo = subManager.getActiveSubscriptionInfoForSimSlotIndex(i);
            if (subInfo != null) {
                for (PrimarySubConfigInfo config : mAllConfigInfos) {
                    PrimarySubConfigInfo newConfig = new PrimarySubConfigInfo(config);
                    if (newConfig.match(subInfo)) {
                        if (mIccConfigs[i] == null
                                || mIccConfigs[i].mMatchedScore < newConfig.mMatchedScore) {
                            mIccConfigs[i] = newConfig;
                        }
                    }
                }
            }
        }
    }

    /**
     * Return primary phoneId which has the biggest priority according to matched icc configs.
     */
    public int getPreferredPrimaryCard() {
        int maxPriorityPhoneId = 0;
        SubscriptionManager subManager = SubscriptionManager.from(mContext);
        for (int i = 0; i < mPhoneCount; i++) {
            if (mIccConfigs[i] != null
                    && (mIccConfigs[maxPriorityPhoneId] == null
                    || mIccConfigs[maxPriorityPhoneId].mPriority < mIccConfigs[i].mPriority)) {
                maxPriorityPhoneId = i;
            }
            SubscriptionInfo subInfo = subManager
                    .getActiveSubscriptionInfoForSimSlotIndex(maxPriorityPhoneId);
            if (subInfo != null && (!subManager.isSubscriptionEnabled(subInfo.getSubscriptionId())
                    || subInfo.isOpportunistic()) && i != maxPriorityPhoneId) {
                logd("the subinfo is disabled or opportunistic");
                maxPriorityPhoneId = i;
            }
        }

        return maxPriorityPhoneId;
    }

    /**
     * Return whether need popup primary data prompt according to icc configs.
     */
    public boolean isPopupDefaultDataPrompt() {
        int maxPriorityPhoneId = getPreferredPrimaryCard();
        if (mIccConfigs[maxPriorityPhoneId] == null) {
            return false;
        }

        int maxPriorityCount = 0;
        boolean allowUserPrompt = false;
        for (int i = 0; i < mPhoneCount; i++) {
            if (mIccConfigs[i] != null
                    && mIccConfigs[i].mPriority == mIccConfigs[maxPriorityPhoneId].mPriority) {
                maxPriorityCount++;
                allowUserPrompt |= mIccConfigs[i].mUserPrompt;
            }
        }
        return maxPriorityCount >= 2 && allowUserPrompt;
    }

    /**
     * Return default network type of matched icc config for specified phoneId.
     *
     * @return network type if not specified, return -1
     * @see RILConstants#NETWORK_MODE_WCDMA_PREF
     * @see RILConstants#NETWORK_MODE_LTE_GSM_WCDMA
     */
    public int getNetworkType(int phoneId) {
        if (mIccConfigs[phoneId] != null) {
            return mIccConfigs[phoneId].mNetwork;
        }
        return -1;
    }

    class PrimarySubConfigInfo {
        private static final int ANY_MATCHED_FIALED = -1;
        private static final int MATCHED_PARTTERN = 1 << 8;
        private static final int MATCHED_APP_TYPE = 1 << 4;
        private static final int MATCHED_ROAMING_STATE = 1 << 2;

        private String mTag;
        private String mPatternType;
        private String mPattern;
        private String mAppType;
        private int mPriority = -1;
        private int mNetwork = -1;
        private int mRestrictedNetwork = -1;
        private String mRestrictedBy;
        private boolean mUserPrompt;
        private String mRoaming;
        private String mGid;
        // Give each of the optional matching conditions a score and calculate
        // the highest score which means the best matched config.
        private int mMatchedScore;
        private SubscriptionInfo mMatchedSubInfo;

        PrimarySubConfigInfo(XmlPullParser parser) {
            mTag = parser.getAttributeValue(null, "tag");
            mPatternType = parser.getAttributeValue(null, "pattern_type");
            mPattern = parser.getAttributeValue(null, "pattern");
            mAppType = parser.getAttributeValue(null, "app_type");
            mRoaming = parser.getAttributeValue(null, "roaming");
            mPriority = Integer.parseInt(parser.getAttributeValue(null, "priority"));
            String netwrokValue = parser.getAttributeValue(null, "network");
            if (!TextUtils.isEmpty(netwrokValue)) {
                mNetwork = Integer.parseInt(netwrokValue);
            }
            String restrictedNetwrokValue = parser.getAttributeValue(null, "restricted_network");
            if (!TextUtils.isEmpty(restrictedNetwrokValue)) {
                mRestrictedNetwork = Integer.parseInt(restrictedNetwrokValue);
            }
            mRestrictedBy = parser.getAttributeValue(null, "restricted_by");
            mUserPrompt = Boolean.valueOf(parser.getAttributeValue(null, "user_prompt"));
            mGid = parser.getAttributeValue(null, "gid");
        }

        PrimarySubConfigInfo(PrimarySubConfigInfo config) {
            copyFrom(config);
        }

        boolean match(SubscriptionInfo subInfo) {
            mMatchedScore = 0;
            if (subInfo != null
                    && SubscriptionManager.isValidSubscriptionId(subInfo.getSubscriptionId())) {
                int phoneId = subInfo.getSimSlotIndex();
                int subId = subInfo.getSubscriptionId();
                if (!TextUtils.isEmpty(mPattern)) {
                    // According to the agreement, ICCID is the only identifier
                    // for operators, but some special operators such as
                    // 'Reliance' have to use MCC/MNC to identify their SIM
                    // cards of different sub-brands. So use ICCID first unless
                    // "pattern_type" is specified.
                    Pattern p = Pattern.compile(mPattern);
                    if ("mccmnc".equalsIgnoreCase(mPatternType)) {
                        String mccMnc = TelephonyManager.from(mContext)
                                .getSimOperatorNumericForPhone(phoneId);
                        if (!TextUtils.isEmpty(mccMnc) && p.matcher(mccMnc).find()) {
                            if (!TextUtils.isEmpty(mGid)) {
                                Pattern p1 = Pattern.compile(mGid);
                                String gid1 = TelephonyManager.from(mContext)
                                        .getGroupIdLevel1(subId);
                                if (!TextUtils.isEmpty(gid1) && p1.matcher(gid1).find()) {
                                    mMatchedScore |= MATCHED_PARTTERN;
                                }
                            } else {
                                mMatchedScore |= MATCHED_PARTTERN;
                            }
                        }
                    } else {
                        if (!TextUtils.isEmpty(subInfo.getIccId()) && p.matcher(subInfo.getIccId()).find()) {
                            mMatchedScore |= MATCHED_PARTTERN;
                        }
                    }
                    if ((mMatchedScore & MATCHED_PARTTERN) != MATCHED_PARTTERN) {
                        mMatchedScore = ANY_MATCHED_FIALED;
                        return false;
                    }
                }

                if (!TextUtils.isEmpty(mAppType)) {
                    UiccCard uiccCard = UiccController.getInstance().getUiccCard(phoneId);
                    if (uiccCard != null) {
                        UiccProfile up = uiccCard.getUiccProfile();
                        if (up != null && up.isApplicationOnIcc(AppType.valueOf(mAppType))) {
                            mMatchedScore |= MATCHED_APP_TYPE;
                        }
                    }
                    if ((mMatchedScore & MATCHED_APP_TYPE) != MATCHED_APP_TYPE) {
                        mMatchedScore = ANY_MATCHED_FIALED;
                        return false;
                    }
                }

                if (!TextUtils.isEmpty(mRoaming)) {
                    TelephonyManager tm = TelephonyManager.from(mContext);
                    ServiceState ss = tm.getServiceStateForSubscriber(subId);
                    boolean isRoaming = ss != null ? ss.getRoaming() : false;
                    if (String.valueOf(isRoaming).equals(mRoaming)) {
                        mMatchedScore |= MATCHED_ROAMING_STATE;
                    }
                    if ((mMatchedScore & MATCHED_ROAMING_STATE) != MATCHED_ROAMING_STATE) {
                        mMatchedScore = ANY_MATCHED_FIALED;
                        return false;
                    }
                }
            }

            if (mMatchedScore > 0) {
                mMatchedSubInfo = subInfo;
            }

            return mMatchedScore > 0;
        }

        private void copyFrom(PrimarySubConfigInfo config) {
            mTag = config.mTag;
            mPatternType = config.mPatternType;
            mPattern = config.mPattern;
            mAppType = config.mAppType;
            mRoaming = config.mRoaming;
            mPriority = config.mPriority;
            mNetwork = config.mNetwork;
            mRestrictedNetwork = config.mRestrictedNetwork;
            mRestrictedBy = config.mRestrictedBy;
            mUserPrompt = config.mUserPrompt;
            mGid = config.mGid;
        }

        @Override
        public String toString() {
            return "PrimarySubConfigInfo: "
                    + " tag= " + mTag
                    + " patternType= " + mPatternType
                    + " parttern= " + mPattern
                    + " appType= " + mAppType
                    + " priority= " + mPriority
                    + " network= " + mNetwork
                    + " restrictedNetwork= " + mRestrictedNetwork
                    + " restrictedBy= " + mRestrictedBy
                    + " userPrompt= " + mUserPrompt
                    + " roaming= " + mRoaming
                    + " gid= " + mGid
                    + " matchedScore= " + mMatchedScore
                    + " sub= "
                    + (mMatchedSubInfo == null ? "null" : mMatchedSubInfo.getSubscriptionId())
                    + "\n";
        }
    }

    private void logd(String msg) {
        if (DBG)
            Rlog.d(TAG, msg);
    }

    private void logw(String msg) {
        if (DBG)
            Rlog.w(TAG, msg);
    }
}
