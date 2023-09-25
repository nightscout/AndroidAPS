package info.nightscout.sensitivity

import app.aaps.annotations.OpenForTesting
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.SMBDefaults
import app.aaps.core.interfaces.aps.Sensitivity.SensitivityType
import app.aaps.core.interfaces.configuration.Constants
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.MidnightUtils
import app.aaps.core.utils.Percentile
import app.aaps.database.entities.TherapyEvent
import dagger.android.HasAndroidInjector
import info.nightscout.database.impl.AppRepository
import info.nightscout.sensitivity.extensions.isPSEvent5minBack
import info.nightscout.sensitivity.extensions.isTherapyEventEvent5minBack
import org.json.JSONException
import org.json.JSONObject
import java.util.Arrays
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@OpenForTesting
@Singleton
class SensitivityOref1Plugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    sp: SP,
    private val profileFunction: ProfileFunction,
    private val dateUtil: DateUtil,
    private val repository: AppRepository
) : AbstractSensitivityPlugin(
    PluginDescription()
        .mainType(PluginType.SENSITIVITY)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_generic_icon)
        .pluginName(R.string.sensitivity_oref1)
        .shortName(R.string.sensitivity_shortname)
        .enableByDefault(true)
        .preferencesId(R.xml.pref_absorption_oref1)
        .description(R.string.description_sensitivity_oref1)
        .setDefault(),
    injector, aapsLogger, rh, sp
), PluginConstraints {

    override fun detectSensitivity(ads: AutosensDataStore, fromTime: Long, toTime: Long): AutosensResult {
        val profile = profileFunction.getProfile()
        if (profile == null) {
            aapsLogger.error("No profile")
            return AutosensResult()
        }
        if (ads.autosensDataTable.size() < 4) {
            aapsLogger.debug(LTag.AUTOSENS, "No autosens data available. lastDataTime=" + ads.lastDataTime(dateUtil))
            return AutosensResult()
        }

        // the current
        val current = ads.getAutosensDataAtTime(toTime) // this is running inside lock already
        if (current == null) {
            aapsLogger.debug(LTag.AUTOSENS, "No autosens data available. toTime: " + dateUtil.dateAndTimeString(toTime) + " lastDataTime: " + ads.lastDataTime(dateUtil))
            return AutosensResult()
        }
        val siteChanges = repository.getTherapyEventDataFromTime(fromTime, TherapyEvent.Type.CANNULA_CHANGE, true).blockingGet()
        val profileSwitches = repository.getProfileSwitchDataFromTime(fromTime, true).blockingGet()

        //[0] = 8 hour
        //[1] = 24 hour
        //deviationsHour has DeviationsArray
        val deviationsHour = mutableListOf(ArrayList(), ArrayList<Double>())
        val pastSensitivityArray = mutableListOf("", "")
        val sensResultArray = mutableListOf("", "")
        val ratioArray = mutableListOf(0.0, 0.0)
        val deviationCategory = listOf(96.0, 288.0)
        val ratioLimitArray = mutableListOf("", "")
        val hoursDetection = listOf(8.0, 24.0)
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
            var hourSegment = 0
            //hourSegment = 0 = 8 hour
            //hourSegment = 1 = 24 hour
            while (hourSegment < deviationsHour.size) {
                val deviationsArray = deviationsHour[hourSegment]
                var pastSensitivity = pastSensitivityArray[hourSegment]

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
                if (autosensData.validDeviation) if (autosensData.time > toTime - hoursDetection[hourSegment] * 60 * 60 * 1000L) deviationsArray.add(deviation)
                deviationsArray.addAll(autosensData.extraDeviation)
                if (deviationsArray.size > deviationCategory[hourSegment]) {
                    deviationsArray.removeAt(0)
                }
                pastSensitivity += autosensData.pastSensitivity
                val secondsFromMidnight = MidnightUtils.secondsFromMidnight(autosensData.time)
                if (secondsFromMidnight % 3600 < 2.5 * 60 || secondsFromMidnight % 3600 > 57.5 * 60) {
                    pastSensitivity += "(" + (secondsFromMidnight / 3600.0).roundToInt() + ")"
                }

                //Update the data back to the parent
                deviationsHour[hourSegment] = deviationsArray
                pastSensitivityArray[hourSegment] = pastSensitivity
                hourSegment++
            }
            index++
        }

        // when we have less than 8h/24 worth of deviation data, add up to 90m of zero deviations
        // this dampens any large sensitivity changes detected based on too little data, without ignoring them completely
        for (i in deviationsHour.indices) {
            val deviations = deviationsHour[i]
            aapsLogger.debug(LTag.AUTOSENS, "Using most recent " + deviations.size + " deviations")
            if (deviations.size < deviationCategory[i]) {
                val pad = ((1 - deviations.size.toDouble() / deviationCategory[i]) * 18).roundToInt()
                aapsLogger.debug(LTag.AUTOSENS, "Adding $pad more zero deviations")
                for (d in 0 until pad) {
                    deviations.add(0.0)
                }
            }
            //Update the data back to the parent
            deviationsHour[i] = deviations
        }
        var hourUsed = 0
        while (hourUsed < deviationsHour.size) {
            val deviationsArray: ArrayList<Double> = deviationsHour[hourUsed]
            val pastSensitivity = pastSensitivityArray[hourUsed]
            var sensResult = "(8 hours) "
            if (hourUsed == 1) sensResult = "(24 hours) "
            val ratioLimit = ""
            val deviations: Array<Double> = Array(deviationsArray.size) { i -> deviationsArray[i] }
            val sens = profile.getIsfMgdl()
            aapsLogger.debug(LTag.AUTOSENS, "Records: $index   $pastSensitivity")
            Arrays.sort(deviations)
            val pSensitive = Percentile.percentile(deviations, 0.50)
            val pResistant = Percentile.percentile(deviations, 0.50)
            var basalOff = 0.0
            when {
                pSensitive < 0 -> { // sensitive
                    basalOff = pSensitive * (60.0 / 5) / sens
                    sensResult += "Excess insulin sensitivity detected"
                }

                pResistant > 0 -> { // resistant
                    basalOff = pResistant * (60.0 / 5) / sens
                    sensResult += "Excess insulin resistance detected"
                }

                else           -> sensResult += "Sensitivity normal"
            }
            aapsLogger.debug(LTag.AUTOSENS, sensResult)
            val ratio = 1 + basalOff / profile.getMaxDailyBasal()

            //Update the data back to the parent
            sensResultArray[hourUsed] = sensResult
            ratioArray[hourUsed] = ratio
            ratioLimitArray[hourUsed] = ratioLimit
            hourUsed++
        }
        var key = 1
        val comparison = " 8 h ratio " + ratioArray[0] + " vs 24h ratio " + ratioArray[1]
        //use 24 hour ratio by default
        //if the 8 hour ratio is less than the 24 hour ratio, the 8 hour ratio is used
        if (ratioArray[0] < ratioArray[1]) {
            key = 0
        }
        //String message = hoursDetection.get(key) + " of sensitivity used";
        val output = fillResult(ratioArray[key], current.cob, pastSensitivityArray[key], ratioLimitArray[key], sensResultArray[key] + comparison, deviationsHour[key].size)
        aapsLogger.debug(
            LTag.AUTOSENS, "Sensitivity to: "
                + dateUtil.dateAndTimeString(toTime) +
                " ratio: " + output.ratio
                + " mealCOB: " + current.cob
        )
        return output
    }

    override fun maxAbsorptionHours(): Double = sp.getDouble(info.nightscout.core.utils.R.string.key_absorption_cutoff, Constants.DEFAULT_MAX_ABSORPTION_TIME)
    override val isMinCarbsAbsorptionDynamic: Boolean = false
    override val isOref1: Boolean = true

    override fun configuration(): JSONObject {
        val c = JSONObject()
        try {
            c.put(
                rh.gs(info.nightscout.core.utils.R.string.key_openapsama_min_5m_carbimpact),
                sp.getDouble(info.nightscout.core.utils.R.string.key_openapsama_min_5m_carbimpact, SMBDefaults.min_5m_carbimpact)
            )
            c.put(rh.gs(info.nightscout.core.utils.R.string.key_absorption_cutoff), sp.getDouble(info.nightscout.core.utils.R.string.key_absorption_cutoff, Constants.DEFAULT_MAX_ABSORPTION_TIME))
            c.put(rh.gs(info.nightscout.core.utils.R.string.key_openapsama_autosens_max), sp.getDouble(info.nightscout.core.utils.R.string.key_openapsama_autosens_max, 1.2))
            c.put(rh.gs(info.nightscout.core.utils.R.string.key_openapsama_autosens_min), sp.getDouble(info.nightscout.core.utils.R.string.key_openapsama_autosens_min, 0.7))
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return c
    }

    override fun applyConfiguration(configuration: JSONObject) {
        try {
            if (configuration.has(rh.gs(info.nightscout.core.utils.R.string.key_openapsama_min_5m_carbimpact))) sp.putDouble(
                info.nightscout.core.utils.R.string.key_openapsama_min_5m_carbimpact,
                configuration.getDouble(rh.gs(info.nightscout.core.utils.R.string.key_openapsama_min_5m_carbimpact))
            )
            if (configuration.has(rh.gs(info.nightscout.core.utils.R.string.key_absorption_cutoff))) sp.putDouble(
                info.nightscout.core.utils.R.string.key_absorption_cutoff, configuration.getDouble(
                    rh.gs(
                        info.nightscout.core.utils.R.string.key_absorption_cutoff
                    )
                )
            )
            if (configuration.has(rh.gs(info.nightscout.core.utils.R.string.key_openapsama_autosens_max))) sp.getDouble(
                info.nightscout.core.utils.R.string.key_openapsama_autosens_max, configuration.getDouble(
                    rh.gs(
                        info.nightscout.core.utils.R.string.key_openapsama_autosens_max
                    )
                )
            )
            if (configuration.has(rh.gs(info.nightscout.core.utils.R.string.key_openapsama_autosens_min))) sp.getDouble(
                info.nightscout.core.utils.R.string.key_openapsama_autosens_min, configuration.getDouble(
                    rh.gs(
                        info.nightscout.core.utils.R.string.key_openapsama_autosens_min
                    )
                )
            )
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    override val id: SensitivityType
        get() = SensitivityType.SENSITIVITY_OREF1

    override fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!isEnabled()) value.set(false, rh.gs(R.string.uam_disabled_oref1_not_selected), this)
        return value
    }
}