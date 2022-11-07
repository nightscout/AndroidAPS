package info.nightscout.interfaces

import info.nightscout.interfaces.data.PumpEnactResult

interface Insight {

    fun setTBROverNotification(enabled: Boolean): PumpEnactResult
    fun startPump(): PumpEnactResult
    fun stopPump(): PumpEnactResult
}