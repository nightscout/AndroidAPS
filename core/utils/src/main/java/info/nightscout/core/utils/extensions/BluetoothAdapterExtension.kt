package info.nightscout.core.utils.extensions

import android.bluetooth.BluetoothAdapter
import android.os.SystemClock

/**
 * @param waitMilliseconds if !=0 wait after enable()
 * @param after Runnable to execute after enable()
 *
 * @return true if enable was executed or not necessary
 */
fun BluetoothAdapter.safeEnable(waitMilliseconds: Long = 0, after: Runnable? = null): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) false
    else @Suppress("DEPRECATION") {
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
fun BluetoothAdapter.safeDisable(waitMilliseconds: Long = 0): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) false
    else @Suppress("DEPRECATION") {
        if (isEnabled) {
            val result = disable()
            if (waitMilliseconds != 0L) SystemClock.sleep(waitMilliseconds)
            result
        } else true
    }