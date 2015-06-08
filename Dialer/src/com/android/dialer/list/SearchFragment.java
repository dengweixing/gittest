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
package com.android.dialer.list;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.Toast;

import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.list.PhoneNumberPickerFragment;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.dialpad.DialpadFragment;
import com.android.dialer.list.OnListFragmentScrolledListener;
import com.sprd.dialer.SprdUtils;

public class SearchFragment extends PhoneNumberPickerFragment {

    private OnListFragmentScrolledListener mActivityScrollListener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        setQuickContactEnabled(true);
        setDarkTheme(false);
        setPhotoPosition(ContactListItemView.getDefaultPhotoPosition(true /* opposite */));
        setUseCallableUri(true);

        try {
            mActivityScrollListener = (OnListFragmentScrolledListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnListFragmentScrolledListener");
        }
        /* SPRD: [bug335812] remove UUI condition @ {*/
        this.mIsDisplayInDialer = true;
        mContext = activity;
        /* @} */
    }

    @Override
    public void onStart() {
        super.onStart();
        if (isSearchMode()) {
            getAdapter().setHasHeader(0, false);
        }
        getListView().setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                mActivityScrollListener.onListFragmentScrollStateChange(scrollState);
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                    int totalItemCount) {
            }
        });
    }

    @Override
    protected void setSearchMode(boolean flag) {
        super.setSearchMode(flag);
        // This hides the "All contacts with phone numbers" header in the search fragment
        final ContactEntryListAdapter adapter = getAdapter();
        if (adapter != null) {
            adapter.setHasHeader(0, false);
        }
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        DialerPhoneNumberListAdapter adapter = new DialerPhoneNumberListAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(super.usesCallableUri());
        return adapter;
    }

    @Override
    protected void onItemClick(int position, long id) {
        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
        final int shortcutType = adapter.getShortcutTypeFromPosition(position);

        if (shortcutType == DialerPhoneNumberListAdapter.SHORTCUT_INVALID) {
            /* SPRD: modify for cmcc case: view contacts details info @ { */
            if(SprdUtils.UNIVERSE_UI_SUPPORT){
                final Cursor item = (Cursor)adapter.getItem(position);
                if(item == null){
                    super.onItemClick(position, id);
                    return;
                }
                final Uri uri = Contacts.getLookupUri(item.getLong(item.getColumnIndex(Phone.CONTACT_ID)),
                        item.getString(item.getColumnIndex(Phone.LOOKUP_KEY)));
                final Intent viewContactIntent = new Intent(Intent.ACTION_VIEW,uri);
                startActivityWithErrorToast(viewContactIntent);
            } else {
                super.onItemClick(position, id);
            }
            /* @} */
        } else if (shortcutType == DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CALL) {
            final OnPhoneNumberPickerActionListener listener =
                    getOnPhoneNumberPickerListener();
            if (listener != null) {
                listener.onCallNumberDirectly(getQueryString());
            }
        } else if (shortcutType == DialerPhoneNumberListAdapter.SHORTCUT_ADD_NUMBER_TO_CONTACTS) {
            final String number = adapter.getFormattedQueryString();
            final Intent intent = DialtactsActivity.getAddNumberToContactIntent(number);
            startActivityWithErrorToast(intent);
        }
    }

    private void startActivityWithErrorToast(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast toast = Toast.makeText(getActivity(), R.string.add_contact_not_available,
                    Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    /* SPRD: add to resolve bug 270439 @{ */
    public final static int DATA_CHANGED = 200;
    private Context mContext;
    private final ContentObserver mContactsObserver = new CustomContentObserver();

    private class CustomContentObserver extends ContentObserver {
        public CustomContentObserver() {
            super(mHandler);
        }
        @Override
        public void onChange(boolean selfChange) {
            mHandler.removeMessages(DATA_CHANGED);
            mHandler.sendEmptyMessageDelayed(DATA_CHANGED, DATA_CHANGED);
        }
    }

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            switch(msg.what){
                case DATA_CHANGED:
                    reloadData();
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if(mContext != null){
            mContext.getContentResolver().registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI, true, mContactsObserver);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(mContext != null){
            mContext.getContentResolver().unregisterContentObserver(mContactsObserver);
        }
    }
    /* @} */

    /* SPRD: add to resolve bug 275881 @{ */
    @Override
    protected void startLoading() {
        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
        adapter.setLoadFinished(false);
        super.startLoading();
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
        super.onLoadFinished(loader, data);
        adapter.setLoadFinished(true);
    }
    /* @} */
}
