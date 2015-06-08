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

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteFullException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.common.widget.GroupingListAdapter;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.util.UriUtils;
import com.android.dialer.CallDetailActivity;
import com.android.dialer.PhoneCallDetails;
import com.android.dialer.PhoneCallDetailsHelper;
import com.android.dialer.R;
import com.android.dialer.util.AsyncTaskExecutor;
import com.android.dialer.util.AsyncTaskExecutors;
import com.android.dialer.util.ExpirableCache;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.sprd.dialer.SprdUtils;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.TelephonyIntents;
import android.provider.Contacts.Intents.Insert;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;

import java.util.LinkedList;
/**
 * SPRD:
 * @{
 */
import android.telephony.TelephonyManager;
/**
* @}
*/

/**
 * Adapter class to fill in data for the Call Log.
 */
public class CallLogAdapter extends GroupingListAdapter
        implements ViewTreeObserver.OnPreDrawListener, CallLogGroupBuilder.GroupCreator {

    /** Interface used to initiate a refresh of the content. */
    public interface CallFetcher {
        public void fetchCalls();
    }

    /**
     * Stores a phone number of a call with the country code where it originally occurred.
     * <p>
     * Note the country does not necessarily specifies the country of the phone number itself, but
     * it is the country in which the user was in when the call was placed or received.
     */
    private static final class NumberWithCountryIso {
        public final String number;
        public final String countryIso;

        public NumberWithCountryIso(String number, String countryIso) {
            this.number = number;
            this.countryIso = countryIso;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) return false;
            if (!(o instanceof NumberWithCountryIso)) return false;
            NumberWithCountryIso other = (NumberWithCountryIso) o;
            return TextUtils.equals(number, other.number)
                    && TextUtils.equals(countryIso, other.countryIso);
        }

        @Override
        public int hashCode() {
            return (number == null ? 0 : number.hashCode())
                    ^ (countryIso == null ? 0 : countryIso.hashCode());
        }
    }

    /** The time in millis to delay starting the thread processing requests. */
    private static final int START_PROCESSING_REQUESTS_DELAY_MILLIS = 1000;

    /** The size of the cache of contact info. */
    private static final int CONTACT_INFO_CACHE_SIZE = 100;

    protected final Context mContext;
    private final ContactInfoHelper mContactInfoHelper;
    private final CallFetcher mCallFetcher;
    private ViewTreeObserver mViewTreeObserver = null;

    /* SPRD: @{ */
    private ContentResolver mResolver;
    /* @} */

    /**
     * A cache of the contact details for the phone numbers in the call log.
     * <p>
     * The content of the cache is expired (but not purged) whenever the application comes to
     * the foreground.
     * <p>
     * The key is number with the country in which the call was placed or received.
     */
    private ExpirableCache<NumberWithCountryIso, ContactInfo> mContactInfoCache;

    /**
     * A request for contact details for the given number.
     */
    private static final class ContactInfoRequest {
        /** The number to look-up. */
        public final String number;
        /** The country in which a call to or from this number was placed or received. */
        public final String countryIso;
        /** The cached contact information stored in the call log. */
        public final ContactInfo callLogInfo;

        public ContactInfoRequest(String number, String countryIso, ContactInfo callLogInfo) {
            this.number = number;
            this.countryIso = countryIso;
            this.callLogInfo = callLogInfo;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (!(obj instanceof ContactInfoRequest)) return false;

            ContactInfoRequest other = (ContactInfoRequest) obj;

            if (!TextUtils.equals(number, other.number)) return false;
            if (!TextUtils.equals(countryIso, other.countryIso)) return false;
            if (!Objects.equal(callLogInfo, other.callLogInfo)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((callLogInfo == null) ? 0 : callLogInfo.hashCode());
            result = prime * result + ((countryIso == null) ? 0 : countryIso.hashCode());
            result = prime * result + ((number == null) ? 0 : number.hashCode());
            return result;
        }
    }

    /**
     * List of requests to update contact details.
     * <p>
     * Each request is made of a phone number to look up, and the contact info currently stored in
     * the call log for this number.
     * <p>
     * The requests are added when displaying the contacts and are processed by a background
     * thread.
     */
    private final LinkedList<ContactInfoRequest> mRequests;

    private boolean mLoading = true;
    private static final int REDRAW = 1;
    private static final int START_THREAD = 2;

    private QueryThread mCallerIdThread;

    /** Instance of helper class for managing views. */
    private final CallLogListItemHelper mCallLogViewsHelper;

    /** Helper to set up contact photos. */
    private final ContactPhotoManager mContactPhotoManager;
    /** Helper to parse and process phone numbers. */
    private PhoneNumberHelper mPhoneNumberHelper;
    /** Helper to group call log entries. */
    private final CallLogGroupBuilder mCallLogGroupBuilder;

    /** Can be set to true by tests to disable processing of requests. */
    private volatile boolean mRequestProcessingDisabled = false;

    /** True if CallLogAdapter is created from the PhoneFavoriteFragment, where the primary
     * action should be set to call a number instead of opening the detail page. */
    private boolean mUseCallAsPrimaryAction = false;

    private boolean mIsCallLog = true;
    private int mNumMissedCalls = 0;
    private int mNumMissedCallsShown = 0;

    private View mBadgeContainer;
    private ImageView mBadgeImageView;
    private TextView mBadgeText;

    /** Listener for the primary or secondary actions in the list.
     *  Primary opens the call details.
     *  Secondary calls or plays.
     **/
    private final View.OnClickListener mActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            startActivityForAction(view);
        }
    };

    private void startActivityForAction(View view) {
        final IntentProvider intentProvider = (IntentProvider) view.getTag();
        if (intentProvider != null) {
            final Intent intent = intentProvider.getIntent(mContext);
            // See IntentProvider.getCallDetailIntentProvider() for why this may be null.
            if (intent != null) {
                mContext.startActivity(intent);
            }
        }
    }
    /* SPRD: add for long click menu@ { */
    private static final String REMOVE_FROM_CALL_LOG_AND_FINISH = "REMOVE_FROM_CALL_LOG_AND_FINISH";
    public static final String NUMBER = "NUMBER";
    public static final String NAME = "NAME";
    public static final String CACHED_LOOKUP_URI = "CACHED_LOOKUP_URI";
    public static final String PHOTO_URI = "PHOTO_URI";
    public static final String NUMBER_TYPE = "NUMBER_TYPE";
    /* SPRD: add for bug274857 @ { */
    public static final String PHONE_ID = "PHONE_ID";
    /* @} */
    public static final int PHONE_ID_1 = 0;
    public static final int PHONE_ID_2 = 1;
    public static final String EXTRA_IS_VIDEOCALL = "android.phone.extra.IS_VIDEOCALL";
    public static final String EXTRA_IS_IPCALL = "is_ip_dial";
    private IntentProvider intentProvider;
    private AsyncTaskExecutor mAsyncTaskExecutor;
    Intent mIntent = null;
    private AlertDialog mAlertDialog = null;

    /** LongClickListener for the primary action in the list, opens the action selection  . */
    private final View.OnLongClickListener mPrimaryActionLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            intentProvider = (IntentProvider) v.getTag();
            if(intentProvider == null){
                return false;
            }
            mIntent = intentProvider.getIntent(mContext);
            if(mIntent == null){
                return false;
            }

            final String mNumber = mIntent.getStringExtra(NUMBER);
            final String mName = mIntent.getStringExtra(NAME);
            int phoneCount = TelephonyManager.getPhoneCount();
            if(mNumber == null ){
                return false;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = null;
            if(SprdUtils.UNIVERSE_UI_SUPPORT){
                view = inflater
                        .inflate(R.layout.call_log_long_click_dialog_sprd, (ViewGroup)
                                v.findViewById(R.id.call_log_long_click_dialog));
            }else{
                view = inflater
                        .inflate(R.layout.call_log_long_click_dialog, (ViewGroup)
                                v.findViewById(R.id.call_log_long_click_dialog));
            }
            builder.setView(view);
            int phoneNumber = TelephonyManager.getPhoneCount();
            ViewGroup simLayout;
            TextView[] callBySim       = new TextView[phoneNumber];
            TextView[] videoCallBySim  = new TextView[phoneNumber];
            TextView[] ipCallBySim     = new TextView[phoneNumber];
            //SPRD : add for bug345607
            ImageView[] callBySimDivider = new ImageView[phoneNumber];
            ImageView[] videoCallBySimDivider = new ImageView[phoneNumber];
            ViewGroup[] simItemLayout  = new ViewGroup[phoneNumber];
            simLayout = (ViewGroup)view.findViewById(R.id.call_by_sim_layout);

            for(int i=0;i<phoneNumber;i++){
                if(SprdUtils.UNIVERSE_UI_SUPPORT){
                    simItemLayout[i] = (ViewGroup)inflater.inflate(R.layout.call_log_long_click_dialog_sim_item_sprd,null);
                } else {
                    simItemLayout[i] = (ViewGroup)inflater.inflate(R.layout.call_log_long_click_dialog_sim_item,null);
                }
                callBySim[i] =  (TextView)simItemLayout[i].findViewById(R.id.call_by_sim);
                videoCallBySim[i] =  (TextView)simItemLayout[i].findViewById(R.id.video_call_by_sim);
                if(SprdUtils.UNIVERSE_UI_SUPPORT){
                    ipCallBySim[i] = (TextView)simItemLayout[i].findViewById(R.id.ip_call_by_sim);
                } else {
                    // SPRD : add for bug345607
                    callBySimDivider[i] = (ImageView) simItemLayout[i]
                            .findViewById(R.id.divider_call_by_sim);
                    videoCallBySimDivider[i] = (ImageView) simItemLayout[i]
                            .findViewById(R.id.divider_Video_call_by_sim);
                }
                simLayout.addView(simItemLayout[i], i);
            }

            TextView editNumberBeforeCall = (TextView)view.findViewById(R.id.edit_number_before_call);
            TextView sendSms = (TextView)view.findViewById(R.id.send_sms);
            TextView addBlacklist = (TextView)view.findViewById(R.id.add_to_blacklist);
            TextView addToContact = (TextView)view.findViewById(R.id.add_to_contact);
            TextView removeFromCallLog = (TextView)view.findViewById(R.id.remove_from_call_log);
            View addBlackListDivider = view.findViewById(R.id.add_to_blacklist_divider);
            View sendSmsSivider = view.findViewById(R.id.send_sms_divider);
            View editNumberBeforeCallDivider = view.findViewById(R.id.edit_number_before_call_divider);
            View addToContactDivider = view.findViewById(R.id.add_to_contact_divider);

            final TelephonyManager telephonyManager = (TelephonyManager) mContext
                    .getSystemService(Context.TELEPHONY_SERVICE);

            if (!telephonyManager.isSupportApplication(TelephonyManager.TYPE_CALL_FIRE_WALL)
                    || SprdUtils.CheckIsBlackNumber(mContext, mNumber)) {
                addBlacklist.setVisibility(View.GONE);
                addBlackListDivider.setVisibility(View.GONE);
            }

            boolean titleFlag = false ;
            if(TextUtils.isEmpty(mNumber)) {
                for(int i=0;i<phoneNumber;i++){
                    callBySim[i].setVisibility(View.GONE);
                    videoCallBySim[i].setVisibility(View.GONE);
                    if(SprdUtils.UNIVERSE_UI_SUPPORT){
                    ipCallBySim[i].setVisibility(View.GONE);
                    }
                }
                editNumberBeforeCall.setVisibility(View.GONE);
                sendSms.setVisibility(View.GONE);
                addBlacklist.setVisibility(View.GONE);
                addToContact.setVisibility(View.GONE);
                editNumberBeforeCallDivider.setVisibility(View.GONE);
                sendSmsSivider.setVisibility(View.GONE);
                addBlackListDivider.setVisibility(View.GONE);
                addToContactDivider.setVisibility(View.GONE);
                titleFlag = true;
            }

            if(mPhoneNumberHelper.isVoicemailNumber(mNumber)){
                editNumberBeforeCall.setVisibility(View.GONE);
                addToContact.setVisibility(View.GONE);
                sendSms.setVisibility(View.GONE);
                sendSmsSivider.setVisibility(View.GONE);
                editNumberBeforeCallDivider.setVisibility(View.GONE);
                addToContactDivider.setVisibility(View.GONE);
            }
            if (!TextUtils.isEmpty(mName)) {
                builder.setTitle(mName);
            } else {
                if(titleFlag){
                    builder.setTitle(R.string.unknown);
                }else {
                    builder.setTitle(mNumber);
                }
            }

            mAlertDialog = builder.create();
            for(int i=0;i<phoneNumber;i++){
                String Str;
                if (SprdUtils.UNIVERSE_UI_SUPPORT) {
                    Str = SprdUtils.getSimName(mContext, i);
                    if (Str == null) {
                        Str = mContext.getString(R.string.sim_no_effect);
                    }
                } else {
                    Str = "SIM" + (i + 1);
                }
                callBySim[i].setText(mContext.getString(R.string.call_log_callNumber_by_sim_ex,
                        Str));
                videoCallBySim[i].setText(mContext.getString(R.string.video_callNumber_by_sim_ex,
                        Str));
                if(SprdUtils.UNIVERSE_UI_SUPPORT){
                    ipCallBySim[i].setText(mContext.getString(R.string.ip_callNumber_by_sim_ex,Str));
                }
                if (!TelephonyManager.getDefault(i).hasIccCard()) {
                    callBySim[i].setVisibility(View.GONE);
                    videoCallBySim[i].setVisibility(View.GONE);
                    if(SprdUtils.UNIVERSE_UI_SUPPORT){
                        ipCallBySim[i].setVisibility(View.GONE);
                    }else {
                        //SPRD : add for bug345607
                        callBySimDivider[i].setVisibility(View.GONE);
                        videoCallBySimDivider[i].setVisibility(View.GONE);
                    }
                }
                if(i != 0){
                    videoCallBySim[i].setVisibility(View.GONE);
                    // SPRD : add for bug345607
                    if (!SprdUtils.UNIVERSE_UI_SUPPORT) {
                        videoCallBySimDivider[i].setVisibility(View.GONE);
                    }
                }
            }

            if(!SprdUtils.VT_SUPPORT){
                for(int i=0;i<phoneNumber;i++){
                    videoCallBySim[i].setVisibility(View.GONE);
                }
            }
            //Uri contactUri = SprdUtils.parseUriOrNull(intentProvider.getIntent(mContext).getStringExtra(CACHED_LOOKUP_URI));
            if (mName != null && !TextUtils.isEmpty(mName)) {
                // already exists in contacts.
                addToContact.setText(mContext.getString(R.string.menu_viewContact));
            }else{
                // add new contact.
                addToContact.setText(mContext.getString(R.string.recentCalls_addToContact));
            }

            for(int i=0;i<phoneNumber;i++){
                final int phoneId = i;
                callBySim[i].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mAlertDialog.dismiss();
                        mContext.startActivity(getIntent(mNumber, phoneId));
                    }
                });

                videoCallBySim[i]
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                mAlertDialog.dismiss();
                                Intent videoIntent = getIntent(mNumber, phoneId);
                                videoIntent.putExtra(EXTRA_IS_VIDEOCALL, true);
                                mContext.startActivity(videoIntent);
                            }
                        });
                if(SprdUtils.UNIVERSE_UI_SUPPORT){
                    ipCallBySim[i].setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mAlertDialog.dismiss();
                            Intent ipIntent = getIntent(mNumber, phoneId);
                            ipIntent.putExtra(EXTRA_IS_IPCALL, true);
                            mContext.startActivity(ipIntent);
                        }
                    });
                }
            }

            editNumberBeforeCall.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mAlertDialog.dismiss();
                    mContext.startActivity(new Intent(Intent.ACTION_DIAL,mPhoneNumberHelper.getCallUri(mNumber)));
                }
            });

            sendSms.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mAlertDialog.dismiss();
                    mContext.startActivity(new Intent(Intent.ACTION_SENDTO,Uri.fromParts("sms", mNumber, null)));
                }
            });

            //Add onclicklistener for launch the BlackCallsListAddActivity.
            addBlacklist.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mAlertDialog.dismiss();
                    try{
                        Intent intent = new Intent(SprdUtils.ACTION_ADD_BLACKLIST);
                        intent.putExtra("Click_BlackCalls_Number", mNumber);
                        intent.putExtra("Click_BlackCalls_Name", mName);
                        mContext.startActivity(intent);
                    }catch(ActivityNotFoundException e){
                        Toast.makeText(mContext, R.string.failed_addToBlacklist,
                                Toast.LENGTH_SHORT).show();
                    }

                }
            });

            addToContact.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mAlertDialog.dismiss();
                    Uri contactUri = SprdUtils.parseUriOrNull(mIntent.getStringExtra(CACHED_LOOKUP_URI));
                    Intent intent = null;
                    if(mName != null && !TextUtils.isEmpty(mName)){
                        intent = new Intent(Intent.ACTION_VIEW, contactUri);
                    }else{
                        intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                        intent.setType(Contacts.CONTENT_ITEM_TYPE);
                        intent.putExtra(Insert.PHONE, mNumber);
                    }
                    mContext.startActivity(intent);
                }
            });

            removeFromCallLog.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mAlertDialog.dismiss();
                    AlertDialog.Builder ad = new AlertDialog.Builder(mContext);
                    /* SPRD: Bug300621@{ */
                        ad.setIconAttribute(android.R.attr.alertDialogIcon);
                    /* @}  */
                    ad.setTitle(R.string.delete_calls);
                    ad.setMessage(R.string.delete_selected_calls);
                    ad.setPositiveButton(mContext.getString(android.R.string.ok),
                            new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            removeFromCallLog();
                        }
                    }).setNegativeButton(mContext.getString(android.R.string.cancel),
                            new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {}
                    });
                    ad.show();
                }
            });

            mAlertDialog.show();
            return true;
        }
    };

    private Intent getIntent(String mNumber, int phoneId){
        Uri uri;
        if (PhoneNumberUtils.isUriNumber(mNumber)) {
            uri = Uri.fromParts("sip", mNumber, null);
        } else {
            uri = Uri.fromParts("tel", mNumber, null);
        }
        Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, uri);
        intent.putExtra(TelephonyIntents.NOT_NEED_SIMCARD_SELECTION, true);
        intent.putExtra(TelephonyIntents.EXTRA_PHONE_ID, phoneId);
        return intent;
    }

    /** Remove from call log. */
    private void removeFromCallLog() {
        final StringBuilder callIds = new StringBuilder();
        if(callIds == null){
            return;
        }
        for (Uri callUri : getCallLogEntryUris()) {
            if (callIds.length() != 0) {
                callIds.append(",");
            }
            callIds.append(ContentUris.parseId(callUri));
        }
        mAsyncTaskExecutor.submit(REMOVE_FROM_CALL_LOG_AND_FINISH,
                new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(Void... params) {
                mContext.getContentResolver().delete(
                        Calls.CONTENT_URI_WITH_VOICEMAIL,
                        Calls._ID + " IN (" + callIds + ")", null);
                return null;
            }
        });
    }

    /**
     * Returns the list of URIs to show.
     * <p>
     * There are two ways the URIs can be provided to the activity: as the data on the intent, or as
     * a list of ids in the call log added as an extra on the URI.
     * <p>
     * If both are available, the data on the intent takes precedence.
     */
    private Uri[] getCallLogEntryUris() {
        Intent tempIntent = intentProvider.getIntent(mContext);
        if(tempIntent == null){
            tempIntent = mIntent;
        }
        Uri uri = tempIntent.getData();
        if (uri != null) {
            // If there is a data on the intent, it takes precedence over the extra.
            return new Uri[]{ uri };
        }
        long[] ids = tempIntent.getLongArrayExtra(CallDetailActivity.EXTRA_CALL_LOG_IDS);
        Uri[] uris = new Uri[ids.length];
        for (int index = 0; index < ids.length; ++index) {
            uris[index] = ContentUris.withAppendedId(Calls.CONTENT_URI_WITH_VOICEMAIL, ids[index]);
        }
        return uris;
    }

    /** Listener for the secondary action in the list, either call or play. */
    private final View.OnClickListener mSecondaryActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            IntentProvider intentProvider = (IntentProvider) view.getTag();
            if (intentProvider != null) {
                String mNumber = PhoneNumberUtils.getNumberFromIntent(intentProvider.getIntent(mContext),mContext);
                boolean canCall = mPhoneNumberHelper.canPlaceCallsTo(mNumber);
                if(canCall){
                    mContext.startActivity(intentProvider.getIntent(mContext));
                }else{
                    return;
                }
            }
        }
    };
    /* @} */

    @Override
    public boolean onPreDraw() {
        // We only wanted to listen for the first draw (and this is it).
        unregisterPreDrawListener();

        // Only schedule a thread-creation message if the thread hasn't been
        // created yet. This is purely an optimization, to queue fewer messages.
        if (mCallerIdThread == null) {
            mHandler.sendEmptyMessageDelayed(START_THREAD, START_PROCESSING_REQUESTS_DELAY_MILLIS);
        }

        return true;
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REDRAW:
                    notifyDataSetChanged();
                    break;
                case START_THREAD:
                    startRequestProcessing();
                    break;
            }
        }
    };

    public CallLogAdapter(Context context, CallFetcher callFetcher,
            ContactInfoHelper contactInfoHelper, boolean useCallAsPrimaryAction,
            boolean isCallLog) {
        super(context);

        mContext = context;
        mCallFetcher = callFetcher;
        mContactInfoHelper = contactInfoHelper;
        mUseCallAsPrimaryAction = useCallAsPrimaryAction;
        mIsCallLog = isCallLog;

        mContactInfoCache = ExpirableCache.create(CONTACT_INFO_CACHE_SIZE);
        mRequests = new LinkedList<ContactInfoRequest>();

        Resources resources = mContext.getResources();
        CallTypeHelper callTypeHelper = new CallTypeHelper(resources);

        mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
        mPhoneNumberHelper = new PhoneNumberHelper(resources);
        PhoneCallDetailsHelper phoneCallDetailsHelper = new PhoneCallDetailsHelper(
                resources, callTypeHelper, new PhoneNumberUtilsWrapper());
        mCallLogViewsHelper =
                new CallLogListItemHelper(context,
                        phoneCallDetailsHelper, mPhoneNumberHelper, resources);
        mCallLogGroupBuilder = new CallLogGroupBuilder(this);
        // SPRD: Init mAsyncTaskExecutor
        mAsyncTaskExecutor = AsyncTaskExecutors.createThreadPoolExecutor();

        mResolver = mContext.getApplicationContext().getContentResolver();
    }

    /**
     * Requery on background thread when {@link Cursor} changes.
     */
    @Override
    protected void onContentChanged() {
        mCallFetcher.fetchCalls();
    }

    public void setLoading(boolean loading) {
        mLoading = loading;
    }

    @Override
    public boolean isEmpty() {
        if (mLoading) {
            // We don't want the empty state to show when loading.
            return false;
        } else {
            return super.isEmpty();
        }
    }

    /**
     * Starts a background thread to process contact-lookup requests, unless one
     * has already been started.
     */
    private synchronized void startRequestProcessing() {
        // For unit-testing.
        if (mRequestProcessingDisabled) return;

        // Idempotence... if a thread is already started, don't start another.
        if (mCallerIdThread != null) return;

        mCallerIdThread = new QueryThread();
        mCallerIdThread.setPriority(Thread.MIN_PRIORITY);
        mCallerIdThread.start();
    }

    /**
     * Stops the background thread that processes updates and cancels any
     * pending requests to start it.
     */
    public synchronized void stopRequestProcessing() {
        // Remove any pending requests to start the processing thread.
        mHandler.removeMessages(START_THREAD);
        if (mCallerIdThread != null) {
            // Stop the thread; we are finished with it.
            mCallerIdThread.stopProcessing();
            mCallerIdThread.interrupt();
            mCallerIdThread = null;
        }
    }

    /**
     * Stop receiving onPreDraw() notifications.
     */
    private void unregisterPreDrawListener() {
        if (mViewTreeObserver != null && mViewTreeObserver.isAlive()) {
            mViewTreeObserver.removeOnPreDrawListener(this);
        }
        mViewTreeObserver = null;
    }

    public void invalidateCache() {
        mContactInfoCache.expireAll();

        // Restart the request-processing thread after the next draw.
        stopRequestProcessing();
        unregisterPreDrawListener();
    }

    /**
     * Enqueues a request to look up the contact details for the given phone number.
     * <p>
     * It also provides the current contact info stored in the call log for this number.
     * <p>
     * If the {@code immediate} parameter is true, it will start immediately the thread that looks
     * up the contact information (if it has not been already started). Otherwise, it will be
     * started with a delay. See {@link #START_PROCESSING_REQUESTS_DELAY_MILLIS}.
     */
    protected void enqueueRequest(String number, String countryIso, ContactInfo callLogInfo,
            boolean immediate) {
        ContactInfoRequest request = new ContactInfoRequest(number, countryIso, callLogInfo);
        synchronized (mRequests) {
            if (!mRequests.contains(request)) {
                mRequests.add(request);
                mRequests.notifyAll();
            }
        }
        if (immediate) startRequestProcessing();
    }

    /**
     * Queries the appropriate content provider for the contact associated with the number.
     * <p>
     * Upon completion it also updates the cache in the call log, if it is different from
     * {@code callLogInfo}.
     * <p>
     * The number might be either a SIP address or a phone number.
     * <p>
     * It returns true if it updated the content of the cache and we should therefore tell the
     * view to update its content.
     */
    private boolean queryContactInfo(String number, String countryIso, ContactInfo callLogInfo) {
        final ContactInfo info = mContactInfoHelper.lookupNumber(number, countryIso);

        if (info == null) {
            // The lookup failed, just return without requesting to update the view.
            return false;
        }

        // Check the existing entry in the cache: only if it has changed we should update the
        // view.
        NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
        ContactInfo existingInfo = mContactInfoCache.getPossiblyExpired(numberCountryIso);

        final boolean isRemoteSource = info.sourceType != 0;

        // Don't force redraw if existing info in the cache is equal to {@link ContactInfo#EMPTY}
        // to avoid updating the data set for every new row that is scrolled into view.
        // see (https://googleplex-android-review.git.corp.google.com/#/c/166680/)

        // Exception: Photo uris for contacts from remote sources are not cached in the call log
        // cache, so we have to force a redraw for these contacts regardless.
        boolean updated = (existingInfo != ContactInfo.EMPTY || isRemoteSource) &&
                !info.equals(existingInfo);

        // Store the data in the cache so that the UI thread can use to display it. Store it
        // even if it has not changed so that it is marked as not expired.
        mContactInfoCache.put(numberCountryIso, info);
        // Update the call log even if the cache it is up-to-date: it is possible that the cache
        // contains the value from a different call log entry.
        updateCallLogContactInfoCache(number, countryIso, info, callLogInfo);
        return updated;
    }

    /*
     * Handles requests for contact name and number type.
     */
    private class QueryThread extends Thread {
        private volatile boolean mDone = false;

        public QueryThread() {
            super("CallLogAdapter.QueryThread");
        }

        public void stopProcessing() {
            mDone = true;
        }

        @Override
        public void run() {
            boolean needRedraw = false;
            while (true) {
                // Check if thread is finished, and if so return immediately.
                if (mDone) return;

                // Obtain next request, if any is available.
                // Keep synchronized section small.
                ContactInfoRequest req = null;
                synchronized (mRequests) {
                    if (!mRequests.isEmpty()) {
                        req = mRequests.removeFirst();
                    }
                }

                if (req != null) {
                    // Process the request. If the lookup succeeds, schedule a
                    // redraw.
                    try {
                        needRedraw |= queryContactInfo(req.number, req.countryIso, req.callLogInfo);
                    }catch (SQLiteFullException e) {
                        e.printStackTrace();
                    }
                } else {
                    // Throttle redraw rate by only sending them when there are
                    // more requests.
                    if (needRedraw) {
                        needRedraw = false;
                        mHandler.sendEmptyMessage(REDRAW);
                    }

                    // Wait until another request is available, or until this
                    // thread is no longer needed (as indicated by being
                    // interrupted).
                    try {
                        synchronized (mRequests) {
                            mRequests.wait(1000);
                        }
                    } catch (InterruptedException ie) {
                        // Ignore, and attempt to continue processing requests.
                    }
                }
            }
        }
    }

    @Override
    protected void addGroups(Cursor cursor) {
        mCallLogGroupBuilder.addGroups(cursor);
    }

    @Override
    protected View newStandAloneView(Context context, ViewGroup parent) {
        return newChildView(context, parent);
    }

    @Override
    protected View newGroupView(Context context, ViewGroup parent) {
        return newChildView(context, parent);
    }

    @Override
    protected View newChildView(Context context, ViewGroup parent) {
        final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view;
        // SPRD: add for Universe UI
        if (SprdUtils.UNIVERSE_UI_SUPPORT) {
            view = inflater.inflate(R.layout.call_log_list_item_sprd, parent, false);
        } else {
            view = inflater.inflate(R.layout.call_log_list_item, parent, false);
        }
        findAndCacheViews(view);
        return view;
    }

    @Override
    protected void bindStandAloneView(View view, Context context, Cursor cursor) {
        bindView(view, cursor, 1);
    }

    @Override
    protected void bindChildView(View view, Context context, Cursor cursor) {
        bindView(view, cursor, 1);
    }

    @Override
    protected void bindGroupView(View view, Context context, Cursor cursor, int groupSize,
            boolean expanded) {
        bindView(view, cursor, groupSize);
    }

    private void findAndCacheViews(View view) {
        // Get the views to bind to.
        CallLogListItemViews views = CallLogListItemViews.fromView(view);
        views.primaryActionView.setOnClickListener(mActionListener);
        views.secondaryActionView.setOnClickListener(mActionListener);
        /* SPRD: add for Universe UI @ { */
        views.primaryActionView.setOnLongClickListener(mPrimaryActionLongClickListener);
        /* @} */
        view.setTag(views);
    }

    /**
     * Binds the views in the entry to the data in the call log.
     *
     * @param view the view corresponding to this entry
     * @param c the cursor pointing to the entry in the call log
     * @param count the number of entries in the current item, greater than 1 if it is a group
     */
    private void bindView(View view, Cursor c, int count) {
        final CallLogListItemViews views = (CallLogListItemViews) view.getTag();

        // Default case: an item in the call log.
        views.primaryActionView.setVisibility(View.VISIBLE);
        views.listHeaderTextView.setVisibility(View.GONE);

        final String number = c.getString(CallLogQuery.NUMBER);
        final int numberPresentation = c.getInt(CallLogQuery.NUMBER_PRESENTATION);
        final long date = c.getLong(CallLogQuery.DATE);
        final long duration = c.getLong(CallLogQuery.DURATION);
        final int callType = c.getInt(CallLogQuery.CALL_TYPE);
        final String countryIso = c.getString(CallLogQuery.COUNTRY_ISO);

        final ContactInfo cachedContactInfo = getContactInfoFromCallLog(c);
        final int videocallflag = c.getInt(CallLogQuery.VIDEO_CALL_FLAG);// SPRD: add for Universe UI

        /* SPRD: modify for Universe UI */
        if(!SprdUtils.UNIVERSE_UI_SUPPORT){
            if (!mUseCallAsPrimaryAction) {
                // Sets the primary action to open call detail page.
                views.primaryActionView.setTag(
                        IntentProvider.getCallDetailIntentProvider(
                                getCursor(), c.getPosition(), c.getLong(CallLogQuery.ID), count));
            } else if (PhoneNumberUtilsWrapper.canPlaceCallsTo(number, numberPresentation)) {
                // Sets the primary action to call the number.
                views.primaryActionView.setTag(IntentProvider.getReturnCallIntentProvider(number));
            } else {
                views.primaryActionView.setTag(null);
            }
        }
        /* @} */


        // Store away the voicemail information so we can play it directly.
        if (callType == Calls.VOICEMAIL_TYPE) {
            String voicemailUri = c.getString(CallLogQuery.VOICEMAIL_URI);
            final long rowId = c.getLong(CallLogQuery.ID);
            views.secondaryActionView.setTag(
                    IntentProvider.getPlayVoicemailIntentProvider(rowId, voicemailUri));
        } else if (!TextUtils.isEmpty(number)) {
            // Store away the number so we can call it directly if you click on the call icon.
            /* SPRD: add for VT @ Bug 252220 { */
            if(1 == videocallflag){
                views.secondaryActionView.setTag(
                        IntentProvider.getReturnCallIntentProvider(number,true));
            }else{
                /* @} */
                views.secondaryActionView.setTag(
                        IntentProvider.getReturnCallIntentProvider(number));
            }

        } else {
            // No action enabled.
            views.secondaryActionView.setTag(null);
        }

        // Lookup contacts with this number
        NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
        ExpirableCache.CachedValue<ContactInfo> cachedInfo =
                mContactInfoCache.getCachedValue(numberCountryIso);
        ContactInfo info = cachedInfo == null ? null : cachedInfo.getValue();
        if (!PhoneNumberUtilsWrapper.canPlaceCallsTo(number, numberPresentation)
                || new PhoneNumberUtilsWrapper().isVoicemailNumber(number)) {
            // If this is a number that cannot be dialed, there is no point in looking up a contact
            // for it.
            info = ContactInfo.EMPTY;
        } else if (cachedInfo == null) {
            mContactInfoCache.put(numberCountryIso, ContactInfo.EMPTY);
            // Use the cached contact info from the call log.
            info = cachedContactInfo;
            // The db request should happen on a non-UI thread.
            // Request the contact details immediately since they are currently missing.
            enqueueRequest(number, countryIso, cachedContactInfo, true);
            // We will format the phone number when we make the background request.
        } else {
            if (cachedInfo.isExpired()) {
                // The contact info is no longer up to date, we should request it. However, we
                // do not need to request them immediately.
                enqueueRequest(number, countryIso, cachedContactInfo, false);
            } else  if (!callLogInfoMatches(cachedContactInfo, info)) {
                // The call log information does not match the one we have, look it up again.
                // We could simply update the call log directly, but that needs to be done in a
                // background thread, so it is easier to simply request a new lookup, which will, as
                // a side-effect, update the call log.
                enqueueRequest(number, countryIso, cachedContactInfo, false);
            }

            if (info == ContactInfo.EMPTY) {
                // Use the cached contact info from the call log.
                info = cachedContactInfo;
            }
        }

        final Uri lookupUri = info.lookupUri;
        final String name = info.name;
        final int ntype = info.type;
        final String label = info.label;
        final long photoId = info.photoId;
        final Uri photoUri = info.photoUri;
        CharSequence formattedNumber = info.formattedNumber;
        final int[] callTypes = getCallTypes(c, count);
        final String geocode = c.getString(CallLogQuery.GEOCODED_LOCATION);
        final PhoneCallDetails details;
        /* SPRD: add for Universe UI @ { */
        final int phoneId = c.getInt(CallLogQuery.PHONE_ID);
        final String iccId = c.getString(CallLogQuery.ICC_ID);
        /* @} */

        //SPRD:shouln't load date when mListFling
        if(mListFling){
            mHandler.removeMessages(REDRAW);
            mHandler.sendEmptyMessageDelayed(REDRAW,200);
            if(TextUtils.isEmpty(views.phoneCallDetailsViews.nameView.getText())){
                views.phoneCallDetailsViews.nameView.setText(number);
            }
        } else {
            if(SprdUtils.UNIVERSE_UI_SUPPORT){
                if (!mUseCallAsPrimaryAction) {
                    // Sets the primary action to open call detail page.
                    views.primaryActionView.setTag(
                            IntentProvider.getCallDetailIntentProvider(
                                    getCursor(), c.getPosition(), c.getLong(CallLogQuery.ID), count, photoUri));
                } else if (PhoneNumberUtilsWrapper.canPlaceCallsTo(number, numberPresentation)) {
                    // Sets the primary action to call the number.
                    views.primaryActionView.setTag(IntentProvider.getReturnCallIntentProvider(number));
                } else {
                    views.primaryActionView.setTag(null);
                }
            }
            if (TextUtils.isEmpty(name)) {
                if (SprdUtils.UNIVERSE_UI_SUPPORT) {
                    details = new PhoneCallDetails(number, numberPresentation, formattedNumber, countryIso, geocode,
                            callTypes, date, duration, phoneId, videocallflag, iccId);
                } else {
                    details = new PhoneCallDetails(number, numberPresentation, formattedNumber, countryIso, geocode,
                            callTypes, date, duration, phoneId, videocallflag);
                }
            } else {
                if (SprdUtils.UNIVERSE_UI_SUPPORT) {
                    details = new PhoneCallDetails(number, numberPresentation, formattedNumber, countryIso, geocode,
                            callTypes, date, duration, name, ntype, label, lookupUri, null, phoneId,
                            videocallflag, iccId);
                } else {
                    details = new PhoneCallDetails(number, numberPresentation, formattedNumber, countryIso, geocode,
                            callTypes, date, duration, name, ntype, label, lookupUri, null, phoneId,
                            videocallflag);
                }
            }
            final boolean isNew = c.getInt(CallLogQuery.IS_READ) == 0;
            // New items also use the highlighted version of the text.
            final boolean isHighlighted = isNew;
            mCallLogViewsHelper.setPhoneCallDetails(views, details, isHighlighted,
                    mUseCallAsPrimaryAction);
            if (photoUri != null) {
                setPhoto(views, photoUri, lookupUri);
            } else {
                setPhoto(views, photoId, lookupUri);
            }
            views.quickContactView.setContentDescription(views.phoneCallDetailsViews.nameView.
                    getText());

            // Listen for the first draw
            if (mViewTreeObserver == null) {
                mViewTreeObserver = view.getViewTreeObserver();
                mViewTreeObserver.addOnPreDrawListener(this);
            }

            bindBadge(view, info, details, callType);
        }
    }

    protected void bindBadge(View view, ContactInfo info, PhoneCallDetails details, int callType) {

        // Do not show badge in call log.
        if (!mIsCallLog) {
            final int numMissed = getNumMissedCalls(callType);
            final ViewStub stub = (ViewStub) view.findViewById(R.id.link_stub);

            if (shouldShowBadge(numMissed, info, details)) {
                // Do not process if the data has not changed (optimization since bind view is
                // called multiple times due to contact lookup).
                if (numMissed == mNumMissedCallsShown) {
                    return;
                }

                // stub will be null if it was already inflated.
                if (stub != null) {
                    final View inflated = stub.inflate();
                    inflated.setVisibility(View.VISIBLE);
                    mBadgeContainer = inflated.findViewById(R.id.badge_link_container);
                    mBadgeImageView = (ImageView) inflated.findViewById(R.id.badge_image);
                    mBadgeText = (TextView) inflated.findViewById(R.id.badge_text);
                }

                mBadgeContainer.setOnClickListener(getBadgeClickListener());
                mBadgeImageView.setImageResource(getBadgeImageResId());
                mBadgeText.setText(getBadgeText(numMissed));

                mNumMissedCallsShown = numMissed;
            } else {
                // Hide badge if it was previously shown.
                if (stub == null) {
                    final View container = view.findViewById(R.id.badge_container);
                    if (container != null) {
                        container.setVisibility(View.GONE);
                    }
                }
            }
        }
    }

    public void setMissedCalls(Cursor data) {
        final int missed;
        if (data == null) {
            missed = 0;
        } else {
            missed = data.getCount();
        }
        // Only need to update if the number of calls changed.
        if (missed != mNumMissedCalls) {
            mNumMissedCalls = missed;
            notifyDataSetChanged();
        }
    }

    protected View.OnClickListener getBadgeClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent intent = new Intent(mContext, CallLogActivity.class);
                mContext.startActivity(intent);
            }
        };
    }

    /**
     * Get the resource id for the image to be shown for the badge.
     */
    protected int getBadgeImageResId() {
        return R.drawable.ic_call_log_blue;
    }

    /**
     * Get the text to be shown for the badge.
     *
     * @param numMissed The number of missed calls.
     */
    protected String getBadgeText(int numMissed) {
        return mContext.getResources().getString(R.string.num_missed_calls, numMissed);
    }

    /**
     * Whether to show the badge.
     *
     * @param numMissedCalls The number of missed calls.
     * @param info The contact info.
     * @param details The call detail.
     * @return {@literal true} if badge should be shown.  {@literal false} otherwise.
     */
    protected boolean shouldShowBadge(int numMissedCalls, ContactInfo info,
            PhoneCallDetails details) {
        return numMissedCalls > 0;
    }

    private int getNumMissedCalls(int callType) {
        if (callType == Calls.MISSED_TYPE) {
            // Exclude the current missed call shown in the shortcut.
            return mNumMissedCalls - 1;
        }
        return mNumMissedCalls;
    }

    /** Checks whether the contact info from the call log matches the one from the contacts db. */
    private boolean callLogInfoMatches(ContactInfo callLogInfo, ContactInfo info) {
        // The call log only contains a subset of the fields in the contacts db.
        // Only check those.
        return TextUtils.equals(callLogInfo.name, info.name)
                && callLogInfo.type == info.type
                && TextUtils.equals(callLogInfo.label, info.label);
    }

    /** Stores the updated contact info in the call log if it is different from the current one. */
    private void updateCallLogContactInfoCache(String number, String countryIso,
            ContactInfo updatedInfo, ContactInfo callLogInfo) {
        final ContentValues values = new ContentValues();
        boolean needsUpdate = false;

        if (callLogInfo != null) {
            if (!TextUtils.equals(updatedInfo.name, callLogInfo.name)) {
                values.put(Calls.CACHED_NAME, updatedInfo.name);
                needsUpdate = true;
            }

            if (updatedInfo.type != callLogInfo.type) {
                values.put(Calls.CACHED_NUMBER_TYPE, updatedInfo.type);
                needsUpdate = true;
            }

            if (!TextUtils.equals(updatedInfo.label, callLogInfo.label)) {
                values.put(Calls.CACHED_NUMBER_LABEL, updatedInfo.label);
                needsUpdate = true;
            }
            if (!UriUtils.areEqual(updatedInfo.lookupUri, callLogInfo.lookupUri)) {
                values.put(Calls.CACHED_LOOKUP_URI, UriUtils.uriToString(updatedInfo.lookupUri));
                needsUpdate = true;
            }
            if (!TextUtils.equals(updatedInfo.normalizedNumber, callLogInfo.normalizedNumber)) {
                values.put(Calls.CACHED_NORMALIZED_NUMBER, updatedInfo.normalizedNumber);
                needsUpdate = true;
            }
            if (!TextUtils.equals(updatedInfo.number, callLogInfo.number)) {
                values.put(Calls.CACHED_MATCHED_NUMBER, updatedInfo.number);
                needsUpdate = true;
            }
            if (updatedInfo.photoId != callLogInfo.photoId) {
                values.put(Calls.CACHED_PHOTO_ID, updatedInfo.photoId);
                needsUpdate = true;
            }
            if (!TextUtils.equals(updatedInfo.formattedNumber, callLogInfo.formattedNumber)) {
                values.put(Calls.CACHED_FORMATTED_NUMBER, updatedInfo.formattedNumber);
                needsUpdate = true;
            }
        } else {
            // No previous values, store all of them.
            values.put(Calls.CACHED_NAME, updatedInfo.name);
            values.put(Calls.CACHED_NUMBER_TYPE, updatedInfo.type);
            values.put(Calls.CACHED_NUMBER_LABEL, updatedInfo.label);
            values.put(Calls.CACHED_LOOKUP_URI, UriUtils.uriToString(updatedInfo.lookupUri));
            values.put(Calls.CACHED_MATCHED_NUMBER, updatedInfo.number);
            values.put(Calls.CACHED_NORMALIZED_NUMBER, updatedInfo.normalizedNumber);
            values.put(Calls.CACHED_PHOTO_ID, updatedInfo.photoId);
            values.put(Calls.CACHED_FORMATTED_NUMBER, updatedInfo.formattedNumber);
            needsUpdate = true;
        }

        if (!needsUpdate) return;

        if (countryIso == null) {
            mResolver.update(Calls.CONTENT_URI_WITH_VOICEMAIL, values,
                    Calls.NUMBER + " = ? AND " + Calls.COUNTRY_ISO + " IS NULL",
                    new String[]{ number });
        } else {
            mResolver.update(Calls.CONTENT_URI_WITH_VOICEMAIL, values,
                    Calls.NUMBER + " = ? AND " + Calls.COUNTRY_ISO + " = ?",
                    new String[]{ number, countryIso });
        }
    }

    /** Returns the contact information as stored in the call log. */
    private ContactInfo getContactInfoFromCallLog(Cursor c) {
        ContactInfo info = new ContactInfo();
        info.lookupUri = UriUtils.parseUriOrNull(c.getString(CallLogQuery.CACHED_LOOKUP_URI));
        info.name = c.getString(CallLogQuery.CACHED_NAME);
        info.type = c.getInt(CallLogQuery.CACHED_NUMBER_TYPE);
        info.label = c.getString(CallLogQuery.CACHED_NUMBER_LABEL);
        String matchedNumber = c.getString(CallLogQuery.CACHED_MATCHED_NUMBER);
        info.number = matchedNumber == null ? c.getString(CallLogQuery.NUMBER) : matchedNumber;
        info.normalizedNumber = c.getString(CallLogQuery.CACHED_NORMALIZED_NUMBER);
        info.photoId = c.getLong(CallLogQuery.CACHED_PHOTO_ID);
        info.photoUri = null;  // We do not cache the photo URI.
        info.formattedNumber = c.getString(CallLogQuery.CACHED_FORMATTED_NUMBER);
        return info;
    }

    /**
     * Returns the call types for the given number of items in the cursor.
     * <p>
     * It uses the next {@code count} rows in the cursor to extract the types.
     * <p>
     * It position in the cursor is unchanged by this function.
     */
    private int[] getCallTypes(Cursor cursor, int count) {
        int position = cursor.getPosition();
        int[] callTypes = new int[count];
        for (int index = 0; index < count; ++index) {
            callTypes[index] = cursor.getInt(CallLogQuery.CALL_TYPE);
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return callTypes;
    }

    private void setPhoto(CallLogListItemViews views, long photoId, Uri contactUri) {
        views.quickContactView.assignContactUri(contactUri);
        mContactPhotoManager.loadThumbnail(views.quickContactView, photoId, false /* darkTheme */);
    }

    private void setPhoto(CallLogListItemViews views, Uri photoUri, Uri contactUri) {
        views.quickContactView.assignContactUri(contactUri);
        mContactPhotoManager.loadDirectoryPhoto(views.quickContactView, photoUri,
                false /* darkTheme */);
    }


    /**
     * Sets whether processing of requests for contact details should be enabled.
     * <p>
     * This method should be called in tests to disable such processing of requests when not
     * needed.
     */
    @VisibleForTesting
    void disableRequestProcessingForTest() {
        mRequestProcessingDisabled = true;
    }

    @VisibleForTesting
    void injectContactInfoForTest(String number, String countryIso, ContactInfo contactInfo) {
        NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
        mContactInfoCache.put(numberCountryIso, contactInfo);
    }

    @Override
    public void addGroup(int cursorPosition, int size, boolean expanded) {
        super.addGroup(cursorPosition, size, expanded);
    }

    /*
     * Get the number from the Contacts, if available, since sometimes
     * the number provided by caller id may not be formatted properly
     * depending on the carrier (roaming) in use at the time of the
     * incoming call.
     * Logic : If the caller-id number starts with a "+", use it
     *         Else if the number in the contacts starts with a "+", use that one
     *         Else if the number in the contacts is longer, use that one
     */
    public String getBetterNumberFromContacts(String number, String countryIso) {
        String matchingNumber = null;
        // Look in the cache first. If it's not found then query the Phones db
        NumberWithCountryIso numberCountryIso = new NumberWithCountryIso(number, countryIso);
        ContactInfo ci = mContactInfoCache.getPossiblyExpired(numberCountryIso);
        if (ci != null && ci != ContactInfo.EMPTY) {
            matchingNumber = ci.number;
        } else {
            try {
                Cursor phonesCursor = mContext.getContentResolver().query(
                        Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, number),
                        PhoneQuery._PROJECTION, null, null, null);
                if (phonesCursor != null) {
                    if (phonesCursor.moveToFirst()) {
                        matchingNumber = phonesCursor.getString(PhoneQuery.MATCHED_NUMBER);
                    }
                    phonesCursor.close();
                }
            } catch (Exception e) {
                // Use the number from the call log
            }
        }
        if (!TextUtils.isEmpty(matchingNumber) &&
                (matchingNumber.startsWith("+")
                        || matchingNumber.length() > number.length())) {
            number = matchingNumber;
        }
        return number;
    }

    /* SPRD: Add for Universe UI @{ */
    public void setSimMapInvalidate(){
        if(mCallLogViewsHelper != null){
            mCallLogViewsHelper.setSimMapInvalidate();
        }
    }

    private boolean mListFling = false;
    public void setListScrolling(int status){
        if(status == OnScrollListener.SCROLL_STATE_FLING){
            mListFling = true;
        } else {
            mListFling = false;
        }
    }
    /* @} */
}
