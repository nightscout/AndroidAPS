package app.aaps.plugins.sensitivity

import app.aaps.core.data.model.TE
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.Sensitivity.SensitivityType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.put
import app.aaps.core.objects.extensions.store
import app.aaps.core.ui.compose.icons.IcAs
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.utils.MidnightUtils
import app.aaps.core.utils.Percentile
import app.aaps.plugins.sensitivity.extensions.isPSEvent5minBack
import app.aaps.plugins.sensitivity.extensions.isTherapyEventEvent5minBack
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import java.util.Arrays
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class SensitivityAAPSPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer
) : AbstractSensitivityPlugin(
    PluginDescription()
        .mainType(PluginType.SENSITIVITY)
        .icon(IcAs)
        .pluginName(R.string.sensitivity_aaps)
        .shortName(R.string.sensitivity_shortname)
        .description(R.string.description_sensitivity_aaps),
    aapsLogger, rh, preferences
) {

    override fun detectSensitivity(ads: AutosensDataStore, fromTime: Long, toTime: Long): AutosensResult {
        val hoursForDetection = preferences.get(IntKey.AutosensPeriod)
        val profile = runBlocking { profileFunction.getProfile() }
        if (profile == null) {
            aapsLogger.error("No profile")
            return AutosensResult()
        }
        if (ads.autosensDataTable.size() < 4) {
            aapsLogger.debug(LTag.AUTOSENS, "No autosens data available. lastDataTime=" + ads.lastDataTime(dateUtil))
            return AutosensResult()
        }
        val current = ads.getAutosensDataAtTime(toTime) // this is running inside lock already
        if (current == null) {
            aapsLogger.debug(LTag.AUTOSENS, "No autosens data available. toTime: " + dateUtil.dateAndTimeString(toTime) + " lastDataTime: " + ads.lastDataTime(dateUtil))
            return AutosensResult()
        }
        val siteChanges = runBlocking { persistenceLayer.getTherapyEventDataFromTime(fromTime, TE.Type.CANNULA_CHANGE, true) }
        val profileSwitches = runBlocking { persistenceLayer.getProfileSwitchesFromTime(fromTime, true) }
        val deviationsArray: MutableList<Double> = ArrayList()
        var pastSensitivity = ""
        var index = 0
        while (index < ads.autosensDataTable.size()) {
            val autosensData = ads.autosensDataTable.valueAt(index)
            if (autosensData.time < fromTime) {
                index++
                continue
            }
            if (autosensData.time > toTime) {
                index++
                continue
            }

            // reset deviations after site change
            if (siteChanges.isTherapyEventEvent5minBack(autosensData.time)) {
                deviationsArray.clear()
                pastSensitivity += "(SITECHANGE)"
            }

            // reset deviations after profile switch
            if (profileSwitches.isPSEvent5minBack(autosensData.time)) {
                deviationsArray.clear()
                pastSensitivity += "(PROFILESWITCH)"
            }
            var deviation = autosensData.deviation

            //set positive deviations to zero if bg < 80
            if (autosensData.bg < 80 && deviation > 0) deviation = 0.0
            if (autosensData.validDeviation) if (autosensData.time > toTime - hoursForDetection * 60 * 60 * 1000L) deviationsArray.add(deviation)
            if (deviationsArray.size > hoursForDetection * 60 / 5) deviationsArray.removeAt(0)
            pastSensitivity += autosensData.pastSensitivity
            val secondsFromMidnight = MidnightUtils.secondsFromMidnight(autosensData.time)
            if (secondsFromMidnight % 3600 < 2.5 * 60 || secondsFromMidnight % 3600 > 57.5 * 60) {
                pastSensitivity += "(" + (secondsFromMidnight / 3600.0).roundToInt() + ")"
            }
            index++
        }
        val deviations = Array(deviationsArray.size) { i -> deviationsArray[i] }
        //val sens = profile.getIsfMgdl(toTime, current.bg, "SensitivityAAPSPlugin")
        val sens = current.sens
        val ratioLimit = ""
        val sensResult: String
        aapsLogger.debug(LTag.AUTOSENS, "Records: $index   $pastSensitivity")
        Arrays.sort(deviations)
        val percentile = Percentile.percentile(deviations, 0.50)
        val basalOff = percentile * (60.0 / 5.0) / sens
        val ratio = 1 + basalOff / profile.getMaxDailyBasal()
        sensResult = when {
            percentile < 0 -> "Excess insulin sensitivity detected"
            percentile > 0 -> "Excess insulin resistance detected"
            else           -> "Sensitivity normal"

        }
        aapsLogger.debug(LTag.AUTOSENS, sensResult)
        val output = fillResult(
            ratio, current.cob, pastSensitivity, ratioLimit,
            sensResult, deviationsArray.size
        )
        aapsLogger.debug(
            LTag.AUTOSENS, "Sensitivity to: "
                + dateUtil.dateAndTimeString(toTime) +
                " ratio: " + output.ratio
                + " mealCOB: " + current.cob
        )
        aapsLogger.debug(LTag.AUTOSENS, "Sensitivity to: deviations " + deviations.contentToString())
        return output
    }

    override fun maxAbsorptionHours(): Double = preferences.get(DoubleKey.AbsorptionMaxTime)
    override val isMinCarbsAbsorptionDynamic: Boolean = true
    override val isOref1: Boolean = false

    override val id: SensitivityType
        get() = SensitivityType.SENSITIVITY_AAPS

    override fun configuration(): JsonObject =
        JsonObject(emptyMap())
            .put(IntKey.AutosensPeriod, preferences)
            .put(DoubleKey.AbsorptionMaxTime, preferences)
            .put(DoubleKey.AutosensMin, preferences)
            .put(DoubleKey.AutosensMin, preferences)

    override fun applyConfiguration(configuration: JsonObject) {
        configuration
            .store(IntKey.AutosensPeriod, preferences)
            .store(DoubleKey.AutosensMin, preferences)
            .store(DoubleKey.AutosensMax, preferences)
            .store(DoubleKey.AbsorptionMaxTime, preferences)
    }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "sensitivity_aaps_settings",
        titleResId = R.string.absorption_settings_title,
        items = listOf(
            DoubleKey.AbsorptionMaxTime,
            IntKey.AutosensPeriod,
            PreferenceSubScreenDef(
                key = "absorption_aaps_advanced",
                titleResId = app.aaps.core.ui.R.string.advanced_settings_title,
                items = listOf(
                    DoubleKey.AutosensMax,
                    DoubleKey.AutosensMin
                )
            )
        ),
        icon = pluginDescription.icon
    )
}