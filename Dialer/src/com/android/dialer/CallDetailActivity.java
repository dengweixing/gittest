/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.VoicemailContract.Voicemails;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ClipboardUtils;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.ContactLoader;
import com.android.contacts.common.util.UriUtils;
import com.android.dialer.BackScrollManager.ScrollableHeader;
import com.android.dialer.calllog.CallDetailHistoryAdapter;
import com.android.dialer.calllog.CallLogAdapter;
import com.android.dialer.calllog.CallTypeHelper;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.calllog.PhoneNumberHelper;
import com.android.dialer.calllog.PhoneNumberUtilsWrapper;
import com.android.dialer.util.AsyncTaskExecutor;
import com.android.dialer.util.AsyncTaskExecutors;
import com.android.dialer.voicemail.VoicemailPlaybackFragment;
import com.android.dialer.voicemail.VoicemailStatusHelper;
import com.android.dialer.voicemail.VoicemailStatusHelper.StatusMessage;
import com.android.dialer.voicemail.VoicemailStatusHelperImpl;
import com.android.dialer.R;
import com.sprd.dialer.PreloadUtils;
import com.sprd.dialer.SprdUtils;

import java.util.HashMap;
import java.util.List;

/**
 * Displays the details of a specific call log entry.
 * <p>
 * This activity can be either started with the URI of a single call log entry, or with the
 * {@link #EXTRA_CALL_LOG_IDS} extra to specify a group of call log entries.
 */
public class CallDetailActivity extends Activity implements ProximitySensorAware {
    private static final String TAG = "CallDetail";

    private static final int LOADER_ID = 0;
    private static final String BUNDLE_CONTACT_URI_EXTRA = "contact_uri_extra";

    private static final char LEFT_TO_RIGHT_EMBEDDING = '\u202A';
    private static final char POP_DIRECTIONAL_FORMATTING = '\u202C';

    /** The time to wait before enabling the blank the screen due to the proximity sensor. */
    private static final long PROXIMITY_BLANK_DELAY_MILLIS = 100;
    /** The time to wait before disabling the blank the screen due to the proximity sensor. */
    private static final long PROXIMITY_UNBLANK_DELAY_MILLIS = 500;

    /** The enumeration of {@link AsyncTask} objects used in this class. */
    public enum Tasks {
        MARK_VOICEMAIL_READ,
        DELETE_VOICEMAIL_AND_FINISH,
        REMOVE_FROM_CALL_LOG_AND_FINISH,
        UPDATE_PHONE_CALL_DETAILS,
    }

    /** A long array extra containing ids of call log entries to display. */
    public static final String EXTRA_CALL_LOG_IDS = "EXTRA_CALL_LOG_IDS";
    /** If we are started with a voicemail, we'll find the uri to play with this extra. */
    public static final String EXTRA_VOICEMAIL_URI = "EXTRA_VOICEMAIL_URI";
    /** If we should immediately start playback of the voicemail, this extra will be set to true. */
    public static final String EXTRA_VOICEMAIL_START_PLAYBACK = "EXTRA_VOICEMAIL_START_PLAYBACK";
    /** If the activity was triggered from a notification. */
    public static final String EXTRA_FROM_NOTIFICATION = "EXTRA_FROM_NOTIFICATION";

    private CallTypeHelper mCallTypeHelper;
    private PhoneNumberHelper mPhoneNumberHelper;
    private PhoneCallDetailsHelper mPhoneCallDetailsHelper;
    private TextView mHeaderTextView;
    private View mHeaderOverlayView;
    private ImageView mMainActionView;
    private ImageButton mMainActionPushLayerView;
    private ImageView mContactBackgroundView;
    private AsyncTaskExecutor mAsyncTaskExecutor;
    private ContactInfoHelper mContactInfoHelper;

    private String mNumber = null;
    private String mDefaultCountryIso;

    /* package */ LayoutInflater mInflater;
    /* package */ Resources mResources;
    /** Helper to load contact photos. */
    private ContactPhotoManager mContactPhotoManager;
    /** Helper to make async queries to content resolver. */
    private CallDetailActivityQueryHandler mAsyncQueryHandler;
    /** Helper to get voicemail status messages. */
    private VoicemailStatusHelper mVoicemailStatusHelper;
    // Views related to voicemail status message.
    private View mStatusMessageView;
    private TextView mStatusMessageText;
    private TextView mStatusMessageAction;

    /** Whether we should show "edit number before call" in the options menu. */
    private boolean mHasEditNumberBeforeCallOption;
    /** Whether we should show "trash" in the options menu. */
    private boolean mHasTrashOption;
    /** Whether we should show "remove from call log" in the options menu. */
    private boolean mHasRemoveFromCallLogOption;

    private ProximitySensorManager mProximitySensorManager;
    private final ProximitySensorListener mProximitySensorListener = new ProximitySensorListener();
    private HashMap<String, ContactInfo> mContactInfoCache = new HashMap<String, ContactInfo>();

    /**
     * The action mode used when the phone number is selected.  This will be non-null only when the
     * phone number is selected.
     */
    private ActionMode mPhoneNumberActionMode;

    private CharSequence mPhoneNumberLabelToCopy;
    private CharSequence mPhoneNumberToCopy;

    /* SPRD: add for Universe UI @ { */
    private boolean mSupportVt = false;
    private TextView mHeaderNumberTypeTextView;
    private boolean mHasSavedInContacts;
    private boolean mIsVoicemailNumber = false;
    /* @} */

    /* SPRD: modify for 347064 @ { */
    private boolean mNeedUpdate = true;
    /* @} */

    /** Listener to changes in the proximity sensor state. */
    private class ProximitySensorListener implements ProximitySensorManager.Listener {
        /** Used to show a blank view and hide the action bar. */
        private final Runnable mBlankRunnable = new Runnable() {
            @Override
            public void run() {
                View blankView = findViewById(R.id.blank);
                blankView.setVisibility(View.VISIBLE);
                getActionBar().hide();
            }
        };
        /** Used to remove the blank view and show the action bar. */
        private final Runnable mUnblankRunnable = new Runnable() {
            @Override
            public void run() {
                View blankView = findViewById(R.id.blank);
                blankView.setVisibility(View.GONE);
                getActionBar().show();
            }
        };

        @Override
        public synchronized void onNear() {
            clearPendingRequests();
            postDelayed(mBlankRunnable, PROXIMITY_BLANK_DELAY_MILLIS);
        }

        @Override
        public synchronized void onFar() {
            clearPendingRequests();
            postDelayed(mUnblankRunnable, PROXIMITY_UNBLANK_DELAY_MILLIS);
        }

