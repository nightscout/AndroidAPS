package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.PumpEnactResult

interface Diaconn {
    fun loadHistory(): PumpEnactResult // for history browser
    fun setUserOptions(): PumpEnactResult // pump etc settings
}