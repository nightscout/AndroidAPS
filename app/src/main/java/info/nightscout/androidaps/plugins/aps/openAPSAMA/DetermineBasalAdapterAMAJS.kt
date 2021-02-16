package info.nightscout.androidaps.plugins.aps.openAPSAMA

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.data.MealData
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.logger.LoggerCallback
import info.nightscout.androidaps.plugins.aps.loop.ScriptReader
import info.nightscout.androidaps.plugins.aps.openAPSSMB.SMBDefaults
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.openhumans.OpenHumansUploader
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.mozilla.javascript.*
import org.mozilla.javascript.Function
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlin.math.min

class DetermineBasalAdapterAMAJS internal constructor(scriptReader: ScriptReader, injector: HasAndroidInjector) {

    private val injector: HasAndroidInjector

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var sp: SP
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var openHumansUploader: OpenHumansUploader

    private val mScriptReader: ScriptReader
    private var profile = JSONObject()
    private var glucoseStatus = JSONObject()
    private var iobData: JSONArray? = null
    private var mealData = JSONObject()
    private var currentTemp = JSONObject()
    private var autosensData = JSONObject()

    var currentTempParam: String? = null
        private set
    var iobDataParam: String? = null
        private set
    var glucoseStatusParam: String? = null
        private set
    var profileParam: String? = null
        private set
    var mealDataParam: String? = null
        private set
    var scriptDebug = ""
        private set

    @Suppress("SpellCheckingInspection")
    operator fun invoke(): DetermineBasalResultAMA? {
        aapsLogger.debug(LTag.APS, ">>> Invoking determine_basal <<<")
        aapsLogger.debug(LTag.APS, "Glucose status: " + glucoseStatus.toString().also { glucoseStatusParam = it })
        aapsLogger.debug(LTag.APS, "IOB data:       " + iobData.toString().also { iobDataParam = it })
        aapsLogger.debug(LTag.APS, "Current temp:   " + currentTemp.toString().also { currentTempParam = it })
        aapsLogger.debug(LTag.APS, "Profile:        " + profile.toString().also { profileParam = it })
        aapsLogger.debug(LTag.APS, "Meal data:      " + mealData.toString().also { mealDataParam = it })
        aapsLogger.debug(LTag.APS, "Autosens data:  $autosensData")
        var determineBasalResultAMA: DetermineBasalResultAMA? = null
        val rhino = Context.enter()
        val scope: Scriptable = rhino.initStandardObjects()
        // Turn off optimization to make Rhino Android compatible
        rhino.optimizationLevel = -1
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
            rhino.evaluateString(scope, readFile("OpenAPSAMA/determine-basal.js"), "JavaScript", 0, null)
            rhino.evaluateString(scope, readFile("OpenAPSAMA/basal-set-temp.js"), "setTempBasal.js", 0, null)
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
                    setTempBasalFunctionsObj)
                val jsResult = determineBasalObj.call(rhino, scope, scope, params) as NativeObject
                scriptDebug = LoggerCallback.scriptDebug

