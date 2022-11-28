package info.nightscout.interfaces.pump

interface Insight {

    fun setTBROverNotification(enabled: Boolean): PumpEnactResult
    fun startPump(): PumpEnactResult
    fun stopPump(): PumpEnactResult
}