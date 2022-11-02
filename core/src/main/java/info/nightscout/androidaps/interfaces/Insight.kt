package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.PumpEnactResult

interface Insight {

    fun setTBROverNotification(enabled: Boolean): PumpEnactResult
    fun startPump(): PumpEnactResult
    fun stopPump(): PumpEnactResult
}