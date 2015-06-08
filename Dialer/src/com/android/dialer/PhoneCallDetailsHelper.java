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

package com.android.dialer;

import java.text.SimpleDateFormat;

import android.content.res.Resources;
import android.graphics.Typeface;
import android.provider.ContactsContract;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import com.android.contacts.common.test.NeededForTesting;
import com.android.dialer.calllog.CallTypeHelper;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.calllog.PhoneNumberHelper;
import com.android.dialer.calllog.PhoneNumberUtilsWrapper;
import com.sprd.dialer.FormatUtils;
import com.sprd.dialer.SprdUtils;
import com.sprd.phone.common.utils.OperatorUtils;
/**
 * Helper class to fill in the views in {@link PhoneCallDetailsViews}.
 */
public class PhoneCallDetailsHelper {
    /** The maximum number of icons will be shown to represent the call types in a group. */
    private static final int MAX_CALL_TYPE_ICONS = 3;
    private static final int MAX_CALL_TYPE_ICONS_NEWUI = 1;// SPRD: add for Universe UI 

    private final Resources mResources;
    /** The injected current time in milliseconds since the epoch. Used only by tests. */
    private Long mCurrentTimeMillisForTest;
    // Helper classes.
    private final CallTypeHelper mCallTypeHelper;
    private final PhoneNumberHelper mPhoneNumberHelper;
    private final PhoneNumberUtilsWrapper mPhoneNumberUtilsWrapper;

    /**
     * Creates a new instance of the helper.
     * <p>
     * Generally you should have a single instance of this helper in any context.
     *
     * @param resources used to look up strings
     */
    public PhoneCallDetailsHelper(Resources resources, CallTypeHelper callTypeHelper,
            PhoneNumberUtilsWrapper phoneUtils) {
        mResources = resources;
        mCallTypeHelper = callTypeHelper;
        mPhoneNumberHelper = new PhoneNumberHelper(resources);
        mPhoneNumberUtilsWrapper = phoneUtils;
    }

    /** Fills the call details views with content. */
    public void setPhoneCallDetails(PhoneCallDetailsViews views, PhoneCallDetails details,
            boolean isHighlighted) {
        // Display up to a given number of icons.
        views.callTypeIcons.clear();
        int count = details.callTypes.length;
        // SPRD: add for Universe UI
        int maxIcon = MAX_CALL_TYPE_ICONS;
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            maxIcon = MAX_CALL_TYPE_ICONS_NEWUI;
        }
        for (int index = 0; index < count && index < maxIcon; ++index) {
            views.callTypeIcons.add(details.callTypes[index]);
        }
        views.callTypeIcons.requestLayout();
        views.callTypeIcons.setVisibility(View.VISIBLE);

        // Show the total call count only if there are more than the maximum number of icons.
        final Integer callCount;

        if (count > maxIcon) {
            callCount = count;
        } else {
            callCount = null;
        }
        // The color to highlight the count and date in, if any. This is based on the first call.
        Integer highlightColor =
                isHighlighted ? mCallTypeHelper.getHighlightedColor(details.callTypes[0]) : null;

        // The date of this call, relative to the current time.
                /*
                 * SPRD: modify
                 * @orig
        CharSequence dateText =
            DateUtils.getRelativeTimeSpanString(details.date,
                    getCurrentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE);*/

        CharSequence  dateText;
        if(details.date <= getCurrentTimeMillis()){
            dateText =
                    DateUtils.getRelativeTimeSpanString(details.date,
                            getCurrentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_NUMERIC_DATE);
        }else{
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            dateText = dateFormat.format(details.date);
        }
        /* @} */
        // Set the call count and date.
        setCallCountAndDate(views, callCount, dateText, highlightColor);

        CharSequence numberFormattedLabel = null;
        // Only show a label if the number is shown and it is not a SIP address.
        if (!TextUtils.isEmpty(details.number)
                && !PhoneNumberUtils.isUriNumber(details.number.toString())) {
            if (details.numberLabel == ContactInfo.GEOCODE_AS_LABEL) {
                numberFormattedLabel = details.geocode;
            } else {
                numberFormattedLabel = Phone.getTypeLabel(mResources, details.numberType,
                        details.numberLabel);
            }
        }

