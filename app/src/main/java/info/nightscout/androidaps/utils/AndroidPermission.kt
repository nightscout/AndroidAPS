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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
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

    companion object {
        const val CASE_STORAGE = 0x1
        const val CASE_SMS = 0x2
        const val CASE_LOCATION = 0x3
        const val CASE_BATTERY = 0x4
        const val CASE_PHONE_STATE = 0x5
        const val CASE_SYSTEM_WINDOW = 0x6
    }

    private var permission_battery_optimization_failed = false

    @SuppressLint("BatteryLife")
    private fun askForPermission(activity: FragmentActivity, permission: Array<String>, requestCode: Int) {
        var test = false
        var testBattery = false
        for (s in permission) {
            test = test || ContextCompat.checkSelfPermission(activity, s) != PackageManager.PERMISSION_GRANTED
            if (s == Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) {
                val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
                val packageName = activity.packageName
                testBattery = testBattery || !powerManager.isIgnoringBatteryOptimizations(packageName)
            }
        }
        if (test) {
            ActivityCompat.requestPermissions(activity, permission, requestCode)
        }
        if (testBattery) {
            try {
                val i = Intent()
                i.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                i.data = Uri.parse("package:" + activity.packageName)
                activity.startActivityForResult(i, CASE_BATTERY)
            } catch (e: ActivityNotFoundException) {
                permission_battery_optimization_failed = true
                show(activity, resourceHelper.gs(R.string.permission), resourceHelper.gs(R.string.alert_dialog_permission_battery_optimization_failed), Runnable { activity.recreate() })
            }
        }
    }

    fun askForPermission(activity: FragmentActivity, permission: String, requestCode: Int) {
        val permissions = arrayOf(permission)
        askForPermission(activity, permissions, requestCode)
    }

    fun permissionNotGranted(context: Context, permission: String): Boolean {
        var selfCheck = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (permission == Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) {
            if (!permission_battery_optimization_failed) {
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
                notification.action(R.string.request, Runnable {
                    askForPermission(activity, arrayOf(Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.RECEIVE_MMS), CASE_SMS)
                })
                rxBus.send(EventNewNotification(notification))
            } else rxBus.send(EventDismissNotification(Notification.PERMISSION_SMS))
            // Following is a bug in Android 8
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
                if (permissionNotGranted(activity, Manifest.permission.READ_PHONE_STATE)) {
                    val notification = NotificationWithAction(injector, Notification.PERMISSION_PHONESTATE, resourceHelper.gs(R.string.smscommunicator_missingphonestatepermission), Notification.URGENT)
                    notification.action(R.string.request, Runnable { askForPermission(activity, arrayOf(Manifest.permission.READ_PHONE_STATE), CASE_PHONE_STATE) })
                    rxBus.send(EventNewNotification(notification))
                } else rxBus.send(EventDismissNotification(Notification.PERMISSION_PHONESTATE))
            }
        }
    }

    @Synchronized
    fun notifyForBatteryOptimizationPermission(activity: FragmentActivity) {
        if (permissionNotGranted(activity, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)) {
            val notification = NotificationWithAction(injector, Notification.PERMISSION_BATTERY, String.format(resourceHelper.gs(R.string.needwhitelisting), resourceHelper.gs(R.string.app_name)), Notification.URGENT)
            notification.action(R.string.request, Runnable { askForPermission(activity, arrayOf(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS), CASE_BATTERY) })
            rxBus.send(EventNewNotification(notification))
        } else rxBus.send(EventDismissNotification(Notification.PERMISSION_BATTERY))
    }

    @Synchronized fun notifyForStoragePermission(activity: FragmentActivity) {
        if (permissionNotGranted(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            val notification = NotificationWithAction(injector, Notification.PERMISSION_STORAGE, resourceHelper.gs(R.string.needstoragepermission), Notification.URGENT)
            notification.action(R.string.request, Runnable {
                askForPermission(activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE), CASE_STORAGE)
            })
            rxBus.send(EventNewNotification(notification))
        } else rxBus.send(EventDismissNotification(Notification.PERMISSION_STORAGE))
    }

    @Synchronized fun notifyForLocationPermissions(activity: FragmentActivity) {
        if (permissionNotGranted(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            val notification = NotificationWithAction(injector, Notification.PERMISSION_LOCATION, resourceHelper.gs(R.string.needlocationpermission), Notification.URGENT)
            notification.action(R.string.request, Runnable { askForPermission(activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), CASE_LOCATION) })
            rxBus.send(EventNewNotification(notification))
        } else rxBus.send(EventDismissNotification(Notification.PERMISSION_LOCATION))
    }

    @Synchronized fun notifyForSystemWindowPermissions(activity: FragmentActivity) {
        // Check if Android Q or higher
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            if (!Settings.canDrawOverlays(activity)) {
                val notification = NotificationWithAction(injector, Notification.PERMISSION_SYSTEM_WINDOW, resourceHelper.gs(R.string.needsystemwindowpermission), Notification.URGENT)
                notification.action(R.string.request, Runnable {
                    // Check if Android Q or higher
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                        // Show alert dialog to the user saying a separate permission is needed
                        // Launch the settings activity if the user prefers
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + activity.packageName))
                        activity.startActivity(intent)
                    }
                })
                rxBus.send(EventNewNotification(notification))
            } else rxBus.send(EventDismissNotification(Notification.PERMISSION_SYSTEM_WINDOW))
        }
    }
}