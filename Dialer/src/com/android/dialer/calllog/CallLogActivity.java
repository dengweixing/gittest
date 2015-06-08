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
package com.android.dialer.calllog;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.TypefaceSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogFragment;
import com.sprd.dialer.CallLogSetting;
import com.sprd.dialer.CallLogClearActivity;
import com.sprd.dialer.SprdUtils;

public class CallLogActivity extends Activity {

    private ViewPager mViewPager;
    private ViewPagerAdapter mViewPagerAdapter;
    private CallLogFragment mAllCallsFragment;
    private CallLogFragment mMissedCallsFragment;
    /* SPRD: add other call type fragment @{ */
    private CallLogFragment mOutgoingCallsFragment;
    private CallLogFragment mInComingCallsFragment;
    /* @} */

    private static final int TAB_INDEX_ALL = 0;
    private static final int TAB_INDEX_MISSED = 1;
    private static final int TAB_INDEX_OUTTING = 2;
    private static final int TAB_INDEX_INCOMING = 3;

    private static final int TAB_INDEX_COUNT = 4;

    public class ViewPagerAdapter extends FragmentPagerAdapter {
        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case TAB_INDEX_ALL:
                    mAllCallsFragment = new CallLogFragment(CallLogQueryHandler.CALL_TYPE_ALL);
                    return mAllCallsFragment;
                case TAB_INDEX_MISSED:
                    mMissedCallsFragment = new CallLogFragment(Calls.MISSED_TYPE);
                    return mMissedCallsFragment;
                /* SPRD: add other type @{ */
                case TAB_INDEX_OUTTING:
                    mOutgoingCallsFragment = new CallLogFragment(Calls.OUTGOING_TYPE);
                    return mOutgoingCallsFragment;
                case TAB_INDEX_INCOMING:
                    mInComingCallsFragment = new CallLogFragment(Calls.INCOMING_TYPE);
                    return mInComingCallsFragment;
                /* @} */
            }
            throw new IllegalStateException("No fragment at position " + position);
        }

        @Override
        public int getCount() {
            return TAB_INDEX_COUNT;
        }
    }

    private final TabListener mTabListener = new TabListener() {
        @Override
        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        }

        @Override
        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            if (mViewPager != null && mViewPager.getCurrentItem() != tab.getPosition()) {
                mViewPager.setCurrentItem(tab.getPosition(), true);
            }
        }

        @Override
        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }
    };

    private final OnPageChangeListener mOnPageChangeListener = new OnPageChangeListener() {

        @Override
        public void onPageScrolled(
                int position, float positionOffset, int positionOffsetPixels) {}

        @Override
        public void onPageSelected(int position) {
            final ActionBar actionBar = getActionBar();
            actionBar.selectTab(actionBar.getTabAt(position));
        }

        @Override
        public void onPageScrollStateChanged(int arg0) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /* SPRD: add for I Log  @ { */
        if (Log.isIloggable()) {
            Log.startPerfTracking("DialerPerf : CallLogActivity.display start");
        }
        /* @}*/
        super.onCreate(savedInstanceState);

        setContentView(R.layout.call_log_activity);
        // bug 386481 begin
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            View frame = findViewById(R.id.calllog_frame);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            params.setMargins(0, 0, 0, 0);
            frame.setLayoutParams(params);
        }
        // bug 386481 end

        final ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        
        /* SPRD: add for Universe UI@{ */
        if(SprdUtils.UNIVERSE_UI_SUPPORT){
            if(savedInstanceState == null){
               CallLogSetting.setCallLogShowType(this,CallLogSetting.TYPE_ALL); 
            }
            final String allTitle = getString(R.string.call_log_all_title);
            final String missedTitle = getString(R.string.call_log_missed_title);
            final String outgoingTitle = getString(R.string.call_log_outgoing_title);
            final String incomingTitle = getString(R.string.call_log_incoming_title);
            setCallLogTab(allTitle);
            setCallLogTab(missedTitle);
            setCallLogTab(outgoingTitle);
            setCallLogTab(incomingTitle);
        } else {
            final Tab allTab = actionBar.newTab();
            final String allTitle = getString(R.string.call_log_all_title);
            allTab.setContentDescription(allTitle);
            allTab.setText(allTitle);
            allTab.setTabListener(mTabListener);
            actionBar.addTab(allTab);

            final Tab missedTab = actionBar.newTab();
            final String missedTitle = getString(R.string.call_log_missed_title);
            missedTab.setContentDescription(missedTitle);
            missedTab.setText(missedTitle);
            missedTab.setTabListener(mTabListener);
            actionBar.addTab(missedTab);

            /* SPRD: add outgoing call @{ */
            final Tab outgoingTab = actionBar.newTab();
            final String outgoingTitle = getString(R.string.call_log_outgoing_title);
            outgoingTab.setContentDescription(outgoingTitle);
            outgoingTab.setText(outgoingTitle);
            outgoingTab.setTabListener(mTabListener);
            actionBar.addTab(outgoingTab);
            /* @} */

            /* SPRD: add incoming call @{ */
            final Tab incomingTab = actionBar.newTab();
            final String incomingTitle = getString(R.string.call_log_incoming_title);
            incomingTab.setContentDescription(incomingTitle);
            incomingTab.setText(incomingTitle);
            incomingTab.setTabListener(mTabListener);
            actionBar.addTab(incomingTab);
            /* @} */
        }
        mViewPager = (ViewPager) findViewById(R.id.call_log_pager);
        mViewPagerAdapter = new ViewPagerAdapter(getFragmentManager());
        mViewPager.setAdapter(mViewPagerAdapter);
        mViewPager.setOnPageChangeListener(mOnPageChangeListener);
        //SPRD: modify offset screen to load outgoing and incoming fragment 
        mViewPager.setOffscreenPageLimit(3);
    }

    private void setCallLogTab(String tabText){
        final Tab allTab = getActionBar().newTab();
        LayoutInflater inflater = getLayoutInflater();  
        View view = inflater.inflate(R.layout.call_log_tab_sprd, null);
        TextView tv = (TextView)view.findViewById(R.id.tab_item);
        tv.setText(tabText);
        allTab.setContentDescription(tabText);
        allTab.setCustomView(view);
        allTab.setTabListener(mTabListener);
        getActionBar().addTab(allTab);
    }

    @Override
    public void onResume(){
        super.onResume();
        if (Log.isIloggable()) {
            Log.stopPerfTracking("DialerPerf : CallLogActivity.display end");
        }
    }

    @Override
    public void onNewIntent(Intent newIntent) {
        super.onNewIntent(newIntent);
        setIntent(newIntent);
    }

    @Override
    public void onBackPressed() {
        if (isTaskRoot()) {
            // Instead of stopping, simply push this to the back of the stack.
            // This is only done when running at the top of the stack;
            // otherwise, we have been launched by someone else so need to
            // allow the user to go back to the caller.
            moveTaskToBack(false);
        } else {
            super.onBackPressed();
        }
    }

}
