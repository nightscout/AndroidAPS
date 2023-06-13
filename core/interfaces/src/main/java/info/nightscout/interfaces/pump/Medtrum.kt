package info.nightscout.interfaces.pump

/**
 * Functionality supported by Medtrum* pumps only
 */
interface Medtrum {

    fun loadEvents(): PumpEnactResult               // events history to build treatments from
    fun setUserOptions(): PumpEnactResult           // set user settings
}