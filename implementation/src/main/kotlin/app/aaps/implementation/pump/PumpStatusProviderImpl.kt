package app.aaps.implementation.pump

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpStatusProvider
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.objects.extensions.putIfThereIsValue
import app.aaps.implementation.R
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PumpStatusProviderImpl @Inject constructor(
    private val activePlugin: ActivePlugin,
    private val pumpSync: PumpSync,
    private val profileFunction: ProfileFunction,
    private val persistenceLayer: PersistenceLayer,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter,
    private val translator: Translator,
    private val config: Config
) : PumpStatusProvider {

    override fun shortStatus(veryShort: Boolean): String {
        val pump = activePlugin.activePump

        val lines = mutableListOf<String>()
        if (!pump.isInitialized())
            lines += rh.gs(R.string.short_status_not_initialized)
        else if (pump.isSuspended())
            lines += rh.gs(app.aaps.core.ui.R.string.pumpsuspended)
        else {
            if (pump.lastDataTime != 0L) {
                val agoMillis: Long = System.currentTimeMillis() - pump.lastDataTime
                val agoMin = (agoMillis / 60.0 / 1000.0).toInt()
                lines += rh.gs(R.string.short_status_last_connection, agoMin)
            }

            pump.lastBolusAmount?.let { lastBolusAmount ->
                pump.lastBolusTime?.let { lastBolusTimestamp ->
                    lines += rh.gs(
                        R.string.short_status_last_bolus,
                        decimalFormatter.to2Decimal(lastBolusAmount),
                        dateUtil.timeString(lastBolusTimestamp)
                    )
                }
            }

            pumpSync.expectedPumpState().temporaryBasal?.let { temporaryBasal ->
                lines += rh.gs(R.string.short_status_temp_basal, temporaryBasal.toStringFull(dateUtil, rh))
            }

            pumpSync.expectedPumpState().extendedBolus?.let { extendedBolus ->
                lines += rh.gs(R.string.short_status_temp_basal, extendedBolus.toStringFull(dateUtil, rh))
            }

            if (pump.batteryLevel != null && pump.batteryLevel != 0) lines += rh.gs(R.string.short_status_battery, pump.batteryLevel)
            val additionalStatus = pump.pumpSpecificShortStatus(veryShort)
            if (additionalStatus.isNotEmpty()) lines += additionalStatus
        }
        val shortStatusString = lines.joinToString("\n")
        return shortStatusString
    }

    /**
     * Generate JSON status of pump sent to the NS
     */
    override fun generatePumpJsonStatus(): JSONObject {
        val pump = activePlugin.activePump
        // do not send data older than 60 minutes
        if (dateUtil.isOlderThan(date = pump.lastDataTime, minutes = 60)) return JSONObject()
        // Do not send any info if there is no running profile
        val profile = profileFunction.getProfile() ?: return JSONObject()
        val expectedPumpState = pumpSync.expectedPumpState()
        val now = System.currentTimeMillis()
        val runningMode = persistenceLayer.getRunningModeActiveAt(now)

        val pumpJson = JSONObject()
            .put("reservoir", pump.reservoirLevel.toInt())
            .put("clock", dateUtil.toISOString(now))
        val battery = JSONObject().putIfThereIsValue("percent", pump.batteryLevel)
        val status = JSONObject()
            .put("status", translator.translate(runningMode.mode))
            .put("timestamp", dateUtil.toISOString(pump.lastDataTime))
        val extended = JSONObject()
            .put("Version", config.VERSION_NAME + "-" + config.BUILD_VERSION)
            .putIfThereIsValue("LastBolus", dateUtil.dateAndTimeStringNullable(pump.lastBolusTime))
            .putIfThereIsValue("LastBolusAmount", pump.lastBolusAmount)
            .putIfThereIsValue("TempBasalAbsoluteRate", expectedPumpState.temporaryBasal?.convertedToAbsolute(now, profile))
            .putIfThereIsValue("TempBasalStart", dateUtil.dateAndTimeStringNullable(expectedPumpState.temporaryBasal?.timestamp))
            .putIfThereIsValue("TempBasalRemaining", expectedPumpState.temporaryBasal?.plannedRemainingMinutes)
            .putIfThereIsValue("ExtendedBolusAbsoluteRate", expectedPumpState.extendedBolus?.rate)
            .putIfThereIsValue("ExtendedBolusStart", dateUtil.dateAndTimeStringNullable(expectedPumpState.extendedBolus?.timestamp))
            .putIfThereIsValue("ExtendedBolusRemaining", expectedPumpState.extendedBolus?.plannedRemainingMinutes)
            .putIfThereIsValue("BaseBasalRate", pump.baseBasalRate)
            .put("ActiveProfile", profileFunction.getProfileName())

        // grab more values from pump if provided
        pump.updateExtendedJsonStatus(extended)
        return pumpJson
            .put("battery", battery)
            .put("status", status)
            .put("extended", extended)
    }
}