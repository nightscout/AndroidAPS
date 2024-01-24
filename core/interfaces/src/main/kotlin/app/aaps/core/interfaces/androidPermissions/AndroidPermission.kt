package app.aaps.core.interfaces.androidPermissions

import android.content.Context
import androidx.fragment.app.FragmentActivity

interface AndroidPermission {

    /**
     * Initiate pop-up and let user confirm permissions
     * @param activity context of caller
     * @param permissions list of requested permissions
     */
    fun askForPermission(activity: FragmentActivity, permissions: Array<String>)

    /**
     * Initiate pop-up and let user confirm permissions
     * @param activity context of caller
     * @param permission requested permission
     */
    fun askForPermission(activity: FragmentActivity, permission: String) = askForPermission(activity, arrayOf(permission))

    /**
     * Check if permission is missing
     * @param context context of caller
     * @param permission permission to check
     */
    fun permissionNotGranted(context: Context, permission: String): Boolean

    /**
     * Check for SMS permission. Raise Overview notification if missing.
     * @param activity context of caller
     */
    fun notifyForSMSPermissions(activity: FragmentActivity)

    /**
     * Check for BT permission. Raise Overview notification if missing.
     * @param activity context of caller
     */
    fun notifyForBtConnectPermission(activity: FragmentActivity)

    /**
     * Check for battery optimization permission. Raise Overview notification if missing.
     * @param activity context of caller
     */
    fun notifyForBatteryOptimizationPermission(activity: FragmentActivity)

    /**
     * Check for storage permission. Raise Overview notification if missing.
     * @param activity context of caller
     */
    fun notifyForStoragePermission(activity: FragmentActivity)

    /**
     * Check for location permission. Raise Overview notification if missing.
     * @param activity context of caller
     */
    fun notifyForLocationPermissions(activity: FragmentActivity)

    /**
     * Check for system window permission. Raise Overview notification if missing.
     * @param activity context of caller
     */
    fun notifyForSystemWindowPermissions(activity: FragmentActivity)
}