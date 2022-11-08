package info.nightscout.shared.extensions

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * Safe version of getInstalledPackages depending on Android version running
 */
fun PackageManager.safeGetInstalledPackages(flags: Int): List<PackageInfo> =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
    else @Suppress("DEPRECATION") getInstalledPackages(flags)

/**
 * Safe version of queryBroadcastReceivers depending on Android version running
 */
fun PackageManager.safeQueryBroadcastReceivers(intent: Intent, flags: Int): List<ResolveInfo> =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) queryBroadcastReceivers(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    else @Suppress("DEPRECATION") queryBroadcastReceivers(intent, flags)

/**
 * Safe version of getPackageInfo depending on Android version running
 */
@Throws(PackageManager.NameNotFoundException::class)
fun PackageManager.safeGetPackageInfo(packageName: String, flags: Int): PackageInfo =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    else @Suppress("DEPRECATION") getPackageInfo(packageName, flags)
