package app.aaps.shared.tests

import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.implementation.pump.PumpEnactResultObject
import org.json.JSONObject

@Suppress("MemberVisibilityCanBePrivate")
class TestPumpPlugin(val rh: ResourceHelper) : Pump {

    var connected = false
    var isProfileSet = true
    var pumpSuspended = false

    override fun isConnected() = connected
    override fun isConnecting() = false
    override fun isHandshakeInProgress() = false
    val lastData = 0L

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

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult = PumpEnactResultObject(rh)
    override fun isThisProfileSet(profile: Profile): Boolean = isProfileSet
    override fun lastDataTime(): Long = lastData
    override val baseBasalRate: Double get() = baseBasal
    override val reservoirLevel: Double = 0.0
    override val batteryLevel: Int = 0
    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult = PumpEnactResultObject(rh).success(true)
    override fun stopBolusDelivering() { /* not needed */
    }

    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult =
        PumpEnactResultObject(rh).success(true)

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult =
        PumpEnactResultObject(rh).success(true)

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult = PumpEnactResultObject(rh).success(true)
    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult = PumpEnactResultObject(rh).success(true)
    override fun cancelExtendedBolus(): PumpEnactResult = PumpEnactResultObject(rh).success(true)
    override fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject = JSONObject()
    override fun manufacturer(): ManufacturerType = ManufacturerType.AAPS
    override fun model(): PumpType = PumpType.GENERIC_AAPS
    override fun serialNumber(): String = "1"
    override fun shortStatus(veryShort: Boolean): String = "Virtual Pump"
    override val isFakingTempsByExtendedBoluses: Boolean = false
    override fun loadTDDs(): PumpEnactResult = PumpEnactResultObject(rh).success(true)
    override fun canHandleDST(): Boolean = true
    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) { /* not needed */
    }
}