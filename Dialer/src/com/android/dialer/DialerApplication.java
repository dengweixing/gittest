// Copyright 2013 Google Inc. All Rights Reserved.

package com.android.dialer;

import android.app.Application;
import android.content.Context;
import android.telephony.TelephonyManager;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.extensions.ExtensionsFactory;

public class DialerApplication extends Application {

    /* SPRD: @{ */
    private ContactPhotoManager mContactPhotoManager;
    /* @} */

    @Override
    public void onCreate() {
        super.onCreate();
        ExtensionsFactory.init(getApplicationContext());
    }

    /* SPRD: @{ */
    @Override
    public Object getSystemService(String name) {
        if (ContactPhotoManager.CONTACT_PHOTO_SERVICE.equals(name)) {
            if (mContactPhotoManager == null) {
                mContactPhotoManager = ContactPhotoManager.createContactPhotoManager(this);
                registerComponentCallbacks(mContactPhotoManager);
                mContactPhotoManager.preloadPhotosInBackground();
            }
            return mContactPhotoManager;
        }
        return super.getSystemService(name);
    }

    private static int sSupportVoiceSearch = -1;
    public static boolean isSupportVoiceSearch(Context context) {
        if (sSupportVoiceSearch == -1) {
            TelephonyManager tm = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
            boolean support = tm.isSupportApplication(TelephonyManager.TYPE_VOICE_SEARCH);
            sSupportVoiceSearch = support ? 1 : 0;
        }
        return sSupportVoiceSearch == 1;
    }
    /* @} */
}
