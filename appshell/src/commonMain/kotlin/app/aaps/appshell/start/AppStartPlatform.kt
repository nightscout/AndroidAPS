package app.aaps.appshell.start

/**
 * What only the platform can do while the app starts.
 *
 * [AppStartSequence] decides when each part runs, so the order is the same on every platform. This only
 * fills in what shared code cannot reach.
 */
interface AppStartPlatform {

    /** The build number, for the version record in the database. */
    val versionCode: Int

    /** The device, for the "AAPS started" note. Example: "Google Pixel 8". */
    val deviceName: String

    /**
     * The platform's own services. Called once the plugins have started and the old records are migrated.
     * Android registers its broadcast receivers and fetches the remote configuration here.
     */
    fun startServices()

    /**
     * Starts what runs the 5-minute checks: `KeepAliveWorker` on Android, the wake-up on iOS, a timer on the
     * desktop.
     *
     * Called after the alarms are pre-snoozed. The first check can run at once, and after a start the newest
     * glucose value is always old, so without the pre-snooze it would raise "BG readings missed" before the
     * first new reading can arrive (nightscout/AndroidAPS#5495).
     */
    fun startKeepAlive()

    /** The last platform work before the app says it is ready. Android checks its password reset files here. */
    suspend fun beforeReady()
}
