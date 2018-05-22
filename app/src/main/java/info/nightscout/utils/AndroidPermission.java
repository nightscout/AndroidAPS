package info.nightscout.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import info.nightscout.androidaps.R;

public class AndroidPermission {

    private static boolean askForSMS = false;
    private static boolean askForLocation = true;

    public static final int CASE_STORAGE = 0x1;
    public static final int CASE_SMS = 0x2;
    public static final int CASE_LOCATION = 0x3;
    public static final int CASE_BATTERY = 0x4;

    public static void askForPermission(Activity activity, String[] permission, Integer requestCode) {
        boolean test = false;
        for (int i = 0; i < permission.length; i++) {
            test = test || (ContextCompat.checkSelfPermission(activity, permission[i]) != PackageManager.PERMISSION_GRANTED);
        }
        if (test) {
            ActivityCompat.requestPermissions(activity, permission, requestCode);
        }
    }

    public static void askForPermission(Activity activity, String permission, Integer requestCode) {
        String[] permissions = {permission};

        if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(activity, permissions, requestCode);
    }

    public static boolean checkForPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static synchronized void askForSMSPermissions(Activity activity) {
        if (askForSMS) { //only when settings were changed an MainActivity resumes.
            askForSMS = false;
            if (SP.getBoolean(R.string.smscommunicator_remotecommandsallowed, false)) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
                    AndroidPermission.askForPermission(activity, new String[]{Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.SEND_SMS,
                            Manifest.permission.RECEIVE_MMS}, AndroidPermission.CASE_SMS);
                }
            }
        }
    }

    public static synchronized void askForBatteryOptimizationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AndroidPermission.askForPermission(activity, new String[]{Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS}, AndroidPermission.CASE_BATTERY);
        }
    }

    public static synchronized void askForStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            AndroidPermission.askForPermission(activity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, AndroidPermission.CASE_STORAGE);
        }
    }

    public static synchronized void askForLocationPermissions(Activity activity) {
        if (askForLocation) { //only when settings were changed an MainActivity resumes.
            askForLocation = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AndroidPermission.askForPermission(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION}, AndroidPermission.CASE_LOCATION);
            }
        }
    }

    public static synchronized void setAskForSMS() {
        askForSMS = true;
    }

}
