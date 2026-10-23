/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.internal.telephony;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.IDeviceIdleController;
import android.os.Looper;
import android.os.ServiceManager;
import android.telephony.Rlog;

import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.cdma.EriManager;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.imsphone.ImsExternalCallTracker;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneCallTracker;
import com.android.internal.telephony.uicc.IccCardProxy;

import dalvik.system.PathClassLoader;

import java.lang.reflect.Constructor;

/**
 * This class has one-line methods to instantiate objects only. The purpose is to make code
 * unit-test friendly and use this class as a way to do dependency injection. Instantiating objects
 * this way makes it easier to mock them in tests.
 */
public class TelephonyComponentFactory {
    protected static String LOG_TAG = "TelephonyComponentFactory";
    private static TelephonyComponentFactory sInstance;

    public static TelephonyComponentFactory getInstance() {
        if (sInstance == null) {
            // CAF 差异：设备基座通过反射从 qti-telephony-common.jar 载入 QTI 工厂实现，
            // 失败时回退到 AOSP 默认工厂。
            String factoryClassName =
                    "com.qualcomm.qti.internal.telephony.QtiTelephonyComponentFactory";
            PathClassLoader classLoader = new PathClassLoader(
                    "/system/framework/qti-telephony-common.jar",
                    ClassLoader.getSystemClassLoader());
            Rlog.d(LOG_TAG, "classLoader = " + classLoader);
            if (factoryClassName == null || factoryClassName.length() == 0) {
                Rlog.d(LOG_TAG, "no customized TelephonyPlugin available, fallback to default");
                factoryClassName = "com.android.internal.telephony.TelephonyComponentFactory";
            }
            try {
                Class<?> clazz = Class.forName(factoryClassName, false, classLoader);
                Rlog.d(LOG_TAG, "cls = " + clazz);
                Constructor<?> constructor = clazz.getConstructor(new Class[0]);
                Rlog.d(LOG_TAG, "constructor method = " + constructor);
                sInstance = (TelephonyComponentFactory) constructor.newInstance(new Object[0]);
            } catch (Exception e) {
                e.printStackTrace();
                Rlog.e(LOG_TAG, "Error loading TelephonyComponentFactory");
                sInstance = new TelephonyComponentFactory();
            } catch (NoClassDefFoundError e) {
                e.printStackTrace();
                Rlog.e(LOG_TAG, "error loading TelephonyComponentFactory");
                sInstance = new TelephonyComponentFactory();
            }
        }
        return sInstance;
    }

    public SubscriptionController initSubscriptionController(Context context,
            CommandsInterface[] ci) {
        Rlog.d(LOG_TAG, "initSubscriptionController");
        return SubscriptionController.init(context, ci);
    }

    public void makeExtTelephonyClasses(Context context, Phone[] phoneArr,
            CommandsInterface[] commandsInterfaceArr) {
        Rlog.d(LOG_TAG, "makeExtTelephonyClasses");
    }

    public GsmCdmaCallTracker makeGsmCdmaCallTracker(GsmCdmaPhone phone) {
        return new GsmCdmaCallTracker(phone);
    }

    public SmsStorageMonitor makeSmsStorageMonitor(Phone phone) {
        return new SmsStorageMonitor(phone);
    }

    public SmsUsageMonitor makeSmsUsageMonitor(Context context) {
        return new SmsUsageMonitor(context);
    }

    public ServiceStateTracker makeServiceStateTracker(GsmCdmaPhone phone, CommandsInterface ci) {
        return new ServiceStateTracker(phone, ci);
    }

    public SimActivationTracker makeSimActivationTracker(Phone phone) {
        return new SimActivationTracker(phone);
    }

    public DcTracker makeDcTracker(Phone phone) {
        return new DcTracker(phone);
    }

    public CarrierSignalAgent makeCarrierSignalAgent(Phone phone) {
        return new CarrierSignalAgent(phone);
    }

    public CarrierActionAgent makeCarrierActionAgent(Phone phone) {
        return new CarrierActionAgent(phone);
    }

    public IccPhoneBookInterfaceManager makeIccPhoneBookInterfaceManager(Phone phone) {
        return new IccPhoneBookInterfaceManager(phone);
    }

    public IccSmsInterfaceManager makeIccSmsInterfaceManager(Phone phone) {
        return new IccSmsInterfaceManager(phone);
    }

    public IccCardProxy makeIccCardProxy(Context context, CommandsInterface ci, int phoneId) {
        return new IccCardProxy(context, ci, phoneId);
    }

