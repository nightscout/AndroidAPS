package info.nightscout.androidaps

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.interfaces.PumpInterface
import info.nightscout.androidaps.plugins.common.ManufacturerType
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.utils.TimeChangeType
import org.json.JSONObject

@Suppress("MemberVisibilityCanBePrivate")
class TestPumpPlugin(val injector: HasAndroidInjector) : PumpInterface {

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
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean): PumpEnactResult = PumpEnactResult(injector).success(true)
    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean): PumpEnactResult = PumpEnactResult(injector).success(true)
    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult = PumpEnactResult(injector).success(true)
    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult = PumpEnactResult(injector).success(true)
    override fun cancelExtendedBolus(): PumpEnactResult = PumpEnactResult(injector).success(true)
    override fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject = JSONObject()
    override fun manufacturer(): ManufacturerType = ManufacturerType.AndroidAPS
    override fun model(): PumpType = PumpType.GenericAAPS
    override fun serialNumber(): String = "1"
    override fun shortStatus(veryShort: Boolean): String = ""
    override val isFakingTempsByExtendedBoluses: Boolean = false
    override fun loadTDDs(): PumpEnactResult = PumpEnactResult(injector).success(true)
    override fun canHandleDST(): Boolean = true
    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {}
}