/*
 * SRPD: create
 */
package com.sprd.dialer;

import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Debug;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.android.contacts.common.CallUtil;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;

public class FastDialUtils extends DialogFragment {
    private static final String TAG = "FastDialUtils";
    private static final boolean DBG = Debug.isDebug();

    private static final String PHONE_PACKAGE = "com.android.phone";
    private static final String SHARED_PREFERENCES_NAME = "fast_dial_numbers";

    public static boolean onCallFastDial(Fragment fragment, Editable edit, int id){
        int key = 0;
        switch(id){
            case R.id.two: key = 2; break;
            case R.id.three: key = 3; break;
            case R.id.four: key = 4; break;
            case R.id.five: key = 5; break;
            case R.id.six: key = 6; break;
            case R.id.seven: key = 7; break;
            case R.id.eight: key = 8; break;
            case R.id.nine: key = 9; break;
            default:
                Log.e(TAG, "Not support key id = " + id);
                return false;
        }
        int length = edit.length();
        if (length > 1) {
            return false;
        } else if (length == 1) {
            int code = edit.toString().charAt(0) - '0';
            if (code != key) {
                return false;
            }
        }

        Log.d(TAG, "onCallFastDial : key = " + key);
        try {
            Context appContext = fragment.getActivity().getApplicationContext();
            Context phoneContext = appContext.createPackageContext(PHONE_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY);
            SharedPreferences sp = phoneContext.getSharedPreferences(SHARED_PREFERENCES_NAME,
                    Context.MODE_WORLD_READABLE | Context.MODE_MULTI_PROCESS);
            String fastCall = sp.getString("fast_dial_" + String.valueOf(key), "");
            if (TextUtils.isEmpty(fastCall)) {
                Toast.makeText(appContext, appContext.getText(R.string.no_fast_dial), Toast.LENGTH_SHORT).show();
                return false;
            }
            
            Intent intent = null;
            if (fastCall.startsWith("content")) {
                intent = CallUtil.getCallIntent(Uri.parse(fastCall), null);
            } else {
                intent = CallUtil.getCallIntent(fastCall);
            }
            appContext.startActivity(intent);
            return true;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }
}
