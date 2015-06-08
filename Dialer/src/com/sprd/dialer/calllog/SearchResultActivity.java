
package com.sprd.dialer.calllog;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import android.app.ListActivity;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Debug;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.PhoneLookup;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.android.dialer.R;
import com.google.common.collect.Lists;
import com.sprd.dialer.CallLogClearColumn;
import com.sprd.dialer.SourceUtils;

public class SearchResultActivity extends ListActivity implements CallLogClearColumn {
    private static final String TAG = "SearchResultActivity";
    private static final boolean DBG = Debug.isDebug();
    private static final Executor sDefaultExecute = Executors.newCachedThreadPool();

    private String mQueryFilter = null;

    public static final int ITEM_SEARCH = Menu.FIRST;

    /** The projection to use when querying the phones table */
    static final String[] PHONES_PROJECTION = new String[] {
            PhoneLookup._ID,
            PhoneLookup.DISPLAY_NAME,
            PhoneLookup.TYPE,
            PhoneLookup.LABEL,
            PhoneLookup.NUMBER
    };

    static final int PERSON_ID_COLUMN_INDEX = 0;
    static final int NAME_COLUMN_INDEX = 1;
    static final int PHONE_TYPE_COLUMN_INDEX = 2;
    static final int LABEL_COLUMN_INDEX = 3;
    static final int MATCHED_NUMBER_COLUMN_INDEX = 4;

    /** For the name first letter matching */
    public static final String CACHED_NORMALIZED_SIMPLE_NAME = "normalized_simple_name";
    /** For the name all letter matching */
    public static final String CACHED_NORMALIZED_FULL_NAME = "normalized_full_name";

