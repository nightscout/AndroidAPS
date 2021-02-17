package info.nightscout.androidaps.plugins.pump.mdi

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.common.ManufacturerType
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.InstanceId.instanceId
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MDIPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    commandQueue: CommandQueueProvider,
    private val treatmentsPlugin: TreatmentsPlugin
) : PumpPluginBase(PluginDescription()
    .mainType(PluginType.PUMP)
    .pluginIcon(R.drawable.ic_ict)
    .pluginName(R.string.mdi)
    .description(R.string.description_pump_mdi),
    injector, aapsLogger, resourceHelper, commandQueue
), PumpInterface {

    override val pumpDescription = PumpDescription()

    init {
        pumpDescription.isBolusCapable = true
        pumpDescription.bolusStep = 0.5
        pumpDescription.isExtendedBolusCapable = false
        pumpDescription.isTempBasalCapable = false
        pumpDescription.isSetBasalProfileCapable = false
        pumpDescription.isRefillingCapable = false
        pumpDescription.isBatteryReplaceable = false
    }

    override val isFakingTempsByExtendedBoluses: Boolean = false

    override fun loadTDDs(): PumpEnactResult = PumpEnactResult(injector)
    override fun isInitialized(): Boolean = true
    override fun isSuspended(): Boolean = false
    override fun isBusy(): Boolean = false
    override fun isConnected(): Boolean = true
    override fun isConnecting(): Boolean = false
    override fun isHandshakeInProgress(): Boolean = false
    override fun connect(reason: String) {}
    override fun disconnect(reason: String) {}
    override fun waitForDisconnectionInSeconds(): Int = 0
    override fun stopConnecting() {}
    override fun getPumpStatus(reason: String) {}
    override fun setNewBasalProfile(profile: Profile): PumpEnactResult = PumpEnactResult(injector).success(true)
    override fun isThisProfileSet(profile: Profile): Boolean = false
    override fun lastDataTime(): Long = System.currentTimeMillis()
    override val baseBasalRate: Double = 0.0
    override val reservoirLevel: Double = -1.0
    override val batteryLevel: Int = -1

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        val result = PumpEnactResult(injector)
        result.success = true
        result.bolusDelivered = detailedBolusInfo.insulin
        result.carbsDelivered = detailedBolusInfo.carbs
        result.comment = resourceHelper.gs(R.string.virtualpump_resultok)
        treatmentsPlugin.addToHistoryTreatment(detailedBolusInfo, false)
        return result
    }

    override fun stopBolusDelivering() {}
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean): PumpEnactResult {
        val result = PumpEnactResult(injector)
        result.success = false
        result.comment = resourceHelper.gs(R.string.pumperror)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Setting temp basal absolute: $result")
        return result
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean): PumpEnactResult {
        val result = PumpEnactResult(injector)
        result.success = false
        result.comment = resourceHelper.gs(R.string.pumperror)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Settings temp basal percent: $result")
        return result
    }

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        val result = PumpEnactResult(injector)
        result.success = false
        result.comment = resourceHelper.gs(R.string.pumperror)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Setting extended bolus: $result")
        return result
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        val result = PumpEnactResult(injector)
        result.success = false
        result.comment = resourceHelper.gs(R.string.pumperror)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Cancel temp basal: $result")
        return result
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        val result = PumpEnactResult(injector)
        result.success = false
        result.comment = resourceHelper.gs(R.string.pumperror)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Canceling extended bolus: $result")
        return result
    }

    override fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject {
        val now = System.currentTimeMillis()
        val pump = JSONObject()
        val status = JSONObject()
        val extended = JSONObject()
        try {
            status.put("status", "normal")
            extended.put("Version", version)
            extended.put("ActiveProfile", profileName)
            status.put("timestamp", DateUtil.toISOString(now))
            pump.put("status", status)
            pump.put("extended", extended)
            pump.put("clock", DateUtil.toISOString(now))
        } catch (e: JSONException) {
            aapsLogger.error("Exception: ", e)
        }
        return pump
    }

    override fun manufacturer(): ManufacturerType = ManufacturerType.AndroidAPS
    override fun model(): PumpType = PumpType.MDI
    override fun serialNumber(): String = instanceId()
    override fun shortStatus(veryShort: Boolean): String = model().model
    override fun canHandleDST(): Boolean = true
}