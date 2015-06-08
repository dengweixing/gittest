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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.provider.CallLog.Calls;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import com.android.dialer.PhoneCallDetails;
import com.android.dialer.PhoneCallDetailsHelper;
import com.android.dialer.R;
import com.sprd.dialer.SprdUtils;
import android.sim.Sim;
import android.sim.SimManager;
/**
 * Helper class to fill in the views of a call log entry.
 */
/* package */class CallLogListItemHelper {
    /** Helper for populating the details of a phone call. */
    private final PhoneCallDetailsHelper mPhoneCallDetailsHelper;
    /** Helper for handling phone numbers. */
    private final PhoneNumberHelper mPhoneNumberHelper;
    /** Resources to look up strings. */
    private final Resources mResources;
    /* SPRD: add for Universe UI @ { */
    private Context mContext;
    private int[] icListSimRes = {R.drawable.ic_list_sim1, R.drawable.ic_list_sim2, 
            R.drawable.ic_list_sim3, R.drawable.ic_list_sim4, R.drawable.ic_list_sim5};
    /* @} */
    /**
     * Creates a new helper instance.
     *
     * @param phoneCallDetailsHelper used to set the details of a phone call
     * @param phoneNumberHelper used to process phone number
     */
    public CallLogListItemHelper(Context context, PhoneCallDetailsHelper phoneCallDetailsHelper,
            PhoneNumberHelper phoneNumberHelper, Resources resources) {
        mPhoneCallDetailsHelper = phoneCallDetailsHelper;
        mPhoneNumberHelper = phoneNumberHelper;
        mResources = resources;
        mContext = context;
    }

    /**
     * Sets the name, label, and number for a contact.
     *
     * @param views the views to populate
     * @param details the details of a phone call needed to fill in the data
     * @param isHighlighted whether to use the highlight text for the call
     */
    public void setPhoneCallDetails(CallLogListItemViews views, PhoneCallDetails details,
            boolean isHighlighted, boolean useCallAsPrimaryAction) {
        SpannableStringBuilder simName = null;
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
           simName = getSimString(details.iccId);
        }
        mPhoneCallDetailsHelper.setPhoneCallDetails(views.phoneCallDetailsViews, details,
                isHighlighted);
        boolean canCall = PhoneNumberUtilsWrapper.canPlaceCallsTo(details.number,
                details.numberPresentation);
        boolean canPlay = details.callTypes[0] == Calls.VOICEMAIL_TYPE;

        if (canPlay) {
            // Playback action takes preference.
            configurePlaySecondaryAction(views, isHighlighted);
        } else if (canCall && !useCallAsPrimaryAction) {
            // Call is the secondary action.
            configureCallSecondaryAction(views, details);
        } else {
            // No action available.
            views.secondaryActionView.setVisibility(View.GONE);
        }
        /* SPRD: add for Universe UI @ { */
        if(!SprdUtils.UNIVERSE_UI_SUPPORT){ 
            if (details != null && (0 <= details.phoneId) 
                    && (details.phoneId < icListSimRes.length)) {
                views.simCardView.setImageResource(icListSimRes[details.phoneId]); 
            }

            if(TelephonyManager.getPhoneCount() > 1){
                views.simCardView.setVisibility(View.VISIBLE);
            }else{
                views.simCardView.setVisibility(View.GONE);
            }
        } else {
            if(views.phoneCallDetailsViews.simNameView != null && simName != null){
                views.phoneCallDetailsViews.simNameView.setVisibility(View.VISIBLE);
                views.phoneCallDetailsViews.simNameView.setText(simName);
            }
        }
        /* @} */
    }

    /** Sets the secondary action to correspond to the call button. */
    private void configureCallSecondaryAction(CallLogListItemViews views,
            PhoneCallDetails details) {
        views.secondaryActionView.setVisibility(View.VISIBLE);
        /* SPRD: add for Universe UI @ { */
        if(details.videoCallFlag == 0){
            if(SprdUtils.UNIVERSE_UI_SUPPORT)
                views.secondaryActionView.setImageResource(R.drawable.calllog_call_icon_sprd);
            else
                views.secondaryActionView.setImageResource(R.drawable.ic_phone_dk);
        }else{
            if(SprdUtils.UNIVERSE_UI_SUPPORT)
                views.secondaryActionView.setImageResource(R.drawable.calllog_video_icon_press_sprd);
            else
                views.secondaryActionView.setImageResource(R.drawable.video_call_sprd);
        }
        /* @} */
        views.secondaryActionView.setContentDescription(getCallActionDescription(details));
    }

    /** Returns the description used by the call action for this phone call. */
    private CharSequence getCallActionDescription(PhoneCallDetails details) {
        final CharSequence recipient;
        if (!TextUtils.isEmpty(details.name)) {
            recipient = details.name;
        } else {
            recipient = mPhoneNumberHelper.getDisplayNumber(
                    details.number, details.numberPresentation, details.formattedNumber);
        }
        return mResources.getString(R.string.description_call, recipient);
    }

    /** Sets the secondary action to correspond to the play button. */
    private void configurePlaySecondaryAction(CallLogListItemViews views, boolean isHighlighted) {
        if(!SprdUtils.UNIVERSE_UI_SUPPORT){
            views.secondaryActionView.setVisibility(View.VISIBLE);
            views.secondaryActionView.setImageResource(
                    isHighlighted ? R.drawable.ic_play_active_holo_dark : R.drawable.ic_play_holo_dark);
            views.secondaryActionView.setContentDescription(
                    mResources.getString(R.string.description_call_log_play_button));
        }
    }

    /* SPRD: Add for Universe UI @{ */
    private HashMap<String,SpannableStringBuilder> mSimStringMap = new HashMap<String,SpannableStringBuilder>();
    private boolean mSimMapInvalidate;

    public void setSimMapInvalidate(){
        mSimMapInvalidate = true;
    }

    public SpannableStringBuilder getSimString(String iccId){
        if(TextUtils.isEmpty(iccId)){
            return null;
        }

        if(mSimMapInvalidate){
            Set<String> key = mSimStringMap.keySet();
            for (Iterator it = key.iterator(); it.hasNext();) {
                updateSimMap((String) it.next());
            }
            mSimMapInvalidate = false;
        } else if (!mSimStringMap.containsKey(iccId)){
            updateSimMap(iccId);
        }
        return mSimStringMap.get(iccId);
    }

    private void updateSimMap(String iccId){
        SimManager simManager = SimManager.get(mContext);
        Sim sim = simManager.getSimByIccId(iccId);
        if(sim != null){
            String simName = sim.getName();
            if(simName != null && !simName.isEmpty()){

                String str = sim.getName();
                int index = str.indexOf(simName);
                SpannableStringBuilder style=new SpannableStringBuilder(str);

                boolean isActiveSim  = false;
                Sim[] sims = simManager.getSims();
                if(sims != null){
                    for(int i=0;i<sims.length;i++){
                        if(sims != null && iccId!=null && iccId.equals(sims[i].getIccId()))
                        {
                            isActiveSim = true;
                        }
                    }
                    if(isActiveSim){
                        style.setSpan(new ForegroundColorSpan(sim.getColor()),index,index+simName.length(),Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                    }else{
                        style.setSpan(new ForegroundColorSpan(Color.GRAY),index,index+simName.length(),Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                    }
                    mSimStringMap.put(iccId, style);
                }
            }
        }
    }
    /* @} */
}
