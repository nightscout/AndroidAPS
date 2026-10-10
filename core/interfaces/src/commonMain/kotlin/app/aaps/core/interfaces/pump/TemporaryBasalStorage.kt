package app.aaps.core.interfaces.pump

interface TemporaryBasalStorage {

    fun add(temporaryBasal: PumpSync.PumpState.TemporaryBasal)
    fun findTemporaryBasal(time: Long, rate: Double): PumpSync.PumpState.TemporaryBasal?
}