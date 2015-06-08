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
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;

import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.PhoneNumberPickerFragment;
import com.android.dialer.R;
import com.sprd.dialer.SprdUtils;

/**
 * Fragments to show all contacts with phone numbers.
 */
public class AllContactsFragment extends PhoneNumberPickerFragment{

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /* SPRD: add for Universe UI @ { */
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            this.mIsDisplayInDialer = true;
        }
        /* @} */
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Customizes the listview according to the dialer specifics.
        setQuickContactEnabled(true);
        setDarkTheme(false);
        setPhotoPosition(ContactListItemView.getDefaultPhotoPosition(true /* opposite */));
        setUseCallableUri(true);
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        View view;
        /* SPRD: add for Universe UI @ { */
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            view = inflater.inflate(R.layout.show_all_contacts_fragment_sprd, null);
        } else {
            view = inflater.inflate(R.layout.show_all_contacts_fragment, null);
        }
        return view;
    }

    /* SPRD: add for Universe UI @ { */
   /* @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == OnScrollListener.SCROLL_STATE_FLING
            && !getListView().isFastScrollEnabled()) {
            getListView().setFastScrollEnabled(true);
        }
    }*/
    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);
        if (SprdUtils.UNIVERSE_UI_SUPPORT) {
            setSectionHeaderDisplayEnabled(true);
            }
        }
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        super.onLoadFinished(loader, data);
        if(data == null || data.getCount() == 0){
            prepareEmptyView();
        }
    }
/* @} */
}
