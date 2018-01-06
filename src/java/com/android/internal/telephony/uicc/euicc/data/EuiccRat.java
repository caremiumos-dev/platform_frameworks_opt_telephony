/*
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
 * limitations under the License
 */

package com.android.euicc.data;

import android.support.annotation.IntDef;
import android.support.annotation.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

/** This represents the RAT (Rules Authorisation Table) stored on eUICC. */
public final class EuiccRat {
    /** Profile policy rule flags */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {PolicyRuleFlag.CONSENT_REQUIRED},
            flag = true
    )
    public @interface PolicyRuleFlag {
        /** User consent is required to install the profile. */
        int CONSENT_REQUIRED = 1;
    }

    private final int[] mPolicyRules;
    private final OperatorId[][] mOperatorIds;
    private final int[] mPolicyRuleFlags;

    /** This is used to build new {@link EuiccRat} instance. */
    public static final class Builder {
        private int[] mPolicyRules;
        private OperatorId[][] mOperatorIds;
        private int[] mPolicyRuleFlags;
        private int mPosition;

        /**
         * Creates a new builder.
         *
         * @param ruleNum The number of authorisation rules in the table.
         */
        public Builder(int ruleNum) {
            mPolicyRules = new int[ruleNum];
            mOperatorIds = new OperatorId[ruleNum][];
            mPolicyRuleFlags = new int[ruleNum];
        }

        /**
         * Builds the RAT instance. This builder should not be used anymore after this method is
         * called, otherwise {@link NullPointerException} will be thrown.
         */
        public EuiccRat build() {
            if (mPosition != mPolicyRules.length) {
                throw new IllegalStateException(
                        "Not enough rules are added, expected: "
                                + mPolicyRules.length
                                + ", added: "
                                + mPosition);
            }
            return new EuiccRat(mPolicyRules, mOperatorIds, mPolicyRuleFlags);
        }

        /**
         * Adds an authorisation rule.
         *
         * @throws ArrayIndexOutOfBoundsException If the {@code mPosition} is larger than the size
         *     this table.
         */
        public Builder add(int policyRules, OperatorId[] opId, int policyRuleFlags) {
            if (mPosition >= mPolicyRules.length) {
                throw new ArrayIndexOutOfBoundsException(mPosition);
            }
            mPolicyRules[mPosition] = policyRules;
            mOperatorIds[mPosition] = opId;
            mPolicyRuleFlags[mPosition] = policyRuleFlags;
            mPosition++;
            return this;
        }
    }

    /**
     * @param mccRule A 2-character or 3-character string which can be either MCC or MNC. The
     *     character 'E' is used as a wild char to match any digit.
     * @param mcc A 2-character or 3-character string which can be either MCC or MNC.
     * @return Whether the {@code mccRule} matches {@code mcc}.
     */
    @VisibleForTesting
    static boolean match(String mccRule, String mcc) {
        if (mccRule.length() < mcc.length()) {
            return false;
        }
        for (int i = 0; i < mccRule.length(); i++) {
            // 'E' is the wild char to match any digit.
            if (mccRule.charAt(i) == 'E'
                    || (i < mcc.length() && mccRule.charAt(i) == mcc.charAt(i))) {
                continue;
            }
            return false;
        }
        return true;
    }

    private EuiccRat(int[] policyRules, OperatorId[][] operatorIds, int[] policyRuleFlags) {
        mPolicyRules = policyRules;
        mOperatorIds = operatorIds;
        mPolicyRuleFlags = policyRuleFlags;
    }

    /**
     * Finds the index of the first authorisation rule matching the given policy and operator id. If
     * the returned index is not negative, the operator is allowed to apply this policy to its
     * profile.
     *
     * @param policy The policy rule.
     * @param opId The operator id.
     * @return The index of authorization rule. If no rule is found, -1 will be returned.
     */
    public int findIndex(@EuiccProfile.PolicyRule int policy, OperatorId opId) {
        for (int i = 0; i < mPolicyRules.length; i++) {
            if ((mPolicyRules[i] & policy) == 0) {
                continue;
            }
            OperatorId[] operatorIds = mOperatorIds[i];
            if (operatorIds == null || operatorIds.length == 0) {
                continue;
            }
            for (int j = 0; j < operatorIds.length; j++) {
                OperatorId ruleOpId = operatorIds[j];
                if (!match(ruleOpId.getMcc(), opId.getMcc())
                        || !match(ruleOpId.getMnc(), opId.getMnc())) {
                    continue;
                }
                byte[] gid = ruleOpId.getGid1();
                if (gid != null && gid.length != 0 && !Arrays.equals(gid, opId.getGid1())) {
                    continue;
                }
                gid = ruleOpId.getGid2();
                if (gid != null && gid.length != 0 && !Arrays.equals(gid, opId.getGid2())) {
                    continue;
                }
                return i;
            }
        }
        return -1;
    }

    /**
     * Tests if the entry in the table has the given policy rule flag.
     *
     * @param index The index of the entry.
     * @param flag The policy rule flag to be tested.
     * @throws ArrayIndexOutOfBoundsException If the {@code index} is negative or larger than the
     *     size of this table.
     */
    public boolean hasPolicyRuleFlag(int index, @PolicyRuleFlag int flag) {
        if (index < 0 || index >= mPolicyRules.length) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return (mPolicyRuleFlags[index] & flag) != 0;
    }
}
