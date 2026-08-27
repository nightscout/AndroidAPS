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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
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

    override suspend fun shortStatus(veryShort: Boolean): String {
        val pump = activePlugin.activePump
        // Concentration comes from the running profile, which owns the authoritative iCfg. Null means
        // no profile is running, so there is no IU/cU conversion to state — the last-bolus line is
        // omitted rather than rendered at a guessed concentration. Same source as generatePumpJsonStatus.
        val concentration = profileFunction.getProfile()?.insulinConcentration()
        val lines = mutableListOf<String>()
        if (!pump.isInitialized())
            lines += rh.gs(R.string.short_status_not_initialized)
        else if (pump.isSuspended())
            lines += rh.gs(app.aaps.core.interfaces.R.string.pumpsuspended)
        else {
            if (pump.lastDataTime.value != 0L) {
                val agoMillis: Long = System.currentTimeMillis() - pump.lastDataTime.value
                val agoMin = (agoMillis / 60.0 / 1000.0).toInt()
                lines += rh.gs(R.string.short_status_last_connection, agoMin)
            }

            pump.lastBolusAmount.value?.let { lastBolusAmount ->
                pump.lastBolusTime.value?.let { lastBolusTimestamp ->
                    concentration?.let {
                        lines += rh.gs(
                            R.string.short_status_last_bolus,
                            decimalFormatter.to2Decimal(lastBolusAmount.iU(it)),
                            dateUtil.timeString(lastBolusTimestamp)
                        )
                    }
                }
            }

            val expectedState = pumpSync.expectedPumpState()
            expectedState.temporaryBasal?.let { temporaryBasal ->
                lines += rh.gs(R.string.short_status_temp_basal, temporaryBasal.toStringFull(dateUtil, rh))
            }

            expectedState.extendedBolus?.let { extendedBolus ->
                lines += rh.gs(R.string.short_status_temp_basal, extendedBolus.toStringFull(dateUtil, rh))
            }

            if (pump.batteryLevel.value != null && pump.batteryLevel.value != 0) lines += rh.gs(R.string.short_status_battery, pump.batteryLevel.value)
            val additionalStatus = pump.pumpSpecificShortStatus(veryShort)
            if (additionalStatus.isNotEmpty()) lines += additionalStatus
        }
        val shortStatusString = lines.joinToString("\n")
        return shortStatusString
    }

    /**
     * Generate JSON status of pump sent to the NS
     */
    override suspend fun generatePumpJsonStatus(): JsonObject {
        val pump = activePlugin.activePump
        // do not send data older than 60 minutes
        if (dateUtil.isOlderThan(date = pump.lastDataTime.value, minutes = 60)) return JsonObject(emptyMap())
        // Do not send any info if there is no running profile
        val profile = profileFunction.getProfile() ?: return JsonObject(emptyMap())
        val expectedPumpState = pumpSync.expectedPumpState()
        val now = System.currentTimeMillis()
        val runningMode = persistenceLayer.getRunningModeActiveAt(now)
        val profileName = profileFunction.getProfileName()

        return buildJsonObject {
            put("reservoir", pump.reservoirLevel.value.iU(profile.insulinConcentration()).toInt())
            put("clock", dateUtil.toISOString(now))
            putJsonObject("battery") {
                putIfThereIsValue("percent", pump.batteryLevel.value)
            }
            putJsonObject("status") {
                put("status", translator.translate(runningMode.mode))
                put("timestamp", dateUtil.toISOString(pump.lastDataTime.value))
            }
            putJsonObject("extended") {
                put("Version", config.VERSION_NAME + "-" + config.BUILD_VERSION)
                putIfThereIsValue("LastBolus", dateUtil.dateAndTimeStringNullable(pump.lastBolusTime.value))
                putIfThereIsValue("LastBolusAmount", pump.lastBolusAmount.value?.iU(profile.insulinConcentration()))
                putIfThereIsValue("TempBasalAbsoluteRate", expectedPumpState.temporaryBasal?.convertedToAbsolute(now, profile))
                putIfThereIsValue("TempBasalStart", dateUtil.dateAndTimeStringNullable(expectedPumpState.temporaryBasal?.timestamp))
                putIfThereIsValue("TempBasalRemaining", expectedPumpState.temporaryBasal?.plannedRemainingMinutes)
                putIfThereIsValue("ExtendedBolusAbsoluteRate", expectedPumpState.extendedBolus?.rate)
                putIfThereIsValue("ExtendedBolusStart", dateUtil.dateAndTimeStringNullable(expectedPumpState.extendedBolus?.timestamp))
                putIfThereIsValue("ExtendedBolusRemaining", expectedPumpState.extendedBolus?.plannedRemainingMinutes)
                putIfThereIsValue("BaseBasalRate", pump.baseBasalRate.iU(profile.insulinConcentration(), true))
                put("ActiveProfile", profileName)
                // Driver specific entries last, matching the previous order - the driver used to be
                // handed the finished object and could still add to it.
                pump.extendedStatus().forEach { (key, value) -> put(key, value) }
            }
        }
    }
}
