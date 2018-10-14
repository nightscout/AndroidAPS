package info.nightscout.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.Overview.notifications.NotificationWithAction;

public class AndroidPermission {

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

    public static synchronized void notifyForSMSPermissions(Activity activity) {
        if (SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
                if (!checkForPermission(activity, Manifest.permission.RECEIVE_SMS)) {
                    NotificationWithAction notification = new NotificationWithAction(Notification.PERMISSION_SMS, MainApp.gs(R.string.smscommunicator_missingsmspermission), Notification.URGENT);
                    notification.action(MainApp.gs(R.string.request), () -> AndroidPermission.askForPermission(activity, new String[]{Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.SEND_SMS,
                            Manifest.permission.RECEIVE_MMS}, AndroidPermission.CASE_SMS));
                    MainApp.bus().post(new EventNewNotification(notification));
                } else
                    MainApp.bus().post(new EventDismissNotification(Notification.PERMISSION_SMS));
            }
        }
    }

    public static synchronized void notifyForBatteryOptimizationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!checkForPermission(activity, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)) {
                NotificationWithAction notification = new NotificationWithAction(Notification.PERMISSION_BATTERY, String.format(MainApp.gs(R.string.needwhitelisting), MainApp.gs(R.string.app_name)), Notification.URGENT);
                notification.action(MainApp.gs(R.string.request), () -> AndroidPermission.askForPermission(activity, new String[]{Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS}, AndroidPermission.CASE_BATTERY));
                MainApp.bus().post(new EventNewNotification(notification));
            } else
                MainApp.bus().post(new EventDismissNotification(Notification.PERMISSION_BATTERY));
        }
    }

    public static synchronized void notifyForStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!checkForPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                NotificationWithAction notification = new NotificationWithAction(Notification.PERMISSION_STORAGE, MainApp.gs(R.string.needstoragepermission), Notification.URGENT);
                notification.action(MainApp.gs(R.string.request), () -> AndroidPermission.askForPermission(activity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE}, AndroidPermission.CASE_STORAGE));
                MainApp.bus().post(new EventNewNotification(notification));
            } else
                MainApp.bus().post(new EventDismissNotification(Notification.PERMISSION_STORAGE));
        }
    }

    public static synchronized void notifyForLocationPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!checkForPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                NotificationWithAction notification = new NotificationWithAction(Notification.PERMISSION_LOCATION, MainApp.gs(R.string.needlocationpermission), Notification.URGENT);
                notification.action(MainApp.gs(R.string.request), () -> AndroidPermission.askForPermission(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, AndroidPermission.CASE_LOCATION));
                MainApp.bus().post(new EventNewNotification(notification));
            } else
                MainApp.bus().post(new EventDismissNotification(Notification.PERMISSION_LOCATION));
        }
    }
}
