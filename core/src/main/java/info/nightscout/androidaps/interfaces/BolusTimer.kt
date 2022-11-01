package info.nightscout.androidaps.interfaces

interface BolusTimer {

    /**
     * Create new Automation event to alarm when is time to bolus
     */
    fun scheduleAutomationEventBolusReminder()

    /**
     * Remove Automation event
     */
    fun removeAutomationEventBolusReminder()
}