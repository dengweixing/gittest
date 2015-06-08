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

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.ListFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.sim.SimManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView;
import android.widget.TextView;

import com.android.common.io.MoreCloseables;
import com.android.contacts.common.CallUtil;
import com.android.contacts.common.util.StopWatch;
import com.android.dialer.R;
import com.android.dialer.util.EmptyLoader;
import com.android.dialer.voicemail.VoicemailStatusHelper;
import com.android.dialer.voicemail.VoicemailStatusHelper.StatusMessage;
import com.android.dialer.voicemail.VoicemailStatusHelperImpl;
import com.android.dialerbind.ObjectFactory;
import com.sprd.dialer.CallLogClearActivity;
import com.sprd.dialer.CallLogSetting;
import com.sprd.dialer.PreloadUtils;
import com.sprd.dialer.SprdUtils;

import java.util.List;

/**
 * Displays a list of call log entries. To filter for a particular kind of call
 * (all, missed or voicemails), specify it in the constructor.
 */
public class CallLogFragment extends ListFragment
        implements CallLogQueryHandler.Listener, CallLogAdapter.CallFetcher {
    private static final String TAG = "CallLogFragment";
    /* SPRD: add for 280186 @{
     * Like activity, we can use Bundle to keep the state of fragment, if the activity process
     * has been killed, and when activity is recreated, you need to restore the fragment state
     * can be used. You can be in onSaveInstanceState (fragment) state of preservation period,
     * and in the onCreate (), onCreateView () or (onActivityCreated) during the recovery period.
     * Here,we use FILTER_TYPE_KEY for saving mCallTypeFilter in Bundle.
     * */
    private static final String FILTER_TYPE_KEY = "filter_type";
    /* @} */
    /**
     * ID of the empty loader to defer other fragments.
     */
    private static final int EMPTY_LOADER_ID = 0;

    private CallLogAdapter mAdapter;
    private CallLogQueryHandler mCallLogQueryHandler;
    private boolean mScrollToTop;

    /** Whether there is at least one voicemail source installed. */
    private boolean mVoicemailSourcesAvailable = false;

    private VoicemailStatusHelper mVoicemailStatusHelper;
    private View mStatusMessageView;
    private TextView mStatusMessageText;
    private TextView mStatusMessageAction;
    private KeyguardManager mKeyguardManager;

    private boolean mEmptyLoaderRunning;
    private boolean mCallLogFetched;
    private boolean mVoicemailStatusFetched;
    /* SPRD: Add for performance optimization @{ */
    private final static int REFRESH_DATA = 100;
    private final static int FETCH_CALL = 101;
    private final static int POST_DELAY = 50; // 100ms
    /* @} */
    private final Handler mHandler = new Handler();

    private TelephonyManager mTelephonyManager;

    //SPRD: Add for bug 274958
    private BroadcastReceiver mSimChangeReceiver;

    private class CustomContentObserver extends ContentObserver {
        public CustomContentObserver() {
            super(mHandler);
        }
        @Override
        public void onChange(boolean selfChange) {
            mRefreshDataRequired = true;
        }
    }

    // See issue 6363009
    private final ContentObserver mCallLogObserver = new CustomContentObserver();
    private final ContentObserver mContactsObserver = new CustomContentObserver();
    private boolean mRefreshDataRequired = true;

    // Exactly same variable is in Fragment as a package private.
    private boolean mMenuVisible = true;

    // Default to all calls.
    private int mCallTypeFilter = CallLogQueryHandler.CALL_TYPE_ALL;

    /* SPRD: show type of call log @{ */
    private int mShowType = CallLogSetting.TYPE_ALL;
    /* @} */

    // Log limit - if no limit is specified, then the default in {@link CallLogQueryHandler}
    // will be used.
    private int mLogLimit = -1;

    public CallLogFragment() {
        this(CallLogQueryHandler.CALL_TYPE_ALL, -1);
    }

    public CallLogFragment(int filterType) {
        this(filterType, -1);
    }

    public CallLogFragment(int filterType, int logLimit) {
        super();
        mCallTypeFilter = filterType;
        mLogLimit = logLimit;
    }
    /* SPRD: add for 280186 @{ */
     public void onSaveInstanceState(Bundle outState) {
         if (outState != null) {
             outState.putInt(FILTER_TYPE_KEY, mCallTypeFilter);
             }
     }
     /* @} */
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        /* SPRD: add for 280186 @{ */
        if (state != null) {
            mCallTypeFilter = state.getInt(FILTER_TYPE_KEY, mCallTypeFilter);
            }
        /* @} */
        final StopWatch stopWatch = StopWatch.start("CallLog.onCreat");
        mCallLogQueryHandler = new CallLogQueryHandler(getActivity().getContentResolver(),
                this, mLogLimit);
        mKeyguardManager =
                (KeyguardManager) getActivity().getSystemService(Context.KEYGUARD_SERVICE);
        getActivity().getContentResolver().registerContentObserver(CallLog.CONTENT_URI, true,
                mCallLogObserver);
        getActivity().getContentResolver().registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI, true, mContactsObserver);
        setHasOptionsMenu(true);

        /* SPRD: show type of call log @{ */
        mShowType = CallLogSetting.getCallLogShowType(getActivity());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        prefs.registerOnSharedPreferenceChangeListener(mShowCalllogListener);
        /* @} */
        stopWatch.lap("fir");
        mFetchCallHandler.removeMessages(FETCH_CALL);
        mFetchCallHandler.sendEmptyMessageDelayed(FETCH_CALL, POST_DELAY); // SPRD: Add for performance optimization
        stopWatch.stopAndLog(TAG, 5);
        /* SPRD: Add for bug 274958 @{ */
        IntentFilter filter = new IntentFilter();
        filter.addAction(SimManager.INSERT_SIMS_CHANGED_ACTION);
        mSimChangeReceiver = new SimChangeReceiver();
        getActivity().registerReceiver(mSimChangeReceiver,filter);
        /* @} */
    }

    /** Called by the CallLogQueryHandler when the list of calls has been fetched or updated. */
    @Override
    public void onCallsFetched(Cursor cursor) {
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        mAdapter.setLoading(false);
        mAdapter.changeCursor(cursor);
        // This will update the state of the "Clear call log" menu item.
        getActivity().invalidateOptionsMenu();
        if (mScrollToTop) {
            final ListView listView = getListView();
            // The smooth-scroll animation happens over a fixed time period.
            // As a result, if it scrolls through a large portion of the list,
            // each frame will jump so far from the previous one that the user
            // will not experience the illusion of downward motion.  Instead,
            // if we're not already near the top of the list, we instantly jump
            // near the top, and animate from there.
            if (listView.getFirstVisiblePosition() > 5) {
                listView.setSelection(5);
            }
            // Workaround for framework issue: the smooth-scroll doesn't
            // occur if setSelection() is called immediately before.
            mHandler.post(new Runnable() {
               @Override
               public void run() {
                   if (getActivity() == null || getActivity().isFinishing()) {
                       return;
                   }
                   listView.smoothScrollToPosition(0);
               }
            });

            mScrollToTop = false;
        }
        mCallLogFetched = true;
        destroyEmptyLoaderIfAllDataFetched();
    }

    /**
     * Called by {@link CallLogQueryHandler} after a successful query to voicemail status provider.
     */
    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        updateVoicemailStatusMessage(statusCursor);

        int activeSources = mVoicemailStatusHelper.getNumberActivityVoicemailSources(statusCursor);
        setVoicemailSourcesAvailable(activeSources != 0);
        MoreCloseables.closeQuietly(statusCursor);
        mVoicemailStatusFetched = true;
        destroyEmptyLoaderIfAllDataFetched();
    }

    private void destroyEmptyLoaderIfAllDataFetched() {
        if (mCallLogFetched && mVoicemailStatusFetched && mEmptyLoaderRunning) {
            mEmptyLoaderRunning = false;
            getLoaderManager().destroyLoader(EMPTY_LOADER_ID);
        }
    }

    /** Sets whether there are any voicemail sources available in the platform. */
    private void setVoicemailSourcesAvailable(boolean voicemailSourcesAvailable) {
        if (mVoicemailSourcesAvailable == voicemailSourcesAvailable) return;
        mVoicemailSourcesAvailable = voicemailSourcesAvailable;

        Activity activity = getActivity();
        if (activity != null) {
            // This is so that the options menu content is updated.
            activity.invalidateOptionsMenu();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        StopWatch stopWatch = StopWatch.start("CallLog.onCreateView");
        View view;
        /* SPRD: add for UUI @ { */
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
             view = inflater.inflate(R.layout.call_log_fragment_sprd, container, false);
        }else{
             view = inflater.inflate(R.layout.call_log_fragment, container, false);
        }
        /* @} */
        stopWatch.lap("inflate");
        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();
        mStatusMessageView = view.findViewById(R.id.voicemail_status);
        mStatusMessageText = (TextView) view.findViewById(R.id.voicemail_status_message);
        mStatusMessageAction = (TextView) view.findViewById(R.id.voicemail_status_action);
        stopWatch.stopAndLog(TAG, 5);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateEmptyMessage(mCallTypeFilter);
        String currentCountryIso = PreloadUtils.getInstance().getCurrentCountryIso(getActivity()); // SPRD: Add for performance optimization
        mAdapter = ObjectFactory.newCallLogAdapter(getActivity(), this, new ContactInfoHelper(
                getActivity(), currentCountryIso), false, true);
        setListAdapter(mAdapter);
        getListView().setItemsCanFocus(true);
        /*if(SprdUtils.UNIVERSE_UI_SUPPORT){*/
            getListView().setOnScrollListener(new OnScrollListener(){
                @Override
                public void onScroll(AbsListView arg0, int arg1, int arg2, int arg3) {
                    // TODO Auto-generated method stub
                }
                @Override
                public void onScrollStateChanged(AbsListView listView, int scrollState) {
                    mAdapter.setListScrolling(scrollState);
                }});
            getListView().setBackgroundColor(Color.WHITE); // SPRD: Modify for performance optimization
        /*}*/
    }

    /**
     * Based on the new intent, decide whether the list should be configured
     * to scroll up to display the first item.
     */
    public void configureScreenFromIntent(Intent newIntent) {
        // Typically, when switching to the call-log we want to show the user
        // the same section of the list that they were most recently looking
        // at.  However, under some circumstances, we want to automatically
        // scroll to the top of the list to present the newest call items.
        // For example, immediately after a call is finished, we want to
        // display information about that call.
        mScrollToTop = Calls.CONTENT_TYPE.equals(newIntent.getType());
    }

    @Override
    public void onStart() {
        // Start the empty loader now to defer other fragments.  We destroy it when both calllog
        // and the voicemail status are fetched.
        getLoaderManager().initLoader(EMPTY_LOADER_ID, null,
                new EmptyLoader.Callback(getActivity()));
        mEmptyLoaderRunning = true;
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mFetchCallHandler.removeMessages(REFRESH_DATA);
        mFetchCallHandler.sendEmptyMessageDelayed(REFRESH_DATA, POST_DELAY); // SPRD: Add for performance optimization
    }

    private void updateVoicemailStatusMessage(Cursor statusCursor) {
        List<StatusMessage> messages = mVoicemailStatusHelper.getStatusMessages(statusCursor);
        if (messages.size() == 0) {
            mStatusMessageView.setVisibility(View.GONE);
        } else {
            mStatusMessageView.setVisibility(View.VISIBLE);
            // TODO: Change the code to show all messages. For now just pick the first message.
            final StatusMessage message = messages.get(0);
            if (message.showInCallLog()) {
                mStatusMessageText.setText(message.callLogMessageId);
            }
            if (message.actionMessageId != -1) {
                mStatusMessageAction.setText(message.actionMessageId);
            }
            if (message.actionUri != null) {
                mStatusMessageAction.setVisibility(View.VISIBLE);
                mStatusMessageAction.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getActivity().startActivity(
                                new Intent(Intent.ACTION_VIEW, message.actionUri));
                    }
                });
            } else {
                mStatusMessageAction.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Kill the requests thread
        mAdapter.stopRequestProcessing();
    }

    @Override
    public void onStop() {
        super.onStop();
        updateOnExit();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAdapter.stopRequestProcessing();
        mAdapter.changeCursor(null);
        getActivity().getContentResolver().unregisterContentObserver(mCallLogObserver);
        getActivity().getContentResolver().unregisterContentObserver(mContactsObserver);

        /* SPRD: remove listener on SharedPreferences @{ */
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        prefs.registerOnSharedPreferenceChangeListener(mShowCalllogListener);
        /* @} */
        /* SPRD: Add for bug 274958 @{ */
        if (mSimChangeReceiver != null) {
            getActivity().unregisterReceiver(mSimChangeReceiver);
            mSimChangeReceiver = null;
        }
        /* @} */
    }

    @Override
    public void fetchCalls() {
        /* SPRD: add for Bug284963 @ { */
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            mCallLogQueryHandler.fetchCalls(mCallTypeFilter, mShowType, mContext);
        } else {
            mCallLogQueryHandler.fetchCalls(mCallTypeFilter, mShowType);
        }
        /* @} */
    }

    public void startCallsQuery() {
        mAdapter.setLoading(true);
        /* SPRD: add for Bug284963 @ { */
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            mCallLogQueryHandler.fetchCalls(mCallTypeFilter, mShowType, mContext);
        } else {
            mCallLogQueryHandler.fetchCalls(mCallTypeFilter, mShowType);
        }
        /* @} */
    }

    private void startVoicemailStatusQuery() {
        mCallLogQueryHandler.fetchVoicemailStatus();
    }

    private void updateCallList(int filterType, int showType) {
        mCallLogQueryHandler.fetchCalls(filterType, showType);
    }

    private void updateEmptyMessage(int filterType) {
        final String message;
        switch (filterType) {
            case Calls.MISSED_TYPE:
                message = getString(R.string.recentMissed_empty);
                break;
            case CallLogQueryHandler.CALL_TYPE_ALL:
                message = getString(R.string.recentCalls_empty);
                break;
            /* SPRD: add for other call type @{ */
            case Calls.OUTGOING_TYPE:
                message = getString(R.string.recentOutgoing_empty);
                break;
            case Calls.INCOMING_TYPE:
                message = getString(R.string.recentIncoming_empty);
                break;
            /* @} */
            default:
                throw new IllegalArgumentException("Unexpected filter type in CallLogFragment: "
                        + filterType);
        }
        ((TextView) getListView().getEmptyView()).setText(message);
    }

    public void callSelectedEntry() {
        int position = getListView().getSelectedItemPosition();
        if (position < 0) {
            // In touch mode you may often not have something selected, so
            // just call the first entry to make sure that [send] [send] calls the
            // most recent entry.
            position = 0;
        }
        final Cursor cursor = (Cursor)mAdapter.getItem(position);
        if (cursor != null) {
            String number = cursor.getString(CallLogQuery.NUMBER);
            int numberPresentation = cursor.getInt(CallLogQuery.NUMBER_PRESENTATION);
            if (!PhoneNumberUtilsWrapper.canPlaceCallsTo(number, numberPresentation)) {
                // This number can't be called, do nothing
                return;
            }
            Intent intent;
            // If "number" is really a SIP address, construct a sip: URI.
            if (PhoneNumberUtils.isUriNumber(number)) {
                intent = CallUtil.getCallIntent(
                        Uri.fromParts(CallUtil.SCHEME_SIP, number, null));
            } else {
                // We're calling a regular PSTN phone number.
                // Construct a tel: URI, but do some other possible cleanup first.
                int callType = cursor.getInt(CallLogQuery.CALL_TYPE);
                if (!number.startsWith("+") &&
                       (callType == Calls.INCOMING_TYPE
                                || callType == Calls.MISSED_TYPE)) {
                    // If the caller-id matches a contact with a better qualified number, use it
                    String countryIso = cursor.getString(CallLogQuery.COUNTRY_ISO);
                    number = mAdapter.getBetterNumberFromContacts(number, countryIso);
                }
                intent = CallUtil.getCallIntent(
                        Uri.fromParts(CallUtil.SCHEME_TEL, number, null));
            }
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(intent);
        }
    }

    CallLogAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void setMenuVisibility(boolean menuVisible) {
        super.setMenuVisibility(menuVisible);
        if (mMenuVisible != menuVisible) {
            mMenuVisible = menuVisible;
            if (!menuVisible) {
                updateOnExit();
            } else if (isResumed()) {
                refreshData();
            }
        }
    }

    /** Requests updates to the data to be shown. */
    private void refreshData() {
        // Prevent unnecessary refresh.
        if (mRefreshDataRequired) {
            // Mark all entries in the contact info cache as out of date, so they will be looked up
            // again once being shown.
            mAdapter.invalidateCache();
            startCallsQuery();
            startVoicemailStatusQuery();
            updateOnEntry();
            mRefreshDataRequired = false;
        /* SPRD: Add for bug 284234 & bug 341529
         * When time changed,we should modify data of time in listview every time using notifyDataSetChanged.
         * @{ */
        }else {
            mAdapter.notifyDataSetChanged();
            /* @} */
        }
    }

    /** Updates call data and notification state while leaving the call log tab. */
    private void updateOnExit() {
        updateOnTransition(false);
    }

    /** Updates call data and notification state while entering the call log tab. */
    private void updateOnEntry() {
        updateOnTransition(true);
    }

    // TODO: Move to CallLogActivity
    private void updateOnTransition(boolean onEntry) {
        // We don't want to update any call data when keyguard is on because the user has likely not
        // seen the new calls yet.
        // This might be called before onCreate() and thus we need to check null explicitly.
        if (mKeyguardManager != null && !mKeyguardManager.inKeyguardRestrictedInputMode()) {
            // On either of the transitions we update the missed call and voicemail notifications.
            // While exiting we additionally consume all missed calls (by marking them as read).
            mCallLogQueryHandler.markNewCallsAsOld();
            if (!onEntry) {
                mCallLogQueryHandler.markMissedCallsAsRead();
            }
            CallLogNotificationsHelper.removeMissedCallNotifications();
            /* SPRD: Add for bug 270793 @{ */
            if(null == getActivity()){
                CallLogNotificationsHelper.updateVoicemailNotifications(mContext);
            }else{
            /* @} */
                CallLogNotificationsHelper.updateVoicemailNotifications(getActivity());
            /* SPRD: Add for bug 270793 @{ */
            }
            /* @} */
        }
    }

    /* SPRD: a listener for call log to notify which type to show */
    OnSharedPreferenceChangeListener mShowCalllogListener = new OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if(CallLogSetting.SHOW_TYPE.equals(key)){
                mShowType = sharedPreferences.getInt(key, CallLogSetting.TYPE_ALL);
                Log.d(TAG, "Preference changed type = " + mShowType);
                if(SprdUtils.UNIVERSE_UI_SUPPORT){
                    mCallLogQueryHandler.fetchCalls(mCallTypeFilter, mShowType, mContext);
                } else {
                    updateCallList(mCallTypeFilter,mShowType);
                }
            }
        }
    };

    /* SPRD: Add for performance optimization @{ */
    private final Handler mFetchCallHandler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            switch (msg.what) {
                case REFRESH_DATA:
                    Log.d(TAG, "refreshData");
                    //if(mAdapter != null) mAdapter.setSimMapInvalidate();
                    refreshData();
                    break;
                case FETCH_CALL:
                    Log.d(TAG, "updateCallList");
                    /* SPRD: add for Bug284963 @ { */
                    if(SprdUtils.UNIVERSE_UI_SUPPORT){
                        mCallLogQueryHandler.fetchCalls(mCallTypeFilter, mShowType, mContext);
                    } else {
                        updateCallList(mCallTypeFilter,mShowType);
                    }
                    /* @} */
                    break;
            }
        }
    };
    /* @} */
    /* SPRD: Add for bug 270793 @{ */
    private Context mContext;
    @Override
    public void onAttach(Activity activity){
        super.onAttach(activity);
        mContext = activity;
    }
    /* @} */

    /* SPRD: Add for bug 274958 @{ */
    public class SimChangeReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent != null && SimManager.INSERT_SIMS_CHANGED_ACTION.equals(intent.getAction())){
                mRefreshDataRequired = true;
                mAdapter.setSimMapInvalidate();
            }
        }
    };
    /* @} */
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.call_log_options, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        final MenuItem itemDeleteAll = menu.findItem(R.id.delete_all);

        if (itemDeleteAll != null && mAdapter != null) {
            itemDeleteAll.setVisible(!mAdapter.isEmpty());
        }
        MenuItem viewCallLog = menu.findItem(R.id.view_setting);
        viewCallLog.setVisible(TelephonyManager.isMultiSim());
        return;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                /* SPRD: add for bug271741,bug317722 @{ */
                // final Intent intent = new Intent(this, DialtactsActivity.class);
                // intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                // startActivity(intent);
                getActivity().onBackPressed();
                /* @} */
                return true;
            case R.id.delete_all:
                Intent intent = new Intent();
                intent.setClass(getActivity(), CallLogClearActivity.class);
                startActivity(intent);
                return true;
            case R.id.view_setting:
                CallLogSetting.show(getFragmentManager());
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