    public EriManager makeEriManager(Phone phone, Context context, int eriFileSource) {
        return new EriManager(phone, context, eriFileSource);
    }

    public WspTypeDecoder makeWspTypeDecoder(byte[] pdu) {
        return new WspTypeDecoder(pdu);
    }

    /**
     * Create a tracker for a single-part SMS.
     */
    public InboundSmsTracker makeInboundSmsTracker(byte[] pdu, long timestamp, int destPort,
            boolean is3gpp2, boolean is3gpp2WapPdu, String address, String displayAddr,
            String messageBody) {
        return new InboundSmsTracker(pdu, timestamp, destPort, is3gpp2, is3gpp2WapPdu, address,
                displayAddr, messageBody);
    }

    /**
     * Create a tracker for a multi-part SMS.
     */
    public InboundSmsTracker makeInboundSmsTracker(byte[] pdu, long timestamp, int destPort,
            boolean is3gpp2, String address, String displayAddr, int referenceNumber, int sequenceNumber,
            int messageCount, boolean is3gpp2WapPdu, String messageBody) {
        return new InboundSmsTracker(pdu, timestamp, destPort, is3gpp2, address, displayAddr,
                referenceNumber, sequenceNumber, messageCount, is3gpp2WapPdu, messageBody);
    }

    /**
     * Create a tracker from a row of raw table
     */
    public InboundSmsTracker makeInboundSmsTracker(Cursor cursor, boolean isCurrentFormat3gpp2) {
        return new InboundSmsTracker(cursor, isCurrentFormat3gpp2);
    }

    public ImsPhoneCallTracker makeImsPhoneCallTracker(ImsPhone imsPhone) {
        return new ImsPhoneCallTracker(imsPhone);
    }

    public ImsExternalCallTracker makeImsExternalCallTracker(ImsPhone imsPhone) {

        return new ImsExternalCallTracker(imsPhone);
    }

    /**
     * Create an AppSmsManager for per-app SMS message.
     */
    public AppSmsManager makeAppSmsManager(Context context) {
        return new AppSmsManager(context);
    }

    public DeviceStateMonitor makeDeviceStateMonitor(Phone phone) {
        return new DeviceStateMonitor(phone);
    }

    // CAF 差异：设备基座由工厂创建 Phone/RIL/PhoneSwitcher/SubscriptionInfoUpdater。
    public Phone makePhone(Context context, CommandsInterface commandsInterface,
            PhoneNotifier phoneNotifier, int phoneId, int phoneType,
            TelephonyComponentFactory telephonyComponentFactory) {
        Rlog.d(LOG_TAG, "makePhone");
        if (phoneType == 1) {
            return new GsmCdmaPhone(context, commandsInterface, phoneNotifier, phoneId, 1,
                    telephonyComponentFactory);
        }
        if (phoneType == 2) {
            return new GsmCdmaPhone(context, commandsInterface, phoneNotifier, phoneId, 6,
                    telephonyComponentFactory);
        }
        return null;
    }

    public PhoneSwitcher makePhoneSwitcher(int maxActivePhones, int numPhones, Context context,
            SubscriptionController subscriptionController, Looper looper,
            ITelephonyRegistry telephonyRegistry, CommandsInterface[] cis, Phone[] phones) {
        Rlog.d(LOG_TAG, "makePhoneSwitcher");
        return new PhoneSwitcher(maxActivePhones, numPhones, context, subscriptionController,
                looper, telephonyRegistry, cis, phones);
    }

    public RIL makeRIL(Context context, int preferredNetworkType, int cdmaSubscription,
            Integer instanceId) {
        Rlog.d(LOG_TAG, "makeRIL");
        return new RIL(context, preferredNetworkType, cdmaSubscription, instanceId);
    }

    public SubscriptionInfoUpdater makeSubscriptionInfoUpdater(Looper looper, Context context,
            Phone[] phoneArr, CommandsInterface[] commandsInterfaceArr) {
        Rlog.d(LOG_TAG, "makeSubscriptionInfoUpdater");
        return new SubscriptionInfoUpdater(looper, context, phoneArr, commandsInterfaceArr);
    }

    public CdmaSubscriptionSourceManager
    getCdmaSubscriptionSourceManagerInstance(Context context, CommandsInterface ci, Handler h,
                                             int what, Object obj) {
        return CdmaSubscriptionSourceManager.getInstance(context, ci, h, what, obj);
    }

    public IDeviceIdleController getIDeviceIdleController() {
        return IDeviceIdleController.Stub.asInterface(
                ServiceManager.getService(Context.DEVICE_IDLE_CONTROLLER));
    }
}
