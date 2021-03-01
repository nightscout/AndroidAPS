package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.PumpEnactResult

interface DanaRInterface {

    fun loadHistory(type: Byte): PumpEnactResult    // for history browser
    fun loadEvents(): PumpEnactResult               // events history to build treatments from
    fun setUserOptions(): PumpEnactResult           // like AnyDana does
}