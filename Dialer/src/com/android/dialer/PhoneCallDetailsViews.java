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

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.android.dialer.calllog.CallTypeIconsView;
import com.sprd.dialer.SprdUtils;

/**
 * Encapsulates the views that are used to display the details of a phone call in the call log.
 */
public final class PhoneCallDetailsViews {
    public final TextView nameView;
    public final View callTypeView;
    public final CallTypeIconsView callTypeIcons;
    public final TextView callTypeAndDate;
    public final TextView labelView;
    /* SPRD: add for Universe UI @ { */
    public final TextView numberView;
    public final TextView simNameView;
    /* @} */

    private PhoneCallDetailsViews(TextView nameView, View callTypeView,
            CallTypeIconsView callTypeIcons, TextView callTypeAndDate,TextView numberView, TextView labelView) {
        this.nameView = nameView;
        this.callTypeView = callTypeView;
        this.callTypeIcons = callTypeIcons;
        this.callTypeAndDate = callTypeAndDate;
        this.labelView = labelView;
        /* SPRD: add for Universe UI @ { */
        this.numberView = numberView;
        this.simNameView = null;
        /* @} */
    }

    /**
     * SPRD: 
     * add for Universe UI 
     * @{
     */
    private PhoneCallDetailsViews(TextView nameView, View callTypeView,
            CallTypeIconsView callTypeIcons, TextView callTypeAndDate, TextView numberView,
            TextView labelView, TextView simNameView) {
        this.nameView = nameView;
        this.callTypeView = callTypeView;
        this.callTypeIcons = callTypeIcons;
        this.callTypeAndDate = callTypeAndDate;
        this.numberView = numberView;
        this.labelView = labelView;
        this.simNameView = simNameView;
    }

    private PhoneCallDetailsViews(TextView nameView, View callTypeView,
            CallTypeIconsView callTypeIcons, TextView callTypeAndDate, TextView numberView,
            TextView labelView, TextView simNameView, TextView callCountView) {
        this.nameView = nameView;
        this.callTypeView = callTypeView;
        this.callTypeIcons = callTypeIcons;
        this.callTypeAndDate = callTypeAndDate;
        this.numberView = numberView;
        this.labelView = labelView;
        this.simNameView = simNameView;
    }
    /* @} */

    /**
     * Create a new instance by extracting the elements from the given view.
     * <p>
     * The view should contain three text views with identifiers {@code R.id.name},
     * {@code R.id.date}, and {@code R.id.number}, and a linear layout with identifier
     * {@code R.id.call_types}.
     */
    public static PhoneCallDetailsViews fromView(View view) {
        // SPRD: add for Universe UI 
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            return new PhoneCallDetailsViews((TextView) view.findViewById(R.id.name),
                    view.findViewById(R.id.call_type),
                    (CallTypeIconsView) view.findViewById(R.id.call_type_icons),
                    (TextView) view.findViewById(R.id.call_count_and_date),
                    (TextView) view.findViewById(R.id.number),
                    (TextView) view.findViewById(R.id.label),
                    (TextView) view.findViewById(R.id.sim_name));
        }else{
            return new PhoneCallDetailsViews((TextView) view.findViewById(R.id.name),
                    view.findViewById(R.id.call_type),
                    (CallTypeIconsView) view.findViewById(R.id.call_type_icons),
                    (TextView) view.findViewById(R.id.call_count_and_date),
                    (TextView) view.findViewById(R.id.number),
                    (TextView) view.findViewById(R.id.label));
        }
    }

    public static PhoneCallDetailsViews createForTest(Context context) {
        return new PhoneCallDetailsViews(
                new TextView(context),
                new View(context),
                new CallTypeIconsView(context),
                new TextView(context),// SPRD: add for Universe UI 
                new TextView(context),
                new TextView(context));
    }
}
