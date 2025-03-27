package app.aaps.plugins.sensitivity

import android.content.Context
import androidx.collection.LongSparseArray
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.model.TE
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.Sensitivity.SensitivityType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.put
import app.aaps.core.objects.extensions.store
import app.aaps.core.utils.MidnightUtils
import app.aaps.plugins.sensitivity.extensions.isPSEvent5minBack
import app.aaps.plugins.sensitivity.extensions.isTherapyEventEvent5minBack
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class SensitivityWeightedAveragePlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer,
    private val activePlugin: ActivePlugin
) : AbstractSensitivityPlugin(
    PluginDescription()
        .mainType(PluginType.SENSITIVITY)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_generic_icon)
        .pluginName(R.string.sensitivity_weighted_average)
        .shortName(R.string.sensitivity_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.description_sensitivity_weighted_average),
    aapsLogger, rh, preferences
) {

    override fun detectSensitivity(ads: AutosensDataStore, fromTime: Long, toTime: Long): AutosensResult {
        val hoursForDetection = preferences.get(IntKey.AutosensPeriod)
        if (ads.autosensDataTable.size() < 4) {
            aapsLogger.debug(LTag.AUTOSENS, "No autosens data available. lastDataTime=" + ads.lastDataTime(dateUtil))
            return AutosensResult()
        }
        val current = ads.getAutosensDataAtTime(toTime) // this is running inside lock already
        if (current == null) {
            aapsLogger.debug(LTag.AUTOSENS, "No autosens data available. toTime: " + dateUtil.dateAndTimeString(toTime) + " lastDataTime: " + ads.lastDataTime(dateUtil))
            return AutosensResult()
        }
        val profile = profileFunction.getProfile()
        if (profile == null) {
            aapsLogger.debug(LTag.AUTOSENS, "No profile available")
            return AutosensResult()
        }
        val siteChanges = persistenceLayer.getTherapyEventDataFromTime(fromTime, TE.Type.CANNULA_CHANGE, true)
        val profileSwitches = persistenceLayer.getProfileSwitchesFromTime(fromTime, true).blockingGet()
        var pastSensitivity = ""
        var index = 0
        val data = LongSparseArray<Double>()
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
            if (autosensData.time < toTime - hoursForDetection * 60 * 60 * 1000L) {
                index++
                continue
            }

            // reset deviations after site change
            if (siteChanges.isTherapyEventEvent5minBack(autosensData.time)) {
                data.clear()
                pastSensitivity += "(SITECHANGE)"
            }

            // reset deviations after profile switch
            if (profileSwitches.isPSEvent5minBack(autosensData.time)) {
                data.clear()
                pastSensitivity += "(PROFILESWITCH)"
            }
            var deviation = autosensData.deviation

            //set positive deviations to zero if bg < 80
            if (autosensData.bg < 80 && deviation > 0) deviation = 0.0

            //data.append(autosensData.time);
            val reverseWeight = (toTime - autosensData.time) / (5 * 60 * 1000L)
            if (autosensData.validDeviation) data.append(reverseWeight, deviation)
            pastSensitivity += autosensData.pastSensitivity
            val secondsFromMidnight = MidnightUtils.secondsFromMidnight(autosensData.time)
            if (secondsFromMidnight % 3600 < 2.5 * 60 || secondsFromMidnight % 3600 > 57.5 * 60) {
                pastSensitivity += "(" + (secondsFromMidnight / 3600.0).roundToInt() + ")"
            }
            index++
        }
        if (data.size() == 0) {
            aapsLogger.debug(LTag.AUTOSENS, "Data size: " + data.size() + " fromTime: " + dateUtil.dateAndTimeString(fromTime) + " toTime: " + dateUtil.dateAndTimeString(toTime))
            return AutosensResult()
        } else {
            aapsLogger.debug(LTag.AUTOSENS, "Data size: " + data.size() + " fromTime: " + dateUtil.dateAndTimeString(fromTime) + " toTime: " + dateUtil.dateAndTimeString(toTime))
        }
        var weightedSum = 0.0
        var weights = 0.0
        val highestWeight = data.keyAt(data.size() - 1)
        for (i in 0 until data.size()) {
            val reversedWeight = data.keyAt(i)
            val value = data.valueAt(i)
            val weight = (highestWeight - reversedWeight) / 2.0
            weights += weight
            weightedSum += weight * value
        }
        if (weights == 0.0) {
            return AutosensResult()
        }
        //val sens = profile.getIsfMgdl(toTime, current.bg, "SensitivityWeightedAveragePlugin")
        val sens = current.sens
        val ratioLimit = ""
        val sensResult: String
        aapsLogger.debug(LTag.AUTOSENS, "Records: $index   $pastSensitivity")
        val average = weightedSum / weights
        val basalOff = average * (60 / 5.0) / sens
        val ratio = 1 + basalOff / profile.getMaxDailyBasal()
        sensResult = when {
            average < 0 -> "Excess insulin sensitivity detected"
            average > 0 -> "Excess insulin resistance detected"
            else        -> "Sensitivity normal"
        }
        aapsLogger.debug(LTag.AUTOSENS, sensResult)
        val output = fillResult(
            ratio, current.cob, pastSensitivity, ratioLimit,
            sensResult, data.size()
        )
        aapsLogger.debug(
            LTag.AUTOSENS, "Sensitivity to: "
                + dateUtil.dateAndTimeString(toTime) +
                " ratio: " + output.ratio
                + " mealCOB: " + current.cob
        )
        return output
    }

    override fun maxAbsorptionHours(): Double = preferences.get(DoubleKey.AbsorptionMaxTime)
    override val isMinCarbsAbsorptionDynamic: Boolean = true
    override val isOref1: Boolean = false

    override val id: SensitivityType
        get() = SensitivityType.SENSITIVITY_WEIGHTED

    override fun configuration(): JSONObject =
        JSONObject()
            .put(DoubleKey.AutosensMin, preferences)
            .put(DoubleKey.AutosensMax, preferences)
            .put(DoubleKey.AbsorptionMaxTime, preferences)
            .put(IntKey.AutosensPeriod, preferences)

    override fun applyConfiguration(configuration: JSONObject) {
        configuration
            .store(DoubleKey.AutosensMin, preferences)
            .store(DoubleKey.AutosensMax, preferences)
            .store(DoubleKey.AbsorptionMaxTime, preferences)
            .store(IntKey.AutosensPeriod, preferences)

    }

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        // Share with SensitivityAAPSPlugin
        val aapsPlugin = activePlugin.getPluginsList().firstOrNull { it::class == SensitivityAAPSPlugin::class } ?: return
        aapsPlugin.addPreferenceScreen(preferenceManager, parent, context, requiredKey)
    }
}