        /** Removed any delayed requests that may be pending. */
        public synchronized void clearPendingRequests() {
            View blankView = findViewById(R.id.blank);
            blankView.removeCallbacks(mBlankRunnable);
            blankView.removeCallbacks(mUnblankRunnable);
        }

        /** Post a {@link Runnable} with a delay on the main thread. */
        private synchronized void postDelayed(Runnable runnable, long delayMillis) {
            // Post these instead of executing immediately so that:
            // - They are guaranteed to be executed on the main thread.
            // - If the sensor values changes rapidly for some time, the UI will not be
            //   updated immediately.
            View blankView = findViewById(R.id.blank);
            blankView.postDelayed(runnable, delayMillis);
        }
    }

    static final String[] CALL_LOG_PROJECTION = new String[] {
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION,
        CallLog.Calls.NUMBER,
        CallLog.Calls.TYPE,
        CallLog.Calls.COUNTRY_ISO,
        CallLog.Calls.GEOCODED_LOCATION,
        CallLog.Calls.NUMBER_PRESENTATION,
        /* SPRD: add for Universe UI @ { */
        CallLog.Calls.VIDEO_CALL_FLAG,
        CallLog.Calls.PHONE_ID,
        CallLog.Calls.ICC_ID
        /* @} */
    };

    static final int DATE_COLUMN_INDEX = 0;
    static final int DURATION_COLUMN_INDEX = 1;
    static final int NUMBER_COLUMN_INDEX = 2;
    static final int CALL_TYPE_COLUMN_INDEX = 3;
    static final int COUNTRY_ISO_COLUMN_INDEX = 4;
    static final int GEOCODED_LOCATION_COLUMN_INDEX = 5;
    static final int NUMBER_PRESENTATION_COLUMN_INDEX = 6;
    /* SPRD: add for Universe UI @ { */
    static final int VIDEO_CALL_FLAG_COLUMN_INDEX = 7;
    static final int PHONE_ID = 8;
    static final int ICC_ID = 9;


