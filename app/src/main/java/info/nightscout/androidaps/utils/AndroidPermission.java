package info.nightscout.androidaps.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationWithAction;
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin;

public class AndroidPermission {

    public static final int CASE_STORAGE = 0x1;
    public static final int CASE_SMS = 0x2;
    public static final int CASE_LOCATION = 0x3;
    public static final int CASE_BATTERY = 0x4;
    public static final int CASE_PHONE_STATE = 0x5;
    public static final int CASE_SYSTEM_WINDOW = 0x6;

    private static boolean permission_battery_optimization_failed = false;

    @SuppressLint("BatteryLife")
    private static void askForPermission(Activity activity, String[] permission, Integer requestCode) {
        boolean test = false;
        boolean testBattery = false;
        for (String s : permission) {
            test = test || (ContextCompat.checkSelfPermission(activity, s) != PackageManager.PERMISSION_GRANTED);
            if (s.equals(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)) {
                PowerManager powerManager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
                String packageName = activity.getPackageName();
                testBattery = testBattery || !powerManager.isIgnoringBatteryOptimizations(packageName);
            }
        }
        if (test) {
            ActivityCompat.requestPermissions(activity, permission, requestCode);
        }
        if (testBattery) {
            try {
                Intent i = new Intent();
                i.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(i, CASE_BATTERY);
            } catch (ActivityNotFoundException e) {
                permission_battery_optimization_failed = true;
                OKDialog.show(activity, MainApp.gs(R.string.permission), MainApp.gs(R.string.alert_dialog_permission_battery_optimization_failed), activity::recreate);
            }
        }
    }

    public static void askForPermission(Activity activity, String permission, Integer requestCode) {
        String[] permissions = {permission};
        askForPermission(activity, permissions, requestCode);
    }

    public static boolean permissionNotGranted(Context context, String permission) {
        boolean selfCheck = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
        if (permission.equals(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)) {
            if (!permission_battery_optimization_failed) {
                PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                String packageName = context.getPackageName();
                selfCheck = selfCheck && powerManager.isIgnoringBatteryOptimizations(packageName);
            }
        }
        return !selfCheck;
    }

    public static synchronized void notifyForSMSPermissions(Activity activity) {
        if (SmsCommunicatorPlugin.INSTANCE.isEnabled(PluginType.GENERAL)) {
            if (permissionNotGranted(activity, Manifest.permission.RECEIVE_SMS)) {
                NotificationWithAction notification = new NotificationWithAction(Notification.PERMISSION_SMS, MainApp.gs(R.string.smscommunicator_missingsmspermission), Notification.URGENT);
                notification.action(R.string.request, () -> AndroidPermission.askForPermission(activity, new String[]{Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.RECEIVE_MMS}, AndroidPermission.CASE_SMS));
                RxBus.INSTANCE.send(new EventNewNotification(notification));
            } else
                RxBus.INSTANCE.send(new EventDismissNotification(Notification.PERMISSION_SMS));
            // Following is a bug in Android 8
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
                if (permissionNotGranted(activity, Manifest.permission.READ_PHONE_STATE)) {
                    NotificationWithAction notification = new NotificationWithAction(Notification.PERMISSION_PHONESTATE, MainApp.gs(R.string.smscommunicator_missingphonestatepermission), Notification.URGENT);
                    notification.action(R.string.request, () ->
                            AndroidPermission.askForPermission(activity, new String[]{Manifest.permission.READ_PHONE_STATE}, AndroidPermission.CASE_PHONE_STATE));
                    RxBus.INSTANCE.send(new EventNewNotification(notification));
                } else
                    RxBus.INSTANCE.send(new EventDismissNotification(Notification.PERMISSION_PHONESTATE));
            }
        }
    }

    public static synchronized void notifyForBatteryOptimizationPermission(Activity activity) {
        if (permissionNotGranted(activity, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)) {
            NotificationWithAction notification = new NotificationWithAction(Notification.PERMISSION_BATTERY, String.format(MainApp.gs(R.string.needwhitelisting), MainApp.gs(R.string.app_name)), Notification.URGENT);
            notification.action(R.string.request, () -> AndroidPermission.askForPermission(activity, new String[]{Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS}, AndroidPermission.CASE_BATTERY));
            RxBus.INSTANCE.send(new EventNewNotification(notification));
        } else
            RxBus.INSTANCE.send(new EventDismissNotification(Notification.PERMISSION_BATTERY));
    }

    public static synchronized void notifyForStoragePermission(Activity activity) {
        if (permissionNotGranted(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            NotificationWithAction notification = new NotificationWithAction(Notification.PERMISSION_STORAGE, MainApp.gs(R.string.needstoragepermission), Notification.URGENT);
            notification.action(R.string.request, () -> AndroidPermission.askForPermission(activity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, AndroidPermission.CASE_STORAGE));
            RxBus.INSTANCE.send(new EventNewNotification(notification));
        } else
            RxBus.INSTANCE.send(new EventDismissNotification(Notification.PERMISSION_STORAGE));
    }

    public static synchronized void notifyForLocationPermissions(Activity activity) {
        if (permissionNotGranted(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            NotificationWithAction notification = new NotificationWithAction(Notification.PERMISSION_LOCATION, MainApp.gs(R.string.needlocationpermission), Notification.URGENT);
            notification.action(R.string.request, () -> AndroidPermission.askForPermission(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, AndroidPermission.CASE_LOCATION));
            RxBus.INSTANCE.send(new EventNewNotification(notification));
        } else
            RxBus.INSTANCE.send(new EventDismissNotification(Notification.PERMISSION_LOCATION));
    }

    public static synchronized void notifyForSystemWindowPermissions(Activity activity) {
        // Check if Android Q or higher
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            if (!Settings.canDrawOverlays(activity)) {
                NotificationWithAction notification = new NotificationWithAction(Notification.PERMISSION_SYSTEM_WINDOW, MainApp.gs(R.string.needsystemwindowpermission), Notification.URGENT);
                notification.action(R.string.request, () -> {
                    // Show alert dialog to the user saying a separate permission is needed
                    // Launch the settings activity if the user prefers
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(intent);
                });
                RxBus.INSTANCE.send(new EventNewNotification(notification));
            } else
                RxBus.INSTANCE.send(new EventDismissNotification(Notification.PERMISSION_SYSTEM_WINDOW));
        }
    }
}