                // Parse the jsResult object to a JSON-String
                val result = NativeJSON.stringify(rhino, scope, jsResult, null, null).toString()
                aapsLogger.debug(LTag.APS, "Result: $result")
                try {
                    val resultJson = JSONObject(result)
                    openHumansUploader.enqueueAMAData(profile, glucoseStatus, iobData, mealData, currentTemp, autosensData, resultJson)
                    determineBasalResultAMA = DetermineBasalResultAMA(injector, jsResult, resultJson)
                } catch (e: JSONException) {
                    aapsLogger.error(LTag.APS, "Unhandled exception", e)
                }
            } else {
                aapsLogger.error(LTag.APS, "Problem loading JS Functions")
            }
        } catch (e: IOException) {
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
        return determineBasalResultAMA
    }

    @Suppress("SpellCheckingInspection")
    @Throws(JSONException::class) fun setData(profile: Profile,
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
                                              tempTargetSet: Boolean) {
        this.profile = JSONObject()
        this.profile.put("max_iob", maxIob)
        this.profile.put("dia", min(profile.dia, 3.0))
        this.profile.put("type", "current")
        this.profile.put("max_daily_basal", profile.maxDailyBasal)
        this.profile.put("max_basal", maxBasal)
        this.profile.put("min_bg", minBg)
        this.profile.put("max_bg", maxBg)
        this.profile.put("target_bg", targetBg)
        this.profile.put("carb_ratio", profile.ic)
        this.profile.put("sens", profile.isfMgdl)
        this.profile.put("max_daily_safety_multiplier", sp.getInt(R.string.key_openapsama_max_daily_safety_multiplier, 3))
        this.profile.put("current_basal_safety_multiplier", sp.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4.0))
        this.profile.put("skip_neutral_temps", true)
        this.profile.put("current_basal", basalRate)
        this.profile.put("temptargetSet", tempTargetSet)
        this.profile.put("autosens_adjust_targets", sp.getBoolean(R.string.key_openapsama_autosens_adjusttargets, true))
        //align with max-absorption model in AMA sensitivity
        if (mealData.usedMinCarbsImpact > 0) {
            this.profile.put("min_5m_carbimpact", mealData.usedMinCarbsImpact)
        } else {
            this.profile.put("min_5m_carbimpact", sp.getDouble(R.string.key_openapsama_min_5m_carbimpact, SMBDefaults.min_5m_carbimpact))
        }
        if (profileFunction.getUnits() == Constants.MMOL) {
            this.profile.put("out_units", "mmol/L")
        }
        val now = System.currentTimeMillis()
        val tb = treatmentsPlugin.getTempBasalFromHistory(now)
        currentTemp = JSONObject()
        currentTemp.put("temp", "absolute")
        currentTemp.put("duration", tb?.plannedRemainingMinutes ?: 0)
        currentTemp.put("rate", tb?.tempBasalConvertedToAbsolute(now, profile) ?: 0.0)

        // as we have non default temps longer than 30 minutes
        val tempBasal = treatmentsPlugin.getTempBasalFromHistory(System.currentTimeMillis())
        if (tempBasal != null) {
            currentTemp.put("minutesrunning", tempBasal.realDuration)
        }
        iobData = IobCobCalculatorPlugin.convertToJSONArray(iobArray)
        this.glucoseStatus = JSONObject()
        this.glucoseStatus.put("glucose", glucoseStatus.glucose)
        if (sp.getBoolean(R.string.key_always_use_shortavg, false)) {
            this.glucoseStatus.put("delta", glucoseStatus.shortAvgDelta)
        } else {
            this.glucoseStatus.put("delta", glucoseStatus.delta)
        }
        this.glucoseStatus.put("short_avgdelta", glucoseStatus.shortAvgDelta)
        this.glucoseStatus.put("long_avgdelta", glucoseStatus.longAvgDelta)
        this.mealData = JSONObject()
        this.mealData.put("carbs", mealData.carbs)
        this.mealData.put("boluses", mealData.boluses)
        this.mealData.put("mealCOB", mealData.mealCOB)
        if (constraintChecker.isAutosensModeEnabled().value()) {
            autosensData.put("ratio", autosensDataRatio)
        } else {
            autosensData.put("ratio", 1.0)
        }
    }

    private fun makeParam(jsonObject: JSONObject?, rhino: Context, scope: Scriptable): Any {
        return if (jsonObject == null) Undefined.instance else NativeJSON.parse(rhino, scope, jsonObject.toString()) { _: Context?, _: Scriptable?, _: Scriptable?, objects: Array<Any?> -> objects[1] }
    }

    private fun makeParamArray(jsonArray: JSONArray?, rhino: Context, scope: Scriptable): Any {
        return NativeJSON.parse(rhino, scope, jsonArray.toString()) { _: Context?, _: Scriptable?, _: Scriptable?, objects: Array<Any?> -> objects[1] }
    }

    @Throws(IOException::class) private fun readFile(filename: String): String {
        val bytes = mScriptReader.readFile(filename)
        var string = String(bytes, StandardCharsets.UTF_8)
        if (string.startsWith("#!/usr/bin/env node")) {
            string = string.substring(20)
        }
        return string
    }

    init {
        injector.androidInjector().inject(this)
        mScriptReader = scriptReader
        this.injector = injector
    }
}