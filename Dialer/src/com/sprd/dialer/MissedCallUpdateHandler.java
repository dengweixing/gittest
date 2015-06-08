/** Added by Spreadst*/
package com.sprd.dialer;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.provider.CallLog.Calls;

public class MissedCallUpdateHandler extends AsyncQueryHandler {

    public MissedCallUpdateHandler(ContentResolver cr) {
        super(cr);
    }

    /** Updates all missed calls to mark them as read. */
    public void markMissedCallsAsRead() {
        // Mark all "new" calls as not new anymore.
        StringBuilder where = new StringBuilder();
        where.append(Calls.IS_READ).append(" = 0");
        where.append(" AND ");
        where.append(Calls.TYPE).append(" = ").append(Calls.MISSED_TYPE);

        ContentValues values = new ContentValues(1);
        values.put(Calls.IS_READ, "1");

        startUpdate(-1, null, Calls.CONTENT_URI, values,
                where.toString(), null);
    }
}
