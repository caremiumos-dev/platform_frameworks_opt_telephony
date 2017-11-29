/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.internal.telephony.euicc;

import android.content.Context;
import android.util.Log;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ServiceManager;
import android.os.RemoteException;

import com.android.internal.telephony.uicc.euicc.card.EuiccCard;
import com.android.internal.telephony.uicc.euicc.card.EuiccCardException;
import com.android.internal.telephony.uicc.euicc.card.EuiccCardImplV2;
import com.android.internal.telephony.uicc.euicc.data.EuiccSpecVersion;
import com.android.internal.telephony.uicc.euicc.telephony.UiccChannelException;
import com.android.internal.telephony.uicc.euicc.async.AsyncResultCallback;
import com.android.internal.telephony.uicc.euicc.data.EuiccProfile;
import com.android.internal.telephony.uicc.euicc.asn1.Asn1Node;
import com.android.internal.telephony.uicc.euicc.asn1.Asn1Decoder;
import com.android.internal.telephony.uicc.euicc.asn1.InvalidAsn1DataException;
import com.android.internal.telephony.uicc.euicc.telephony.UiccLogicalChannel;
import com.android.internal.telephony.uicc.euicc.asn1.TagNotFoundException;
import android.telephony.TelephonyManager;
import java.util.concurrent.ExecutionException;

import com.android.internal.telephony.euicc.IEuiccCardController;
import android.service.euicc.IGetAllProfilesCallback;

/** Backing implementation of {@link EuiccCardManager}. */
public class EuiccCardController extends IEuiccCardController.Stub {

    private static final String TAG = "EuiccCardController";

    // ASN.1 Tags
    private static final int TAG_ISD_R_APP_TEMPLATE = 0xE0;
    private static final int TAG_VERSION = 0x82;

    private static final EuiccSpecVersion DEFAULT_SPEC_VERSION =
            new EuiccSpecVersion(1 /* major */, 0 /* minor */, 0 /* version */);
    private static final EuiccSpecVersion.Range CARD_V2_VER_RANGE =
            new EuiccSpecVersion.Range(
                    new EuiccSpecVersion(2 /* major */, 0 /* minor */, 0 /* version */),
                    new EuiccSpecVersion(2 /* major */, 0 /* minor */, 1 /* version */));

    private static EuiccCardController sInstance;
    private final Context mContext;
    private EuiccCard mEuiccCard;

    private HandlerThread mEuiccThread;
    private Handler mEuiccHandler;
    private final Object mLock = new Object();

    // Constructor

    /** Initialize the instance. Should only be called once. */
    public static EuiccCardController init(Context context) {
        synchronized (EuiccCardController.class) {
            if (sInstance == null) {
                Log.e(TAG, "HOLLY DEBUG - EuiccCardController - init EuiccCardController");
                sInstance = new EuiccCardController(context);
                Log.e(TAG, "HOLLY DEBUG - EuiccCardController - sInstance" + sInstance);
            } else {
                Log.wtf(TAG, "init() called multiple times! sInstance = " + sInstance);
            }
        }
        return sInstance;
    }

    /** Get an instance. Assumes one has already been initialized with {@link #init}. */
    public static EuiccCardController get() {
        if (sInstance == null) {
            synchronized (EuiccCardController.class) {
                if (sInstance == null) {
                    throw new IllegalStateException("get() called before init()");
                }
            }
        }
        return sInstance;
    }

    private EuiccCardController(Context context) {
        Log.e(TAG, "HOLLY DEBUG - EuiccCardController - before constructor");
        mContext = context;
        //        mAppOps = (AppOpsManager)app.getSystemService(Context.APP_OPS_SERVICE);

        mEuiccThread = new HandlerThread("EuiccServiceImpl.Euicc");
        mEuiccThread.start();
        mEuiccHandler = new Handler(mEuiccThread.getLooper());

        ServiceManager.addService("euicc_card_controller", this);

        Log.e(TAG, "HOLLY DEBUG - EuiccCardController - after constructor");
    }

    // APIs

