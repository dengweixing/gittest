/*
 * Copyright (C) 2013 Spreadtrum Communications Inc. 
 *
 */

package com.sprd.dialer;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemProperties;
import android.provider.BaseColumns;
import android.sim.Sim;
import android.sim.SimManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import android.util.Log;


public class SprdUtils {
    public static final String MIME_TYPE_VIDEO_CHAT = "vnd.android.cursor.item/video-chat-address";

    public static final String SCHEME_TEL = "tel";
    public static final String SCHEME_VTEL = "vtel";
    public static final String SCHEME_SMSTO = "smsto";
    public static final String SCHEME_MAILTO = "mailto";
    public static final String SCHEME_IMTO = "imto";
    public static final String SCHEME_SIP = "sip";
    public static final String ACTION_ADD_BLACKLIST = "com.sprd.firewall.ui.BlackCallsListAddActivity.action";

    public static final boolean UNIVERSE_UI_SUPPORT = SystemProperties.getBoolean("universe_ui_support",false);
    //    public static final boolean UNIVERSE_UI_SUPPORT = true;
    public static final boolean VT_SUPPORT = SystemProperties.getBoolean("persist.sys.support.vt", true);
    public static final boolean SHOW_DURATION_SUPPORT = 
            SystemProperties.getBoolean("ro.device.support.showduration", false);
    public static final boolean SUPPORT_CALLFIREWALL = 
            !SystemProperties.getBoolean("ro.callfirewall.disabled",false);

    public static String TAG = "UniverseUtils";
    public static final String IS_IP_DIAL = "is_ip_dial";
    public static int getValidSimNumber(){
        int validSimNum = 0;
        int phoneCount = TelephonyManager.getPhoneCount();
        for(int i=0;i<phoneCount;i++){
            if(TelephonyManager.getDefault(i).getSimState() == TelephonyManager.SIM_STATE_READY){
                validSimNum ++;
            }
        }
        return validSimNum;
    }
    public static int getValidSimNumberEx(){
        int validSimNum = 0;
        int phoneCount = TelephonyManager.getPhoneCount();
        for(int i=0;i<phoneCount;i++){
            if(TelephonyManager.getDefault(i).hasIccCard() && TelephonyManager.getDefault(i).getSimState() == TelephonyManager.SIM_STATE_READY){
                validSimNum ++;
            }
        }
        return validSimNum;
    }

    public static int getValidPhoneId(){
        int phoneCount = TelephonyManager.getPhoneCount();
        for(int i=0;i<phoneCount;i++){
            if(TelephonyManager.getDefault(i).getSimState() == TelephonyManager.SIM_STATE_READY){
                return i;
            }
        }
        return 0;
    }

    public static Sim[] getValidSim(Context context){
        Sim[] result = null;
        if(context == null){
            return result;
        }
        SimManager simManager = SimManager.get(context);
        ArrayList simList = new ArrayList<Sim>();
        int phoneCount = TelephonyManager.getPhoneCount();
        for(int i=0; i<phoneCount;i++){
            if(TelephonyManager.getDefault(i).getSimState() == TelephonyManager.SIM_STATE_READY){
                Sim sim = simManager.getSimById(i);
                simList.add(sim);
            }
        }

        result = new Sim[simList.size()];
        for(int i=0 ;i<simList.size();i++){
            result[i] = (Sim)simList.get(i);
        }
        return result;
    }

    public static String  getSimName(Context context,int phoneId){
        String result = null;
        if(context == null){
            Log.i(TAG, "context == null");
            return result;
        }
        SimManager simManager = SimManager.get(context);
        Sim sim = simManager.getSimById(phoneId);
        if(sim != null){
            result = sim.getName();
        }else{
            Log.i(TAG, "sim == null)");
        }
        return result;
    }

    public static int getSimColor(Context context,int phoneId){
        int result = 0;
        if(context == null){
            Log.i(TAG, "context == null");
            return result;
        }
        SimManager simManager = SimManager.get(context);
        Sim sim = simManager.getSimById(phoneId);
        if(sim != null){
            result = sim.getColor();

        }else{
            Log.i(TAG, "sim == null)");
        }
        return result;
    }

    public static boolean CheckIsBlackNumber(Context context, String str){
        ContentResolver cr = context.getContentResolver();
        String mumber_value;
        int block_type;
        String[] columns = new String[]{BlackColumns.BlackMumber.MUMBER_VALUE,
                BlackColumns.BlackMumber.BLOCK_TYPE};

        Cursor cursor = cr.query(BlackColumns.BlackMumber.CONTENT_URI, columns, null, null, null);
        try{
            if(cursor != null && cursor.moveToFirst()) {
                do{
                    mumber_value = cursor.getString(cursor.getColumnIndex(
                            BlackColumns.BlackMumber.MUMBER_VALUE));
                    block_type = cursor.getInt(cursor.getColumnIndex(
                            BlackColumns.BlackMumber.BLOCK_TYPE));
                    //jinwei 2011-11-18 refer to recent call log
                    if(PhoneNumberUtils.compareStrictly(str.trim(), mumber_value.trim())){

                        return true;
                    }
                }while(cursor.moveToNext());
            }
        } catch (Exception e) {
            // process exception
            Log.e(TAG, "CheckIsBlackNumber:exceotion");
        } finally {
            if(cursor != null)
                cursor.close();
            else Log.i(TAG, "cursor == null");
        }
        return false;
    }

