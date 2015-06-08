/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentManager.BackStackEntry;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.DatabaseUtils;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.Intents.UI;
import android.speech.RecognizerIntent;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView.OnScrollListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.activity.TransactionSafeActivity;
import com.android.contacts.common.dialog.ClearFrequentsDialog;
import com.android.contacts.common.interactions.ImportExportDialogFragment;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.dialer.calllog.CallLogActivity;
import com.android.dialer.calllog.CallLogNotificationsHelper;
import com.android.dialer.database.DialerDatabaseHelper;
import com.android.dialer.dialpad.DialpadFragment;
import com.android.dialer.dialpad.SmartDialNameMatcher;
import com.android.dialer.dialpad.SmartDialPrefix;
import com.android.dialer.interactions.PhoneNumberInteraction;
import com.android.dialer.list.AllContactsActivity;
import com.android.dialer.list.OnListFragmentScrolledListener;
import com.android.dialer.list.PhoneFavoriteFragment;
import com.android.dialer.list.RegularSearchFragment;
import com.android.dialer.list.SearchFragment;
import com.android.dialer.list.SmartDialSearchFragment;
import com.android.dialerbind.DatabaseHelperManager;
import com.android.internal.telephony.ITelephony;
import com.sprd.dialer.MissedCallUpdateHandler;
import com.sprd.dialer.PreloadUtils;
import com.sprd.dialer.SprdUtils;

import java.lang.ref.SoftReference;
import java.util.ArrayList;

/**
 * The dialer tab's title is 'phone', a more common name (see strings.xml).
 */
