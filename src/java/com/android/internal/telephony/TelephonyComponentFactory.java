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

import android.annotation.NonNull;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.os.Handler;
import android.os.IDeviceIdleController;
import android.os.Looper;
import android.os.ServiceManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.cdma.EriManager;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.dataconnection.TransportManager;
import com.android.internal.telephony.imsphone.ImsExternalCallTracker;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneCallTracker;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccProfile;

import dalvik.system.PathClassLoader;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * This class has one-line methods to instantiate objects only. The purpose is to make code
 * unit-test friendly and use this class as a way to do dependency injection. Instantiating objects
 * this way makes it easier to mock them in tests.
 */
public class TelephonyComponentFactory {
    private static TelephonyComponentFactory sInstance;
    private static TelephonyComponentFactory sInjectedInstance;

    private static Set<String> sInjectedComponents = new HashSet<>();
    private static String sInjectedPackage;
    private static String sInjectedJarPath;

    private static final String TAG = TelephonyComponentFactory.class.getSimpleName();

    private static final String TAG_INJECTION = "injection";
    private static final String ATTRIBUTE_JAR = "jar";
    private static final String ATTRIBUTE_PACKAGE = "package";
    private static final String TAG_COMPONENTS = "components";
    private static final String TAG_COMPONENT = "component";

    public static TelephonyComponentFactory getInstance() {
        if (sInstance == null) {
            sInstance = new TelephonyComponentFactory();
            if (!TextUtils.isEmpty(sInjectedPackage)) {
                PathClassLoader classLoader = new PathClassLoader(sInjectedJarPath,
                        ClassLoader.getSystemClassLoader());
                try {
                    Class<?> cls = classLoader.loadClass(sInjectedPackage);
                    sInjectedInstance = (TelephonyComponentFactory) cls.newInstance();
                } catch (ClassNotFoundException e) {
                    Log.e(TAG, "failed: " + e.getMessage());
                } catch (IllegalAccessException | InstantiationException e) {
                    Log.e(TAG, "injection failed: " + e.getMessage());
                }
            }
        }
        return sInstance;
    }

    /**
     * Inject TelephonyComponentFactory using a xml config file.
     * @param parser a nullable {@link XmlResourceParser} created with the injection config file.
     * The config xml should has below formats:
     * <injection package="package.InjectedTelephonyComponentFactory" jar="path to jar file">
     *     <components>
     *         <component>example.package.ComponentAbc</component>
     *         <component>example.package.ComponentXyz</component>
     *         <!-- e.g. com.android.internal.telephony.GsmCdmaPhone -->
     *     </components>
     * </injection>
     */
    public static void injectTheComponentFactory(XmlResourceParser parser) {
        if (sInjectedPackage != null && sInjectedJarPath != null) {
            Log.i(TAG, "already injected.");
            return;
        }

        if (parser != null) {
            parseXml(parser);
            Log.i(TAG, "total components injected: " + sInjectedComponents.size());
        }
    }

    /**
     * Find the injection tag, assign injected package name and jar path. Then parse the injection.
     */
    private static void parseXml(@NonNull XmlPullParser parser) {
        parseXmlByTag(parser, false, p -> {
            for (int i = 0; i < parser.getAttributeCount(); i++) {
                String name = parser.getAttributeName(i);
                String value = parser.getAttributeValue(i);
                if (ATTRIBUTE_PACKAGE.equals(name)) {
                    sInjectedPackage = value;
                } else if (ATTRIBUTE_JAR.equals(name)) {
                    sInjectedJarPath = value;
                }
            }
            parseInjection(p);
        }, TAG_INJECTION);
    }

    /**
     * Only parse the first injection tag. Find the components tag, then try parse it next.
     */
    private static void parseInjection(@NonNull XmlPullParser parser) {
        parseXmlByTag(parser, false, p -> parseComponents(p), TAG_COMPONENTS);
    }

    /**
     * Only parse the first components tag. Find the component tags, then try parse them next.
     */
    private static void parseComponents(@NonNull XmlPullParser parser) {
        parseXmlByTag(parser, true, p -> parseComponent(p), TAG_COMPONENT);
    }

    /**
     * Extract text values from component tags.
     */
    private static void parseComponent(@NonNull XmlPullParser parser) {
        try {
            int outerDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.TEXT) {
                    sInjectedComponents.add(parser.getText());
                }
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Iterates the tags, finds the corresponding tag and then applies the consumer.
     */
    private static void parseXmlByTag(@NonNull XmlPullParser parser, boolean allowDuplicate,
            @NonNull Consumer<XmlPullParser> consumer, @NonNull final String tag) {
        try {
            int outerDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.START_TAG && tag.equals(parser.getName())) {
                    consumer.accept(parser);
                    if (!allowDuplicate) {
                        return;
                    }
                }
            }
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Failed to parse or find tag: " + tag, e);
        }
    }

    private boolean isFactoryInjectedForComponent(String componentName) {
        if (sInjectedInstance == null) {
            return false;
        }
        return sInjectedComponents.contains(componentName);
    }

    /**
     * Use the injected TelephonyComponentFactory if configured. Otherwise, use the default.
     * @param componentName Name of the component class uses the injected component factory,
     * e.g. GsmCdmaPhone.class.getName() for {@link GsmCdmaPhone}
     * @return injected component factory. If not configured or injected, return the default one.
     */
    public TelephonyComponentFactory inject(String componentName) {
        if (isFactoryInjectedForComponent(componentName)) {
            return sInjectedInstance;
        }
        return sInstance;
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

    /**
     * Sets the NitzStateMachine implementation to use during implementation. This boolean
     * should be removed once the new implementation is stable.
     */
    static final boolean USE_NEW_NITZ_STATE_MACHINE = true;

    /**
     * Returns a new {@link NitzStateMachine} instance.
     */
    public NitzStateMachine makeNitzStateMachine(GsmCdmaPhone phone) {
        return USE_NEW_NITZ_STATE_MACHINE
                ? new NewNitzStateMachine(phone)
                : new OldNitzStateMachine(phone);
    }

    public SimActivationTracker makeSimActivationTracker(Phone phone) {
        return new SimActivationTracker(phone);
    }

    public DcTracker makeDcTracker(Phone phone, int transportType) {
        return new DcTracker(phone, transportType);
    }

    public CarrierSignalAgent makeCarrierSignalAgent(Phone phone) {
        return new CarrierSignalAgent(phone);
    }

    public CarrierActionAgent makeCarrierActionAgent(Phone phone) {
        return new CarrierActionAgent(phone);
    }

    public CarrierResolver makeCarrierResolver(Phone phone) {
        return new CarrierResolver(phone);
    }

    public IccPhoneBookInterfaceManager makeIccPhoneBookInterfaceManager(Phone phone) {
        return new IccPhoneBookInterfaceManager(phone);
    }

    public IccSmsInterfaceManager makeIccSmsInterfaceManager(Phone phone) {
        return new IccSmsInterfaceManager(phone);
    }

    /**
     * Create a new UiccProfile object.
     */
    public UiccProfile makeUiccProfile(Context context, CommandsInterface ci, IccCardStatus ics,
                                       int phoneId, UiccCard uiccCard, Object lock) {
        return new UiccProfile(context, ci, ics, phoneId, uiccCard, lock);
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

    public TransportManager makeTransportManager(Phone phone) {
        return new TransportManager(phone);
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
    public LocaleTracker makeLocaleTracker(Phone phone, NitzStateMachine nitzStateMachine,
                                           Looper looper) {
        return new LocaleTracker(phone, nitzStateMachine, looper);
    }
}
