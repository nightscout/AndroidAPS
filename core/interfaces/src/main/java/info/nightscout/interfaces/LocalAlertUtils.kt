package info.nightscout.interfaces

interface LocalAlertUtils {

    fun checkPumpUnreachableAlarm(lastConnection: Long, isStatusOutdated: Boolean, isDisconnected: Boolean)

    /* Pre-snoozes the alarms with 5 minutes if no snooze exists.
     * Call only at startup!
     */
    fun preSnoozeAlarms()

    /* shortens alarm times in case of setting changes or future data
     */
    fun shortenSnoozeInterval()
    fun notifyPumpStatusRead()
    fun checkStaleBGAlert()
}