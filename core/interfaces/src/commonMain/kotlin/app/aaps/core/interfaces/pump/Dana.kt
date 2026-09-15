package app.aaps.core.interfaces.pump

/**
 * Functionality supported by Dana* pumps only
 */
interface Dana {

    fun loadHistory(type: Byte): PumpEnactResult    // for history browser
    fun loadEvents(): PumpEnactResult               // events history to build treatments from
    fun setUserOptions(): PumpEnactResult           // like AnyDana does
    fun clearPairing()
}