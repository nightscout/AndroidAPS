package info.nightscout.core.utils.extensions

import android.content.Intent
import java.io.Serializable

/**
 * Safe version of getParcelableExtra depending on Android version running
 */
fun <T> Intent.safeGetParcelableExtra(name: String?, clazz: Class<T>): T? =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) getParcelableExtra(name, clazz)
    else @Suppress("DEPRECATION") getParcelableExtra(name)

/**
 * Safe version of getSerializableExtra depending on Android version running
 */
@Suppress("UNCHECKED_CAST")
fun <T : Serializable?> Intent.safeGetSerializableExtra(name: String?, clazz: Class<T>): T? =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) getSerializableExtra(name, clazz)
    else @Suppress("DEPRECATION") (getSerializableExtra(name) as T?)
