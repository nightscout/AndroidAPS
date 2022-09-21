package info.nightscout.androidaps.extensions

import android.content.Intent

fun <T> Intent.safeGetParcelableExtra(name: String?, clazz: Class<T>): T? =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) getParcelableExtra(name, clazz)
    else @Suppress("DEPRECATION") getParcelableExtra(name)
