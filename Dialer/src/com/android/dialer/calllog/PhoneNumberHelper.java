/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.calllog;

import java.util.HashMap;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.PhoneFactory;

import android.content.res.Resources;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.dialer.R;

/**
 * Helper for formatting and managing phone numbers.
 */
public class PhoneNumberHelper {
    private final Resources mResources;
    private HashMap<Integer, String> mVoiceMailCache;
    public PhoneNumberHelper(Resources resources) {
        mResources = resources;
        resetVoiceMailCache();
    }
    /* SPRD: add for Universe UI @ { */
    public synchronized void resetVoiceMailCache() {
        HashMap<Integer, String> cache = new HashMap<Integer, String>();
        for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
            String vmNumber = TelephonyManager.getDefault(i).getVoiceMailNumber();
            String number = PhoneNumberUtils.extractNetworkPortionAlt(vmNumber);
            cache.put(i, number);
        }
        mVoiceMailCache = cache;
    }
    /* @} */

    /* package */ CharSequence getDisplayName(CharSequence number, int presentation) {
        if (presentation == Calls.PRESENTATION_UNKNOWN) {
            return mResources.getString(R.string.unknown);
        }
        if (presentation == Calls.PRESENTATION_RESTRICTED) {
            return mResources.getString(R.string.private_num);
        }
        if (presentation == Calls.PRESENTATION_PAYPHONE) {
            return mResources.getString(R.string.payphone);
        }
        if (new PhoneNumberUtilsWrapper().isVoicemailNumber(number)) {
            return mResources.getString(R.string.voicemail);
        }
        if (PhoneNumberUtilsWrapper.isLegacyUnknownNumbers(number)) {
            return mResources.getString(R.string.unknown);
        }
        return "";
    }

    /**
     * Returns the string to display for the given phone number.
     *
     * @param number the number to display
     * @param formattedNumber the formatted number if available, may be null
     */
    public CharSequence getDisplayNumber(CharSequence number,
            int presentation, CharSequence formattedNumber) {

        final CharSequence displayName = getDisplayName(number, presentation);

        if (!TextUtils.isEmpty(displayName)) {
            return displayName;
        }

        if (TextUtils.isEmpty(number)) {
            return "";
        }

        if (TextUtils.isEmpty(formattedNumber)) {
            return number;
        } else {
            return formattedNumber;
        }
    }

    /** Returns true if it is possible to place a call to the given number. */
    public boolean canPlaceCallsTo(CharSequence number) {
        return !(TextUtils.isEmpty(number));
    }

    /** Returns true if it is possible to send an SMS to the given number. */
    public boolean canSendSmsTo(CharSequence number) {
        return canPlaceCallsTo(number) && !isSipNumber(number);
    }
    /** Returns a URI that can be used to place a call to this number. */
    public Uri getCallUri(String number) {
        if (isVoicemailNumber(number,0)) {
            return Uri.parse("voicemail:x");
        }
        if (isSipNumber(number)) {
            return Uri.fromParts("sip", number, null);
        }
        return Uri.fromParts("tel", number, null);
    }

    public Uri getCallUri(String number,int phoneId) {
        if (isVoicemailNumber(number,phoneId)) {
            return Uri.parse("voicemail:x");
        }
        if (isSipNumber(number)) {
            return Uri.fromParts("sip", number, null);
        }
        return Uri.fromParts("tel", number, null);
    }
    /**
     * Returns true if the given number is the number of the configured
     * voicemail.
     */
    public boolean isVoicemailNumber(CharSequence number, int phoneId) {
        if (TextUtils.isEmpty(number)) {
            return false;
        }
        String vmNumber = mVoiceMailCache.get(phoneId);
        String tmp = PhoneNumberUtils.extractNetworkPortionAlt(number.toString());
        boolean result = PhoneNumberUtils.compare(tmp, vmNumber);
        return result;
    }
    /*
     * SPRD: modify
     * @orig public boolean isVoicemailNumber(CharSequence number) {
        if (TextUtils.isEmpty(number)) {
            return false;
        }
        String vmNumber = mVoiceMailCache.get(0);
        String tmp = PhoneNumberUtils.extractNetworkPortionAlt(number.toString());
        boolean result = PhoneNumberUtils.compare(tmp, vmNumber);
        return result;
    }
     */
    public boolean isVoicemailNumber(CharSequence number) {
        if (TextUtils.isEmpty(number)) {
            return false;
        }
        String vmNumber;
        String mVoiceMailNumber;
        String tmp;
        boolean result = false;
        for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
            vmNumber = TelephonyManager.getDefault(i).getVoiceMailNumber();
            mVoiceMailNumber = PhoneNumberUtils.extractNetworkPortionAlt(vmNumber);
            tmp = PhoneNumberUtils.extractNetworkPortionAlt(number.toString());
            result = PhoneNumberUtils.compare(tmp, mVoiceMailNumber);
            if(result){
                return result;
            }
        }
        return result;
    }
    /* @} */
    /**
     * Returns true if the given number is a SIP address.
     * To be able to mock-out this, it is not a static method.
     */
    public boolean isSipNumber(CharSequence number) {
        return PhoneNumberUtils.isUriNumber(number.toString());
    }
}