    private final View.OnClickListener mVtActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            startActivity(((ViewEntry) view.getTag()).vtIntent);
        }
    };
    /* @} */

    private final View.OnClickListener mPrimaryActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (finishPhoneNumerSelectedActionModeIfShown()) {
                return;
            }
            startActivity(((ViewEntry) view.getTag()).primaryIntent);
        }
    };

    private final View.OnClickListener mSecondaryActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (finishPhoneNumerSelectedActionModeIfShown()) {
                return;
            }
            startActivity(((ViewEntry) view.getTag()).secondaryIntent);
        }
    };

    private final View.OnLongClickListener mPrimaryLongClickListener =
            new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            if (finishPhoneNumerSelectedActionModeIfShown()) {
                return true;
            }
            startPhoneNumberSelectedActionMode(v);
            return true;
        }
    };

    private final LoaderCallbacks<Contact> mLoaderCallbacks = new LoaderCallbacks<Contact>() {
        @Override
        public void onLoaderReset(Loader<Contact> loader) {
        }

        @Override
        public void onLoadFinished(Loader<Contact> loader, Contact data) {
            final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.setType(Contacts.CONTENT_ITEM_TYPE);
            if (data.getDisplayNameSource() >= DisplayNameSources.ORGANIZATION) {
                intent.putExtra(Insert.NAME, data.getDisplayName());
            }
            intent.putExtra(Insert.DATA, data.getContentValues());
            bindContactPhotoAction(intent, R.drawable.ic_add_contact_holo_dark,
                    getString(R.string.description_add_contact));
        }

        @Override
        public Loader<Contact> onCreateLoader(int id, Bundle args) {
            final Uri contactUri = args.getParcelable(BUNDLE_CONTACT_URI_EXTRA);
            if (contactUri == null) {
                Log.wtf(TAG, "No contact lookup uri provided.");
            }
            return new ContactLoader(CallDetailActivity.this, contactUri,
                    false /* loadGroupMetaData */, false /* loadInvitableAccountTypes */,
                    false /* postViewNotification */, true /* computeFormattedPhoneNumber */);
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        /* SPRD: add for Universe UI @ { */
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            setContentView(R.layout.call_detail_sprd);
        }else{
            setContentView(R.layout.call_detail);
        }

        mSupportVt = SprdUtils.VT_SUPPORT;
        View convertView = findViewById(R.id.call_and_sms);
        ImageView vtIconImg = (ImageView) convertView.findViewById(R.id.video_call_icon);
        View vtLayout= convertView.findViewById(R.id.vedio_call_new_ui);
        View vtDivider = null;
        if (mSupportVt) {
            vtIconImg.setVisibility(View.VISIBLE);
        } else {
            if(vtLayout != null){
               vtLayout.setVisibility(View.GONE);
            }
            vtIconImg.setVisibility(View.GONE);
        }
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            mHeaderNumberTypeTextView = (TextView) findViewById(R.id.header_text_number_type);
            getActionBar().setDisplayShowHomeEnabled(false);
        } else {
            vtDivider = convertView.findViewById(R.id.call_and_videocall_divider);
            if (mSupportVt) {
                vtDivider.setVisibility(View.VISIBLE);
            } else {
                vtDivider.setVisibility(View.GONE);
            }
        }
        /* @} */
        mAsyncTaskExecutor = AsyncTaskExecutors.createThreadPoolExecutor();
        mInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mResources = getResources();

        mCallTypeHelper = new CallTypeHelper(getResources());
        mPhoneNumberHelper = new PhoneNumberHelper(mResources);
        mPhoneCallDetailsHelper = new PhoneCallDetailsHelper(mResources, mCallTypeHelper,
                new PhoneNumberUtilsWrapper());
        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();
        mAsyncQueryHandler = new CallDetailActivityQueryHandler(this);
        mHeaderTextView = (TextView) findViewById(R.id.header_text);
        mHeaderOverlayView = findViewById(R.id.photo_text_bar);
        mStatusMessageView = findViewById(R.id.voicemail_status);
        mStatusMessageText = (TextView) findViewById(R.id.voicemail_status_message);
        mStatusMessageAction = (TextView) findViewById(R.id.voicemail_status_action);
        if(!SprdUtils.UNIVERSE_UI_SUPPORT){
            mMainActionView = (ImageView) findViewById(R.id.main_action);
        }
        mMainActionPushLayerView = (ImageButton) findViewById(R.id.main_action_push_layer);
        mContactBackgroundView = (ImageView) findViewById(R.id.contact_background);
        mDefaultCountryIso = PreloadUtils.getInstance().getCurrentCountryIso(this); // SPRD: Modify for performance optimization
        mContactPhotoManager = ContactPhotoManager.getInstance(this);
        mProximitySensorManager = new ProximitySensorManager(this, mProximitySensorListener);
        mContactInfoHelper = new ContactInfoHelper(this, mDefaultCountryIso);// SPRD: Modify for performance optimization
        getActionBar().setDisplayHomeAsUpEnabled(true);
        optionallyHandleVoicemail();
        if (getIntent().getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            closeSystemDialogs();
        }
        if(SprdUtils.UNIVERSE_UI_SUPPORT) initLayout();

        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            View smsIcon = convertView.findViewById(R.id.sms_call_new_ui);
            String number = getIntent().getStringExtra(CallLogAdapter.NUMBER);
            if(mPhoneNumberHelper.isVoicemailNumber(number)){
                smsIcon.setVisibility(View.GONE);
            }else{
                smsIcon.setVisibility(View.VISIBLE);
            }
        }
        /* SPRD: modify for 347064 @ { */
        getContentResolver().registerContentObserver(Contacts.CONTENT_URI, true, mObserver);
        /* @} */
    }

    @Override
    public void onResume() {
        super.onResume();
        /* SPRD: modify for 347064 @ { */
        Log.d(TAG, "onResume " + mNeedUpdate);
        if (mNeedUpdate) {
            updateData(getCallLogEntryUris());
            mNeedUpdate = false;
        }
        /* @} */
    }

    /**
     * Handle voicemail playback or hide voicemail ui.
     * <p>
     * If the Intent used to start this Activity contains the suitable extras, then start voicemail
     * playback.  If it doesn't, then hide the voicemail ui.
     */
    private void optionallyHandleVoicemail() {
        View voicemailContainer = findViewById(R.id.voicemail_container);
        if (hasVoicemail()) {
            // Has voicemail: add the voicemail fragment.  Add suitable arguments to set the uri
            // to play and optionally start the playback.
            // Do a query to fetch the voicemail status messages.
            VoicemailPlaybackFragment playbackFragment = new VoicemailPlaybackFragment();
            Bundle fragmentArguments = new Bundle();
            fragmentArguments.putParcelable(EXTRA_VOICEMAIL_URI, getVoicemailUri());
            if (getIntent().getBooleanExtra(EXTRA_VOICEMAIL_START_PLAYBACK, false)) {
                fragmentArguments.putBoolean(EXTRA_VOICEMAIL_START_PLAYBACK, true);
            }
            playbackFragment.setArguments(fragmentArguments);
            voicemailContainer.setVisibility(View.VISIBLE);
            getFragmentManager().beginTransaction()
            .add(R.id.voicemail_container, playbackFragment)
            .commitAllowingStateLoss();
            mAsyncQueryHandler.startVoicemailStatusQuery(getVoicemailUri());
            markVoicemailAsRead(getVoicemailUri());
        } else {
            // No voicemail uri: hide the status view.
            mStatusMessageView.setVisibility(View.GONE);
            voicemailContainer.setVisibility(View.GONE);
        }
    }

    private boolean hasVoicemail() {
        return getVoicemailUri() != null;
    }

    private Uri getVoicemailUri() {
        return getIntent().getParcelableExtra(EXTRA_VOICEMAIL_URI);
    }

    private void markVoicemailAsRead(final Uri voicemailUri) {
        mAsyncTaskExecutor.submit(Tasks.MARK_VOICEMAIL_READ, new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(Void... params) {
                ContentValues values = new ContentValues();
                values.put(Voicemails.IS_READ, true);
                getContentResolver().update(voicemailUri, values,
                        Voicemails.IS_READ + " = 0", null);
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
        Uri uri = getIntent().getData();

        if (uri != null) {
            // If there is a data on the intent, it takes precedence over the extra.
            return new Uri[]{ uri };
        }
        long[] ids = getIntent().getLongArrayExtra(EXTRA_CALL_LOG_IDS);
        Uri[] uris = new Uri[ids.length];
        for (int index = 0; index < ids.length; ++index) {
            uris[index] = ContentUris.withAppendedId(Calls.CONTENT_URI_WITH_VOICEMAIL, ids[index]);
        }
        return uris;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_CALL: {
            // Make sure phone isn't already busy before starting direct call
            TelephonyManager tm = (TelephonyManager)
                    getSystemService(Context.TELEPHONY_SERVICE);
            if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                startActivity(CallUtil.getCallIntent(
                        Uri.fromParts(CallUtil.SCHEME_TEL, mNumber, null)));
                return true;
            }
        }
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Update user interface with details of given call.
     *
     * @param callUris URIs into {@link CallLog.Calls} of the calls to be displayed
     */
    private void updateData(final Uri... callUris) {
        class UpdateContactDetailsTask extends AsyncTask<Void, Void, PhoneCallDetails[]> {
            @Override
            public void onPreExecute(){
            }

            @Override
            public PhoneCallDetails[] doInBackground(Void... params) {
                // TODO: All phone calls correspond to the same person, so we can make a single
                // lookup.
                final int numCalls = callUris.length;
                PhoneCallDetails[] details = new PhoneCallDetails[numCalls];
                try {
                    for (int index = 0; index < numCalls; ++index) {
                        details[index] = getPhoneCallDetailsForUri(callUris[index]);
                    }
                    Log.d(TAG, "get details length: "+details.length);
                    return details;
                } catch (IllegalArgumentException e) {
                    // Something went wrong reading in our primary data.
                    Log.w(TAG, "invalid URI starting call details", e);
                    return null;
                }
            }

            @Override
            public void onPostExecute(PhoneCallDetails[] details) {
                if (details == null ) {
                    // Somewhere went wrong: we're going to bail out and show error to users.
                    Toast.makeText(CallDetailActivity.this, R.string.toast_call_detail_error,
                            Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                // We know that all calls are from the same number and the same contact, so pick the
                // first.
                PhoneCallDetails firstDetails = details[0];
                mNumber = firstDetails.number.toString();
                final int numberPresentation = firstDetails.numberPresentation;
                final Uri contactUri = firstDetails.contactUri;
                final Uri photoUri = firstDetails.photoUri;

                // Set the details header, based on the first phone call.
                if(SprdUtils.UNIVERSE_UI_SUPPORT){
                    if(TextUtils.isEmpty(mHeaderNumberTypeTextView.getText())){
                        mPhoneCallDetailsHelper.setCallDetailsHeaderNumberType(mHeaderNumberTypeTextView, firstDetails);
                    }
                    if(TextUtils.isEmpty(mHeaderTextView.getText())){
                        mPhoneCallDetailsHelper.setCallDetailsHeader(mHeaderTextView, firstDetails);
                    }
                } else {
                    mPhoneCallDetailsHelper.setCallDetailsHeader(mHeaderTextView, firstDetails);
                }

                // Cache the details about the phone number.
                final boolean canPlaceCallsTo =
                        PhoneNumberUtilsWrapper.canPlaceCallsTo(mNumber, numberPresentation);
                final PhoneNumberUtilsWrapper phoneUtils = new PhoneNumberUtilsWrapper();
                final boolean isVoicemailNumber = phoneUtils.isVoicemailNumber(mNumber);
                final boolean isSipNumber = phoneUtils.isSipNumber(mNumber);
                /* SPRD: add for Universe UI @ { */
                final Uri numberCallUri = mPhoneNumberHelper.getCallUri(mNumber);
                final boolean canPlaceVideoCallsTo = mPhoneNumberHelper.canPlaceCallsTo(mNumber);
                /* @} */

                // Let user view contact details if they exist, otherwise add option to create new
                // contact from this number.
                final Intent mainActionIntent;
                final int mainActionIcon;
                final String mainActionDescription;

                Log.d(TAG, "firstDetails: " + firstDetails.number + "    " + firstDetails.name);
                final CharSequence nameOrNumber;
                if (!TextUtils.isEmpty(firstDetails.name)) {
                    nameOrNumber = firstDetails.name;
                    // SPRD: add for Universe UI
                    if(SprdUtils.UNIVERSE_UI_SUPPORT){
                        mHasSavedInContacts = false;
                    }
                } else {
                    nameOrNumber = firstDetails.number;
                    // SPRD: add for Universe UI
                    if(SprdUtils.UNIVERSE_UI_SUPPORT){
                        mHasSavedInContacts = true;
                    }
                }

                boolean skipBind = false;

                if (contactUri != null && !UriUtils.isEncodedContactUri(contactUri)) {
                    mainActionIntent = new Intent(Intent.ACTION_VIEW, contactUri);
                    // This will launch People's detail contact screen, so we probably want to
                    // treat it as a separate People task.

                    // SPRD: Remove this code for fix  Bug 146542
                    //                    mainActionIntent.setFlags(
                    //                            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    mainActionIcon = R.drawable.ic_contacts_holo_dark;
                    mainActionDescription =
                            getString(R.string.description_view_contact, nameOrNumber);
                } else if (UriUtils.isEncodedContactUri(contactUri)) {
                    final Bundle bundle = new Bundle(1);
                    bundle.putParcelable(BUNDLE_CONTACT_URI_EXTRA, contactUri);
                    /* SPRD: add for bug267666 @ { */
                    /*
                     * SPRD: modify
                     * @orig getLoaderManager().initLoader(LOADER_ID, bundle, mLoaderCallbacks);
                     */

                    getLoaderManager().restartLoader(LOADER_ID, bundle, mLoaderCallbacks);
                    /* @} */
                    mainActionIntent = null;
                    mainActionIcon = R.drawable.ic_add_contact_holo_dark;
                    mainActionDescription = getString(R.string.description_add_contact);
                    skipBind = true;
                } else if (isVoicemailNumber) {
                    mainActionIntent = null;
                    mainActionIcon = 0;
                    mainActionDescription = null;
                    if(SprdUtils.UNIVERSE_UI_SUPPORT){
                        mIsVoicemailNumber = true;
                    }
                } else if (isSipNumber) {
                    // TODO: This item is currently disabled for SIP addresses, because
                    // the Insert.PHONE extra only works correctly for PSTN numbers.
                    //
                    // To fix this for SIP addresses, we need to:
                    // - define ContactsContract.Intents.Insert.SIP_ADDRESS, and use it here if
                    //   the current number is a SIP address
                    // - update the contacts UI code to handle Insert.SIP_ADDRESS by
                    //   updating the SipAddress field
                    // and then we can remove the "!isSipNumber" check above.
                    mainActionIntent = null;
                    mainActionIcon = 0;
                    mainActionDescription = null;
                } else if (canPlaceCallsTo) {
                    mainActionIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                    mainActionIntent.setType(Contacts.CONTENT_ITEM_TYPE);
                    mainActionIntent.putExtra(Insert.PHONE, mNumber);
                    mainActionIcon = R.drawable.ic_add_contact_holo_dark;
                    mainActionDescription = getString(R.string.description_add_contact);
                } else {
                    // If we cannot call the number, when we probably cannot add it as a contact
                    // either. This is usually the case of private, unknown, or payphone numbers.
                    mainActionIntent = null;
                    mainActionIcon = 0;
                    mainActionDescription = null;
                }

                if (mainActionIntent == null) {
                    // SPRD: add for Universe UI
                    if(!SprdUtils.UNIVERSE_UI_SUPPORT){
                        mMainActionView.setVisibility(View.INVISIBLE);
                        mHeaderTextView.setVisibility(View.INVISIBLE);
                    }
                    mMainActionPushLayerView.setVisibility(View.GONE);
                    if(SprdUtils.UNIVERSE_UI_SUPPORT){
                        mHeaderNumberTypeTextView.setVisibility(View.INVISIBLE);
                    } else {
                        mHeaderOverlayView.setVisibility(View.INVISIBLE);
                    }
                } else {
                    // SPRD: add for Universe UI
                    if(!SprdUtils.UNIVERSE_UI_SUPPORT){
                        mMainActionView.setVisibility(View.VISIBLE);
                        mMainActionView.setImageResource(mainActionIcon);
                    }
                    mMainActionPushLayerView.setVisibility(View.VISIBLE);
                    mMainActionPushLayerView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startActivity(mainActionIntent);
                        }
                    });
                    mMainActionPushLayerView.setContentDescription(mainActionDescription);
                    mHeaderTextView.setVisibility(View.VISIBLE);
                    // SPRD: add for Universe UI
                    if(SprdUtils.UNIVERSE_UI_SUPPORT){
                        mHeaderNumberTypeTextView.setVisibility(View.VISIBLE);
                    }
                    mHeaderOverlayView.setVisibility(View.VISIBLE);
                }

                if (!skipBind) {
                    bindContactPhotoAction(mainActionIntent, mainActionIcon,
                            mainActionDescription);
                }

                // This action allows to call the number that places the call.
                if (canPlaceCallsTo) {
                    final CharSequence displayNumber =
                            mPhoneNumberHelper.getDisplayNumber(
                                    firstDetails.number,
                                    firstDetails.numberPresentation,
                                    firstDetails.formattedNumber);

                    ViewEntry entry = new ViewEntry(
                            getString(R.string.menu_callNumber,
                                    forceLeftToRight(displayNumber)),
                                    CallUtil.getCallIntent(mNumber),
                                    getString(R.string.description_call, nameOrNumber));

                    // Only show a label if the number is shown and it is not a SIP address.
                    if (!TextUtils.isEmpty(firstDetails.name)
                            && !TextUtils.isEmpty(firstDetails.number)
                            && !PhoneNumberUtils.isUriNumber(firstDetails.number.toString())) {
                        entry.label = Phone.getTypeLabel(mResources, firstDetails.numberType,
                                firstDetails.numberLabel);
                    }

                    // The secondary action allows to send an SMS to the number that placed the
                    // call.
                    if (phoneUtils.canSendSmsTo(mNumber, numberPresentation)) {
                        entry.setSecondaryAction(
                                R.drawable.ic_text_holo_light,
                                new Intent(Intent.ACTION_SENDTO,
                                        Uri.fromParts("sms", mNumber, null)),
                                        getString(R.string.description_send_text_message, nameOrNumber));
                    }
                    /* SPRD: add for Universe UI @ { */
                    if(SprdUtils.UNIVERSE_UI_SUPPORT){
                        entry.setFirstAction(new Intent(Intent.ACTION_CALL_PRIVILEGED, numberCallUri),
                                getString(R.string.description_dial_phone_number, nameOrNumber));

                        Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, numberCallUri);
                        intent.putExtra(SprdUtils.IS_IP_DIAL, true);
                        entry.setsecondAction(intent, getString(R.string.description_dial_phone_number, nameOrNumber));

                        if(canPlaceVideoCallsTo){
                            Uri uri;
                            uri = Uri.fromParts("tel", mNumber, null);
                            Intent ipIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED, uri);
                            ipIntent.putExtra("android.phone.extra.IS_VIDEOCALL", true);
                            entry.setThirdAction(ipIntent, getString(R.string.description_dial_phone_number, nameOrNumber));
                        }
                        if (mPhoneNumberHelper.canSendSmsTo(mNumber)) {
                            entry.setFourthAction(new Intent(Intent.ACTION_SENDTO,
                                    Uri.fromParts("sms", mNumber, null)),
                                    getString(R.string.description_send_text_message, nameOrNumber));
                        }
                    }
                    if(mSupportVt){
                        Uri uri = Uri.fromParts("tel", mNumber, null);
                        Intent vtIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED, uri);
                        vtIntent.putExtra("android.phone.extra.IS_VIDEOCALL",
                                true);
                        entry.setVtAction(
                                R.drawable.video_call,
                                vtIntent,
                                getString(
                                        R.string.description_dial_phone_number,
                                        nameOrNumber));
                    }
                    /* @} */

                    configureCallButton(entry);
                    mPhoneNumberToCopy = displayNumber;
                    mPhoneNumberLabelToCopy = entry.label;
                } else {
                    disableCallButton();
                    mPhoneNumberToCopy = null;
                    mPhoneNumberLabelToCopy = null;
                }

                mHasEditNumberBeforeCallOption =
                        canPlaceCallsTo && !isSipNumber && !isVoicemailNumber;
                mHasTrashOption = hasVoicemail();
                mHasRemoveFromCallLogOption = !hasVoicemail();
                invalidateOptionsMenu();

                ListView historyList = (ListView) findViewById(R.id.history);
                historyList.setAdapter(
                        new CallDetailHistoryAdapter(CallDetailActivity.this, mInflater,
                                mCallTypeHelper, details, hasVoicemail(), canPlaceCallsTo,
                                findViewById(R.id.controls)));
                BackScrollManager.bind(
                        new ScrollableHeader() {
                            private View mControls = findViewById(R.id.controls);
                            private View mPhoto = findViewById(R.id.contact_background_sizer);
                            private View mHeader = findViewById(R.id.photo_text_bar);
                            private View mSeparator = findViewById(R.id.separator);

                            @Override
                            public void setOffset(int offset) {
                                mControls.setY(-offset);
                            }

                            @Override
                            public int getMaximumScrollableHeaderOffset() {
                                // We can scroll the photo out, but we should keep the header if
                                // present.
                                if (mHeader.getVisibility() == View.VISIBLE) {
                                    return mPhoto.getHeight() - mHeader.getHeight();
                                } else {
                                    // If the header is not present, we should also scroll out the
                                    // separator line.
                                    return mPhoto.getHeight() + mSeparator.getHeight();
                                }
                            }
                        },
                        historyList);
                loadContactPhotos(photoUri);
                findViewById(R.id.call_detail).setVisibility(View.VISIBLE);
                if (!TextUtils.isEmpty(firstDetails.name)) {
                    getActionBar().setTitle(firstDetails.name);
                }
                /* SPRD: add for bug308900 @ { */
                if(SprdUtils.UNIVERSE_UI_SUPPORT){
                    boolean isNumEmpty = TextUtils.isEmpty(firstDetails.number);
                    boolean isNameEmpty = TextUtils.isEmpty(firstDetails.name);
                    if(!isNumEmpty){
                        mHeaderTextView.setText(firstDetails.number);
                    } else if(!isNameEmpty){
                        mHeaderTextView.setText(firstDetails.name);
                    } else {
                        mHeaderTextView.setText(mResources.getString(R.string.unknown));
                    }
                }
                /* @} */
            }
        }
        mAsyncTaskExecutor.submit(Tasks.UPDATE_PHONE_CALL_DETAILS, new UpdateContactDetailsTask());
    }

    private void bindContactPhotoAction(final Intent actionIntent, int actionIcon,
            String actionDescription) {
        if (actionIntent == null) {
            /* SPRD: add for Universe UI @ { */
            if(!SprdUtils.UNIVERSE_UI_SUPPORT){
                mMainActionView.setVisibility(View.INVISIBLE);
                mHeaderTextView.setVisibility(View.INVISIBLE);
                mHeaderOverlayView.setVisibility(View.INVISIBLE);
            }
            /* @} */
            mMainActionPushLayerView.setVisibility(View.GONE);
        } else {
            /* SPRD: add for Universe UI @ { */
            if(!SprdUtils.UNIVERSE_UI_SUPPORT){
                mMainActionView.setVisibility(View.VISIBLE);
                mMainActionView.setImageResource(actionIcon);
            }
            /* @} */
            mMainActionPushLayerView.setVisibility(View.VISIBLE);
            mMainActionPushLayerView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(actionIntent);
                }
            });
            mMainActionPushLayerView.setContentDescription(actionDescription);
            mHeaderTextView.setVisibility(View.VISIBLE);
            mHeaderOverlayView.setVisibility(View.VISIBLE);
        }
    }

    /** Return the phone call details for a given call log URI. */
    private PhoneCallDetails getPhoneCallDetailsForUri(Uri callUri) {
        ContentResolver resolver = getContentResolver();
        Cursor callCursor = resolver.query(callUri, CALL_LOG_PROJECTION, null, null, null);
        try {
            if (callCursor == null || !callCursor.moveToFirst()) {
                throw new IllegalArgumentException("Cannot find content: " + callUri);
            }

            // Read call log specifics.
            final String number = callCursor.getString(NUMBER_COLUMN_INDEX);
            final int numberPresentation = callCursor.getInt(
                    NUMBER_PRESENTATION_COLUMN_INDEX);
            final long date = callCursor.getLong(DATE_COLUMN_INDEX);
            final long duration = callCursor.getLong(DURATION_COLUMN_INDEX);
            final int callType = callCursor.getInt(CALL_TYPE_COLUMN_INDEX);
            String countryIso = callCursor.getString(COUNTRY_ISO_COLUMN_INDEX);
            final String geocode = callCursor.getString(GEOCODED_LOCATION_COLUMN_INDEX);
            /* SPRD: add for Universe UI @ { */
            int videoCall = callCursor.getInt(VIDEO_CALL_FLAG_COLUMN_INDEX);
            int phoneId = 0;
            if (TelephonyManager.getPhoneCount() >1) {
                phoneId = callCursor.getInt(PHONE_ID);
            }
            String iccId = callCursor.getString(ICC_ID);
            /* @} */

            if (TextUtils.isEmpty(countryIso)) {
                countryIso = mDefaultCountryIso;
            }

            // Formatted phone number.
            final CharSequence formattedNumber;
            // Read contact specifics.
            final CharSequence nameText;
            final int numberType;
            final CharSequence numberLabel;
            final Uri photoUri;
            final Uri lookupUri;
            // If this is not a regular number, there is no point in looking it up in the contacts.
            ContactInfo info =
                    PhoneNumberUtilsWrapper.canPlaceCallsTo(number, numberPresentation)
                    && !new PhoneNumberUtilsWrapper().isVoicemailNumber(number)
                    ? contactInfoGetFromCache(countryIso,number)
                            : null;
                    if (info == null) {
                        formattedNumber = mPhoneNumberHelper.getDisplayNumber(number,
                                numberPresentation, null);
                        nameText = "";
                        numberType = 0;
                        numberLabel = "";
                        photoUri = null;
                        lookupUri = null;
                    } else {
                        formattedNumber = info.formattedNumber;
                        nameText = info.name;
                        numberType = info.type;
                        numberLabel = info.label;
                        photoUri = info.photoUri;
                        lookupUri = info.lookupUri;
                    }
                    /* SPRD: add for Universe UI @ { */
                    if(SprdUtils.UNIVERSE_UI_SUPPORT){
                        return new PhoneCallDetails(number, numberPresentation, formattedNumber, countryIso, geocode,
                                new int[]{ callType }, date, duration, nameText, numberType,
                                numberLabel, lookupUri, photoUri, phoneId, videoCall,iccId);
                    } else {
                        return new PhoneCallDetails(number, numberPresentation, formattedNumber, countryIso, geocode,
                                new int[]{ callType }, date, duration,
                                nameText, numberType, numberLabel, lookupUri, photoUri, phoneId, videoCall);
                    }
                    /* @} */
        } finally {
            if (callCursor != null) {
                callCursor.close();
            }
        }
    }

    /** Load the contact photos and places them in the corresponding views. */
    private void loadContactPhotos(Uri photoUri) {
        /* SPRD: add for Universe UI @ { */
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            if(mPhotoLoaded) return;
            if(null != photoUri){
                mContactPhotoManager.loadPhoto(mContactBackgroundView, photoUri,
                        mContactBackgroundView.getWidth(), true);
            }
        }else{
            mContactPhotoManager.loadPhoto(mContactBackgroundView, photoUri,
                    mContactBackgroundView.getWidth(), false);
        }
        /* @} */
    }

    static final class ViewEntry {
        public final String text;
        public final Intent primaryIntent;
        /** The description for accessibility of the primary action. */
        public final String primaryDescription;

        public CharSequence label = null;
        /** Icon for the secondary action. */
        public int secondaryIcon = 0;
        /** Intent for the secondary action. If not null, an icon must be defined. */
        public Intent secondaryIntent = null;
        /** The description for accessibility of the secondary action. */
        public String secondaryDescription = null;

        /* SPRD: add for Universe UI @ { */
        public Intent vtIntent = null;
        public String vtDescription = null;
        public int vtIcon = 0;

        public Intent firstIntent = null;
        public String firstDescription = null;
        public int firstIcon = 0;

        public Intent secondIntent = null;
        public String secondDescription = null;
        public int secondIcon = 0;

        public Intent thirdIntent = null;
        public String thirdDescription = null;
        public int thirdIcon = 0;

        public Intent fourthIntent = null;
        public String fourthDescription = null;
        public int fourthIcon = 0;

        public void setVtAction(int icon, Intent intent, String description){
            vtIcon = icon;
            vtIntent = intent;
            vtDescription = description;
        }

        public void setFirstAction(Intent intent, String description){
            firstIntent = intent;
            firstDescription = description;

        }

        public void setsecondAction(Intent intent, String description){
            secondIntent = intent;
            secondDescription = description;

        }

        public void setThirdAction(Intent intent, String description){
            thirdIntent = intent;
            thirdDescription = description;

        }

        public void setFourthAction(Intent intent, String description){
            fourthIntent = intent;
            fourthDescription = description;
        }
        /* @} */

        public ViewEntry(String text, Intent intent, String description) {
            this.text = text;
            primaryIntent = intent;
            primaryDescription = description;
        }

        public void setSecondaryAction(int icon, Intent intent, String description) {
            secondaryIcon = icon;
            secondaryIntent = intent;
            secondaryDescription = description;
        }
    }

    /** Disables the call button area, e.g., for private numbers. */
    private void disableCallButton() {
        findViewById(R.id.call_and_sms).setVisibility(View.GONE);
    }

    /** Configures the call button area using the given entry. */
    private void configureCallButton(ViewEntry entry) {
        View convertView = findViewById(R.id.call_and_sms);
        convertView.setVisibility(View.VISIBLE);

        ImageView icon = (ImageView) convertView.findViewById(R.id.call_and_sms_icon);
        /* SPRD: add for Universe UI @ { */
        ImageView vtIconImg=(ImageView) convertView.findViewById(R.id.video_call_icon);
        View divider = convertView.findViewById(R.id.call_and_sms_divider);
        TextView text = (TextView) convertView.findViewById(R.id.call_and_sms_text);

        ImageView smsIcon =null;
        ImageView voiceCallIcon=null;
        ImageView IpCallIcon=null;
        ImageView videoCallIcon=null;
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            smsIcon = (ImageView) convertView.findViewById(R.id.call_and_sms_icon);
            voiceCallIcon = (ImageView) convertView.findViewById(R.id.voice_call_icon);
            IpCallIcon = (ImageView) convertView.findViewById(R.id.ip_call_icon);
            videoCallIcon = (ImageView) convertView.findViewById(R.id.video_call_icon);
        }else{
            View mainAction = convertView.findViewById(R.id.call_and_sms_main_action);
            mainAction.setOnClickListener(mPrimaryActionListener);
            mainAction.setTag(entry);
            mainAction.setContentDescription(entry.primaryDescription);
            mainAction.setOnLongClickListener(mPrimaryLongClickListener);
        }

        if(entry.vtIntent !=null){
            if(!SprdUtils.UNIVERSE_UI_SUPPORT){
                vtIconImg.setImageResource(entry.vtIcon);
            }
            vtIconImg.setOnClickListener(mVtActionListener);
            vtIconImg.setTag(entry);
            vtIconImg.setContentDescription(entry.vtDescription);

        }
        /* @} */

        if (entry.secondaryIntent != null) {
            icon.setOnClickListener(mSecondaryActionListener);
            if(!SprdUtils.UNIVERSE_UI_SUPPORT)
                icon.setImageResource(entry.secondaryIcon);
            icon.setVisibility(View.VISIBLE);
            icon.setTag(entry);
            icon.setContentDescription(entry.secondaryDescription);
            if(!SprdUtils.UNIVERSE_UI_SUPPORT)
                divider.setVisibility(View.VISIBLE);
        } else {
            icon.setVisibility(View.GONE);
            if(!SprdUtils.UNIVERSE_UI_SUPPORT)
                divider.setVisibility(View.GONE);
        }

        /* SPRD: add for Universe UI @ { */
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            if(entry.firstIntent != null){
                voiceCallIcon.setTag(entry);
                voiceCallIcon.setOnClickListener(mFirstActionListener);
            }
            if(entry.secondIntent != null){
                IpCallIcon.setTag(entry);
                IpCallIcon.setImageResource(R.drawable.call_icon_ip_sprd);
                IpCallIcon.setOnClickListener(mSecondActionListener);
            }
            if(entry.thirdIntent != null){
                videoCallIcon.setTag(entry);
                videoCallIcon.setOnClickListener(mThirdActionListener);
            }

            if(entry.fourthIntent != null){
                smsIcon.setTag(entry);
                smsIcon.setOnClickListener(mFourthActionListener);
            }
        }else {
            text.setText(entry.text);
            TextView label = (TextView) convertView.findViewById(R.id.call_and_sms_label);
            if (TextUtils.isEmpty(entry.label)) {
                label.setVisibility(View.GONE);
            } else {
                label.setText(entry.label);
                label.setVisibility(View.VISIBLE);
            }
        }
        /* @} */
    }

    protected void updateVoicemailStatusMessage(Cursor statusCursor) {
        if (statusCursor == null) {
            mStatusMessageView.setVisibility(View.GONE);
            return;
        }
        final StatusMessage message = getStatusMessage(statusCursor);
        if (message == null || !message.showInCallDetails()) {
            mStatusMessageView.setVisibility(View.GONE);
            return;
        }

        mStatusMessageView.setVisibility(View.VISIBLE);
        mStatusMessageText.setText(message.callDetailsMessageId);
        if (message.actionMessageId != -1) {
            mStatusMessageAction.setText(message.actionMessageId);
        }
        if (message.actionUri != null) {
            mStatusMessageAction.setClickable(true);
            mStatusMessageAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(Intent.ACTION_VIEW, message.actionUri));
                }
            });
        } else {
            mStatusMessageAction.setClickable(false);
        }
    }

    private StatusMessage getStatusMessage(Cursor statusCursor) {
        List<StatusMessage> messages = mVoicemailStatusHelper.getStatusMessages(statusCursor);
        if (messages.size() == 0) {
            return null;
        }
        // There can only be a single status message per source package, so num of messages can
        // at most be 1.
        if (messages.size() > 1) {
            Log.w(TAG, String.format("Expected 1, found (%d) num of status messages." +
                    " Will use the first one.", messages.size()));
        }
        return messages.get(0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // SPRD: add for Universe UI
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            getMenuInflater().inflate(R.menu.call_details_options_sprd, menu);
        }else{
            getMenuInflater().inflate(R.menu.call_details_options, menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // This action deletes all elements in the group from the call log.
        // We don't have this action for voicemails, because you can just use the trash button.
        if(!SprdUtils.UNIVERSE_UI_SUPPORT){
            menu.findItem(R.id.menu_remove_from_call_log).setVisible(mHasRemoveFromCallLogOption);
        }
        menu.findItem(R.id.menu_edit_number_before_call).setVisible(mHasEditNumberBeforeCallOption);
        menu.findItem(R.id.menu_trash).setVisible(mHasTrashOption);
        // SPRD: add for Universe UI
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            menu.findItem(R.id.menu_delete).setVisible(true);
            menu.findItem(R.id.menu_add_to_contacts).setVisible(mHasSavedInContacts && !mIsVoicemailNumber  && (!TextUtils.isEmpty(mNumber)));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    public void onMenuRemoveFromCallLog(MenuItem menuItem) {
        final StringBuilder callIds = new StringBuilder();
        for (Uri callUri : getCallLogEntryUris()) {
            if (callIds.length() != 0) {
                callIds.append(",");
            }
            callIds.append(ContentUris.parseId(callUri));
        }
        mAsyncTaskExecutor.submit(Tasks.REMOVE_FROM_CALL_LOG_AND_FINISH,
                new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(Void... params) {
                getContentResolver().delete(Calls.CONTENT_URI_WITH_VOICEMAIL,
                        Calls._ID + " IN (" + callIds + ")", null);
                return null;
            }

            @Override
            public void onPostExecute(Void result) {
                finish();
            }
        });
    }

    public void onMenuEditNumberBeforeCall(MenuItem menuItem) {
        startActivity(new Intent(Intent.ACTION_DIAL, CallUtil.getCallUri(mNumber)));
    }

    public void onMenuTrashVoicemail(MenuItem menuItem) {
        final Uri voicemailUri = getVoicemailUri();
        mAsyncTaskExecutor.submit(Tasks.DELETE_VOICEMAIL_AND_FINISH,
                new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(Void... params) {
                getContentResolver().delete(voicemailUri, null, null);
                return null;
            }
            @Override
            public void onPostExecute(Void result) {
                finish();
            }
        });
    }

    /** Invoked when the user presses the home button in the action bar. */
    private void onHomeSelected() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Calls.CONTENT_URI);
        // This will open the call log even if the detail view has been opened directly.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onPause() {
        // Immediately stop the proximity sensor.
        disableProximitySensor(false);
        mProximitySensorListener.clearPendingRequests();
        super.onPause();
    }

    @Override
    public void enableProximitySensor() {
        mProximitySensorManager.enable();
    }

    @Override
    public void disableProximitySensor(boolean waitForFarState) {
        mProximitySensorManager.disable(waitForFarState);
    }

    /**
     * If the phone number is selected, unselect it and return {@code true}.
     * Otherwise, just {@code false}.
     */
    private boolean finishPhoneNumerSelectedActionModeIfShown() {
        if (mPhoneNumberActionMode == null) return false;
        mPhoneNumberActionMode.finish();
        return true;
    }

    private void startPhoneNumberSelectedActionMode(View targetView) {
        mPhoneNumberActionMode = startActionMode(new PhoneNumberActionModeCallback(targetView));
    }

    private void closeSystemDialogs() {
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }

    private class PhoneNumberActionModeCallback implements ActionMode.Callback {
        private final View mTargetView;
        private final Drawable mOriginalViewBackground;

        public PhoneNumberActionModeCallback(View targetView) {
            mTargetView = targetView;

            // Highlight the phone number view.  Remember the old background, and put a new one.
            mOriginalViewBackground = mTargetView.getBackground();
            mTargetView.setBackgroundColor(getResources().getColor(R.color.item_selected));
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            if (TextUtils.isEmpty(mPhoneNumberToCopy)) return false;

            getMenuInflater().inflate(R.menu.call_details_cab, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
            case R.id.copy_phone_number:
                ClipboardUtils.copyText(CallDetailActivity.this, mPhoneNumberLabelToCopy,
                        mPhoneNumberToCopy, true);
                mode.finish(); // Close the CAB
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mPhoneNumberActionMode = null;

            // Restore the view background.
            mTargetView.setBackground(mOriginalViewBackground);
        }
    }

    /** Returns the given text, forced to be left-to-right. */
    private static CharSequence forceLeftToRight(CharSequence text) {
        StringBuilder sb = new StringBuilder();
        sb.append(LEFT_TO_RIGHT_EMBEDDING);
        sb.append(text);
        sb.append(POP_DIRECTIONAL_FORMATTING);
        return sb.toString();
    }

    /**
     * SPRD:
     * add for Universe UI
     * @{
     */
    private final View.OnClickListener mFirstActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            startActivity(((ViewEntry) view.getTag()).firstIntent);
            finish();
        }
    };

    private final View.OnClickListener mSecondActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            startActivity(((ViewEntry) view.getTag()).secondIntent);
            finish();
        }
    };

    private final View.OnClickListener mThirdActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            startActivity(((ViewEntry) view.getTag()).thirdIntent);
            finish();
        }
    };
    private final View.OnClickListener mFourthActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            startActivity(((ViewEntry) view.getTag()).fourthIntent);
            finish();
        }
    };

    public void onMenuAddToContacts(MenuItem menuItem) {
        Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.setType(Contacts.CONTENT_ITEM_TYPE);
        //	intent.putExtra(Insert.PHONE, mPhoneNumberHelper.getCallUri(mNumber));
        intent.putExtra(Insert.PHONE, mNumber);
        startActivity(intent);
    }

    public void onMenuDeleteFromCallLog(MenuItem menuItem) {
        final StringBuilder callIds = new StringBuilder();

        AlertDialog.Builder ad = new AlertDialog.Builder(CallDetailActivity.this);
        ad.setIconAttribute(android.R.attr.alertDialogIcon);
        ad.setTitle(R.string.delete_calls);
        ad.setMessage(R.string.delete_contact_all_calls);
        ad.setPositiveButton(CallDetailActivity.this.getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                for (Uri callUri : getCallLogEntryUris()) {
                    if (callIds.length() != 0) {
                        callIds.append(",");
                    }
                    callIds.append(ContentUris.parseId(callUri));
                }
                mAsyncTaskExecutor.submit(Tasks.REMOVE_FROM_CALL_LOG_AND_FINISH,
                        new AsyncTask<Void, Void, Void>() {
                    @Override
                    public Void doInBackground(Void... params) {
                        getContentResolver().delete(Calls.CONTENT_URI_WITH_VOICEMAIL,
                                Calls._ID + " IN (" + callIds + ")", null);
                        return null;
                    }

                    @Override
                    public void onPostExecute(Void result) {
                        finish();
                    }
                });
            }
        }).setNegativeButton(CallDetailActivity.this.getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {}
        });
        ad.show();
    }

    private boolean mPhotoLoaded;
    private void initLayout(){
        mPhotoLoaded = false;
        mContactBackgroundView.setImageResource(R.drawable.call_detailmodified_photo_sprd_full_sprd);
        Intent intent = getIntent();
        String number = intent.getStringExtra(CallLogAdapter.NUMBER);
        String name = intent.getStringExtra(CallLogAdapter.NAME);
        String photoUri = intent.getStringExtra(CallLogAdapter.PHOTO_URI);
        if(photoUri != null){
            loadContactPhotos(Uri.parse(photoUri));
            mPhotoLoaded = true;
        }

        // HeaderTextView Priority: number, name, unknown
        boolean isNumEmpty = TextUtils.isEmpty(number);
        boolean isNameEmpty = TextUtils.isEmpty(name);
        if(!isNumEmpty){
            mHeaderTextView.setText(number);
        } else if(!isNameEmpty){
            mHeaderTextView.setText(name);
        } else {
            mHeaderTextView.setText(mResources.getString(R.string.unknown));
        }

        // ActitionBar Priority: name, number, unknown
        if(!isNameEmpty){
            getActionBar().setTitle(name);
        } else if (!isNumEmpty) {
            getActionBar().setTitle(number);
        } else {
            getActionBar().setTitle(mResources.getString(R.string.unknown));
        }
    }
    /* @} */

    /**
     * More than the same number phone records, add cache
     * 
     * @return Call records
     */
    private ContactInfo contactInfoGetFromCache(String countryIso, String number) {
        ContactInfo cacheContactInfo = mContactInfoCache.get(countryIso + number);
        if (cacheContactInfo == null) {
            cacheContactInfo = mContactInfoHelper.lookupNumber(number, countryIso);
            if (cacheContactInfo != null) {
                mContactInfoCache.put(countryIso + number, cacheContactInfo);
                Log.d(TAG, "put Contact Info Cache");
            }
        }
        return cacheContactInfo;
    }

    /* SPRD: modify for 347064 @ { */
    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            Log.d(TAG, "Contact detail change");
            mNeedUpdate = true;
            mContactInfoCache.clear();
        }
    };

    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy to unregisterContentObserver");
        getContentResolver().unregisterContentObserver(mObserver);
    };
    /* @} */

}
