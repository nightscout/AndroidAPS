package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.PumpEnactResult

/**
 * Functionality supported by Dana* pumps only
 */
interface Dana {

    fun loadHistory(type: Byte): PumpEnactResult    // for history browser
    fun loadEvents(): PumpEnactResult               // events history to build treatments from
    fun setUserOptions(): PumpEnactResult           // like AnyDana does
    fun clearPairing()
}