package app.aaps.shared.impl.extensions

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * Safe version of queryBroadcastReceivers depending on Android version running
 */
fun PackageManager.safeQueryBroadcastReceivers(intent: Intent, flags: Long): List<ResolveInfo> =
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) queryBroadcastReceivers(intent, PackageManager.ResolveInfoFlags.of(flags))
        else @Suppress("DEPRECATION") queryBroadcastReceivers(intent, flags.toInt())
    } catch (_: Exception) {
        emptyList()
    }

/**
 * Safe version of getPackageInfo depending on Android version running
 */
@Throws(PackageManager.NameNotFoundException::class)
fun PackageManager.safeGetPackageInfo(packageName: String, flags: Int): PackageInfo =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    else @Suppress("DEPRECATION") getPackageInfo(packageName, flags)