    @Override
    public void getAllProfiles(String eid, String callingPackage, IGetAllProfilesCallback resCallback) {
        // check the caller is the current LPA
//        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
//        ComponentInfo bestComponent =
//                EuiccConnector.findBestComponent(mPhone.getContext().getPackageManager());
//        if (bestComponent == null
//                || !TextUtils.equals(callingPackage, bestComponent.packageName)) {
//            loge("The calling package is not allowed to access ISD-R.");
//            throw new SecurityException("The calling package is not allowed to access ISD-R.");
//        }

        // cb: check not null

        Log.e(TAG, "HOLLY DEBUG - EuiccCardController - in getAllProfiles");
        AsyncResultCallback<EuiccProfile[]> callback = new AsyncResultCallback<>();
        EuiccProfile[] profiles;
        getEuiccCard().getAllProfiles(callback, null /* handler */);
        try {
            profiles = callback.asFuture().get();
            Log.e(TAG, "HOLLY DEBUG - EuiccCardController profiles : " + profiles);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "HOLLY DEBUG - No available profile. 1");
        } catch (ExecutionException e) {
            Log.e(TAG, "HOLLY DEBUG - No available profile. 2");
        }
        try {
            resCallback.onGetAllProfilesComplete(0);
        } catch (RemoteException e) {
            Log.e(TAG, "HOLLY DEBUG - No available profile. 3");
        }
    }

    private EuiccCard getEuiccCard() {
        try {
            synchronized (mLock) {
                // check sim unavailable states
                if (mEuiccCard == null) {
                    mEuiccCard = loadEuiccCard();
                }
                return mEuiccCard;
            }
        } catch (EuiccCardException | UiccChannelException e) {
            Log.e(TAG, "Cannot load euicc card.");
        }
        return null;
    }

    private EuiccCard loadEuiccCard() throws EuiccCardException, UiccChannelException {
        EuiccSpecVersion cardVer = readCardVersion();
        return createEuiccCard(cardVer);
    }

    private EuiccSpecVersion readCardVersion() throws UiccChannelException {
        Asn1Node node;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(getTelephonyManager())) {
            try {
                Asn1Decoder decoder = new Asn1Decoder(ch.getSelectResponse());
                if (!decoder.hasNextNode()) {
                    return DEFAULT_SPEC_VERSION;
                }
                node = decoder.nextNode();
            } catch (InvalidAsn1DataException e) {
                Log.e(TAG, "Cannot parse the select response of ISD-R.");
                return DEFAULT_SPEC_VERSION;
            }
        } catch (UiccChannelException e) {
            throw e;
        }

        try {
            byte[] versionType;
            if (node.getTag() == TAG_ISD_R_APP_TEMPLATE) {
                versionType = node.getChild(TAG_VERSION).asBytes();
            } else {
                versionType = node.getChild(TAG_ISD_R_APP_TEMPLATE, TAG_VERSION).asBytes();
            }
            if (versionType.length != 3) {
               Log.e(TAG, String.format("Cannot parse select response of ISD-R: " + node.toHex()));
                return DEFAULT_SPEC_VERSION;
            }
            return new EuiccSpecVersion(
                    versionType[0] & 0xFF, versionType[1] & 0xFF, versionType[2] & 0xFF);
        } catch (InvalidAsn1DataException | TagNotFoundException e) {
            return DEFAULT_SPEC_VERSION;
        }
    }

    private EuiccCard createEuiccCard(EuiccSpecVersion cardVer) throws EuiccCardException {
        if (CARD_V2_VER_RANGE.includes(cardVer)) {
            return new EuiccCardImplV2(mEuiccThread.getLooper(), cardVer, getTelephonyManager());
        }
        throw new EuiccCardException(String.format(
                "eUICC version is not supported by any EuiccCard implementation: "
                        + cardVer.toString()));
    }

    private void disposeEuiccCard() {
        synchronized (mLock) {
            if (mEuiccCard != null) {
                mEuiccCard.close();
                mEuiccCard = null;
            }
        }
    }

    TelephonyManager getTelephonyManager() {
        return (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }
}