        CharSequence nameText;
        CharSequence numberText;
        CharSequence labelText;
        CharSequence displayNumber =
            mPhoneNumberHelper.getDisplayNumber(details.number,
                    details.numberPresentation, details.formattedNumber);
        if (TextUtils.isEmpty(details.name)) {
            nameText = displayNumber;
            // SPRD: add for Universe UI 
            if (SprdUtils.UNIVERSE_UI_SUPPORT) {
                numberText = mResources.getString(R.string.call_log_contacts_not_save);
            } else {
                if (TextUtils.isEmpty(details.geocode)
                        || mPhoneNumberHelper.isVoicemailNumber(details.number)) {
                    numberText = "";
                } else {
                    /* SPRD: add for bug298002
                     * numberText = details.geocode;
                     * @{ */
                    if (OperatorUtils.IS_CMCC || OperatorUtils.IS_CUCC) {
                        numberText = details.geocode;
                    }else{
                        numberText = "";
                    }
                    /* @} */
                }
            }
            if (TextUtils.isEmpty(details.geocode)
                    || mPhoneNumberUtilsWrapper.isVoicemailNumber(details.number)) {
                numberText = mResources.getString(R.string.call_log_empty_gecode);
            } else {
                /* SPRD: add for bug298002
                 * numberText = details.geocode;
                 * @{ */
                if (OperatorUtils.IS_CMCC || OperatorUtils.IS_CUCC) {
                    numberText = details.geocode;
                }else{
                    numberText = "";
                }
                /* @} */
            }
            labelText = numberText;
            // We have a real phone number as "nameView" so make it always LTR
            views.nameView.setTextDirection(View.TEXT_DIRECTION_LTR);
        } else {
            nameText = details.name;
            numberText = displayNumber;
            labelText = TextUtils.isEmpty(numberFormattedLabel) ? numberText :
                    numberFormattedLabel;
        }
        /* SPRD: add for bug267026 @ { */
        if (SprdUtils.UNIVERSE_UI_SUPPORT){
            final CharSequence formattedText;
            if (highlightColor != null) {
                formattedText = addBoldAndColor(nameText, highlightColor);
            } else {
                formattedText = nameText;
            }
            views.nameView.setText(formattedText);
        }else{
            views.nameView.setText(nameText);
        }
        /* @} */

        views.labelView.setText(labelText);

        // SPRD: add for Universe UI 
        if (SprdUtils.UNIVERSE_UI_SUPPORT){
            views.numberView.setText(numberText);
            views.numberView.setVisibility(View.VISIBLE);
            views.labelView.setVisibility(View.GONE);
        } else {
            views.labelView.setVisibility(TextUtils.isEmpty(labelText) ? View.GONE : View.VISIBLE);
        }
    }

    /** Sets the text of the header view for the details page of a phone call. */
    public void setCallDetailsHeader(TextView nameView, PhoneCallDetails details) {
        CharSequence nameText;
        CharSequence displayNumber =
            mPhoneNumberHelper.getDisplayNumber(details.number, details.numberPresentation,
                        mResources.getString(R.string.recentCalls_addToContact));
        // SPRD: add for Universe UI 
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            displayNumber = mPhoneNumberHelper.getDisplayNumber(details.number, 
                    details.numberPresentation,null);
            nameText = displayNumber;
        } else {
            if (TextUtils.isEmpty(details.name)) {
                nameText = displayNumber;
            } else {
                nameText = details.name;
            }
        }
        nameView.setText(nameText);
    }

    @NeededForTesting
    public void setCurrentTimeForTest(long currentTimeMillis) {
        mCurrentTimeMillisForTest = currentTimeMillis;
    }

    /**
     * Returns the current time in milliseconds since the epoch.
     * <p>
     * It can be injected in tests using {@link #setCurrentTimeForTest(long)}.
     */
    private long getCurrentTimeMillis() {
        if (mCurrentTimeMillisForTest == null) {
            return System.currentTimeMillis();
        } else {
            return mCurrentTimeMillisForTest;
        }
    }

    /** Sets the call count and date. */
    private void setCallCountAndDate(PhoneCallDetailsViews views, Integer callCount,
            CharSequence dateText, Integer highlightColor) {
        // Combine the count (if present) and the date.
        final CharSequence text;
        if (callCount != null) {
            text = mResources.getString(
                    R.string.call_log_item_count_and_date, callCount.intValue(), dateText);
        } else {
            text = dateText;
        }

        // Apply the highlight color if present.
        /* SPRD: add for bug267026 @ { */
        if(!SprdUtils.UNIVERSE_UI_SUPPORT){
            final CharSequence formattedText;
            if (highlightColor != null) {
                formattedText = addBoldAndColor(text, highlightColor);
            } else {
                formattedText = text;
            }
            views.callTypeAndDate.setVisibility(View.VISIBLE);
            views.callTypeAndDate.setText(formattedText);
        }else{
            views.callTypeAndDate.setVisibility(View.VISIBLE);
            views.callTypeAndDate.setText(text);
        }
        /* @} */
    }

    /** Creates a SpannableString for the given text which is bold and in the given color. */
    private CharSequence addBoldAndColor(CharSequence text, int color) {
        int flags = Spanned.SPAN_INCLUSIVE_INCLUSIVE;
        SpannableString result = new SpannableString(text);
        result.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), flags);
        result.setSpan(new ForegroundColorSpan(color), 0, text.length(), flags);
        return result;
    }
    
    /**
     * SPRD: 
     * add for Universe UI 
     * @{
     */
    /** Sets the text of the header view for the details page of a phone call. */
    public void setCallDetailsHeaderNumberType(TextView nameView, PhoneCallDetails details) {
        CharSequence nameText = null;
        if (!TextUtils.isEmpty(details.name)) {
            CharSequence numberFormattedLabel = null;
            // Only show a label if the number is shown and it is not a SIP address.
            if (!TextUtils.isEmpty(details.number)
                    && !PhoneNumberUtils.isUriNumber(details.number.toString())) {
                numberFormattedLabel = Phone.getTypeLabel(mResources, details.numberType,
                        details.numberLabel);
            }
            if (numberFormattedLabel != null) {
                CharSequence numberText = FormatUtils.applyStyleToSpan(Typeface.BOLD,
                        "  (" + numberFormattedLabel + ") ", 0,
                        numberFormattedLabel.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                nameText = numberText;
            }    
        }

        nameView.setText(nameText);
    }
    /* @}
     */ 
}
