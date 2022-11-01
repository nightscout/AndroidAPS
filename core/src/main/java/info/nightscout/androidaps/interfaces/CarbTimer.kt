package info.nightscout.androidaps.interfaces

interface CarbTimer {

    /**
     * Generate reminder via [info.nightscout.androidaps.utils.TimerUtil]
     *
     * @param seconds seconds to the future
     */
    fun scheduleTimeToEatReminder(seconds: Int)

    /**
     * Create new Automation event to alarm when is time to eat
     */
    fun scheduleAutomationEventEatReminder()

    /**
     * Remove Automation event
     */
    fun removeAutomationEventEatReminder()
}