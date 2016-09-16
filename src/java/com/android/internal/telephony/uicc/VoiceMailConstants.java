/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony.uicc;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.os.Environment;
import android.util.Xml;
import android.telephony.Rlog;

import java.util.HashMap;
import java.util.Locale;
import java.io.FileReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import com.android.internal.util.XmlUtils;

/**
 * {@hide}
 */
class VoiceMailConstants {
    private HashMap<String, String[]> CarrierVmMap;
    private Context mContext;
    private Locale mLocale;

    static final String LOG_TAG = "VoiceMailConstants";
    static final String PARTNER_VOICEMAIL_PATH ="etc/voicemail-conf.xml";

    static final int NAME = 0;
    static final int NUMBER = 1;
    static final int TAG = 2;
    static final int SIZE = 3;

    VoiceMailConstants(Context context) {
        mContext = context;
        loadVoiceMail();

        // The legacy XML file is more prioritized than the XML resource.
        if (CarrierVmMap.size() == 0) {
            loadVoiceMailFromResource();
        }
    }

    boolean containsCarrier(String carrier) {
        reloadIfNecessary();
        return CarrierVmMap.containsKey(carrier);
    }

    String getCarrierName(String carrier) {
        reloadIfNecessary();
        String[] data = CarrierVmMap.get(carrier);
        return data[NAME];
    }

    String getVoiceMailNumber(String carrier) {
        reloadIfNecessary();
        String[] data = CarrierVmMap.get(carrier);
        return data[NUMBER];
    }

    String getVoiceMailTag(String carrier) {
        reloadIfNecessary();
        String[] data = CarrierVmMap.get(carrier);
        return data[TAG];
    }

    private void reloadIfNecessary() {
        if (mLocale != null) {
            loadVoiceMailFromResource();
        }
    }

    private void loadVoiceMail() {
        FileReader vmReader;

        final File vmFile = new File(Environment.getRootDirectory(),
                PARTNER_VOICEMAIL_PATH);
        CarrierVmMap = new HashMap<String, String[]>();

        try {
            vmReader = new FileReader(vmFile);
        } catch (FileNotFoundException e) {
            Rlog.w(LOG_TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PARTNER_VOICEMAIL_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(vmReader);
            loadXmlSettings(parser);
        } catch (XmlPullParserException e) {
            Rlog.w(LOG_TAG, "Exception in Voicemail parser " + e);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "Exception in Voicemail parser " + e);
        } finally {
            try {
                vmReader.close();
            } catch (IOException e) {}
        }
    }

    private void loadXmlSettings(XmlPullParser parser) throws XmlPullParserException, IOException {
        XmlUtils.beginDocument(parser, "voicemail");

        while (true) {
            XmlUtils.nextElement(parser);

            String name = parser.getName();
            if (!"voicemail".equals(name)) {
                break;
            }

            String[] data = new String[SIZE];
            String numeric = parser.getAttributeValue(null, "numeric");
            data[NAME]     = parser.getAttributeValue(null, "carrier");
            data[NUMBER]   = parser.getAttributeValue(null, "vmnumber");
            data[TAG]      = parser.getAttributeValue(null, "vmtag");

            if (Build.IS_DEBUGGABLE) {
                Rlog.d(LOG_TAG, "[Voicemail] numeric = " + numeric + ", name = " + data[NAME]
                        + ", number = " + data[NUMBER] + ", tag = " + data[TAG]);
            }

            CarrierVmMap.put(numeric, data);
        }
    }

    private void loadVoiceMailFromResource() {
        Locale currentLocale = mContext.getResources().getConfiguration().locale;
        if (mLocale == currentLocale) {
            return;
        }

        mLocale = currentLocale;
        if (Build.IS_DEBUGGABLE) {
            Rlog.d(LOG_TAG, "Attempt to parse the xml resource : locale = " + mLocale);
        }

        CarrierVmMap = new HashMap<String, String[]>();
        XmlResourceParser parser = mContext.getResources().getXml(
                com.android.internal.R.xml.voicemail_conf);

        try {
            loadXmlSettings(parser);
        } catch (XmlPullParserException e) {
            Rlog.w(LOG_TAG, "Exception in Voicemail parser " + e);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "Exception in Voicemail parser " + e);
        } finally {
            parser.close();
        }
    }
}