    public static boolean putToBlockList(Context context,String phoneNumber, int Blocktype,String name){
        ContentResolver cr = context.getContentResolver();
        String normalizeNumber = PhoneNumberUtils.normalizeNumber(phoneNumber);
        ContentValues values = new ContentValues();
        if(values != null){
            try{
                values.put(BlackColumns.BlackMumber.MUMBER_VALUE, phoneNumber);
                values.put(BlackColumns.BlackMumber.BLOCK_TYPE, Blocktype);
                values.put(BlackColumns.BlackMumber.NAME, name);
                values.put(BlackColumns.BlackMumber.MIN_MATCH, PhoneNumberUtils.toCallerIDMinMatch(normalizeNumber));
                Log.d(TAG, "putToBlockList:values="+values);
            }catch(Exception e){
                Log.e(TAG, "putToBlockList:exception");
            }
        }
        Uri result = null;
        try { 
            result = cr.insert(BlackColumns.BlackMumber.CONTENT_URI, values);
        } catch (Exception e) {
            Log.e(TAG, "putToBlockList: provider == null");
        }
        return result != null ? true : false;
    }

    public static boolean deleteFromBlockList(Context context,String phoneNumber){
        ContentResolver cr = context.getContentResolver();
        String[] columns = new String[] {
                BlackColumns.BlackMumber._ID, BlackColumns.BlackMumber.MUMBER_VALUE,
        };
        String mumber_value;
        int result = -1;
        Cursor cursor = cr.query(BlackColumns.BlackMumber.CONTENT_URI, columns, null, null, null);
        try{
            if(cursor != null && cursor.moveToFirst()) {
                do{
                    mumber_value = cursor.getString(cursor.getColumnIndex(
                            BlackColumns.BlackMumber.MUMBER_VALUE));

                    if(PhoneNumberUtils.compareStrictly(phoneNumber.trim(), mumber_value.trim())){

                        result = cr.delete(BlackColumns.BlackMumber.CONTENT_URI,
                                BlackColumns.BlackMumber.MUMBER_VALUE + "='" + mumber_value + "'", null);
                        break;
                    }
                }while(cursor.moveToNext());
            }
        } catch (Exception e) {
            // process exception
            Log.e(TAG, e.getMessage(), e);
        } finally {
            if(cursor != null) {
                cursor.close();
            }else {
                Log.i(TAG, "cursor == null");
            }
        }  
        if (result < 0) {
            return false;
        }
        return true;
    }

    public static int getBlockType(Context context, String str) {
        ContentResolver cr = context.getContentResolver();
        String number;
        int type;
        String[] columns = new String[] {
                BlackColumns.BlackMumber.MUMBER_VALUE,
                BlackColumns.BlackMumber.BLOCK_TYPE
        };
        Cursor cursor = cr.query(BlackColumns.BlackMumber.CONTENT_URI, columns,
                null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    number = cursor
                            .getString(cursor
                                    .getColumnIndex(BlackColumns.BlackMumber.MUMBER_VALUE));
                    if (PhoneNumberUtils.compareStrictly(str.trim(),
                            number.trim())) {
                        type = cursor
                                .getInt(cursor
                                        .getColumnIndex(BlackColumns.BlackMumber.BLOCK_TYPE));
                        return type;
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "getBlockType:exception");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return 0;
    }

    public static class BlackColumns {
        public static final String AUTHORITY  = "com.sprd.providers.block";

        public static final class BlackMumber implements BaseColumns {
            public static final Uri CONTENT_URI  = Uri.parse("content://com.sprd.providers.block/black_mumbers");

            public static final String MUMBER_VALUE = "mumber_value";
            public static final String BLOCK_TYPE   = "block_type"; 
            public static final String NOTES        = "notes";
            public static final String NAME        = "name";
            public static final String MIN_MATCH = "min_match";
        }

        public static final class BlockRecorder implements BaseColumns {
            public static final Uri CONTENT_URI  = Uri.parse("content://com.sprd.providers.block/block_recorded");

            public static final String MUMBER_VALUE = "mumber_value";
            public static final String BLOCK_DATE = "block_date";
            public static final String NAME        = "name";
        }
    }

    /** Checks whether two URI are equal, taking care of the case where either is null. */
    public static boolean areEqual(Uri uri1, Uri uri2) {
        if (uri1 == null && uri2 == null) {
            return true;
        }
        if (uri1 == null || uri2 == null) {
            return false;
        }
        return uri1.equals(uri2);
    }

    /** Parses a string into a URI and returns null if the given string is null. */
    public static Uri parseUriOrNull(String uriString) {
        if (uriString == null) {
            return null;
        }
        return Uri.parse(uriString);
    }

    /** Converts a URI into a string, returns null if the given URI is null. */
    public static String uriToString(Uri uri) {
        return uri == null ? null : uri.toString();
    }

    public static String encodeFilePath(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        String [] fragments=path.split(File.separator);
        StringBuilder sb=new StringBuilder();
        int N=fragments.length;
        for (int i=0;i<N;++i) {
            if (i!=0) {
                sb.append(File.separator);
            }
            sb.append(Uri.encode(fragments[i]));
        } 
        return sb.toString();
    }

    public static String getIccIdByPhoneId(int phoneId ,Context context){
        String result = null;
        SimManager simManager = SimManager.get(context);
        Sim sim = simManager.getSimById(phoneId);
        if(sim != null){
            result = sim.getIccId();
        }else{
            Log.e(TAG, "sim == null)");
        }
        return result;
    }
    
    /**
     * Check SIMs are locked by PIN or PUK or not.
     * If one of the SIMs is locked, return true, else return false.
     * @return
     */
    public static boolean isSimLocked() {
        int phoneCount = TelephonyManager.getPhoneCount();

        int state = -1;
        for (int i = 0; i < phoneCount; i++) {
            state = TelephonyManager.getDefault(i).getSimState();

            if (state == TelephonyManager.SIM_STATE_PIN_REQUIRED
                    || state == TelephonyManager.SIM_STATE_PUK_REQUIRED) {
                return true;
            }
        }
        return false;
    }

}
