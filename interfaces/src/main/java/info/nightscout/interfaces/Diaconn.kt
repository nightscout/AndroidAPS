package info.nightscout.interfaces

import info.nightscout.interfaces.data.PumpEnactResult

interface Diaconn {

    fun loadHistory(): PumpEnactResult // for history browser
    fun setUserOptions(): PumpEnactResult // pump etc settings
}