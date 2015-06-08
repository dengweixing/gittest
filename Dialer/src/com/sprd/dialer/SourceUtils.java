/*
 * SPRD: create
 */

package com.sprd.dialer;

import java.util.HashMap;

import com.android.dialer.R;
import android.provider.CallLog.Calls;

public class SourceUtils {
    private static HashMap<Integer, Integer> sSimDrawable;
    static {
        sSimDrawable = new HashMap<Integer, Integer>();
        sSimDrawable.put(0, R.drawable.ic_list_sim1);
        sSimDrawable.put(1, R.drawable.ic_list_sim2);
    }

    public static int getSimDrawableFromPhoneId(int phoneId) {
        Integer drawable = sSimDrawable.get(phoneId);
        if (drawable != null) {
            return drawable.intValue();
        }
        return sSimDrawable.get(0).intValue();
    }

    public static int getDrawableFromCallType(int callType) {
        switch (callType) {
            case Calls.INCOMING_TYPE:
                return R.drawable.ic_call_incoming_holo_dark;
            case Calls.OUTGOING_TYPE:
                return R.drawable.ic_call_outgoing_holo_dark;
            case Calls.MISSED_TYPE:
                return R.drawable.ic_call_missed_holo_dark;
            case Calls.VOICEMAIL_TYPE:
                return R.drawable.ic_call_voicemail_holo_dark;
            default:
                throw new IllegalArgumentException("Unknow call type = " + callType);
        }
    }
}
