package app.aaps.core.interfaces.alerts

interface LocalAlertUtils {

    /**
     * Check for unreachable pump (there was no connection with pump for some time).
     * Raise alarm if needed.
     * Overview notification with sound, Therapy event, SMS
     */
    fun checkPumpUnreachableAlarm(lastConnection: Long, isStatusOutdated: Boolean, isDisconnected: Boolean)

    /**
     * Preset next alarm at least 5 min after start of app
     * Call only at startup!
     */
    fun preSnoozeAlarms()

    /**
     * Shortens alarm times in case of setting changes or future data
     */
    fun shortenSnoozeInterval()

    /**
     * Report pump status has been read.
     * Shifts threshold for next alarm to now + preset_interval
     */
    suspend fun reportPumpStatusRead()

    /**
     * Check for missing BGs.
     * Raise alarm if needed.
     * Overview notification with sound, Therapy event
     */
    suspend fun checkStaleBGAlert()

    /**
     * Check the last glucose value against the alarms the user has set up (low, high, rapid fall)
     * and raise or dismiss their notifications.
     *
     * Works for every BG source, because it reads the last stored glucose value. It only notifies:
     * it never changes dosing or the loop. It does nothing when the data is stale, because the
     * missed readings alarm owns that case.
     */
    suspend fun checkGlucoseAlerts()

    /**
     * Check the sensor state of the active BG source (failed, ended, ending soon, no signal, warm-up
     * done, second sensor ready) and raise or dismiss its alarms.
     *
     * Only a source that implements [app.aaps.core.interfaces.source.CgmSensorAlertProvider] gets
     * these alarms; for every other source it does nothing. It only notifies: it never changes
     * dosing or the loop.
     */
    suspend fun checkSensorAlerts()
}