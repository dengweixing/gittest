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

import android.content.Context;
import android.provider.CallLog.Calls;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.android.dialer.PhoneCallDetails;
import com.android.dialer.R;
import com.sprd.dialer.SprdUtils;
import com.sprd.phone.common.utils.OperatorUtils;

import android.sim.Sim;
import android.sim.SimManager;
import android.widget.ImageView;
import android.graphics.Color;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

/**
 * Adapter for a ListView containing history items from the details of a call.
 */
public class CallDetailHistoryAdapter extends BaseAdapter {
    /** The top element is a blank header, which is hidden under the rest of the UI. */
    private static final int VIEW_TYPE_HEADER = 0;
    /** Each history item shows the detail of a call. */
    private static final int VIEW_TYPE_HISTORY_ITEM = 1;

    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final CallTypeHelper mCallTypeHelper;
    private final PhoneCallDetails[] mPhoneCallDetails;
    /** Whether the voicemail controls are shown. */
    private final boolean mShowVoicemail;
    /** Whether the call and SMS controls are shown. */
    private final boolean mShowCallAndSms;
    //add for Universe UI
    private static String mDateValue;

    /** The controls that are shown on top of the history list. */
    private final View mControls;
    /** The listener to changes of focus of the header. */
    private View.OnFocusChangeListener mHeaderFocusChangeListener =
            new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            // When the header is focused, focus the controls above it instead.
            if (hasFocus) {
                mControls.requestFocus();
            }
        }
    };

    public CallDetailHistoryAdapter(Context context, LayoutInflater layoutInflater,
            CallTypeHelper callTypeHelper, PhoneCallDetails[] phoneCallDetails,
            boolean showVoicemail, boolean showCallAndSms, View controls) {
        mContext = context;
        mLayoutInflater = layoutInflater;
        mCallTypeHelper = callTypeHelper;
        // SPRD: add for Universe UI 
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            mPhoneCallDetails = getDisplayPhoneCallDetails(phoneCallDetails);
        }else {
            mPhoneCallDetails = phoneCallDetails;
        }
        mShowVoicemail = showVoicemail;
        mShowCallAndSms = showCallAndSms;
        mControls = controls;
    }

    @Override
    public boolean isEnabled(int position) {
        // None of history will be clickable.
        return false;
    }

    @Override
    public int getCount() {
        return mPhoneCallDetails.length + 1;
    }

    @Override
    public Object getItem(int position) {
        if (position == 0) {
            return null;
        }
        return mPhoneCallDetails[position - 1];
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) {
            return -1;
        }
        return position - 1;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return VIEW_TYPE_HEADER;
        }
        return VIEW_TYPE_HISTORY_ITEM;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position == 0) {
            final View header = convertView == null
                    ? mLayoutInflater.inflate(R.layout.call_detail_history_header, parent, false)
                            : convertView;
                    // Voicemail controls are only shown in the main UI if there is a voicemail.
                    View voicemailContainer = header.findViewById(R.id.header_voicemail_container);
                    voicemailContainer.setVisibility(mShowVoicemail ? View.VISIBLE : View.GONE);
                    // Call and SMS controls are only shown in the main UI if there is a known number.
                    View callAndSmsContainer = header.findViewById(R.id.header_call_and_sms_container);
                    callAndSmsContainer.setVisibility(mShowCallAndSms ? View.VISIBLE : View.GONE);
                    header.setFocusable(true);
                    header.setOnFocusChangeListener(mHeaderFocusChangeListener);
                    return header;
        }
        // SPRD: add for Universe UI 
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            if(position == 1){
                final View result = mLayoutInflater.inflate(R.layout.call_detail_histroy_list_tag_sprd, parent, false);
                mDateValue = DateUtils.formatDateRange(mContext,((PhoneCallDetails)getItem(position)).date,
                        ((PhoneCallDetails)getItem(position)).date,
                        DateUtils.FORMAT_SHOW_DATE |DateUtils.FORMAT_SHOW_YEAR);
                TextView tagTextView = (TextView) result.findViewById(R.id.group_list_tag_text);
                tagTextView.setText(mDateValue);
                //tagTextView.setTextColor(Color.argb(50, 0, 255, 0));
                //tagTextView.setBackgroundColor(Color.argb(155, 0, 255, 0));
                return result;
            }
            mDateValue = DateUtils.formatDateRange(mContext,((PhoneCallDetails)getItem(position-1)).date,
                    ((PhoneCallDetails)getItem(position-1)).date,
                    DateUtils.FORMAT_SHOW_DATE |DateUtils.FORMAT_SHOW_YEAR);
            if(mDateValue == null ||
                    ! mDateValue.equals(DateUtils.formatDateRange(mContext,((PhoneCallDetails)getItem(position)).date,
                            ((PhoneCallDetails)getItem(position)).date,
                            DateUtils.FORMAT_SHOW_DATE |DateUtils.FORMAT_SHOW_YEAR)))
            {
                /*final View result  = convertView == null
        	                ? mLayoutInflater.inflate(R.layout.call_detail_histroy_list_tag_new_ui, parent, false)
        	                : convertView;*/

                final View result =  mLayoutInflater.inflate(R.layout.call_detail_histroy_list_tag_sprd, parent, false);
                mDateValue = DateUtils.formatDateRange(mContext,((PhoneCallDetails)getItem(position)).date,
                        ((PhoneCallDetails)getItem(position)).date,
                        DateUtils.FORMAT_SHOW_DATE |DateUtils.FORMAT_SHOW_YEAR);
                TextView tagTextView = (TextView) result.findViewById(R.id.group_list_tag_text);
                tagTextView.setText(mDateValue);
                //tagTextView.setTextColor(Color.argb(50, 0, 255, 0));
                //tagTextView.setBackgroundColor(Color.argb(155, 0, 255, 0));
                return result;
            }else{
                // Make sure we have a valid convertView to start with
                final View result  = mLayoutInflater.inflate(R.layout.call_detail_history_item_sprd, parent, false);
                PhoneCallDetails details = mPhoneCallDetails[position - 1];


                CallTypeIconsView callTypeIconView =
                        (CallTypeIconsView) result.findViewById(R.id.call_type_icon);
                TextView callTypeTextView = (TextView) result.findViewById(R.id.call_type_text);
                TextView callTypeExplainView = (TextView) result.findViewById(R.id.call_type_explain);
                //ImageView videoCallIcon = (ImageView) result.findViewById(R.id.video_call_icon);
                TextView dateView = (TextView) result.findViewById(R.id.date);
                TextView durationView = (TextView) result.findViewById(R.id.duration);
                TextView tagTextView = (TextView) result.findViewById(R.id.group_list_tag_text);
                if(tagTextView != null){
                    tagTextView.setTextColor(Color.argb(50, 0, 255, 0));
                }

                int callType = details.callTypes[0];
                boolean vedioCall = details.videoCallFlag == 1;
                int callIcon = vedioCall ? R.drawable.calllog_video : R.drawable.calllog_voice;
                //videoCallIcon.setImageResource(callIcon);

                callTypeIconView.clear();
                callTypeIconView.add(callType);
                if (TelephonyManager.getPhoneCount() > 1) {
                    String iccId = details.iccId;
                    SimManager simManager = SimManager.get(mContext);
                    Sim sim = simManager.getSimByIccId(iccId);

                    int simColor = 0;
                    String simName = null;
                    if(sim != null){
                        simName = sim.getName();
                        simColor = sim.getColor();
                    }
                    if(simName != null && !simName.isEmpty()){
                        String str = sim.getName();
                        int index = str.indexOf(simName);
                        SpannableStringBuilder style=new SpannableStringBuilder(str);
                        style.setSpan(new ForegroundColorSpan(sim.getColor()),
                                index,index+simName.length(),Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                        callTypeTextView.setText(style);
                    }

                }
                callTypeExplainView.setText(mCallTypeHelper.getCallTypeText(callType));
                // Set the date.
                CharSequence timeValue = DateUtils.formatDateRange(mContext, details.date, details.date,
                        DateUtils.FORMAT_SHOW_TIME);
                dateView.setText(timeValue);

                // Set the duration
                //not display the duration.
                durationView.setVisibility(View.GONE);
                if (OperatorUtils.ENABLR_CALLLOG_DURATION) {
                    if (callType == Calls.MISSED_TYPE || callType == Calls.VOICEMAIL_TYPE) {
                        durationView.setVisibility(View.GONE);
                    } else {
                        durationView.setVisibility(View.VISIBLE);
                        durationView.setText(formatDuration(details.duration));
                    }
                }

                return result;
            }
        }else {
            // Make sure we have a valid convertView to start with
            final View result  = convertView == null
                    ? mLayoutInflater.inflate(R.layout.call_detail_history_item, parent, false)
                            : convertView;

                    PhoneCallDetails details = mPhoneCallDetails[position - 1];
                    CallTypeIconsView callTypeIconView =
                            (CallTypeIconsView) result.findViewById(R.id.call_type_icon);
                    TextView callTypeTextView = (TextView) result.findViewById(R.id.call_type_text);
                    ImageView videoCallIcon = (ImageView) result.findViewById(R.id.video_call_icon);
                    TextView dateView = (TextView) result.findViewById(R.id.date);
                    TextView durationView = (TextView) result.findViewById(R.id.duration);

                    int callType = details.callTypes[0];
                    boolean vedioCall = details.videoCallFlag == 1;
                    int callIcon = vedioCall ? R.drawable.calllog_video : R.drawable.calllog_voice;
                    videoCallIcon.setImageResource(callIcon);

                    callTypeIconView.clear();
                    callTypeIconView.add(callType);

                    if (TelephonyManager.getPhoneCount() > 1) {
                        callTypeTextView.setText("SIM" + (details.phoneId + 1) + "  "
                                + mCallTypeHelper.getCallTypeText(callType));
                    } else {
                        callTypeTextView.setText(mCallTypeHelper.getCallTypeText(callType));
                    }

                    // Set the date.
                    CharSequence dateValue = DateUtils.formatDateRange(mContext, details.date, details.date,
                            DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE |
                            DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_YEAR);
                    dateView.setText(dateValue);

                    // Set the duration
                    if(SprdUtils.SHOW_DURATION_SUPPORT){
                        if (Calls.VOICEMAIL_TYPE == callType || CallTypeHelper.isMissedCallType(callType)) {
                            durationView.setVisibility(View.GONE);
                        } else {
                            durationView.setVisibility(View.VISIBLE);
                            durationView.setText(formatDuration(details.duration));
                        }
                    }

                    return result;
        } 
    }

    private String formatDuration(long elapsedSeconds) {
        long minutes = 0;
        long seconds = 0;

        if (elapsedSeconds >= 60) {
            minutes = elapsedSeconds / 60;
            elapsedSeconds -= minutes * 60;
        }
        seconds = elapsedSeconds;

        return mContext.getString(R.string.callDetailsDurationFormat, minutes, seconds);
    }

    /**
     * SPRD: 
     * add for Universe UI 
     * @{
     */
    private PhoneCallDetails[] getDisplayPhoneCallDetails(PhoneCallDetails[] phoneCallDetails) {
        String oldDay = "";
        String newDay = oldDay;
        int displayNum = phoneCallDetails.length ;
        for(int i=0; i< phoneCallDetails.length; i++){
            newDay = DateUtils.formatDateRange(mContext,phoneCallDetails[i].date,
                    phoneCallDetails[i].date,DateUtils.FORMAT_SHOW_DATE |DateUtils.FORMAT_SHOW_YEAR);
            if(!oldDay.equals(newDay)){
                displayNum++;
                oldDay = newDay;
            }
        }
        PhoneCallDetails[] displayPhoneCallDetails = new PhoneCallDetails[displayNum];
        int displayIndex = 0;
        oldDay = "";
        for(int i= 0; i< phoneCallDetails.length; i++){
            newDay = DateUtils.formatDateRange(mContext,phoneCallDetails[i].date,
                    phoneCallDetails[i].date,DateUtils.FORMAT_SHOW_DATE |DateUtils.FORMAT_SHOW_YEAR);
            if(!oldDay.equals(newDay)){
                displayPhoneCallDetails[displayIndex] = phoneCallDetails[i];
                displayIndex++;
            }
            oldDay = newDay;
            displayPhoneCallDetails[displayIndex] = phoneCallDetails[i];
            displayIndex++;
        }
        return displayPhoneCallDetails;
    }
    /* @} */
}
