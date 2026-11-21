package app.aaps.implementation.lifecycle

import app.aaps.core.interfaces.workflow.CalculationWakeLock

/**
 * [CalculationWakeLock] on iOS: background time from [IosBackgroundTime].
 *
 * The loop runs at the end of a calculation, and a calculation on a locked phone starts when a reading wakes the app.
 * Without the time, iOS froze the run about 10 seconds after the reading, and the loop came to its decision only at the
 * next wake.
 */
class IosCalculationWakeLock : CalculationWakeLock {

    override fun acquire(timeoutMs: Long): CalculationWakeLock.Held {
        val hold = IosBackgroundTime.hold("calculation", timeoutMs)
        return object : CalculationWakeLock.Held {
            override fun release() = hold.release()
        }
    }
}
