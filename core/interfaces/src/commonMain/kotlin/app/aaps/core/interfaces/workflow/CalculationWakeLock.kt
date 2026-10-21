package app.aaps.core.interfaces.workflow

/**
 * Keeps the device awake while one calculation run is in flight, and while a new BG waits for its run.
 *
 * A WorkManager job did not need this: the system holds a wake lock for a job while it runs. A
 * coroutine has nothing like that, so with the screen off the CPU could go to sleep in the middle of
 * a run, and the loop at the end of the run would come late. Android answers with a partial wake
 * lock.
 *
 * A platform that has no such lock passes null rather than an implementation that does nothing, so
 * the difference stays visible.
 */
interface CalculationWakeLock {

    /** Keeps the device awake until [Held.release] is called, but never longer than [timeoutMs]. */
    fun acquire(timeoutMs: Long): Held

    /** One acquired lock. [release] is safe to call more than once, and after the timeout ran out. */
    interface Held {

        fun release()
    }
}
