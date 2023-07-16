package info.nightscout.interfaces.pump

/**
 * Functionality supported by Medtrum* pumps only
 */
interface Medtrum {

    fun loadEvents(): PumpEnactResult               // events history to build treatments from
    fun setUserOptions(): PumpEnactResult           // set user settings
    fun clearAlarms(): PumpEnactResult              // clear alarms
    fun deactivate(): PumpEnactResult               // deactivate patch
    fun updateTime(): PumpEnactResult               // update time
}