public class DialtactsActivity extends TransactionSafeActivity implements View.OnClickListener,
        DialpadFragment.OnDialpadQueryChangedListener, PopupMenu.OnMenuItemClickListener,
        OnListFragmentScrolledListener,
        DialpadFragment.OnDialpadFragmentStartedListener,
        PhoneFavoriteFragment.OnShowAllContactsListener {
    private static final String TAG = "DialtactsActivity";

    public static final boolean DEBUG = true;

    public static final String SHARED_PREFS_NAME = "com.android.dialer_preferences";

    /** Used to open Call Setting */
    private static final String PHONE_PACKAGE = "com.android.phone";
    private static final String CALL_SETTINGS_CLASS_NAME =
            "com.android.phone.CallFeaturesSetting";

    private static final String SIM_CHOOSE_CLASS_NAME =
            "com.sprd.phone.callsetting.MobileSimChoose"; // SPRD: Add for multi sim

    private static final String SIM_CHOOSE_CLASS_NAME_UUI =
            "com.sprd.phone.callsetting.MobileSimChooserActivity"; // SPRD: Add for Universe UI


    /** @see #getCallOrigin() */
    private static final String CALL_ORIGIN_DIALTACTS =
            "com.android.dialer.DialtactsActivity";

    private static final String KEY_IN_REGULAR_SEARCH_UI = "in_regular_search_ui";
    private static final String KEY_IN_DIALPAD_SEARCH_UI = "in_dialpad_search_ui";
    private static final String KEY_SEARCH_QUERY = "search_query";
    private static final String KEY_FIRST_LAUNCH = "first_launch";

    private static final String TAG_DIALPAD_FRAGMENT = "dialpad";
    private static final String TAG_REGULAR_SEARCH_FRAGMENT = "search";
    private static final String TAG_SMARTDIAL_SEARCH_FRAGMENT = "smartdial";
    private static final String TAG_FAVORITES_FRAGMENT = "favorites";

    /**
     * Just for backward compatibility. Should behave as same as {@link Intent#ACTION_DIAL}.
     */
    private static final String ACTION_TOUCH_DIALER = "com.android.phone.action.TOUCH_DIALER";

    private static final int SUBACTIVITY_ACCOUNT_FILTER = 1;

    private static final int ACTIVITY_REQUEST_CODE_VOICE_SEARCH = 1;

    private String mFilterText;

    /**
     * The main fragment displaying the user's favorites and frequent contacts
     */
    private PhoneFavoriteFragment mPhoneFavoriteFragment;

    /**
     * Fragment containing the dialpad that slides into view
     */
    private DialpadFragment mDialpadFragment;

    /**
     * Fragment for searching phone numbers using the alphanumeric keyboard.
     */
    private RegularSearchFragment mRegularSearchFragment;

    /**
     * Fragment for searching phone numbers using the dialpad.
     */
    private SmartDialSearchFragment mSmartDialSearchFragment;

    private View mMenuButton;
    private View mCallHistoryButton;
    private View mDialpadButton;
    private PopupMenu mOverflowMenu;

    // Padding view used to shift the fragments up when the dialpad is shown.
    private View mBottomPaddingView;
    private View mFragmentsFrame;
    private View mActionBar;

    private boolean mInDialpadSearch;
    private boolean mInRegularSearch;
    private boolean mClearSearchOnPause;
    /**
     * True if the dialpad is only temporarily showing
     */
    private boolean mShowDialpad;

    /**
     * True when this activity has been launched for the first time.
     */
    private boolean mFirstLaunch;
    private View mSearchViewContainer;
    private View mSearchViewCloseButton;
    private View mVoiceSearchButton;
    private EditText mSearchView;

    private String mSearchQuery;

    private DialerDatabaseHelper mDialerDatabaseHelper;
    private static final int UPDATE_DATABASE = 100; // SPRD: Add for performance optimization
    private static final int INIT_DATABASE_NANP = 101; // SPRD: Add for performance optimization
    public static final int POST_DELAY = 200; // SPRD: Add for performance optimization 200ms
    private static final int UPDATE_CALLLOG_ISREAD = 102; // SPRD: Add for update missed call
    private MissedCallUpdateHandler mMissedCallUpdateHanlder; // SPRD: Add an async handler to update missed call

    private class OverflowPopupMenu extends PopupMenu {
        public OverflowPopupMenu(Context context, View anchor) {
            super(context, anchor);
        }

        @Override
        public void show() {
            final Menu menu = getMenu();
            final MenuItem clearFrequents = menu.findItem(R.id.menu_clear_frequents);
            clearFrequents.setVisible(mPhoneFavoriteFragment.hasFrequents());
            super.show();
        }
    }

    /**
     * Listener used when one of phone numbers in search UI is selected. This will initiate a
     * phone call using the phone number.
     */
    private final OnPhoneNumberPickerActionListener mPhoneNumberPickerActionListener =
            new OnPhoneNumberPickerActionListener() {
                @Override
                public void onPickPhoneNumberAction(Uri dataUri) {
                    // Specify call-origin so that users will see the previous tab instead of
                    // CallLog screen (search UI will be automatically exited).
                    PhoneNumberInteraction.startInteractionForPhoneCall(
                        DialtactsActivity.this, dataUri, getCallOrigin());
                    mClearSearchOnPause = true;
                }

                @Override
                public void onCallNumberDirectly(String phoneNumber) {
                    Intent intent = CallUtil.getCallIntent(phoneNumber, getCallOrigin());
                    startActivity(intent);
                    mClearSearchOnPause = true;
                }

                @Override
                public void onShortcutIntentCreated(Intent intent) {
                    Log.w(TAG, "Unsupported intent has come (" + intent + "). Ignoring.");
                }

                @Override
                public void onHomeInActionBarSelected() {
                    exitSearchUi();
                }
    };

    /**
     * Listener used to send search queries to the phone search fragment.
     */
    private final TextWatcher mPhoneSearchQueryTextListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                /* SPRD: modify for bug294348
                 * @Orig:final String newText = s.toString();
                 * { */
                String queryText = s.toString();
                if(!TextUtils.isEmpty(s)){
                    queryText = DatabaseUtils.sqlEscapeString(queryText);
                    int invlideCharIndex = queryText.indexOf(0);
                    if(invlideCharIndex != -1){
                        queryText = queryText.replace(queryText.charAt(invlideCharIndex), ' ');
                    }
                }
                final String newText = queryText;
                /* @} */
                if (newText.equals(mSearchQuery)) {
                    // If the query hasn't changed (perhaps due to activity being destroyed
                    // and restored, or user launching the same DIAL intent twice), then there is
                    // no need to do anything here.
                    return;
                }
                mSearchQuery = newText;
                if (DEBUG) {
                    Log.d(TAG, "onTextChange for mSearchView called with new query: " + s);
                }

                /* SPRD: modify for bug come form performance optimization @{ */
                // final boolean dialpadSearch = isDialpadShowing();
                boolean dialpadSearch = isDialpadShowing();
                dialpadSearch = isDigitsFilledByIntent() ? true : dialpadSearch;
                /* @} */

                // Show search result with non-empty text. Show a bare list otherwise.
                if (TextUtils.isEmpty(newText) && getInSearchUi()) {
                    exitSearchUi();
                    mSearchViewCloseButton.setVisibility(View.GONE);
                    /* SPRD: @ { */
                    if(!DialerApplication.isSupportVoiceSearch(getApplicationContext())){
                        mVoiceSearchButton.setVisibility(View.GONE);
                    } else {
                        mVoiceSearchButton.setVisibility(View.VISIBLE);
                    }
                    /* @} */
                    return;
                } else if (!TextUtils.isEmpty(newText)) {
                    final boolean sameSearchMode = (dialpadSearch && mInDialpadSearch) ||
                            (!dialpadSearch && mInRegularSearch);
                    if (!sameSearchMode) {
                        // call enterSearchUi only if we are switching search modes, or entering
                        // search ui for the first time
                        enterSearchUi(dialpadSearch, newText);
                    }

                    if (dialpadSearch && mSmartDialSearchFragment != null) {
                            mSmartDialSearchFragment.setQueryString(newText, false);
                    } else if (mRegularSearchFragment != null) {
                        mRegularSearchFragment.setQueryString(newText, false);
                    }
                    mSearchViewCloseButton.setVisibility(View.VISIBLE);
                    mVoiceSearchButton.setVisibility(View.GONE);
                    /* SPRD: add for I Log  @ { */
                    if (Log.isIloggable()) {
                        Log.startPerfTracking("DialerPerf : DialtactsActivity.contacts match start");
                    }
                    /* @}*/
                    return;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
    };

    private boolean isDialpadShowing() {
        return mDialpadFragment != null && mDialpadFragment.isVisible();
    }

    private boolean isDigitsFilledByIntent(){
        return mDialpadFragment != null && mDialpadFragment.isDigitsFilledByIntent();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /* SPRD: add for I Log  @ { */
        if (Log.isIloggable()) {
            Log.startPerfTracking("DialerPerf : DialtactsActivity.display start");
        }
        /* @}*/
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreat");
        mFirstLaunch = true;

        final Intent intent = getIntent();
        fixIntent(intent);
        /* SPRD: add for performance optimization @{ */
        try {
            View view = PreloadUtils.getInstance().getDialtactsActivityView();
            if(view != null){
                setContentView(view);
            }else{
                tryToSetContentView();
            }
        } catch (Exception e) {
            tryToSetContentView();
        }
        /* @} */
        getActionBar().hide();
        boolean phoneIsInUse = phoneIsInUse();// SPRD: Add for performance optimization
        // Add the favorites fragment, and the dialpad fragment, but only if savedInstanceState
        // is null. Otherwise the fragment manager takes care of recreating these fragments.
        if (savedInstanceState == null) {
            final PhoneFavoriteFragment phoneFavoriteFragment = new PhoneFavoriteFragment();

            final FragmentTransaction ft = getFragmentManager().beginTransaction();
            /*
             * SPRD: modify
             * @orig ft.add(R.id.dialtacts_frame, phoneFavoriteFragment, TAG_FAVORITES_FRAGMENT);
             */
            ft.replace(R.id.dialtacts_frame, phoneFavoriteFragment, TAG_FAVORITES_FRAGMENT);
            /* @} */
            /* SPRD: Add for performance optimization @{ */
            if (phoneIsInUse || isDialIntent(intent)) {
                mDialpadFragment = new DialpadFragment();
                ft.replace(R.id.dialtacts_container, mDialpadFragment, TAG_DIALPAD_FRAGMENT);
                mShowDialpad = true;
            }/* @} */
            ft.commit();
        } else {
            mSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY);
            mInRegularSearch = savedInstanceState.getBoolean(KEY_IN_REGULAR_SEARCH_UI);
            mInDialpadSearch = savedInstanceState.getBoolean(KEY_IN_DIALPAD_SEARCH_UI);
            mFirstLaunch = savedInstanceState.getBoolean(KEY_FIRST_LAUNCH);
            updateFragmentsVisibility(mSearchQuery,false);//SPRD: add for 288411
            /* SPRD: Add for Bug289790 @{ */
            if (mDialpadFragment == null) {
                Log.i(TAG, "onCreate,new DialpadFragment.");
                final FragmentTransaction ft = getFragmentManager().beginTransaction();
                mDialpadFragment = new DialpadFragment();
                ft.replace(R.id.dialtacts_container, mDialpadFragment, TAG_DIALPAD_FRAGMENT);
                hideFragment(ft,mDialpadFragment);
                if (!ft.isEmpty()) {
                    ft.commitAllowingStateLoss();
                    getFragmentManager().executePendingTransactions();
                }
            }
            /* @} */
        }

        mBottomPaddingView = findViewById(R.id.dialtacts_bottom_padding);
        mFragmentsFrame = findViewById(R.id.dialtacts_frame);
        mActionBar = findViewById(R.id.fake_action_bar);
        //sprd: add for 266207
        mFragmentsFrame.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (null != mDialpadFragment && mDialpadFragment.isResumed()){
                    if (mDialpadFragment.isDigitsEmpty()) {
                        hideDialpadFragment(false, true);
                    return true;
                }}
                return false;
            }
        });
      //sprd: add for 266207
        prepareSearchView();
        // SPRD: add for Universe UI
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            prepareUniverseUiViews();
        }

        if (UI.FILTER_CONTACTS_ACTION.equals(intent.getAction())
                && savedInstanceState == null) {
            setupFilterText(intent);
        }

        // SPRD: Create an async handler to update Missed Calls.
        mMissedCallUpdateHanlder = new MissedCallUpdateHandler(
                DialtactsActivity.this.getContentResolver());

        setupFakeActionBarItems();
        mDisplayHandler.removeMessages(INIT_DATABASE_NANP);
        mDisplayHandler.sendEmptyMessageDelayed(INIT_DATABASE_NANP, POST_DELAY); // SPRD: Add for performance optimization
