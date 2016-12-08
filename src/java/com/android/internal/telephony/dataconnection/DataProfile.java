/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.internal.telephony.dataconnection;

import android.os.Parcel;
import android.telephony.ServiceState;

public class DataProfile {

    static final int TYPE_COMMON = 0;
    static final int TYPE_3GPP = 1;
    static final int TYPE_3GPP2 = 2;

    //id of the data profile
    public final int profileId;
    //the APN to connect to
    public final String apn;
    //one of the PDP_type values in TS 27.007 section 10.1.1.
    //For example, "IP", "IPV6", "IPV4V6", or "PPP".
    public final String protocol;
    //authentication protocol used for this PDP context
    //(None: 0, PAP: 1, CHAP: 2, PAP&CHAP: 3)
    public final int authType;
    //the username for APN, or NULL
    public final String user;
    //the password for APN, or NULL
    public final String password;
    //the profile type, TYPE_COMMON, TYPE_3GPP, TYPE_3GPP2
    public final int type;
    //the period in seconds to limit the maximum connections
    public final int maxConnsTime;
    //the maximum connections during maxConnsTime
    public final int maxConns;
    //the required wait time in seconds after a successful UE initiated
    //disconnect of a given PDN connection before the device can send
    //a new PDN connection request for that given PDN
    public final int waitTime;
    //true to enable the profile, false to disable
    public final boolean enabled;
    //supported APN types bitmask. See RIL_ApnTypes for the value of each bit.
    public final int supportedTypesBitmask;
    //one of the PDP_type values in TS 27.007 section 10.1.1 used on roaming network.
    //For example, "IP", "IPV6", "IPV4V6", or "PPP".
    public final String roamingProtocol;
    //The bearer bitmask. See RIL_RadioAccessFamily for the value of each bit.
    public final int bearerBitmask;
    //maximum transmission unit (MTU) size in bytes
    public final int mtu;
    //the MVNO type: possible values are "imsi", "gid", "spn"
    public final String mvnoType;
    //MVNO match data. For example, SPN: A MOBILE, BEN NL, ...
    //IMSI: 302720x94, 2060188, ...
    //GID: 4E, 33, ...
    public final String mvnoMatchData;

    DataProfile(int profileId, String apn, String protocol, int authType,
            String user, String password, int type, int maxConnsTime, int maxConns,
            int waitTime, boolean enabled, int supportedTypesBitmask, String roamingProtocol, 
            int bearerBitmask, int mtu, String mvnoType, String mvnoMatchData) {

        this.profileId = profileId;
        this.apn = apn;
        this.protocol = protocol;
        this.authType = authType;
        this.user = user;
        this.password = password;
        this.type = type;
        this.maxConnsTime = maxConnsTime;
        this.maxConns = maxConns;
        this.waitTime = waitTime;
        this.enabled = enabled;

        // The followings are added since v15.
        this.supportedTypesBitmask = supportedTypesBitmask;
        this.roamingProtocol = roamingProtocol;
        this.bearerBitmask = bearerBitmask;
        this.mtu = mtu;
        this.mvnoType = mvnoType;
        this.mvnoMatchData = mvnoMatchData;
    }

    public DataProfile(ApnSetting apn) {
        this(apn.profileId, apn.apn, apn.protocol,
                apn.authType, apn.user, apn.password, apn.bearerBitmask == 0
                        ? TYPE_COMMON : (ServiceState.bearerBitmapHasCdma(apn.bearerBitmask)
                        ? TYPE_3GPP2 : TYPE_3GPP),
                apn.maxConnsTime, apn.maxConns, apn.waitTime, apn.carrierEnabled, apn.typesBitmask,
                apn.roamingProtocol, apn.bearerBitmask, apn.mtu, apn.mvnoType, apn.mvnoMatchData);
    }

    public static Parcel toParcel(Parcel pc, DataProfile[] dps) {

        if(pc == null) {
            return null;
        }

        pc.writeInt(dps.length);
        for(int i = 0; i < dps.length; i++) {
            pc.writeInt(dps[i].profileId);
            pc.writeString(dps[i].apn);
            pc.writeString(dps[i].protocol);
            pc.writeInt(dps[i].authType);
            pc.writeString(dps[i].user);
            pc.writeString(dps[i].password);
            pc.writeInt(dps[i].type);
            pc.writeInt(dps[i].maxConnsTime);
            pc.writeInt(dps[i].maxConns);
            pc.writeInt(dps[i].waitTime);
            pc.writeInt(dps[i].enabled ? 1 : 0);
            pc.writeInt(dps[i].supportedTypesBitmask);
            pc.writeString(dps[i].roamingProtocol);
            pc.writeInt(dps[i].bearerBitmask);
            pc.writeInt(dps[i].mtu);
            pc.writeString(dps[i].mvnoType);
            pc.writeString(dps[i].mvnoMatchData);
        }
        return pc;
    }

    @Override
    public String toString() {
        return "DataProfile " + profileId + "/" + apn + "/" + protocol + "/" + authType
                + "/" + user + "/" + password + "/" + type + "/" + maxConnsTime
                + "/" + maxConns + "/" + waitTime + "/" + enabled + "/" + supportedTypesBitmask
                + "/" + roamingProtocol + "/" + bearerBitmask + "/" + mtu + "/" + mvnoType + "/" 
                + mvnoMatchData;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DataProfile == false) return false;
        return (o == this || toString().equals(o.toString()));
    }
}
