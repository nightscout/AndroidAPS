package info.nightscout.androidaps.plugins.sensitivity

import androidx.collection.LongSparseArray
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.extensions.isEPSEvent5minBack
import info.nightscout.androidaps.extensions.isTherapyEventEvent5minBack
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.Sensitivity.SensitivityType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensDataStore
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
open class SensitivityWeightedAveragePlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    sp: SP,
    private val profileFunction: ProfileFunction,
    private val dateUtil: DateUtil,
    private val repository: AppRepository
) : AbstractSensitivityPlugin(PluginDescription()
    .mainType(PluginType.SENSITIVITY)
    .pluginIcon(R.drawable.ic_generic_icon)
    .pluginName(R.string.sensitivityweightedaverage)
    .shortName(R.string.sensitivity_shortname)
    .preferencesId(R.xml.pref_absorption_aaps)
    .description(R.string.description_sensitivity_weighted_average),
    injector, aapsLogger, resourceHelper, sp
) {

    override fun detectSensitivity(ads: AutosensDataStore, fromTime: Long, toTime: Long): AutosensResult {
        val age = sp.getString(R.string.key_age, "")
        var defaultHours = 24
        if (age == resourceHelper.gs(R.string.key_adult)) defaultHours = 24
        if (age == resourceHelper.gs(R.string.key_teenage)) defaultHours = 4
        if (age == resourceHelper.gs(R.string.key_child)) defaultHours = 4
        val hoursForDetection = sp.getInt(R.string.key_openapsama_autosens_period, defaultHours)
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
        val siteChanges = repository.getTherapyEventDataFromTime(fromTime, TherapyEvent.Type.CANNULA_CHANGE, true).blockingGet()
        val profileSwitches = repository.getEffectiveProfileSwitchDataFromTime(fromTime, true).blockingGet()
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
            if (profileSwitches.isEPSEvent5minBack(autosensData.time)) {
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
            val secondsFromMidnight = Profile.secondsFromMidnight(autosensData.time)
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
        val sens = profile.getIsfMgdl()
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
        val output = fillResult(ratio, current.cob, pastSensitivity, ratioLimit,
            sensResult, data.size())
        aapsLogger.debug(LTag.AUTOSENS, "Sensitivity to: "
            + dateUtil.dateAndTimeString(toTime) +
            " ratio: " + output.ratio
            + " mealCOB: " + current.cob)
        return output
    }

    override val id: SensitivityType
        get() = SensitivityType.SENSITIVITY_WEIGHTED

    override fun configuration(): JSONObject {
        val c = JSONObject()
        try {
            c.put(resourceHelper.gs(R.string.key_absorption_maxtime), sp.getDouble(R.string.key_absorption_maxtime, Constants.DEFAULT_MAX_ABSORPTION_TIME))
            c.put(resourceHelper.gs(R.string.key_openapsama_autosens_period), sp.getInt(R.string.key_openapsama_autosens_period, 24))
            c.put(resourceHelper.gs(R.string.key_openapsama_autosens_max), sp.getDouble(R.string.key_openapsama_autosens_max, 1.2))
            c.put(resourceHelper.gs(R.string.key_openapsama_autosens_min), sp.getDouble(R.string.key_openapsama_autosens_min, 0.7))
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return c
    }

    override fun applyConfiguration(configuration: JSONObject) {
        try {
            if (configuration.has(resourceHelper.gs(R.string.key_absorption_maxtime))) sp.putDouble(R.string.key_absorption_maxtime, configuration.getDouble(resourceHelper.gs(R.string.key_absorption_maxtime)))
            if (configuration.has(resourceHelper.gs(R.string.key_openapsama_autosens_period))) sp.putDouble(R.string.key_openapsama_autosens_period, configuration.getDouble(resourceHelper.gs(R.string.key_openapsama_autosens_period)))
            if (configuration.has(resourceHelper.gs(R.string.key_openapsama_autosens_max))) sp.getDouble(R.string.key_openapsama_autosens_max, configuration.getDouble(resourceHelper.gs(R.string.key_openapsama_autosens_max)))
            if (configuration.has(resourceHelper.gs(R.string.key_openapsama_autosens_min))) sp.getDouble(R.string.key_openapsama_autosens_min, configuration.getDouble(resourceHelper.gs(R.string.key_openapsama_autosens_min)))
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }
}