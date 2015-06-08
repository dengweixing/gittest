/*
 * SPRD: create
 */

package com.sprd.dialer;

import android.content.Context;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;

import com.android.contacts.common.GeoUtil;
import com.android.dialer.R;

public class PreloadUtils {
    private static PreloadUtils INSTANCE;
    private SpannableString mDialerDialpadHintText;
    private String mCurrentCountryIso;

    private View mDialtactsActivityView;

    public synchronized static PreloadUtils getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PreloadUtils();
        }
        return INSTANCE;
    }

    private PreloadUtils() {

    }

    public void preloadNotTouchLocal(Context context) {
        getCurrentCountryIso(context);
    }

    public void preloadTouchLocal(Context context) {
        Context app = context.getApplicationContext();
        LayoutInflater inflater = LayoutInflater.from(app);
        if (SprdUtils.UNIVERSE_UI_SUPPORT) {
            mDialtactsActivityView = inflater.inflate(R.layout.dialtacts_activity_sprd, null);
        } else {
            mDialtactsActivityView = inflater.inflate(R.layout.dialtacts_activity, null);
        }
        getDialerDialpadHintText(app);
    }

    public SpannableString getDialerDialpadHintText(Context context) {
        mDialerDialpadHintText = new SpannableString(
                context.getString(R.string.dialerDialpadHintText));
        mDialerDialpadHintText.setSpan(new RelativeSizeSpan(0.8f), 0,
                mDialerDialpadHintText.length(), 0);
        return mDialerDialpadHintText;
    }

    public synchronized String getCurrentCountryIso(Context context) {
        if (mCurrentCountryIso == null) {
            mCurrentCountryIso = GeoUtil.getCurrentCountryIso(context);
        }
        return mCurrentCountryIso;
    }

    public View getDialtactsActivityView() {
        return mDialtactsActivityView;
    }

}
