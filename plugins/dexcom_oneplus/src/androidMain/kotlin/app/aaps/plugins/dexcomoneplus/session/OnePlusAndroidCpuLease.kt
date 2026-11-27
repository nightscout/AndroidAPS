package app.aaps.plugins.dexcomoneplus.session

import android.content.Context
import android.os.PowerManager
import app.aaps.plugins.dexcomoneplus.OnePlusLog
import app.aaps.plugins.dexcomoneplus.OnePlusLogMarkers

/**
 * Android [OnePlusCpuLease]: a partial wake lock, the same one the session used to take itself.
 *
 * @param slot which sensor slot asked for it, so the log line still says who is holding it.
 */
class OnePlusAndroidCpuLease(context: Context, private val slot: String) : OnePlusCpuLease {

    private val appContext = context.applicationContext

    override fun hold(maxMillis: Long): OnePlusCpuLease.Hold? =
        try {
            val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS::DexcomOnePlusAuth")
            lock.setReferenceCounted(false)
            lock.acquire(maxMillis)
            OnePlusLog.i("${OnePlusLogMarkers.SESSION}: [$slot] wakeLock acquired ${maxMillis}ms")
            WakeLockHold(lock)
        } catch (t: Throwable) {
            OnePlusLog.w("${OnePlusLogMarkers.SESSION}: [$slot] wakeLock ${t.message}")
            null
        }

    private class WakeLockHold(private val lock: PowerManager.WakeLock) : OnePlusCpuLease.Hold {

        override fun release() {
            try {
                if (lock.isHeld) lock.release()
            } catch (_: Throwable) {
            }
        }
    }
}
