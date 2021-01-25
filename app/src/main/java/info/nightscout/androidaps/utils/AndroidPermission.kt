package info.nightscout.androidaps.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.DaggerAppCompatActivityWithResult
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationWithAction
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.utils.alertDialogs.OKDialog.show
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPermission @Inject constructor(
    val resourceHelper: ResourceHelper,
    val rxBus: RxBusWrapper,
    val injector: HasAndroidInjector
) {

    private var permissionBatteryOptimizationFailed = false

    @SuppressLint("BatteryLife")
    fun askForPermission(activity: FragmentActivity, permissions: Array<String>) {
        var test = false
        var testBattery = false
        for (s in permissions) {
            test = test || ContextCompat.checkSelfPermission(activity, s) != PackageManager.PERMISSION_GRANTED
            if (s == Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) {
                val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
                val packageName = activity.packageName
                testBattery = testBattery || !powerManager.isIgnoringBatteryOptimizations(packageName)
            }
        }
        if (test) {
            if (activity is DaggerAppCompatActivityWithResult)
                activity.requestMultiplePermissions.launch(permissions)
        }
        if (testBattery) {
            try {
                if (activity is DaggerAppCompatActivityWithResult)
                    activity.callForBatteryOptimization.launch(null)
            } catch (e: ActivityNotFoundException) {
                permissionBatteryOptimizationFailed = true
                show(activity, resourceHelper.gs(R.string.permission), resourceHelper.gs(R.string.alert_dialog_permission_battery_optimization_failed), Runnable { activity.recreate() })
            }
        }
    }

    fun askForPermission(activity: FragmentActivity, permission: String) = askForPermission(activity, arrayOf(permission))

    fun permissionNotGranted(context: Context, permission: String): Boolean {
        var selfCheck = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (permission == Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) {
            if (!permissionBatteryOptimizationFailed) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val packageName = context.packageName
                selfCheck = selfCheck && powerManager.isIgnoringBatteryOptimizations(packageName)
            }
        }
        return !selfCheck
    }

    @Synchronized
    fun notifyForSMSPermissions(activity: FragmentActivity, smsCommunicatorPlugin: SmsCommunicatorPlugin) {
        if (smsCommunicatorPlugin.isEnabled(PluginType.GENERAL)) {
            if (permissionNotGranted(activity, Manifest.permission.RECEIVE_SMS)) {
                val notification = NotificationWithAction(injector, Notification.PERMISSION_SMS, resourceHelper.gs(R.string.smscommunicator_missingsmspermission), Notification.URGENT)
                notification.action(R.string.request) {
                    askForPermission(activity, arrayOf(Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.RECEIVE_MMS))
                }
                rxBus.send(EventNewNotification(notification))
            } else rxBus.send(EventDismissNotification(Notification.PERMISSION_SMS))
            // Following is a bug in Android 8
            if (permissionNotGranted(activity, Manifest.permission.READ_PHONE_STATE)) {
                val notification = NotificationWithAction(injector, Notification.PERMISSION_PHONESTATE, resourceHelper.gs(R.string.smscommunicator_missingphonestatepermission), Notification.URGENT)
                notification.action(R.string.request) { askForPermission(activity, arrayOf(Manifest.permission.READ_PHONE_STATE)) }
                rxBus.send(EventNewNotification(notification))
            } else rxBus.send(EventDismissNotification(Notification.PERMISSION_PHONESTATE))
        }
    }

    @Synchronized
    fun notifyForBatteryOptimizationPermission(activity: FragmentActivity) {
        if (permissionNotGranted(activity, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)) {
            val notification = NotificationWithAction(injector, Notification.PERMISSION_BATTERY, String.format(resourceHelper.gs(R.string.needwhitelisting), resourceHelper.gs(R.string.app_name)), Notification.URGENT)
            notification.action(R.string.request) { askForPermission(activity, arrayOf(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)) }
            rxBus.send(EventNewNotification(notification))
        } else rxBus.send(EventDismissNotification(Notification.PERMISSION_BATTERY))
    }

    @Synchronized fun notifyForStoragePermission(activity: FragmentActivity) {
        if (permissionNotGranted(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            val notification = NotificationWithAction(injector, Notification.PERMISSION_STORAGE, resourceHelper.gs(R.string.needstoragepermission), Notification.URGENT)
            notification.action(R.string.request) {
                askForPermission(activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
            rxBus.send(EventNewNotification(notification))
        } else rxBus.send(EventDismissNotification(Notification.PERMISSION_STORAGE))
    }

    @Synchronized fun notifyForLocationPermissions(activity: FragmentActivity) {
        if (permissionNotGranted(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            val notification = NotificationWithAction(injector, Notification.PERMISSION_LOCATION, resourceHelper.gs(R.string.needlocationpermission), Notification.URGENT)
            notification.action(R.string.request) { askForPermission(activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) }
            rxBus.send(EventNewNotification(notification))
        } else rxBus.send(EventDismissNotification(Notification.PERMISSION_LOCATION))
    }

    @Synchronized fun notifyForSystemWindowPermissions(activity: FragmentActivity) {
        // Check if Android Q or higher
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            if (!Settings.canDrawOverlays(activity)) {
                val notification = NotificationWithAction(injector, Notification.PERMISSION_SYSTEM_WINDOW, resourceHelper.gs(R.string.needsystemwindowpermission), Notification.URGENT)
                notification.action(R.string.request) {
                    // Check if Android Q or higher
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                        // Show alert dialog to the user saying a separate permission is needed
                        // Launch the settings activity if the user prefers
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + activity.packageName))
                        activity.startActivity(intent)
                    }
                }
                rxBus.send(EventNewNotification(notification))
            } else rxBus.send(EventDismissNotification(Notification.PERMISSION_SYSTEM_WINDOW))
        }
    }
}