package app.aaps.plugins.dexcomoneplus

import android.content.Context
import android.content.pm.PackageManager

/**
 * [OnePlusInstalledApps] on Android, through the `PackageManager`.
 *
 * From Android 11 an app sees only the packages it declares. The packages of
 * [OnePlusCompetingApps.KNOWN] are declared in this module's manifest under `<queries>`; a package
 * missing there would always read as "not installed".
 */
class OnePlusInstalledAppsAndroid(context: Context) : OnePlusInstalledApps {

    private val packageManager: PackageManager = context.applicationContext.packageManager

    override fun isInstalled(packageName: String): Boolean =
        try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
}
