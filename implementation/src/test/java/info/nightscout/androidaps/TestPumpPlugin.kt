package info.nightscout.androidaps

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.pump.Pump
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.defs.ManufacturerType
import info.nightscout.interfaces.pump.defs.PumpDescription
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.interfaces.utils.TimeChangeType
import org.json.JSONObject

@Suppress("MemberVisibilityCanBePrivate")
@OpenForTesting
class TestPumpPlugin(val injector: HasAndroidInjector) : Pump {

    var connected = false
    var isProfileSet = true

    override fun isConnected() = connected
    override fun isConnecting() = false
    override fun isHandshakeInProgress() = false
    val lastData = 0L

    val baseBasal = 0.0
    override val pumpDescription = PumpDescription()

    override fun isInitialized(): Boolean = true
    override fun isSuspended(): Boolean = false
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
    override fun getPumpStatus(reason: String) {}
    override fun setNewBasalProfile(profile: Profile): PumpEnactResult = PumpEnactResult(injector)
    override fun isThisProfileSet(profile: Profile): Boolean = isProfileSet
    override fun lastDataTime(): Long = lastData
    override val baseBasalRate: Double = baseBasal
    override val reservoirLevel: Double = 0.0
    override val batteryLevel: Int = 0
    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult = PumpEnactResult(injector).success(true)
    override fun stopBolusDelivering() {}
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult =
        PumpEnactResult(injector).success(true)

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult =
        PumpEnactResult(injector).success(true)

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult = PumpEnactResult(injector).success(true)
    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult = PumpEnactResult(injector).success(true)
    override fun cancelExtendedBolus(): PumpEnactResult = PumpEnactResult(injector).success(true)
    override fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject = JSONObject()
    override fun manufacturer(): ManufacturerType = ManufacturerType.AAPS
    override fun model(): PumpType = PumpType.GENERIC_AAPS
    override fun serialNumber(): String = "1"
    override fun shortStatus(veryShort: Boolean): String = ""
    override val isFakingTempsByExtendedBoluses: Boolean = false
    override fun loadTDDs(): PumpEnactResult = PumpEnactResult(injector).success(true)
    override fun canHandleDST(): Boolean = true
    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {}
}