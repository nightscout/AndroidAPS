package app.aaps.shared.tests

import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.implementation.pump.PumpEnactResultObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Suppress("MemberVisibilityCanBePrivate")
class TestPumpPlugin(val rh: ResourceHelper) : PumpWithConcentration {

    var connected = false
    var isProfileSet = true
    var pumpSuspended = false

    override fun isConnected() = connected
    override fun isConnecting() = false
    override fun isHandshakeInProgress() = false
    private val _lastDataTime = MutableStateFlow(0L)
    var lastData: Long
        get() = _lastDataTime.value
        set(value) {
            _lastDataTime.value = value
        }

    val baseBasal = 0.0
    override var pumpDescription = PumpDescription()

    override fun isInitialized(): Boolean = true
    override fun isSuspended(): Boolean = pumpSuspended
    override fun isBusy(): Boolean = false
    override fun connect(reason: String) {
        connected = true
    }

    override fun disconnect(reason: String) {
        connected = false
    }

    override fun stopConnecting() {
        connected = false
    }

    override fun waitForDisconnectionInSeconds(): Int = 0
    override fun getPumpStatus(reason: String) { /* not needed */
    }

    override fun setNewBasalProfile(profile: EffectiveProfile): PumpEnactResult = PumpEnactResultObject(rh)
    override fun setNewBasalProfile(profile: PumpProfile): PumpEnactResult = PumpEnactResultObject(rh)
    override fun isThisProfileSet(profile: EffectiveProfile): Boolean = isProfileSet
    override fun isThisProfileSet(profile: PumpProfile): Boolean = isProfileSet
    private val _lastBolusTime = MutableStateFlow<Long?>(null)
    override val lastBolusTime: StateFlow<Long?> = _lastBolusTime
    private val _lastBolusAmount = MutableStateFlow<PumpInsulin?>(null)
    override val lastBolusAmount: StateFlow<PumpInsulin?> = _lastBolusAmount
    override val lastDataTime: StateFlow<Long> = _lastDataTime
    override val baseBasalRate: PumpRate get() = PumpRate(baseBasal)
    private val _reservoirLevel = MutableStateFlow(PumpInsulin(0.0))
    override val reservoirLevel: StateFlow<PumpInsulin> = _reservoirLevel
    private val _batteryLevel = MutableStateFlow<Int?>(null)
    override val batteryLevel: StateFlow<Int?> = _batteryLevel
    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult = PumpEnactResultObject(rh).success(true)
    override fun stopBolusDelivering() { /* not needed */
    }

    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult =
        PumpEnactResultObject(rh).success(true)

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult =
        PumpEnactResultObject(rh).success(true)

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult = PumpEnactResultObject(rh).success(true)
    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult = PumpEnactResultObject(rh).success(true)
    override fun cancelExtendedBolus(): PumpEnactResult = PumpEnactResultObject(rh).success(true)
    override fun manufacturer(): ManufacturerType = ManufacturerType.AAPS
    override fun model(): PumpType = PumpType.GENERIC_AAPS
    override fun serialNumber(): String = "1"
    override fun pumpSpecificShortStatus(veryShort: Boolean): String = "Virtual Pump"
    override val isFakingTempsByExtendedBoluses: Boolean = false
    override fun loadTDDs(): PumpEnactResult = PumpEnactResultObject(rh).success(true)
    override fun canHandleDST(): Boolean = true
    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) { /* not needed */
    }

    override fun selectedActivePump(): Pump = this
}