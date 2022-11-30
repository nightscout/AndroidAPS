package info.nightscout.androidaps.interaction.utils

import android.content.Context
import android.os.PowerManager
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by andy on 3/5/19.
 * Adapted by dlvoy on 2019-11-06 using code from jamorham JoH class
 */
@Singleton
@OpenForTesting
open class WearUtil @Inject constructor(
    open val context: Context,
    open val aapsLogger: AAPSLogger
) {

    private val debugWakelocks = false
    open val rateLimits: MutableMap<String, Long> = HashMap()

    //==============================================================================================
    // Time related util methods
    //==============================================================================================
    open fun timestamp(): Long {
        return System.currentTimeMillis()
    }

    fun msSince(`when`: Long): Long {
        return timestamp() - `when`
    }

    fun msTill(`when`: Long): Long {
        return `when` - timestamp()
    }

    //==============================================================================================
    // Thread and power management utils
    //==============================================================================================
    // return true if below rate limit
    @Synchronized fun isBelowRateLimit(named: String, onceForSeconds: Int): Boolean {
        // check if over limit
        if (rateLimits.containsKey(named) && timestamp() - rateLimits[named]!! < onceForSeconds * 1000) {
            aapsLogger.debug(LTag.WEAR, "$named rate limited to one for $onceForSeconds seconds")
            return false
        }
        // not over limit
        rateLimits[named] = timestamp()
        return true
    }

    open fun getWakeLock(name: String, millis: Int): PowerManager.WakeLock {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AAPS::$name")
        wl.acquire(millis.toLong())
        if (debugWakelocks) aapsLogger.debug(LTag.WEAR, "getWakeLock: $name $wl")
        return wl
    }

    open fun releaseWakeLock(wl: PowerManager.WakeLock?) {
        if (debugWakelocks) aapsLogger.debug(LTag.WEAR, "releaseWakeLock: " + wl.toString())
        if (wl?.isHeld == true) wl.release()
    }
}
