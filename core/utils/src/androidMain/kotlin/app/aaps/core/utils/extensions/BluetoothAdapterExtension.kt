package app.aaps.core.utils.extensions

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.os.SystemClock

/**
 * @param waitMilliseconds if !=0 wait after enable()
 * @param after Runnable to execute after enable()
 *
 * @return true if enable was executed or not necessary
 */
// enable() is deprecated from API 33, where it only returns false. The guard below means it is
// called on API 31-32 only, where it still works, so there is nothing to migrate to. The API 33+
// replacement is an ACTION_REQUEST_ENABLE intent, which needs an Activity and asks the user - not
// something this background helper should do, and returning false here is already the right answer.
@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
fun BluetoothAdapter.safeEnable(waitMilliseconds: Long = 0, after: Runnable? = null): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) false
    else {
        if (!isEnabled) {
            val result = enable()
            if (waitMilliseconds != 0L) SystemClock.sleep(waitMilliseconds)
            after?.run()
            result
        } else true
    }

/**
 * @param waitMilliseconds if !=0 wait after disable()
 *
 * @return true if disable was executed or not necessary
 */
/** See [safeEnable] for why the deprecation is suppressed instead of migrated. */
@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
fun BluetoothAdapter.safeDisable(waitMilliseconds: Long = 0): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) false
    else {
        if (isEnabled) {
            val result = disable()
            if (waitMilliseconds != 0L) SystemClock.sleep(waitMilliseconds)
            result
        } else true
    }