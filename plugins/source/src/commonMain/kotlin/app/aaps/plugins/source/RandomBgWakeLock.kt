package app.aaps.plugins.source

/**
 * Keeps the device awake while [RandomBgPlugin] runs, so that its readings keep coming with the screen
 * off.
 *
 * The plugin makes a reading on a timer, and a timer stops while the CPU sleeps. Android holds a partial
 * wake lock for as long as the plugin runs, as it always has.
 *
 * iOS has no such lock: nothing keeps an app running once the system suspends it. Neither does the
 * desktop app. Their [acquire] answers null rather than pretending, so the difference stays visible -
 * the same rule as [app.aaps.core.interfaces.workflow.CalculationWakeLock]. There the plugin makes
 * readings while the app runs, and stops when the app is suspended, like everything else in it.
 */
interface RandomBgWakeLock {

    /** Keeps the device awake until [Held.release] is called, or null where the platform has no such lock. */
    fun acquire(): Held?

    /** One held lock. [release] is safe to call more than once. */
    interface Held {

        fun release()
    }
}