    private SearchCallLogAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.calls_delete_activity);

        mAdapter = new SearchCallLogAdapter(this, null);
        String emptyText = getString(R.string.recentCalls_empty);
        ((TextView) (getListView().getEmptyView())).setText(emptyText);
        setListAdapter(mAdapter);
        getListView().setItemsCanFocus(true);

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (DBG) Log.d(TAG, "action = " + intent.getAction());
        mQueryFilter = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            // handles a click on a search suggestion
            Intent callDetail = new Intent(Intent.ACTION_VIEW);
            callDetail.setDataAndType(intent.getData(), CallLog.Calls.CONTENT_ITEM_TYPE);
            startActivity(callDetail);
            finish();
            return;
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            mQueryFilter = intent.getStringExtra(SearchManager.QUERY);
        }
        AsyncQueryThread thread = new AsyncQueryThread(getApplicationContext());
        thread.executeOnExecutor(sDefaultExecute);
    }

    private final class SearchCallLogAdapter extends ResourceCursorAdapter {
        public SearchCallLogAdapter(Context context, Cursor cursor) {
            super(context, R.layout.delete_calls_list_child_item, cursor,
                    FLAG_REGISTER_CONTENT_OBSERVER);
        }

        @Override
        public void bindView(View view, Context context, Cursor c) {
            ImageView iconView = (ImageView) view.findViewById(R.id.call_type_icon);
            ImageView simView = (ImageView) view.findViewById(R.id.sim);
            TextView line1View = (TextView) view.findViewById(R.id.line1);

            // TextView labelView = (TextView) view.findViewById(R.id.label);
            TextView numberView = (TextView) view.findViewById(R.id.number);
            TextView dateView = (TextView) view.findViewById(R.id.date);
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.call_icon);
            View dividerView = (View) view.findViewById(R.id.divider);
            dividerView.setVisibility(View.INVISIBLE);
            checkBox.setVisibility(View.INVISIBLE);

            int phoneId = 0;
            if (null != c) {
                phoneId = c.getInt(SIM_COLUMN_INDEX);
            }
            int sourceId = SourceUtils.getSimDrawableFromPhoneId(phoneId);
            simView.setBackgroundResource(sourceId);

            final long id = c.getLong(ID_COLUMN_INDEX);
            long date = c.getLong(DATE_COLUMN_INDEX);

            String number = c.getString(NUMBER_COLUMN_INDEX);
            String formattedNumber = number;
            String name = c.getString(CALLER_NAME_COLUMN_INDEX);

            if (!TextUtils.isEmpty(name)) {
                line1View.setText(name);
                numberView.setText(formattedNumber);
            } else {
                line1View.setText(number);
                numberView.setVisibility(View.GONE);
            }

            if (iconView != null) {
                int type = c.getInt(CALL_TYPE_COLUMN_INDEX);
                Drawable drawable = getResources().getDrawable(
                        SourceUtils.getDrawableFromCallType(type));
                iconView.setImageDrawable(drawable);
            }
            int flags = DateUtils.FORMAT_ABBREV_RELATIVE;
            dateView.setText(DateUtils.getRelativeTimeSpanString(date, System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS, flags));
        }

        @Override
        protected void onContentChanged() {
            super.onContentChanged();
            AsyncQueryThread thread = new AsyncQueryThread(getApplicationContext());
            thread.executeOnExecutor(sDefaultExecute);
        }
    }

    private class AsyncQueryThread extends AsyncTask<Void, Void, Cursor> {
        private Context aContext;
        public AsyncQueryThread(Context context) {
            aContext = context;
        }

        @Override
        protected Cursor doInBackground(Void... params) {
            ContentResolver cr = aContext.getContentResolver();
            Cursor cursor = cr.query(CallLog.Calls.CONTENT_URI, null, null, null, null);

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String number = cursor.getString(cursor.getColumnIndex("number"));
                    String oldName = cursor.getString(cursor.getColumnIndex("name"));
                    if(TextUtils.isEmpty(number)){
                        Log.e(TAG,"oldName is "+oldName + " number is null");
                        continue;
                    }
                    Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
                    Cursor phoneCursor = cr.query(uri, PHONES_PROJECTION, null, null, null);
                    String whereArg = Calls.NUMBER + "='" + number + "'";
                    if (null != phoneCursor && phoneCursor.moveToFirst()) {
                        String name = phoneCursor.getString(NAME_COLUMN_INDEX);
                        ContentValues values = new ContentValues();
                        values.put(Calls.CACHED_NAME, name);
                        cr.update(Calls.CONTENT_URI, values, whereArg, null);
                    } else if (!TextUtils.isEmpty(oldName)) {
                        ContentValues values = new ContentValues();
                        values.putNull(Calls.CACHED_NAME);
                        cr.update(Calls.CONTENT_URI, values, whereArg, null);
                    }

                    if (phoneCursor != null) {
                        phoneCursor.close();
                    }
                }
                cursor.close();
            }

            String where = null;
            String[] selectArgs = null;
            if (!TextUtils.isEmpty(mQueryFilter)) {
                StringBuffer whereArg = new StringBuffer();
                List<String> args = Lists.newArrayList();
                whereArg.append(String.format("(%s like ?)", Calls.CACHED_NAME));
                whereArg.append(" OR ");
                whereArg.append(String.format("(%s like ?)", Calls.NUMBER));
                whereArg.append(" OR ");
                whereArg.append(String.format("(%s like ?)", CACHED_NORMALIZED_SIMPLE_NAME));
                whereArg.append(" OR ");
                whereArg.append(String.format("(%s like ?)", CACHED_NORMALIZED_FULL_NAME));

                args.add("%" + mQueryFilter + "%");
                args.add("%" + mQueryFilter + "%");
                args.add("%" + mQueryFilter + "%");
                args.add(mQueryFilter + "%");

                where = whereArg.toString();
                selectArgs = args.toArray(new String[0]);
            }

            Cursor c = cr.query(CallLog.Calls.CONTENT_URI, CALL_LOG_PROJECTION, where, selectArgs, null);
            return c;
        }

        @Override
        protected void onPostExecute(Cursor result) {
            if (mAdapter != null) {
                mAdapter.changeCursor(result);
            } else {
                result.close();
            }
        }
    };

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Intent callDetail = new Intent(Intent.ACTION_VIEW);
        Uri uri = ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI, id);
        callDetail.setDataAndType(uri, CallLog.Calls.CONTENT_ITEM_TYPE);
        startActivity(callDetail);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != mAdapter) {
            mAdapter.changeCursor(null);
        }
    }
}
