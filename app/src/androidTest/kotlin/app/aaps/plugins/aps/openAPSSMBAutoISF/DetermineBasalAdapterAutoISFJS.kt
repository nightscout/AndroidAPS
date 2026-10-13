package app.aaps.plugins.aps.openAPSSMBAutoISF

//import app.aaps.plugins.aps.openAPSAutoISF.
import androidx.annotation.VisibleForTesting
import app.aaps.core.data.aps.SMBDefaults
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.DetermineBasalAdapter
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.GlucoseStatusAutoIsf
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.convertToJSONArray
import app.aaps.core.objects.extensions.convertedToAbsolute
import app.aaps.core.objects.extensions.getPassedDurationToTimeInMinutes
import app.aaps.core.objects.extensions.plannedRemainingMinutes
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.plugins.aps.logger.LoggerCallback
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalResultSMBFromJS
import app.aaps.plugins.aps.utils.ScriptReader
import dagger.android.HasAndroidInjector
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class DetermineBasalAdapterAutoISFJS(private val scriptReader: ScriptReader, private val injector: HasAndroidInjector) : DetermineBasalAdapter {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var processedTbrEbData: ProcessedTbrEbData
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var dateUtil: DateUtil

    @VisibleForTesting var profile = JSONObject()
    @VisibleForTesting var glucoseStatus = JSONObject()
    @VisibleForTesting var iobData: JSONArray? = null
    @VisibleForTesting var mealData = JSONObject()
    @VisibleForTesting var currentTemp = JSONObject()
    @VisibleForTesting var autosensData = JSONObject()
    @VisibleForTesting var microBolusAllowed = false
    @VisibleForTesting var smbAlwaysAllowed = false
    @VisibleForTesting var currentTime: Long = 0
    @VisibleForTesting var flatBGsDetected = false

    override var currentTempParam: String? = null
    override var iobDataParam: String? = null
    override var glucoseStatusParam: String? = null
    override var profileParam: String? = null
    override var mealDataParam: String? = null
    override var scriptDebug = ""

    @Suppress("SpellCheckingInspection")
    override fun json(): JSONObject = JSONObject().apply {
        put("glucoseStatus", glucoseStatus)
        put("currenttemp", currentTemp)
        put("iob_data", iobData)
        put("profile", profile)
        put("autosens_data", autosensData)
        put("meal_data", mealData)
        put("microBolusAllowed", microBolusAllowed)
        put("reservoir_data", null)
        put("currentTime", currentTime)
        put("flatBGsDetected", flatBGsDetected)
    }

    @Suppress("SpellCheckingInspection")
    override operator fun invoke(): APSResult? {
        aapsLogger.debug(LTag.APS, ">>> Invoking determine_basal <<<")
        aapsLogger.debug(LTag.APS, "Glucose status: " + glucoseStatus.toString().also { glucoseStatusParam = it })
        aapsLogger.debug(LTag.APS, "IOB data:       " + iobData.toString().also { iobDataParam = it })
        aapsLogger.debug(LTag.APS, "Current temp:   " + currentTemp.toString().also { currentTempParam = it })
        aapsLogger.debug(LTag.APS, "Profile:        " + profile.toString().also { profileParam = it })
        aapsLogger.debug(LTag.APS, "Meal data:      " + mealData.toString().also { mealDataParam = it })
        aapsLogger.debug(LTag.APS, "Autosens data:  $autosensData")
        aapsLogger.debug(LTag.APS, "Reservoir data: " + "undefined")
        aapsLogger.debug(LTag.APS, "MicroBolusAllowed:  $microBolusAllowed")
        aapsLogger.debug(LTag.APS, "SMBAlwaysAllowed:  $smbAlwaysAllowed")
        aapsLogger.debug(LTag.APS, "CurrentTime: $currentTime")
        aapsLogger.debug(LTag.APS, "flatBGsDetected: $flatBGsDetected")
        var determineBasalResultSMB: APSResult? = null //var determineBasalResultSMB: DetermineBasalResultSMBFromJS? = null
        val rhino = Context.enter()
        val scope: Scriptable = rhino.initStandardObjects()
        // Turn off optimization to make Rhino Android compatible
        rhino.isInterpretedMode = true
        try {

            //register logger callback for console.log and console.error
            ScriptableObject.defineClass(scope, LoggerCallback::class.java)
            val myLogger = rhino.newObject(scope, "LoggerCallback", null)
            scope.put("console2", scope, myLogger)
            rhino.evaluateString(scope, readFile("OpenAPSAMA/loggerhelper.js"), "JavaScript", 0, null)

            //set module parent
            rhino.evaluateString(scope, "var module = {\"parent\":Boolean(1)};", "JavaScript", 0, null)
            rhino.evaluateString(scope, "var round_basal = function round_basal(basal, profile) { return basal; };", "JavaScript", 0, null)
            rhino.evaluateString(scope, "require = function() {return round_basal;};", "JavaScript", 0, null)

            //generate functions "determine_basal" and "setTempBasal"
            rhino.evaluateString(scope, readFile("OpenAPSSMBAutoISF/determine-basal.js"), "JavaScript", 0, null)
            rhino.evaluateString(scope, readFile("OpenAPSSMB/basal-set-temp.js"), "setTempBasal.js", 0, null)
            val determineBasalObj = scope["determine_basal", scope]
            val setTempBasalFunctionsObj = scope["tempBasalFunctions", scope]

            //call determine-basal
            if (determineBasalObj is Function && setTempBasalFunctionsObj is NativeObject) {

                //prepare parameters
                val params = arrayOf(
                    makeParam(glucoseStatus, rhino, scope),
                    makeParam(currentTemp, rhino, scope),
                    makeParamArray(iobData, rhino, scope),
                    makeParam(profile, rhino, scope),
                    makeParam(autosensData, rhino, scope),
                    makeParam(mealData, rhino, scope),
                    setTempBasalFunctionsObj,
                    java.lang.Boolean.valueOf(microBolusAllowed),
                    makeParam(null, rhino, scope),  // reservoir data as undefined
                    java.lang.Long.valueOf(currentTime),
                    java.lang.Boolean.valueOf(flatBGsDetected)
                )
                val jsResult = determineBasalObj.call(rhino, scope, scope, params) as NativeObject
                scriptDebug = LoggerCallback.scriptDebug

                // Parse the jsResult object to a JSON-String
                val result = NativeJSON.stringify(rhino, scope, jsResult, null, null).toString()
                aapsLogger.debug(LTag.APS, "Result: $result")
                try {
                    val resultJson = JSONObject(result)
                    determineBasalResultSMB = DetermineBasalResultSMBFromJS(injector, resultJson)
                } catch (e: JSONException) {
                    aapsLogger.error(LTag.APS, "Unhandled exception", e)
                }
            } else {
                aapsLogger.error(LTag.APS, "Problem loading JS Functions")
            }
        } catch (_: IOException) {
            aapsLogger.error(LTag.APS, "IOException")
        } catch (e: RhinoException) {
            aapsLogger.error(LTag.APS, "RhinoException: (" + e.lineNumber() + "," + e.columnNumber() + ") " + e.toString())
        } catch (e: IllegalAccessException) {
            aapsLogger.error(LTag.APS, e.toString())
        } catch (e: InstantiationException) {
            aapsLogger.error(LTag.APS, e.toString())
        } catch (e: InvocationTargetException) {
            aapsLogger.error(LTag.APS, e.toString())
        } finally {
            Context.exit()
        }
        glucoseStatusParam = glucoseStatus.toString()
        iobDataParam = iobData.toString()
        currentTempParam = currentTemp.toString()
        profileParam = profile.toString()
        mealDataParam = mealData.toString()
        return determineBasalResultSMB
    }

    @Suppress("SpellCheckingInspection", "KotlinConstantConditions")
    override fun setData(
        profile: Profile,
        maxIob: Double,
        maxBasal: Double,
        minBg: Double,
        maxBg: Double,
        targetBg: Double,
        basalRate: Double,
        iobArray: Array<IobTotal>,
        glucoseStatus: GlucoseStatus,
        mealData: MealData,
        autosensDataRatio: Double,
        tempTargetSet: Boolean,
        microBolusAllowed: Boolean,
        uamAllowed: Boolean,
        advancedFiltering: Boolean,
        flatBGsDetected: Boolean,
        tdd1D: Double?,
        tdd7D: Double?,
        tddLast24H: Double?,
        tddLast4H: Double?,
        tddLast8to4H: Double?
    ) {
        val pump = activePlugin.activePump
        val pumpBolusStep = pump.pumpDescription.bolusStep
        val glucoseStatusAutoIsf = glucoseStatus as GlucoseStatusAutoIsf
        this.profile.put("max_iob", maxIob)
        //mProfile.put("dia", profile.getDia());
        this.profile.put("type", "current")
        this.profile.put("max_daily_basal", profile.getMaxDailyBasal())
        this.profile.put("max_basal", maxBasal)
        this.profile.put("min_bg", minBg)
        this.profile.put("max_bg", maxBg)
        this.profile.put("target_bg", targetBg)
        this.profile.put("carb_ratio", profile.getIc())
        this.profile.put("sens", profile.getIsfMgdl("test"))
        this.profile.put("max_daily_safety_multiplier", preferences.get(DoubleKey.ApsMaxDailyMultiplier))
        this.profile.put("current_basal_safety_multiplier", preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier))

        this.profile.put("high_temptarget_raises_sensitivity", preferences.get(BooleanKey.ApsAutoIsfHighTtRaisesSens))
        this.profile.put("low_temptarget_lowers_sensitivity", preferences.get(BooleanKey.ApsAutoIsfLowTtLowersSens))
        this.profile.put("sensitivity_raises_target", preferences.get(BooleanKey.ApsSensitivityRaisesTarget))
        this.profile.put("resistance_lowers_target", preferences.get(BooleanKey.ApsResistanceLowersTarget))
        this.profile.put("adv_target_adjustments", SMBDefaults.adv_target_adjustments)
        this.profile.put("exercise_mode", SMBDefaults.exercise_mode)
        this.profile.put("half_basal_exercise_target", preferences.get(IntKey.ApsAutoIsfHalfBasalExerciseTarget))
        this.profile.put("maxCOB", SMBDefaults.maxCOB)
        this.profile.put("skip_neutral_temps", pump.setNeutralTempAtFullHour())
        // min_5m_carbimpact is not used within SMB determinebasal
        //if (mealData.usedMinCarbsImpact > 0) {
        //    mProfile.put("min_5m_carbimpact", mealData.usedMinCarbsImpact);
        //} else {
        //    mProfile.put("min_5m_carbimpact", preferences.getDouble(R.string.key_openapsama_min_5m_carbimpact, SMBDefaults.min_5m_carbimpact));
        //}
        this.profile.put("remainingCarbsCap", SMBDefaults.remainingCarbsCap)
        this.profile.put("enableUAM", uamAllowed)
        this.profile.put("A52_risk_enable", SMBDefaults.A52_risk_enable)
        val smbEnabled = preferences.get(BooleanKey.ApsUseSmb)
        this.profile.put("SMBInterval", preferences.get(IntKey.ApsMaxSmbFrequency))
        this.profile.put("enableSMB_with_COB", smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithCob))
        this.profile.put("enableSMB_with_temptarget", smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithLowTt))
        this.profile.put("allowSMB_with_high_temptarget", smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithHighTt))
        this.profile.put("enableSMB_always", smbEnabled && preferences.get(BooleanKey.ApsUseSmbAlways) && advancedFiltering)
        this.profile.put("enableSMB_after_carbs", smbEnabled && preferences.get(BooleanKey.ApsUseSmbAfterCarbs) && advancedFiltering)
        this.profile.put("maxSMBBasalMinutes", preferences.get(IntKey.ApsMaxMinutesOfBasalToLimitSmb))
        this.profile.put("maxUAMSMBBasalMinutes", preferences.get(IntKey.ApsUamMaxMinutesOfBasalToLimitSmb))
        //set the min SMB amount to be the amount set by the pump.
        this.profile.put("bolus_increment", pumpBolusStep)
        this.profile.put("carbsReqThreshold", preferences.get(IntKey.ApsCarbsRequestThreshold))
        this.profile.put("current_basal", basalRate)
        this.profile.put("temptargetSet", tempTargetSet)
        this.profile.put("autosens_max", preferences.get(DoubleKey.AutosensMax))
        // mod use autoisf here
        this.profile.put("autoISF_version", "3.0.1")        // was BuildConfig.AUTOISF_VERSION)
        this.profile.put("enable_autoISF", preferences.get(BooleanKey.ApsUseAutoIsfWeights))
        this.profile.put("autoISF_max", preferences.get(DoubleKey.ApsAutoIsfMax))
        this.profile.put("autoISF_min", preferences.get(DoubleKey.ApsAutoIsfMin))
        this.profile.put("bgAccel_ISF_weight", preferences.get(DoubleKey.ApsAutoIsfBgAccelWeight))
        this.profile.put("bgBrake_ISF_weight", preferences.get(DoubleKey.ApsAutoIsfBgBrakeWeight))
        //this.profile.put("enable_pp_ISF_always", preferences.get(BooleanKey.ApsAutoIsfPpAlways))
        //this.profile.put("pp_ISF_hours",  preferences.get(IntKey.ApsAutoIsfPpIsfHours))
        this.profile.put("pp_ISF_weight", preferences.get(DoubleKey.ApsAutoIsfPpWeight))
        //this.profile.put("delta_ISFrange_weight",  preferences.get(DoubleKey.ApsAutoIsfDeltaWeight))
        this.profile.put("lower_ISFrange_weight", preferences.get(DoubleKey.ApsAutoIsfLowBgWeight))
        this.profile.put("higher_ISFrange_weight", preferences.get(DoubleKey.ApsAutoIsfHighBgWeight))
        //this.profile.put("enable_dura_ISF_with_COB", preferences.get(BooleanKey.ApsAutoIsfDuraAfterCarbs))
        this.profile.put("dura_ISF_weight", preferences.get(DoubleKey.ApsAutoIsfDuraWeight))
        // include SMB adaptations
        this.profile.put("smb_delivery_ratio", preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatio))
        this.profile.put("smb_delivery_ratio_min", preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioMin))
        this.profile.put("smb_delivery_ratio_max", preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioMax))
        this.profile.put("smb_delivery_ratio_bg_range", preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange))
        this.profile.put("smb_max_range_extension", preferences.get(DoubleKey.ApsAutoIsfSmbMaxRangeExtension))
        //this.profile.put("enableSMB_EvenOn_OddOff", preferences.get(BooleanKey.ApsAutoIsfSmbOnEvenTt)) // TT
        this.profile.put("enableSMB_EvenOn_OddOff_always", preferences.get(BooleanKey.ApsAutoIsfSmbOnEvenTarget)) // profile
        this.profile.put("iob_threshold_percent", preferences.get(IntKey.ApsAutoIsfIobThPercent))
        this.profile.put("profile_percentage", if (profile is ProfileSealed.EPS) profile.value.originalPercentage else 100)
        if (profileFunction.getUnits() == GlucoseUnit.MMOL) {
            this.profile.put("out_units", "mmol/L")
        }
        val now = System.currentTimeMillis()
        val tb = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        currentTemp.put("temp", "absolute")
        currentTemp.put("duration", tb?.plannedRemainingMinutes ?: 0)
        currentTemp.put("rate", tb?.convertedToAbsolute(now, profile) ?: 0.0)
        // as we have non default temps longer than 30 mintues
        if (tb != null) currentTemp.put("minutesrunning", tb.getPassedDurationToTimeInMinutes(now))

        iobData = iobArray.convertToJSONArray(dateUtil)
        this.glucoseStatus.put("glucose", glucoseStatusAutoIsf.glucose)
        this.glucoseStatus.put("noise", glucoseStatusAutoIsf.noise)
        if (preferences.get(BooleanKey.ApsAlwaysUseShortDeltas)) {
            this.glucoseStatus.put("delta", glucoseStatusAutoIsf.shortAvgDelta)
        } else {
            this.glucoseStatus.put("delta", glucoseStatusAutoIsf.delta)
        }
        this.glucoseStatus.put("short_avgdelta", glucoseStatusAutoIsf.shortAvgDelta)
        this.glucoseStatus.put("long_avgdelta", glucoseStatusAutoIsf.longAvgDelta)
        this.glucoseStatus.put("date", glucoseStatusAutoIsf.date)
        this.glucoseStatus.put("duraISFminutes", glucoseStatusAutoIsf.duraISFminutes)
        this.glucoseStatus.put("duraISFaverage", glucoseStatusAutoIsf.duraISFaverage)
        this.glucoseStatus.put("a0", glucoseStatusAutoIsf.a0)
        this.glucoseStatus.put("a1", glucoseStatusAutoIsf.a1)
        this.glucoseStatus.put("a2", glucoseStatusAutoIsf.a2)
        this.glucoseStatus.put("bgAcceleration", glucoseStatusAutoIsf.bgAcceleration)
        this.glucoseStatus.put("corrSqu", glucoseStatusAutoIsf.corrSqu)
        this.mealData.put("carbs", mealData.carbs)
        this.mealData.put("mealCOB", mealData.mealCOB)
        this.mealData.put("slopeFromMaxDeviation", mealData.slopeFromMaxDeviation)
        this.mealData.put("slopeFromMinDeviation", mealData.slopeFromMinDeviation)
        this.mealData.put("lastBolusTime", mealData.lastBolusTime)
        this.mealData.put("lastCarbTime", mealData.lastCarbTime)
        if (constraintChecker.isAutosensModeEnabled().value()) {
            autosensData.put("ratio", autosensDataRatio)
        } else {
            autosensData.put("ratio", 1.0)
        }
        this.microBolusAllowed = microBolusAllowed
        smbAlwaysAllowed = advancedFiltering
        currentTime = now
        this.flatBGsDetected = flatBGsDetected
    }

    private fun makeParam(jsonObject: JSONObject?, rhino: Context, scope: Scriptable): Any {
        return if (jsonObject == null) Undefined.instance
        else NativeJSON.parse(rhino, scope, jsonObject.toString()) { _: Context?, _: Scriptable?, _: Scriptable?, objects: Array<Any?> -> objects[1] }
    }

    private fun makeParamArray(jsonArray: JSONArray?, rhino: Context, scope: Scriptable): Any {
        return NativeJSON.parse(rhino, scope, jsonArray.toString()) { _: Context?, _: Scriptable?, _: Scriptable?, objects: Array<Any?> -> objects[1] }
    }

    @Throws(IOException::class) private fun readFile(filename: String): String {
        val bytes = scriptReader.readFile(filename)
        var string = String(bytes, StandardCharsets.UTF_8)
        if (string.startsWith("#!/usr/bin/env node")) {
            string = string.substring(20)
        }
        return string
    }

    init {
        injector.androidInjector().inject(this)
    }
}