package info.nightscout.androidaps.interfaces

import android.content.Context
import androidx.fragment.app.FragmentActivity

interface AndroidPermission {

    fun askForPermission(activity: FragmentActivity, permissions: Array<String>)
    fun askForPermission(activity: FragmentActivity, permission: String) = askForPermission(activity, arrayOf(permission))
    fun permissionNotGranted(context: Context, permission: String): Boolean
    fun notifyForSMSPermissions(activity: FragmentActivity, smsCommunicator: SmsCommunicator)
    fun notifyForBtConnectPermission(activity: FragmentActivity)
    fun notifyForBatteryOptimizationPermission(activity: FragmentActivity)
    fun notifyForStoragePermission(activity: FragmentActivity)
    fun notifyForLocationPermissions(activity: FragmentActivity)
    fun notifyForSystemWindowPermissions(activity: FragmentActivity)
}