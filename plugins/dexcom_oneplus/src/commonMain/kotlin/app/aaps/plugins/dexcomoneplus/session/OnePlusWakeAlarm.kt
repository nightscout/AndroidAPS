package app.aaps.plugins.dexcomoneplus.session

/**
 * Wakes the processor at a set time, for a driver that sleeps between two sensor cycles.
 *
 * The session waits on its own thread, and a waiting thread stands still while the device sleeps.
 * So the wait itself needs nothing from here but to be woken: the alarm brings the processor back
 * and keeps it awake for a few seconds, and the session's wait then sees that the time has come.
 *
 * Like [OnePlusCpuLease], it says what is wanted and leaves the how to each platform. On Android
 * that is an exact alarm.
 */
interface OnePlusWakeAlarm {

    /**
     * Wake the processor at [epochMs] (wall clock). A new call replaces the one before.
     *
     * @return false when this device cannot wake at an exact time. The caller must then wait in a
     *   way that does not need it.
     */
    fun wakeAt(epochMs: Long): Boolean

    /** Drop the alarm that is set, if any. */
    fun cancel()
}
