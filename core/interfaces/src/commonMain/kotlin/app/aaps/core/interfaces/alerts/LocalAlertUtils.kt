package app.aaps.core.interfaces.alerts

import kotlinx.coroutines.flow.StateFlow

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
     * Run the CGM alarms (see [CgmAlarmType]): check the current glucose against the glucose alarms
     * the user has set up (urgent low, urgent low soon, low, fall rate, high, rise rate), and the
     * sensor state of the active BG source against the sensor alarms, then raise, repeat or clear
     * their notifications.
     *
     * The glucose alarms work for any CGM source, because they read the last stored glucose. The
     * sensor alarms work for a source that reports its sensor state
     * ([app.aaps.core.interfaces.source.CgmSensorAlertProvider]). It only notifies: it never changes
     * dosing, the loop or the glucose values the loop receives. The glucose alarms do nothing on stale
     * data, because the stale-data alarm owns that case.
     */
    suspend fun checkGlucoseAlerts()

    /**
     * The user says they treated the current hypo (took carbs): clear the low-glucose alarm and hold
     * it for about the time carbs need to work, instead of the shorter automatic repeat.
     *
     * This only silences. The alarm comes back by itself if glucose is still low when the hold ends,
     * and [checkGlucoseAlerts] clears the hold as soon as glucose has recovered.
     *
     * Returns at once. The hold is written in the background, after a check that is running at that
     * moment has finished, so the check cannot overwrite it.
     */
    fun snoozeHypoAfterTreatment()

    /**
     * The user acknowledged a CGM alarm ([type]), or every CGM alarm that is going off when [type] is
     * null: it stops, and stays quiet for its repeat time. When the repeat time is over and the
     * condition is still there, it goes off again. Returns at once.
     */
    fun acknowledgeCgmAlarms(type: CgmAlarmType? = null)

    /**
     * Mute the CGM alarms for [minutes], or end the mute with 0. Urgent low is never muted
     * ([CgmAlarmType.neverMuted]). Returns at once.
     */
    fun muteCgmAlarms(minutes: Int)

    /** When the mute of the CGM alarms ends (epoch ms), 0 when they are not muted. */
    val cgmAlarmsMutedUntil: StateFlow<Long>

    /**
     * Play [type] once as a test, with its sound and on the same path as a real alarm, so the user
     * hears what it sounds like at night. It goes on until the user dismisses it. It changes nothing
     * in the state of the real alarm.
     */
    fun testCgmAlarm(type: CgmAlarmType)
}