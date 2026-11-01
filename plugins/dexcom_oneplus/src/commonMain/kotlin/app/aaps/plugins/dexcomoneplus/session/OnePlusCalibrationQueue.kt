package app.aaps.plugins.dexcomoneplus.session

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.plugins.dexcomoneplus.parse.OnePlusCalibrateTx

/**
 * The one command a caller outside the BLE thread may ask a running session to send.
 *
 * The Control/EGV loop is a blocking cycle: it writes a request, waits for an indication, handles it,
 * and starts again. There is no general command channel into it — the only thing of this kind before
 * was a boolean for "start a new sensor". A calibration cannot use that shape, because it carries a
 * value, a time, and an answer that has to come back to the user.
 *
 * So: one slot, not a list. A second fingerstick while one is still waiting is a mistake far more
 * often than an intention, and a sensor keeps every calibration it accepts for good.
 *
 * Nothing in this file sends anything. What may be offered is decided by `OnePlusCalibrationGate`.
 */
class OnePlusCalibrationQueue {

    /** A fingerstick waiting to be handed to the sensor. */
    data class Pending(
        /** Blood value in mg/dL. */
        val glucoseMgdl: Int,
        /**
         * Wall clock (epoch ms) given as the time of the blood. The calibration dialog passes the
         * moment the user confirmed the value, not the moment of the finger prick, so it is a few
         * seconds to minutes after the real blood time.
         */
        val bloodAtMs: Long,
    )

    private val lock = AapsLock()

    private var pending: Pending? = null

    /**
     * Ask for [glucoseMgdl] to be sent to the sensor.
     *
     * @return false when the value is outside what the sensor accepts, when the fingerstick is
     *   already too old to be meaningful, or when another one is still waiting. A waiting one that
     *   is older than [MAX_AGE_MS] does not count: it would be dropped at sending anyway, and it
     *   must not block every new fingerstick when the sensor stays out of reach.
     */
    fun offer(glucoseMgdl: Int, bloodAtMs: Long, nowMs: Long): Boolean {
        if (glucoseMgdl < OnePlusCalibrateTx.MIN_GLUCOSE_MGDL || glucoseMgdl > OnePlusCalibrateTx.MAX_GLUCOSE_MGDL) return false
        if (isTooOld(bloodAtMs, nowMs) || bloodAtMs > nowMs) return false
        return lock.withLock {
            val waiting = pending
            if (waiting != null && !isTooOld(waiting.bloodAtMs, nowMs)) return@withLock false
            pending = Pending(glucoseMgdl, bloodAtMs)
            true
        }
    }

    /** What is waiting, without taking it. */
    fun peek(): Pending? = lock.withLock { pending }

    /** Take the waiting fingerstick, if any. The slot is free again from here on. */
    fun take(): Pending? = lock.withLock { pending.also { pending = null } }

    /**
     * Take [expected] out of the slot, only when it is still the value waiting there.
     *
     * @return false when the slot is empty or holds another value (it was replaced or cleared
     *   since [expected] was read); the slot is then left as it is
     */
    fun take(expected: Pending): Boolean = lock.withLock {
        if (pending !== expected) return@withLock false
        pending = null
        true
    }

    companion object {

        /**
         * Past this age a fingerstick is not worth sending: the sensor lines it up with its own
         * history, and an hour of drift makes the pairing wrong. Same limit as xDrip and as the
         * Dexcom apps, which ask for the value within minutes of the prick.
         */
        const val MAX_AGE_MS = 60L * 60L * 1000L

        fun isTooOld(bloodAtMs: Long, nowMs: Long): Boolean = nowMs - bloodAtMs > MAX_AGE_MS

        /**
         * Transmitter time for the time given for the blood ([Pending.bloodAtMs], which is the
         * moment the user confirmed the value).
         *
         * The sensor is told when the blood was taken, not when the packet is written, so the
         * wall-clock age of the fingerstick is taken off the transmitter clock. The transmitter
         * clock itself is only known as "this value, read at that moment", so its own age counts too.
         *
         * @param lastDexTimeSeconds transmitter time last read from the sensor (0 = never)
         * @param lastDexTimeAtMs wall clock when that transmitter time was read
         * @return the transmitter second to put in the packet, or null when it cannot be worked out
         */
        fun dexTimeForBlood(
            bloodAtMs: Long,
            nowMs: Long,
            lastDexTimeSeconds: Int,
            lastDexTimeAtMs: Long,
        ): Int? {
            if (lastDexTimeSeconds <= 0 || lastDexTimeAtMs <= 0L) return null
            val dexNow = lastDexTimeSeconds + ((nowMs - lastDexTimeAtMs) / 1000L)
            val dexBlood = dexNow - ((nowMs - bloodAtMs) / 1000L)
            if (dexBlood <= 0L || dexBlood > Int.MAX_VALUE) return null
            return dexBlood.toInt()
        }
    }
}
