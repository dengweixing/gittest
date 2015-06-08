/*
 * SPRD: create
 */

package com.sprd.dialer;

import android.provider.CallLog.Calls;

public interface CallLogClearColumn {

    /** The projection to use when querying the call log table */
    public static final String[] CALL_LOG_PROJECTION = new String[] {
            Calls._ID,
            Calls.NUMBER,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.CACHED_NAME,
            Calls.CACHED_NUMBER_TYPE,
            Calls.CACHED_NUMBER_LABEL,
            Calls.PHONE_ID,
            Calls.ICC_ID
    };

    public static final int ID_COLUMN_INDEX = 0;
    public static final int NUMBER_COLUMN_INDEX = 1;
    public static final int DATE_COLUMN_INDEX = 2;
    public static final int DURATION_COLUMN_INDEX = 3;
    public static final int CALL_TYPE_COLUMN_INDEX = 4;
    public static final int CALLER_NAME_COLUMN_INDEX = 5;
    public static final int CALLER_NUMBERTYPE_COLUMN_INDEX = 6;
    public static final int CALLER_NUMBERLABEL_COLUMN_INDEX = 7;
    public static final int SIM_COLUMN_INDEX = 8;
    public static final int SIM_NAME_INDEX = 9;
}