//        mDialerDatabaseHelper = DatabaseHelperManager.getDatabaseHelper(this);
//        SmartDialPrefix.initializeNanpSettings(this);

    }

    @Override
    protected void onResume() {
        super.onResume();
        /* SPRD: modify for performance optimization @ { */
        if (!mShowDialpad) {
            if (mFirstLaunch){
                displayFragment(getIntent());
            }/*else {
                hideDialpadFragment(false, false);
            }*/
        } else if (mShowDialpad) {
            showDialpadFragment(false);
            mShowDialpad = false;
        }
        mFirstLaunch = false;
        mDisplayHandler.removeMessages(UPDATE_DATABASE);
        mDisplayHandler.sendEmptyMessageDelayed(UPDATE_DATABASE, POST_DELAY);
        /* @} */
        /* SPRD: add for I Log  @ { */
        if (Log.isIloggable()) {
            Log.stopPerfTracking("DialerPerf : DialtactsActivity.display end");
        }
        /* @}*/
        /* SPRD: bug 263256@ { */
        if(mSearchView != null && mSearchView.getText().length() > 0){
            mSearchViewCloseButton.setVisibility(View.VISIBLE);
        }
        /* @} */

        /* SPRD: Update missed calls notifications @{ */
        mDisplayHandler.removeMessages(UPDATE_CALLLOG_ISREAD);
        mDisplayHandler.sendEmptyMessageDelayed(UPDATE_CALLLOG_ISREAD, POST_DELAY);
        /* @} */
    }

    @Override
    protected void onPause() {
        if (mClearSearchOnPause) {
            hideDialpadAndSearchUi();
            mClearSearchOnPause = false;
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SEARCH_QUERY, mSearchQuery);
        outState.putBoolean(KEY_IN_REGULAR_SEARCH_UI, mInRegularSearch);
        outState.putBoolean(KEY_IN_DIALPAD_SEARCH_UI, mInDialpadSearch);
        outState.putBoolean(KEY_FIRST_LAUNCH, mFirstLaunch);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        /*if (fragment instanceof DialpadFragment) {
            mDialpadFragment = (DialpadFragment) fragment;
            final FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.hide(mDialpadFragment);
            transaction.commit();
        } else*/
        if (fragment instanceof SmartDialSearchFragment) {
            mSmartDialSearchFragment = (SmartDialSearchFragment) fragment;
            mSmartDialSearchFragment.setOnPhoneNumberPickerActionListener(
                    mPhoneNumberPickerActionListener);
        } else if (fragment instanceof SearchFragment) {
            mRegularSearchFragment = (RegularSearchFragment) fragment;
            mRegularSearchFragment.setOnPhoneNumberPickerActionListener(
                    mPhoneNumberPickerActionListener);
        } else if (fragment instanceof PhoneFavoriteFragment) {
            mPhoneFavoriteFragment = (PhoneFavoriteFragment) fragment;
            mPhoneFavoriteFragment.setListener(mPhoneFavoriteListener);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_import_export:
                // We hard-code the "contactsAreAvailable" argument because doing it properly would
                // involve querying a {@link ProviderStatusLoader}, which we don't want to do right
                // now in Dialtacts for (potential) performance reasons. Compare with how it is
                // done in {@link PeopleActivity}.
                ImportExportDialogFragment.show(getFragmentManager(), true,
                        DialtactsActivity.class);
                return true;
            case R.id.menu_clear_frequents:
                ClearFrequentsDialog.show(getFragmentManager());
                return true;
            case R.id.menu_add_contact:
                try {
                    startActivity(new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI));
                } catch (ActivityNotFoundException e) {
                    Toast toast = Toast.makeText(this,
                            R.string.add_contact_not_available,
                            Toast.LENGTH_SHORT);
                    toast.show();
                }
                return true;
            case R.id.menu_call_settings:
                handleMenuSettings();
                return true;
            case R.id.menu_all_contacts:
                onShowAllContacts();
                return true;
        }
        return false;
    }

    public void handleMenuSettings() {
        openTelephonySetting(this);
    }

    public static void openTelephonySetting(Activity activity) {
        final Intent settingsIntent = getCallSettingsIntent();
        activity.startActivity(settingsIntent);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.overflow_menu: {
                // SPRD: add for Universe UI
                if(SprdUtils.UNIVERSE_UI_SUPPORT){
                    if(mDialpadFragment != null && mDialpadFragment.isVisible()){
                        mDialpadFragment.showDialpadOverflowMenu(view);
                    } else {
                        if(mOverflowMenu == null) InitOverflowPopupMenu();//SPRD:Add to resolve OOM
                        mOverflowMenu.show();
                    }
                } else {
                    if(mOverflowMenu == null) InitOverflowPopupMenu();//SPRD:Add to resolve OOM
                    mOverflowMenu.show();
                }
                break;
            }
            case R.id.dialpad_button:
                // Reset the boolean flag that tracks whether the dialpad was up because
                // we were in call. Regardless of whether it was true before, we want to
                // show the dialpad because the user has explicitly clicked the dialpad
                // button.
//                mInCallDialpadUp = false;
                showDialpadFragment(true);
                break;
            case R.id.call_history_on_dialpad_button:
            case R.id.call_history_button:
                // Use explicit CallLogActivity intent instead of ACTION_VIEW +
                // CONTENT_TYPE, so that we always open our call log from our dialer
                final Intent intent = new Intent(this, CallLogActivity.class);
                startActivity(intent);
                break;
            case R.id.search_close_button:
                // Clear the search field
                if (!TextUtils.isEmpty(mSearchView.getText())) {
                    //SPRD:add to resolve NullPointException
                    if(mDialpadFragment != null){
                        mDialpadFragment.clearDialpad();
                    }
                    mSearchView.setText("");
                }
                break;
            case R.id.voice_search_button:
                final Intent voiceIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                try {
                    startActivityForResult(voiceIntent, ACTIVITY_REQUEST_CODE_VOICE_SEARCH);
                } catch (ActivityNotFoundException exception) {
                    Log.e(TAG, "No Activity found! E: " + exception);
                    Toast.makeText(DialtactsActivity.this,
                            R.string.toast_no_activity_to_handle_intent, Toast.LENGTH_LONG).show();
                }
                break;
            default: {
                Log.wtf(TAG, "Unexpected onClick event from " + view);
                break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ACTIVITY_REQUEST_CODE_VOICE_SEARCH) {
            if (resultCode == RESULT_OK) {
                final ArrayList<String> matches = data.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS);
                if (matches.size() > 0) {
                    final String match = matches.get(0);
                    mSearchView.setText(match);
                } else {
                    Log.e(TAG, "Voice search - nothing heard");
                }
            } else {
                Log.e(TAG, "Voice search failed");
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void showDialpadFragment(boolean animate) {
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        /* SPRD: Add for performance optimization @{ */
        if (mDialpadFragment == null) {
            mDialpadFragment = new DialpadFragment();
            ft.replace(R.id.dialtacts_container, mDialpadFragment, TAG_DIALPAD_FRAGMENT);
        }
        /* @} */
        /*SPRD: Add for 273486 with first launch dialpad not show searchview @{ */
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            showAcitonBar(false);
        }/* SPRD: fix bug 335287 @{ */else {
            hideSearchBar();
        }/* @} */
        /* @} */
        /** SPRD: Remove. do not support translation @{ */
//        mDialpadFragment.setAdjustTranslationForAnimation(animate);
//        if (animate) {
//            ft.setCustomAnimations(R.anim.slide_in, 0);
//        } else {
//            mDialpadFragment.setYFraction(0);
//        }
        /** @} */

        ft.show(mDialpadFragment);
        // SPRD: Modify for monkey exception.
        // A transaction can only be committed with this method prior to its
        // containing activity saving its state. If the commit is attempted after
        // that point, an exception will be thrown. This is because the state
        // after the commit can be lost if the activity needs to be restored from its state.
        // commitAllowingStateLoss() allows the commit to be executed after an activity's state is saved.
        // ft.commit();
        ft.commitAllowingStateLoss();
        /* SPRD: Add for Bug275287 @{ */
        if(mDialpadFragment.getView() != null){
            mDialpadFragment.getView().setVisibility(View.VISIBLE);
        }
        /* @} */
    }

    public void hideDialpadFragment(boolean animate, boolean clearDialpad) {
        if (mDialpadFragment == null) return;
        if (clearDialpad) {
            mDialpadFragment.clearDialpad();
        }
        if (!mDialpadFragment.isVisible()) return;
        mDialpadFragment.setAdjustTranslationForAnimation(animate);
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (animate) {
            ft.setCustomAnimations(0, R.anim.slide_out);
        }
        ft.hide(mDialpadFragment);
        // SPRD: Modify for monkey exception for 293592. @{
        // A transaction can only be committed with this method prior to its
        // containing activity saving its state. If the commit is attempted after
        // that point, an exception will be thrown. This is because the state
        // after the commit can be lost if the activity needs to be restored from its state.
        // commitAllowingStateLoss() allows the commit to be executed after an activity's state is saved.
        //ft.commit();
        ft.commitAllowingStateLoss();
        getFragmentManager().executePendingTransactions();
        /* @} */
    }

    private void prepareSearchView() {
        mSearchViewContainer = findViewById(R.id.search_view_container);
        mSearchViewCloseButton = findViewById(R.id.search_close_button);
        mSearchViewCloseButton.setOnClickListener(this);
        mVoiceSearchButton = findViewById(R.id.voice_search_button);
        mVoiceSearchButton.setOnClickListener(this);
        /* SPRD: @ { */
        if(!DialerApplication.isSupportVoiceSearch(getApplicationContext())){
            mVoiceSearchButton.setVisibility(View.GONE);
        } else {
            mVoiceSearchButton.setVisibility(View.VISIBLE);
        }
        /* @} */
        mSearchView = (EditText) findViewById(R.id.search_view);
        mSearchView.addTextChangedListener(mPhoneSearchQueryTextListener);

        final String hintText = getString(R.string.dialer_hint_find_contact);

        // The following code is used to insert an icon into a CharSequence (copied from
        // SearchView)
        final SpannableStringBuilder ssb = new SpannableStringBuilder("   "); // for the icon
        ssb.append(hintText);
        final Drawable searchIcon = getResources().getDrawable(R.drawable.ic_ab_search);
        final int textSize = (int) (mSearchView.getTextSize() * 1.20);
        searchIcon.setBounds(0, 0, textSize, textSize);
        ssb.setSpan(new ImageSpan(searchIcon), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        mSearchView.setHint(ssb);
    }

    final AnimatorListener mHideListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            mSearchViewContainer.setVisibility(View.GONE);
        }
    };

    private boolean getInSearchUi() {
        return mInDialpadSearch || mInRegularSearch;
    }

    private void setNotInSearchUi() {
        mInDialpadSearch = false;
        mInRegularSearch = false;
    }

    private void hideDialpadAndSearchUi() {
        mSearchView.setText(null);
        hideDialpadFragment(false, true);
    }

    public void hideSearchBar() {
        hideSearchBar(true);
    }

    public void hideSearchBar(boolean shiftView) {
        if (shiftView) {
            mSearchViewContainer.animate().cancel();
            mSearchViewContainer.setAlpha(1);
            mSearchViewContainer.setTranslationY(0);
            mSearchViewContainer.animate().withLayer().alpha(0).translationY(-mSearchView.getHeight())
                    .setDuration(200).setListener(mHideListener);

            mFragmentsFrame.animate().withLayer()
                    .translationY(-mSearchViewContainer.getHeight()).setDuration(200).setListener(
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mBottomPaddingView.setVisibility(View.VISIBLE);
                            mFragmentsFrame.setTranslationY(0);
                            mActionBar.setVisibility(View.INVISIBLE);
                        }
                    });
        } else {
            mSearchViewContainer.setTranslationY(-mSearchView.getHeight());
            mActionBar.setVisibility(View.INVISIBLE);
        }
    }

    public void showSearchBar() {
        mSearchViewContainer.animate().cancel();
        mSearchViewContainer.setAlpha(0);
        mSearchViewContainer.setTranslationY(-mSearchViewContainer.getHeight());
        mSearchViewContainer.animate().withLayer().alpha(1).translationY(0).setDuration(200)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        mSearchViewContainer.setVisibility(View.VISIBLE);
                        mActionBar.setVisibility(View.VISIBLE);
                    }
                });

        mFragmentsFrame.setTranslationY(-mSearchViewContainer.getHeight());
        mFragmentsFrame.animate().withLayer().translationY(0).setDuration(200)
                .setListener(
                        new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(Animator animation) {
                                mBottomPaddingView.setVisibility(View.GONE);
                            }
                        });
    }

    public void setupFakeActionBarItems() {
        mMenuButton = findViewById(R.id.overflow_menu);
        if (mMenuButton != null) {
            mMenuButton.setOnClickListener(this);
            /* SPRD: add for resolve OOM
             * @orig: mOverflowMenu = new OverflowPopupMenu(DialtactsActivity.this, mMenuButton);
             *  @ { */
            InitOverflowPopupMenu();
            /* @} */
            final Menu menu = mOverflowMenu.getMenu();
            mOverflowMenu.inflate(R.menu.dialtacts_options);
            mOverflowMenu.setOnMenuItemClickListener(this);
            mMenuButton.setOnTouchListener(mOverflowMenu.getDragToOpenListener());
            /* SPRD: modify for hide import_export contact @{ */
            final MenuItem importExprotMenuItem = menu.findItem(R.id.menu_import_export);
            importExprotMenuItem.setVisible(false);
            /* @} */
        }

        mCallHistoryButton = findViewById(R.id.call_history_button);
        // mCallHistoryButton.setMinimumWidth(fakeMenuItemWidth);
        mCallHistoryButton.setOnClickListener(this);

        mDialpadButton = findViewById(R.id.dialpad_button);
        // DialpadButton.setMinimumWidth(fakeMenuItemWidth);
        mDialpadButton.setOnClickListener(this);
    }

    public void setupFakeActionBarItemsForDialpadFragment() {
        final View callhistoryButton = findViewById(R.id.call_history_on_dialpad_button);
        callhistoryButton.setOnClickListener(this);
    }

    private void fixIntent(Intent intent) {
        // This should be cleaned up: the call key used to send an Intent
        // that just said to go to the recent calls list.  It now sends this
        // abstract action, but this class hasn't been rewritten to deal with it.
        if (Intent.ACTION_CALL_BUTTON.equals(intent.getAction())) {
            intent.setDataAndType(Calls.CONTENT_URI, Calls.CONTENT_TYPE);
            intent.putExtra("call_key", true);
            setIntent(intent);
        }
    }

    /**
     * Returns true if the intent is due to hitting the green send key (hardware call button:
     * KEYCODE_CALL) while in a call.
     *
     * @param intent the intent that launched this activity
     * @param recentCallsRequest true if the intent is requesting to view recent calls
     * @return true if the intent is due to hitting the green send key while in a call
     */
    private boolean isSendKeyWhileInCall(Intent intent, boolean recentCallsRequest) {
        // If there is a call in progress go to the call screen
        if (recentCallsRequest) {
            final boolean callKey = intent.getBooleanExtra("call_key", false);

            try {
                ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
                if (callKey && phone != null && phone.showCallScreen()) {
                    return true;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to handle send while in call", e);
            }
        }

        return false;
    }

    /**
     * Sets the current tab based on the intent's request type
     *
     * @param intent Intent that contains information about which tab should be selected
     */
    private void displayFragment(Intent intent) {
        // If we got here by hitting send and we're in call forward along to the in-call activity
        boolean recentCallsRequest = Calls.CONTENT_TYPE.equals(intent.resolveType(
            getContentResolver()));
        if (isSendKeyWhileInCall(intent, recentCallsRequest)) {
            finish();
            return;
        }

        if (mDialpadFragment != null) {
            final boolean phoneIsInUse = phoneIsInUse();
            if (phoneIsInUse || isDialIntent(intent)) {
                mDialpadFragment.setStartedFromNewIntent(true);
//                if (phoneIsInUse && !mDialpadFragment.isVisible()) {
//                    // mInCallDialpadUp = true;
//                }
                mShowDialpad = true;// SPRD: Modify for performance optimization
            }
        }
    }

    @Override
    public void onNewIntent(Intent newIntent) {
        Log.d("yaojt", "onNewIntent");
        /* SPRD: Add for performance optimization @{ */
        if (mDialpadFragment == null) {
            final FragmentTransaction ft = getFragmentManager().beginTransaction();
            mDialpadFragment = new DialpadFragment();
            ft.replace(R.id.dialtacts_container, mDialpadFragment, TAG_DIALPAD_FRAGMENT);
            ft.commit();
        }
        /* @} */
        setIntent(newIntent);
        fixIntent(newIntent);
        displayFragment(newIntent);
        final String action = newIntent.getAction();

        invalidateOptionsMenu();
    }

    /** Returns true if the given intent contains a phone number to populate the dialer with */
    private boolean isDialIntent(Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || ACTION_TOUCH_DIALER.equals(action)) {
            return true;
        }
        if (Intent.ACTION_VIEW.equals(action)) {
            final Uri data = intent.getData();
            if (data != null && CallUtil.SCHEME_TEL.equals(data.getScheme())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns an appropriate call origin for this Activity. May return null when no call origin
     * should be used (e.g. when some 3rd party application launched the screen. Call origin is
     * for remembering the tab in which the user made a phone call, so the external app's DIAL
     * request should not be counted.)
     */
    public String getCallOrigin() {
        return !isDialIntent(getIntent()) ? CALL_ORIGIN_DIALTACTS : null;
    }

    /**
     * Retrieves the filter text stored in {@link #setupFilterText(Intent)}.
     * This text originally came from a FILTER_CONTACTS_ACTION intent received
     * by this activity. The stored text will then be cleared after after this
     * method returns.
     *
     * @return The stored filter text
     */
    public String getAndClearFilterText() {
        String filterText = mFilterText;
        mFilterText = null;
        return filterText;
    }

    /**
     * Stores the filter text associated with a FILTER_CONTACTS_ACTION intent.
     * This is so child activities can check if they are supposed to display a filter.
     *
     * @param intent The intent received in {@link #onNewIntent(Intent)}
     */
    private void setupFilterText(Intent intent) {
        // If the intent was relaunched from history, don't apply the filter text.
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            return;
        }
        String filter = intent.getStringExtra(UI.FILTER_TEXT_EXTRA_KEY);
        if (filter != null && filter.length() > 0) {
            mFilterText = filter;
        }
    }

    private final PhoneFavoriteFragment.Listener mPhoneFavoriteListener =
            new PhoneFavoriteFragment.Listener() {
        @Override
        public void onContactSelected(Uri contactUri) {
            PhoneNumberInteraction.startInteractionForPhoneCall(
                        DialtactsActivity.this, contactUri, getCallOrigin());
        }

        @Override
        public void onCallNumberDirectly(String phoneNumber) {
            Intent intent = CallUtil.getCallIntent(phoneNumber, getCallOrigin());
            startActivity(intent);
        }
    };

    /* TODO krelease: This is only relevant for phones that have a hard button search key (i.e.
     * Nexus S). Supporting it is a little more tricky because of the dialpad fragment might
     * be showing when the search key is pressed so there is more state management involved.

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {
        if (mRegularSearchFragment != null && mRegularSearchFragment.isAdded() && !globalSearch) {
            if (mInSearchUi) {
                if (mSearchView.hasFocus()) {
                    showInputMethod(mSearchView.findFocus());
                } else {
                    mSearchView.requestFocus();
                }
            } else {
                enterSearchUi();
            }
        } else {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        }
    }*/

    private void showInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, 0);
        }
    }

    private void hideInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Shows the search fragment
     */
    private void enterSearchUi(boolean smartDialSearch, String query) {
        if (getFragmentManager().isDestroyed()) {
            // Weird race condition where fragment is doing work after the activity is destroyed
            // due to talkback being on (b/10209937). Just return since we can't do any
            // constructive here.
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Entering search UI - smart dial " + smartDialSearch);
        }

        /*SPRD:modify to resolve Bug288411
        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);

        SearchFragment fragment;
        if (mInDialpadSearch) {
            transaction.remove(mSmartDialSearchFragment);
        } else if (mInRegularSearch) {
            transaction.remove(mRegularSearchFragment);
        } else {
            transaction.remove(mPhoneFavoriteFragment);
        }

        final String tag;
        if (smartDialSearch) {
            tag = TAG_SMARTDIAL_SEARCH_FRAGMENT;
        } else {
            tag = TAG_REGULAR_SEARCH_FRAGMENT;
        }
        */
        mInDialpadSearch = smartDialSearch;
        mInRegularSearch = !smartDialSearch;

        /*SPRD:modify to resolve Bug288411
         fragment = (SearchFragment) getFragmentManager().findFragmentByTag(tag);

        if (fragment == null) {
            if (smartDialSearch) {
                fragment = new SmartDialSearchFragment();
            } else {
                fragment = new RegularSearchFragment();
            }
        }

        transaction.replace(R.id.dialtacts_frame, fragment, tag);

        fragment.setQueryString(query, false);
        transaction.commit();
        */
        updateFragmentsVisibility(query, false);//SPRD: add to resolve Bug288411
    }

    /**
     * Hides the search fragment
     */
    private void exitSearchUi() {
        // See related bug in enterSearchUI();
        if (getFragmentManager().isDestroyed()) {
            return;
        }

        // Go all the way back to the favorites fragment, regardless of how many times we
        // transitioned between search fragments
        /*SPRD:modify to resolve Bug288411
        getFragmentManager().popBackStack(0, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        */
        setNotInSearchUi();
        updateFragmentsVisibility(null, false);//SPRD: add to resolve Bug288411
    }

    /** Returns an Intent to launch Call Settings screen */
    public static Intent getCallSettingsIntent() {
        String className = TelephonyManager.isMultiSim() ? SIM_CHOOSE_CLASS_NAME
                : CALL_SETTINGS_CLASS_NAME;
        // SPRD: add for Universe UI
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            className = SIM_CHOOSE_CLASS_NAME_UUI;
        }
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(PHONE_PACKAGE, className);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    @Override
    public void onBackPressed() {
        if (mDialpadFragment != null && mDialpadFragment.isVisible()) {
            hideDialpadFragment(true, false);
        } else if (getInSearchUi()) {
            mSearchView.setText(null);
            //SPRD:add to resolve NullPointException
            if(mDialpadFragment != null){
                mDialpadFragment.clearDialpad();
            }
        } else if (isTaskRoot()) {
            // Instead of stopping, simply push this to the back of the stack.
            // This is only done when running at the top of the stack;
            // otherwise, we have been launched by someone else so need to
            // allow the user to go back to the caller.
            moveTaskToBack(false);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDialpadQueryChanged(String query) {
        final String normalizedQuery = SmartDialNameMatcher.normalizeNumber(query,
                SmartDialNameMatcher.LATIN_SMART_DIAL_MAP);
        /*SPRD:modify to resolve Bug301674*/
        String formatQuery = "";
        if(!TextUtils.isEmpty(query) && query.startsWith("+",0)){
            formatQuery = "+" + normalizedQuery;
        }else{
            formatQuery = normalizedQuery;
        }
        /* @} */
        if (!TextUtils.equals(mSearchView.getText(), formatQuery)) {
            if (DEBUG) {
                Log.d(TAG, "onDialpadQueryChanged - new query: " + query);
            }
            if (mDialpadFragment == null || !mDialpadFragment.isResumed()) {
                // This callback can happen if the dialpad fragment is recreated because of
                // activity destruction. In that case, don't update the search view because
                // that would bring the user back to the search fragment regardless of the
                // previous state of the application. Instead, just return here and let the
                // fragment manager correctly figure out whatever fragment was last displayed.
                return;
            }
            mSearchView.setText(formatQuery);
        }
    }

    @Override
    public void onListFragmentScrollStateChange(int scrollState) {
        if (scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            hideDialpadFragment(true, false);
            hideInputMethod(getCurrentFocus());
        }
    }

    @Override
    public void onDialpadFragmentStarted() {
        setupFakeActionBarItemsForDialpadFragment();
    }

    private boolean phoneIsInUse() {
        final TelephonyManager tm = (TelephonyManager) getSystemService(
                Context.TELEPHONY_SERVICE);
        return tm.getCallState() != TelephonyManager.CALL_STATE_IDLE;
    }

    @Override
    public void onShowAllContacts() {
        final Intent intent = new Intent(this, AllContactsActivity.class);
        startActivity(intent);
    }

    public static Intent getAddNumberToContactIntent(CharSequence text) {
        final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Intents.Insert.PHONE, text);
        intent.setType(Contacts.CONTENT_ITEM_TYPE);
        return intent;
    }

    public static Intent getInsertContactWithNameIntent(CharSequence text) {
        final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
        intent.putExtra(Intents.Insert.NAME, text);
        return intent;
    }

    /**
     * SPRD:
     * add for Universe UI
     * @{
     */
    private TextView mAcitonBarTitile;
    private ImageButton mAllContactsButton;

    public void prepareUniverseUiViews(){
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) mActionBar.getContext().
                getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        Resources resources = mActionBar.getContext().getResources();
        int buttonHeight;
        if(displayMetrics.widthPixels < 500){
            buttonHeight = resources.getDimensionPixelSize(R.dimen.dialpad_button_layout_button_height_sprd);
        } else {
            buttonHeight = resources.getDimensionPixelSize(R.dimen.dialpad_button_layout_button_height_qhd_sprd);
        }
        mActionBar.getLayoutParams().height = buttonHeight;
        mAcitonBarTitile   = (TextView)findViewById(R.id.aciton_bar_tiltle);
        mAcitonBarTitile.setText(R.string.applicationLabel);
        mAllContactsButton = (ImageButton)findViewById(R.id.all_contacts_button);
        mAllContactsButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                onShowAllContacts();
            }
        });

    }

    public void showAcitonBar(boolean show){
        if(show){
            mSearchViewContainer.setVisibility(View.VISIBLE);
            mActionBar.setVisibility(View.VISIBLE);
            mAcitonBarTitile.setVisibility(View.GONE);
        } else {
            mSearchViewContainer.setVisibility(View.INVISIBLE);
            mActionBar.setVisibility(View.INVISIBLE);
            mAcitonBarTitile.setVisibility(View.VISIBLE);
        }
    }

    /*add for bug 255599*/
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (mDialpadFragment != null && mDialpadFragment.isVisible() && !SprdUtils.UNIVERSE_UI_SUPPORT) {
                return mDialpadFragment.onKeyUp(keyCode, event);
            } else if (mMenuButton != null) {
                return mMenuButton.performClick();
            }
        }

       return super.onKeyUp(keyCode, event);
      }
    /*add for bug 255599*/

    /* @}
     */
    /* SPRD: Add for performance optimization @{ */
    private Handler mDisplayHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_DATABASE:
                    mDialerDatabaseHelper.startSmartDialUpdateThread();
                    break;
                case INIT_DATABASE_NANP:
                    mDialerDatabaseHelper = DatabaseHelperManager
                            .getDatabaseHelper(getApplicationContext());
                    SmartDialPrefix.initializeNanpSettings(getApplicationContext());
                    break;
                case UPDATE_CALLLOG_ISREAD:
                    if (mMissedCallUpdateHanlder == null) {
                        mMissedCallUpdateHanlder = new MissedCallUpdateHandler(
                                DialtactsActivity.this.getContentResolver());
                    }
                    mMissedCallUpdateHanlder.markMissedCallsAsRead();
                    CallLogNotificationsHelper.removeMissedCallNotifications();
                    break;
            }
        }
    };
    private void tryToSetContentView() {
        Log.d(TAG, "tryToSetContentView");
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            setContentView(R.layout.dialtacts_activity_sprd);
        } else {
            setContentView(R.layout.dialtacts_activity);
        }
    }
    /* @} */
    /* SPRD: Add to resolve OOM  @{ */
    private SoftReference mOverflowMenuRef;
    private void InitOverflowPopupMenu(){
        if(mMenuButton == null){
            mMenuButton = findViewById(R.id.overflow_menu);
        }
        if (mMenuButton != null) {
            mMenuButton.setOnClickListener(this);
            OverflowPopupMenu overflowMenu = new OverflowPopupMenu(DialtactsActivity.this, mMenuButton);
            mOverflowMenuRef = new SoftReference(overflowMenu);
            mOverflowMenu = (OverflowPopupMenu)mOverflowMenuRef.get();
            overflowMenu = null;
        }
    }
    /* @} */
    /* SPRD: Add to resolve Bug288411@{ */
    private void updateFragmentsVisibility(String queryString, boolean delaySelection) {
        final FragmentManager fragmentManager = getFragmentManager();
        final FragmentTransaction ft = fragmentManager.beginTransaction();

        if (mInDialpadSearch) {
            if(mSmartDialSearchFragment == null){
                mSmartDialSearchFragment = new SmartDialSearchFragment();
                Log.i(TAG,"new SmartDialSearchFragment");
                ft.add(R.id.dialtacts_frame, mSmartDialSearchFragment, TAG_SMARTDIAL_SEARCH_FRAGMENT);
            }

            showFragment(ft, mSmartDialSearchFragment);
            hideFragment(ft, mRegularSearchFragment);
            hideFragment(ft, mPhoneFavoriteFragment);
            mSmartDialSearchFragment.setQueryString(queryString, false);
        } else if (mInRegularSearch) {
            if(mRegularSearchFragment == null){
                mRegularSearchFragment = new RegularSearchFragment();
                Log.i(TAG,"new RegularSearchFragment");
                ft.add(R.id.dialtacts_frame, mRegularSearchFragment, TAG_REGULAR_SEARCH_FRAGMENT);
            }
            showFragment(ft, mRegularSearchFragment);
            hideFragment(ft, mSmartDialSearchFragment);
            hideFragment(ft, mPhoneFavoriteFragment);
            mRegularSearchFragment.setQueryString(queryString, false);
        } else {
            if(mPhoneFavoriteFragment == null){
                mPhoneFavoriteFragment = new PhoneFavoriteFragment();
                Log.i(TAG,"new PhoneFavoriteFragment");
                ft.add(R.id.dialtacts_frame, mPhoneFavoriteFragment, TAG_FAVORITES_FRAGMENT);
            }
            showFragment(ft, mPhoneFavoriteFragment);
            hideFragment(ft, mSmartDialSearchFragment);
            hideFragment(ft, mRegularSearchFragment);
        }
        if (!ft.isEmpty()) {
            ft.commitAllowingStateLoss();
            fragmentManager.executePendingTransactions();
        }
    }
    private void showFragment(FragmentTransaction ft, Fragment f) {
        if ((f != null) && f.isHidden()) ft.show(f);
    }

    private void hideFragment(FragmentTransaction ft, Fragment f) {
        if ((f != null) && !f.isHidden()) ft.hide(f);
    }
    /* @} */
}
