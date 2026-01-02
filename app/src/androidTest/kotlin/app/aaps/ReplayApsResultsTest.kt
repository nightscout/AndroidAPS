package app.aaps

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatusAutoIsf
import app.aaps.core.interfaces.aps.GlucoseStatusSMB
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.OapsProfile
import app.aaps.core.interfaces.aps.OapsProfileAutoIsf
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.JsonHelper
import app.aaps.di.TestApplication
import app.aaps.plugins.aps.openAPSAMA.DetermineBasalAMA
import app.aaps.plugins.aps.openAPSAMA.DetermineBasalAdapterAMAJS
import app.aaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import app.aaps.plugins.aps.openAPSAutoISF.DetermineBasalAutoISF
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalAdapterSMBJS
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalSMB
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import app.aaps.plugins.aps.openAPSSMBAutoISF.DetermineBasalAdapterAutoISFJS
import app.aaps.plugins.aps.openAPSSMBDynamicISF.DetermineBasalAdapterSMBDynamicISFJS
import app.aaps.plugins.aps.utils.ScriptReader
import com.google.common.truth.Truth.assertThat
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlin.math.floor

class ReplayApsResultsTest @Inject constructor() {

    @Inject lateinit var fileListProvider: FileListProvider
    @Inject lateinit var storage: Storage
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var determineBasalAMA: DetermineBasalAMA
    @Inject lateinit var determineBasalSMBDynamicISF: DetermineBasalSMB
    @Inject lateinit var determineBasalAutoISF: DetermineBasalAutoISF
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var preferences: Preferences

    private val context = ApplicationProvider.getApplicationContext<TestApplication>()

    @get:Rule
    var runtimePermissionRule = GrantPermissionRule.grant(android.Manifest.permission.READ_EXTERNAL_STORAGE)!!

    private var ktTime = 0L
    private var jsTime = 0L

    @Before
    fun inject() {
        context.androidInjector().inject(this)
    }

    @Test
    fun replayTest() {
        @Suppress("SpellCheckingInspection") var amas = 0
        @Suppress("SpellCheckingInspection") var smbs = 0
        @Suppress("SpellCheckingInspection") var dynisfs = 0
        @Suppress("SpellCheckingInspection") var autoisfs = 0
        val results = readResultFiles()
        assertThat(results.size).isGreaterThan(0)
        results.forEach { test ->
            val result = test.readContent()
            val algorithm = JsonHelper.safeGetString(result, "algorithm")
            val inputString = JsonHelper.safeGetString(result, "input") ?: error("Missing input")
            val outputString = JsonHelper.safeGetString(result, "output") ?: error("Missing output")
            val filename = JsonHelper.safeGetString(result, "filename") ?: "Unknown filename"
            val input = JSONObject(inputString)
            val output = JSONObject(outputString)
            aapsLogger.info(LTag.CORE, "***** File: $filename *****")
            when (algorithm) {
                OpenAPSSMBPlugin::class.simpleName -> smbs++
                "OpenAPSSMBAutoISFPlugin"          -> autoisfs++
                "OpenAPSSMBDynamicISFPlugin"       -> dynisfs++
                OpenAPSAMAPlugin::class.simpleName -> amas++
            }
            when (algorithm) {
                OpenAPSSMBPlugin::class.simpleName -> testOpenAPSSMB(filename, input, output, injector)
                "OpenAPSSMBAutoISFPlugin"          -> testOpenAPSSMBAutoISF(filename, input, output, injector)
                "OpenAPSSMBDynamicISFPlugin"       -> testOpenAPSSMBDynamicISF(filename, input, output, injector)
                OpenAPSAMAPlugin::class.simpleName -> testOpenAPSAMA(filename, input, output, injector)
                else                               -> error("Unsupported")
            }
        }
        aapsLogger.info(LTag.CORE, "\n**********\nAMA: $amas\nSMB: $smbs\nDynISFs: $dynisfs\nAutoISFs: $autoisfs\nJS time: $jsTime\nKT time: $ktTime\n**********")
    }

    private fun testOpenAPSSMB(filename: String, input: JSONObject, output: JSONObject, injector: HasAndroidInjector) {
        val startJs = System.currentTimeMillis()
        val determineBasalResult = DetermineBasalAdapterSMBJS(ScriptReader(), injector)
        determineBasalResult.profile = input.getJSONObject("profile")
        determineBasalResult.glucoseStatus = input.getJSONObject("glucoseStatus")
        determineBasalResult.iobData = input.getJSONArray("iob_data")
        determineBasalResult.mealData = input.getJSONObject("meal_data")
        determineBasalResult.currentTemp = input.getJSONObject("currenttemp")
        determineBasalResult.autosensData = input.getJSONObject("autosens_data")
        determineBasalResult.microBolusAllowed = input.getBoolean("microBolusAllowed")
        determineBasalResult.currentTime = input.getLong("currentTime")
        determineBasalResult.flatBGsDetected = input.getBoolean("flatBGsDetected")
        val result = determineBasalResult.invoke()
        val endJs = System.currentTimeMillis()
        jsTime += (endJs - startJs)

        aapsLogger.info(LTag.APS, "Expected --> $output")
        assertThat(result).isNotNull()
        JSONAssert.assertEquals(
            "Error in file $filename",
            output.toString(),
            result?.json()?.apply {
                // this is added afterwards to json. Copy from original
                put("timestamp", output.getString("timestamp"))
            }.toString(),
            CustomComparator(JSONCompareMode.LENIENT, Customization("tick") { o1: Any?, o2: Any? -> o1.toString() == o2.toString() })
        )

        // Exclude these with whole number delta as the alg is producing different results
        // on inputs like 2.0 which are evaluated as Int 2
        val delta = determineBasalResult.glucoseStatus.getDouble("delta")
        if (floor(delta) == delta) return
        // Pass to DetermineBasalSMB

        if (determineBasalResult.profile.optString("out_units") == "mmol/L")
            preferences.put(StringKey.GeneralUnits, GlucoseUnit.MMOL.asText)
        else
            preferences.put(StringKey.GeneralUnits, GlucoseUnit.MGDL.asText)

        val startKt = System.currentTimeMillis()
        val glucoseStatus = GlucoseStatusSMB(
            glucose = determineBasalResult.glucoseStatus.getDouble("glucose"),
            noise = determineBasalResult.glucoseStatus.getDouble("noise"),
            delta = determineBasalResult.glucoseStatus.getDouble("delta"),
            shortAvgDelta = determineBasalResult.glucoseStatus.getDouble("short_avgdelta"),
            longAvgDelta = determineBasalResult.glucoseStatus.getDouble("long_avgdelta"),
            date = determineBasalResult.glucoseStatus.getLong("date")
        )
        val currentTemp = CurrentTemp(
            duration = determineBasalResult.currentTemp.getInt("duration"),
            rate = determineBasalResult.currentTemp.getDouble("rate"),
            minutesrunning = null
        )
        val autosensData = AutosensResult(
            ratio = determineBasalResult.autosensData.getDouble("ratio")
        )

        fun JSONObject.toIob(): IobTotal =
            IobTotal(
                time = dateUtil.fromISODateString(this.getString("time")),
                iob = this.getDouble("iob"),
                basaliob = this.getDouble("basaliob"),
                bolussnooze = this.getDouble("bolussnooze"),
                activity = this.getDouble("activity"),
                lastBolusTime = this.getLong("lastBolusTime"),
                iobWithZeroTemp = this.optJSONObject("iobWithZeroTemp")?.toIob()
            )

        val iobData = arrayListOf<IobTotal>()
        for (i in 0 until determineBasalResult.iobData!!.length())
            iobData.add(determineBasalResult.iobData!!.getJSONObject(i).toIob())
        val currentTime = determineBasalResult.currentTime
        val profile = OapsProfile(
            dia = 0.0,
            min_5m_carbimpact = 0.0,
            max_iob = determineBasalResult.profile.getDouble("max_iob"),
            max_daily_basal = determineBasalResult.profile.getDouble("max_daily_basal"),
            max_basal = determineBasalResult.profile.getDouble("max_basal"),
            min_bg = determineBasalResult.profile.getDouble("min_bg"),
            max_bg = determineBasalResult.profile.getDouble("max_bg"),
            target_bg = determineBasalResult.profile.getDouble("target_bg"),
            carb_ratio = determineBasalResult.profile.getDouble("carb_ratio"),
            sens = determineBasalResult.profile.getDouble("sens"),
            autosens_adjust_targets = false,
            max_daily_safety_multiplier = determineBasalResult.profile.getDouble("max_daily_safety_multiplier"),
            current_basal_safety_multiplier = determineBasalResult.profile.getDouble("current_basal_safety_multiplier"),
            lgsThreshold = null,
            high_temptarget_raises_sensitivity = determineBasalResult.profile.getBoolean("high_temptarget_raises_sensitivity"),
            low_temptarget_lowers_sensitivity = determineBasalResult.profile.getBoolean("low_temptarget_lowers_sensitivity"),
            sensitivity_raises_target = determineBasalResult.profile.getBoolean("sensitivity_raises_target"),
            resistance_lowers_target = determineBasalResult.profile.getBoolean("resistance_lowers_target"),
            adv_target_adjustments = determineBasalResult.profile.getBoolean("adv_target_adjustments"),
            exercise_mode = determineBasalResult.profile.getBoolean("exercise_mode"),
            half_basal_exercise_target = determineBasalResult.profile.getInt("half_basal_exercise_target"),
            maxCOB = determineBasalResult.profile.getInt("maxCOB"),
            skip_neutral_temps = determineBasalResult.profile.getBoolean("skip_neutral_temps"),
            remainingCarbsCap = determineBasalResult.profile.getInt("remainingCarbsCap"),
            enableUAM = determineBasalResult.profile.getBoolean("enableUAM"),
            A52_risk_enable = determineBasalResult.profile.getBoolean("A52_risk_enable"),
            SMBInterval = determineBasalResult.profile.getInt("SMBInterval"),
            enableSMB_with_COB = determineBasalResult.profile.getBoolean("enableSMB_with_COB"),
            enableSMB_with_temptarget = determineBasalResult.profile.getBoolean("enableSMB_with_temptarget"),
            allowSMB_with_high_temptarget = determineBasalResult.profile.getBoolean("allowSMB_with_high_temptarget"),
            enableSMB_always = determineBasalResult.profile.getBoolean("enableSMB_always"),
            enableSMB_after_carbs = determineBasalResult.profile.getBoolean("enableSMB_after_carbs"),
            maxSMBBasalMinutes = determineBasalResult.profile.getInt("maxSMBBasalMinutes"),
            maxUAMSMBBasalMinutes = determineBasalResult.profile.getInt("maxUAMSMBBasalMinutes"),
            bolus_increment = determineBasalResult.profile.getDouble("bolus_increment"),
            carbsReqThreshold = determineBasalResult.profile.getInt("carbsReqThreshold"),
            current_basal = determineBasalResult.profile.getDouble("current_basal"),
            temptargetSet = determineBasalResult.profile.getBoolean("temptargetSet"),
            autosens_max = determineBasalResult.profile.getDouble("autosens_max"),
            out_units = determineBasalResult.profile.optString("out_units"),
            variable_sens = 0.0,
            insulinDivisor = 0,
            TDD = 0.0
        )
        val meatData = MealData(
            carbs = determineBasalResult.mealData.getDouble("carbs"),
            mealCOB = determineBasalResult.mealData.getDouble("mealCOB"),
            slopeFromMaxDeviation = determineBasalResult.mealData.getDouble("slopeFromMaxDeviation"),
            slopeFromMinDeviation = determineBasalResult.mealData.getDouble("slopeFromMinDeviation"),
            lastBolusTime = determineBasalResult.mealData.getLong("lastBolusTime"),
            lastCarbTime = determineBasalResult.mealData.getLong("lastCarbTime")
        )
        val resultKt = determineBasalSMBDynamicISF.determine_basal(
            glucose_status = glucoseStatus,
            currenttemp = currentTemp,
            iob_data_array = iobData.toTypedArray(),
            profile = profile,
            autosens_data = autosensData,
            meal_data = meatData,
            microBolusAllowed = determineBasalResult.microBolusAllowed,
            currentTime = currentTime,
            flatBGsDetected = determineBasalResult.flatBGsDetected,
            dynIsfMode = false
        )
        val endKt = System.currentTimeMillis()
        ktTime += (endKt - startKt)

        aapsLogger.info(LTag.APS, resultKt.toString())

        aapsLogger.debug(LTag.APS, result?.json()?.getString("reason") ?: "")
        aapsLogger.debug(LTag.APS, resultKt.reason.toString())
        aapsLogger.debug(LTag.APS, "File: $filename")
//        assertThat(resultKt.reason.toString()).isEqualTo(result?.json?.getString("reason"))
        assertThat(resultKt.tick ?: "").isEqualTo(result?.json()?.optString("tick"))
        assertThat(resultKt.eventualBG ?: Double.NaN).isEqualTo(result?.json()?.optDouble("eventualBG"))
        assertThat(resultKt.targetBG ?: Double.NaN).isEqualTo(result?.json()?.optDouble("targetBG"))
        assertThat(resultKt.insulinReq ?: Double.NaN).isEqualTo(result?.json()?.optDouble("insulinReq"))
        assertThat(resultKt.carbsReq ?: 0).isEqualTo(result?.json()?.optInt("carbsReq"))
        assertThat(resultKt.carbsReqWithin ?: 0).isEqualTo(result?.json()?.optInt("carbsReqWithin"))
        assertThat(resultKt.units ?: Double.NaN).isEqualTo(result?.json()?.optDouble("units"))
        assertThat(resultKt.sensitivityRatio ?: Double.NaN).isEqualTo(result?.json()?.optDouble("sensitivityRatio"))
        assertThat(resultKt.duration ?: 0).isEqualTo(result?.json()?.optInt("duration"))
        assertThat(resultKt.rate ?: Double.NaN).isEqualTo(result?.json()?.optDouble("rate"))
        assertThat(resultKt.COB ?: Double.NaN).isEqualTo(result?.json()?.optDouble("COB"))
        assertThat(resultKt.IOB ?: Double.NaN).isEqualTo(result?.json()?.optDouble("IOB"))
    }

    private fun testOpenAPSSMBDynamicISF(filename: String, input: JSONObject, output: JSONObject, injector: HasAndroidInjector) {
        val startJs = System.currentTimeMillis()
        val determineBasalResult = DetermineBasalAdapterSMBDynamicISFJS(ScriptReader(), injector)
        determineBasalResult.profile = input.getJSONObject("profile")
        determineBasalResult.glucoseStatus = input.getJSONObject("glucoseStatus")
        determineBasalResult.iobData = input.getJSONArray("iob_data")
        determineBasalResult.mealData = input.getJSONObject("meal_data")
        determineBasalResult.currentTemp = input.getJSONObject("currenttemp")
        determineBasalResult.autosensData = input.getJSONObject("autosens_data")
        determineBasalResult.microBolusAllowed = input.getBoolean("microBolusAllowed")
        determineBasalResult.currentTime = input.getLong("currentTime")
        determineBasalResult.flatBGsDetected = input.getBoolean("flatBGsDetected")
        determineBasalResult.tdd1D = input.getDouble("tdd1D")
        determineBasalResult.tdd7D = input.getDouble("tdd7D")
        determineBasalResult.tddLast24H = input.getDouble("tddLast24H")
        determineBasalResult.tddLast4H = input.getDouble("tddLast4H")
        determineBasalResult.tddLast8to4H = input.getDouble("tddLast8to4H")
        val result = determineBasalResult.invoke()
        val endJs = System.currentTimeMillis()
        jsTime += (endJs - startJs)

        aapsLogger.info(LTag.APS, "Expected --> $output")
        assertThat(result).isNotNull()
        JSONAssert.assertEquals(
            "Error in file $filename",
            output.toString(),
            result?.json()?.apply {
                // this is added afterwards to json. Copy from original
                put("timestamp", output.getString("timestamp"))
            }.toString(),
            CustomComparator(JSONCompareMode.LENIENT, Customization("tick") { o1: Any?, o2: Any? -> o1.toString() == o2.toString() })
        )
        // Exclude these with whole number delta as the alg is producing different results
        // on inputs like 2.0 which are evaluated as Int 2
        val delta = determineBasalResult.glucoseStatus.getDouble("delta")
        if (floor(delta) == delta) return
        // Pass to DetermineBasalSMBDynamicISF

        if (determineBasalResult.profile.optString("out_units") == "mmol/L")
            preferences.put(StringKey.GeneralUnits, GlucoseUnit.MMOL.asText)
        else
            preferences.put(StringKey.GeneralUnits, GlucoseUnit.MGDL.asText)

        val startKt = System.currentTimeMillis()
        val glucoseStatus = GlucoseStatusSMB(
            glucose = determineBasalResult.glucoseStatus.getDouble("glucose"),
            noise = determineBasalResult.glucoseStatus.getDouble("noise"),
            delta = determineBasalResult.glucoseStatus.getDouble("delta"),
            shortAvgDelta = determineBasalResult.glucoseStatus.getDouble("short_avgdelta"),
            longAvgDelta = determineBasalResult.glucoseStatus.getDouble("long_avgdelta"),
            date = determineBasalResult.glucoseStatus.getLong("date")
        )
        val currentTemp = CurrentTemp(
            duration = determineBasalResult.currentTemp.getInt("duration"),
            rate = determineBasalResult.currentTemp.getDouble("rate"),
            minutesrunning = null
        )
        val autosensData = AutosensResult(
            ratio = determineBasalResult.autosensData.getDouble("ratio")
        )

        fun JSONObject.toIob(): IobTotal =
            IobTotal(
                time = dateUtil.fromISODateString(this.getString("time")),
                iob = this.getDouble("iob"),
                basaliob = this.getDouble("basaliob"),
                bolussnooze = this.getDouble("bolussnooze"),
                activity = this.getDouble("activity"),
                lastBolusTime = this.getLong("lastBolusTime"),
                iobWithZeroTemp = this.optJSONObject("iobWithZeroTemp")?.toIob()
            )

        val iobData = arrayListOf<IobTotal>()
        for (i in 0 until determineBasalResult.iobData!!.length())
            iobData.add(determineBasalResult.iobData!!.getJSONObject(i).toIob())
        val currentTime = determineBasalResult.currentTime
        val profile = OapsProfile(
            dia = 0.0,
            min_5m_carbimpact = 0.0,
            max_iob = determineBasalResult.profile.getDouble("max_iob"),
            max_daily_basal = determineBasalResult.profile.getDouble("max_daily_basal"),
            max_basal = determineBasalResult.profile.getDouble("max_basal"),
            min_bg = determineBasalResult.profile.getDouble("min_bg"),
            max_bg = determineBasalResult.profile.getDouble("max_bg"),
            target_bg = determineBasalResult.profile.getDouble("target_bg"),
            carb_ratio = determineBasalResult.profile.getDouble("carb_ratio"),
            sens = determineBasalResult.profile.getDouble("sens"),
            autosens_adjust_targets = false,
            max_daily_safety_multiplier = determineBasalResult.profile.getDouble("max_daily_safety_multiplier"),
            current_basal_safety_multiplier = determineBasalResult.profile.getDouble("current_basal_safety_multiplier"),
            lgsThreshold = determineBasalResult.profile.getInt("lgsThreshold"),
            high_temptarget_raises_sensitivity = determineBasalResult.profile.getBoolean("high_temptarget_raises_sensitivity"),
            low_temptarget_lowers_sensitivity = determineBasalResult.profile.getBoolean("low_temptarget_lowers_sensitivity"),
            sensitivity_raises_target = determineBasalResult.profile.getBoolean("sensitivity_raises_target"),
            resistance_lowers_target = determineBasalResult.profile.getBoolean("resistance_lowers_target"),
            adv_target_adjustments = determineBasalResult.profile.getBoolean("adv_target_adjustments"),
            exercise_mode = determineBasalResult.profile.getBoolean("exercise_mode"),
            half_basal_exercise_target = determineBasalResult.profile.getInt("half_basal_exercise_target"),
            maxCOB = determineBasalResult.profile.getInt("maxCOB"),
            skip_neutral_temps = determineBasalResult.profile.getBoolean("skip_neutral_temps"),
            remainingCarbsCap = determineBasalResult.profile.getInt("remainingCarbsCap"),
            enableUAM = determineBasalResult.profile.getBoolean("enableUAM"),
            A52_risk_enable = determineBasalResult.profile.getBoolean("A52_risk_enable"),
            SMBInterval = determineBasalResult.profile.getInt("SMBInterval"),
            enableSMB_with_COB = determineBasalResult.profile.getBoolean("enableSMB_with_COB"),
            enableSMB_with_temptarget = determineBasalResult.profile.getBoolean("enableSMB_with_temptarget"),
            allowSMB_with_high_temptarget = determineBasalResult.profile.getBoolean("allowSMB_with_high_temptarget"),
            enableSMB_always = determineBasalResult.profile.getBoolean("enableSMB_always"),
            enableSMB_after_carbs = determineBasalResult.profile.getBoolean("enableSMB_after_carbs"),
            maxSMBBasalMinutes = determineBasalResult.profile.getInt("maxSMBBasalMinutes"),
            maxUAMSMBBasalMinutes = determineBasalResult.profile.getInt("maxUAMSMBBasalMinutes"),
            bolus_increment = determineBasalResult.profile.getDouble("bolus_increment"),
            carbsReqThreshold = determineBasalResult.profile.getInt("carbsReqThreshold"),
            current_basal = determineBasalResult.profile.getDouble("current_basal"),
            temptargetSet = determineBasalResult.profile.getBoolean("temptargetSet"),
            autosens_max = determineBasalResult.profile.getDouble("autosens_max"),
            out_units = determineBasalResult.profile.optString("out_units"),
            variable_sens = determineBasalResult.profile.getDouble("variable_sens"),
            insulinDivisor = determineBasalResult.profile.getInt("insulinDivisor"),
            TDD = determineBasalResult.profile.getDouble("TDD")
        )
        val meatData = MealData(
            carbs = determineBasalResult.mealData.getDouble("carbs"),
            mealCOB = determineBasalResult.mealData.getDouble("mealCOB"),
            slopeFromMaxDeviation = determineBasalResult.mealData.getDouble("slopeFromMaxDeviation"),
            slopeFromMinDeviation = determineBasalResult.mealData.getDouble("slopeFromMinDeviation"),
            lastBolusTime = determineBasalResult.mealData.getLong("lastBolusTime"),
            lastCarbTime = determineBasalResult.mealData.getLong("lastCarbTime")
        )

        val resultKt = determineBasalSMBDynamicISF.determine_basal(
            glucose_status = glucoseStatus,
            currenttemp = currentTemp,
            iob_data_array = iobData.toTypedArray(),
            profile = profile,
            autosens_data = autosensData,
            meal_data = meatData,
            microBolusAllowed = determineBasalResult.microBolusAllowed,
            currentTime = currentTime,
            flatBGsDetected = determineBasalResult.flatBGsDetected,
            dynIsfMode = true
        )
        val endKt = System.currentTimeMillis()
        ktTime += (endKt - startKt)

        aapsLogger.info(LTag.APS, resultKt.toString())

        aapsLogger.debug(LTag.APS, result?.json()?.getString("reason") ?: "")
        aapsLogger.debug(LTag.APS, resultKt.reason.toString())
        aapsLogger.debug(LTag.APS, "File: $filename")
//        assertThat(resultKt.reason.toString()).isEqualTo(result?.json()?.getString("reason"))
        assertThat(resultKt.tick ?: "").isEqualTo(result?.json()?.optString("tick"))
        assertThat(resultKt.eventualBG ?: Double.NaN).isEqualTo(result?.json()?.optDouble("eventualBG"))
        assertThat(resultKt.targetBG ?: Double.NaN).isEqualTo(result?.json()?.optDouble("targetBG"))
        assertThat(resultKt.insulinReq ?: Double.NaN).isEqualTo(result?.json()?.optDouble("insulinReq"))
        assertThat(resultKt.carbsReq ?: 0).isEqualTo(result?.json()?.optInt("carbsReq"))
        assertThat(resultKt.carbsReqWithin ?: 0).isEqualTo(result?.json()?.optInt("carbsReqWithin"))
        assertThat(resultKt.units ?: Double.NaN).isEqualTo(result?.json()?.optDouble("units"))
        assertThat(resultKt.sensitivityRatio ?: Double.NaN).isEqualTo(result?.json()?.optDouble("sensitivityRatio"))
        assertThat(resultKt.duration ?: 0).isEqualTo(result?.json()?.optInt("duration"))
        assertThat(resultKt.rate ?: Double.NaN).isEqualTo(result?.json()?.optDouble("rate"))
        assertThat(resultKt.COB ?: Double.NaN).isEqualTo(result?.json()?.optDouble("COB"))
        assertThat(resultKt.IOB ?: Double.NaN).isEqualTo(result?.json()?.optDouble("IOB"))
        assertThat(resultKt.variable_sens ?: Double.NaN).isEqualTo(result?.json()?.optDouble("variable_sens"))
    }

    private fun testOpenAPSAMA(filename: String, input: JSONObject, output: JSONObject, injector: HasAndroidInjector) {

        val startJs = System.currentTimeMillis()
        val determineBasalResult = DetermineBasalAdapterAMAJS(ScriptReader(), injector)
        determineBasalResult.profile = input.getJSONObject("profile")
        determineBasalResult.glucoseStatus = input.getJSONObject("glucoseStatus")
        determineBasalResult.iobData = input.getJSONArray("iob_data")
        determineBasalResult.mealData = input.getJSONObject("meal_data")
        determineBasalResult.currentTemp = input.getJSONObject("currenttemp")
        determineBasalResult.autosensData = input.getJSONObject("autosens_data")
        val result = determineBasalResult.invoke()
        val endJs = System.currentTimeMillis()
        jsTime += (endJs - startJs)

        aapsLogger.info(LTag.APS, "Expected --> $output")
        assertThat(result).isNotNull()
        JSONAssert.assertEquals(
            "Error in file $filename",
            output.toString(),
            result?.json()?.apply {
                // this is added afterwards to json. Copy from original
                put("timestamp", output.getString("timestamp"))
            }.toString(),
            CustomComparator(JSONCompareMode.LENIENT, Customization("tick") { o1: Any?, o2: Any? -> o1.toString() == o2.toString() })
        )
        // Exclude these with whole number delta as the alg is producing different results
        // on inputs like 2.0 which are evaluated as Int 2
        val delta = determineBasalResult.glucoseStatus.getDouble("delta")
        if (floor(delta) == delta) return
        // Pass to DetermineBasalSMBDynamicISF

        if (determineBasalResult.profile.optString("out_units") == "mmol/L")
            preferences.put(StringKey.GeneralUnits, GlucoseUnit.MMOL.asText)
        else
            preferences.put(StringKey.GeneralUnits, GlucoseUnit.MGDL.asText)

        val startKt = System.currentTimeMillis()
        val glucoseStatus = GlucoseStatusSMB(
            glucose = determineBasalResult.glucoseStatus.getDouble("glucose"),
            noise = 0.0,
            delta = determineBasalResult.glucoseStatus.getDouble("delta"),
            shortAvgDelta = determineBasalResult.glucoseStatus.getDouble("short_avgdelta"),
            longAvgDelta = determineBasalResult.glucoseStatus.getDouble("long_avgdelta"),
            date = 0
        )
        val currentTemp = CurrentTemp(
            duration = determineBasalResult.currentTemp.getInt("duration"),
            rate = determineBasalResult.currentTemp.getDouble("rate"),
            minutesrunning = null
        )
        val autosensData = AutosensResult(
            ratio = determineBasalResult.autosensData.getDouble("ratio")
        )

        fun JSONObject.toIob(): IobTotal =
            IobTotal(
                time = dateUtil.fromISODateString(this.getString("time")),
                iob = this.getDouble("iob"),
                basaliob = this.getDouble("basaliob"),
                bolussnooze = this.getDouble("bolussnooze"),
                activity = this.getDouble("activity"),
                lastBolusTime = this.getLong("lastBolusTime"),
                iobWithZeroTemp = this.optJSONObject("iobWithZeroTemp")?.toIob()
            )

        val iobData = arrayListOf<IobTotal>()
        for (i in 0 until determineBasalResult.iobData!!.length())
            iobData.add(determineBasalResult.iobData!!.getJSONObject(i).toIob())
        val profile = OapsProfile(
            dia = determineBasalResult.profile.getDouble("dia"),
            min_5m_carbimpact = determineBasalResult.profile.getDouble("min_5m_carbimpact"),
            max_iob = determineBasalResult.profile.getDouble("max_iob"),
            max_daily_basal = determineBasalResult.profile.getDouble("max_daily_basal"),
            max_basal = determineBasalResult.profile.getDouble("max_basal"),
            min_bg = determineBasalResult.profile.getDouble("min_bg"),
            max_bg = determineBasalResult.profile.getDouble("max_bg"),
            target_bg = determineBasalResult.profile.getDouble("target_bg"),
            carb_ratio = determineBasalResult.profile.getDouble("carb_ratio"),
            sens = determineBasalResult.profile.getDouble("sens"),
            autosens_adjust_targets = determineBasalResult.profile.getBoolean("autosens_adjust_targets"),
            max_daily_safety_multiplier = determineBasalResult.profile.getDouble("max_daily_safety_multiplier"),
            current_basal_safety_multiplier = determineBasalResult.profile.getDouble("current_basal_safety_multiplier"),
            lgsThreshold = 0,
            high_temptarget_raises_sensitivity = false,
            low_temptarget_lowers_sensitivity = false,
            sensitivity_raises_target = false,
            resistance_lowers_target = false,
            adv_target_adjustments = false,
            exercise_mode = false,
            half_basal_exercise_target = 0,
            maxCOB = 0,
            skip_neutral_temps = determineBasalResult.profile.getBoolean("skip_neutral_temps"),
            remainingCarbsCap = 0,
            enableUAM = false,
            A52_risk_enable = false,
            SMBInterval = 0,
            enableSMB_with_COB = false,
            enableSMB_with_temptarget = false,
            allowSMB_with_high_temptarget = false,
            enableSMB_always = false,
            enableSMB_after_carbs = false,
            maxSMBBasalMinutes = 0,
            maxUAMSMBBasalMinutes = 0,
            bolus_increment = 0.0,
            carbsReqThreshold = 0,
            current_basal = determineBasalResult.profile.getDouble("current_basal"),
            temptargetSet = determineBasalResult.profile.getBoolean("temptargetSet"),
            autosens_max = 0.0,
            out_units = determineBasalResult.profile.optString("out_units"),
            variable_sens = 0.0,
            insulinDivisor = 0,
            TDD = 0.0
        )
        val mealData = MealData(
            carbs = determineBasalResult.mealData.getDouble("carbs"),
            mealCOB = determineBasalResult.mealData.getDouble("mealCOB"),
            slopeFromMaxDeviation = 0.0,
            slopeFromMinDeviation = 0.0,
            lastBolusTime = 0,
            lastCarbTime = 0
        )
        val resultKt = determineBasalAMA.determine_basal(
            glucose_status = glucoseStatus,
            currenttemp = currentTemp,
            iob_data_array = iobData.toTypedArray(),
            profile = profile,
            autosens_data = autosensData,
            meal_data = mealData,
            currentTime = 0
        )
        val endKt = System.currentTimeMillis()
        ktTime += (endKt - startKt)

        aapsLogger.info(LTag.APS, resultKt.toString())

        aapsLogger.debug(LTag.APS, result?.json()?.getString("reason") ?: "")
        aapsLogger.debug(LTag.APS, resultKt.reason.toString())
        aapsLogger.debug(LTag.APS, "File: $filename")
//        assertThat(resultKt.reason.toString()).isEqualTo(result?.json()?.getString("reason"))
        assertThat(resultKt.tick ?: "").isEqualTo(result?.json()?.optString("tick"))
        assertThat(resultKt.eventualBG ?: Double.NaN).isEqualTo(result?.json()?.optDouble("eventualBG"))
        assertThat(resultKt.targetBG ?: Double.NaN).isEqualTo(result?.json()?.optDouble("targetBG"))
        assertThat(resultKt.insulinReq ?: Double.NaN).isEqualTo(result?.json()?.optDouble("insulinReq"))
        assertThat(resultKt.carbsReq ?: 0).isEqualTo(result?.json()?.optInt("carbsReq"))
        assertThat(resultKt.carbsReqWithin ?: 0).isEqualTo(result?.json()?.optInt("carbsReqWithin"))
        assertThat(resultKt.units ?: Double.NaN).isEqualTo(result?.json()?.optDouble("units"))
        assertThat(resultKt.sensitivityRatio ?: Double.NaN).isEqualTo(result?.json()?.optDouble("sensitivityRatio"))
        assertThat(resultKt.duration ?: 0).isEqualTo(result?.json()?.optInt("duration"))
        assertThat(resultKt.rate ?: Double.NaN).isEqualTo(result?.json()?.optDouble("rate"))
        assertThat(resultKt.COB ?: Double.NaN).isEqualTo(result?.json()?.optDouble("COB"))
        assertThat(resultKt.IOB ?: Double.NaN).isEqualTo(result?.json()?.optDouble("IOB"))
        assertThat(resultKt.variable_sens ?: Double.NaN).isEqualTo(result?.json()?.optDouble("variable_sens"))
    }

    private fun testOpenAPSSMBAutoISF(filename: String, input: JSONObject, output: JSONObject, injector: HasAndroidInjector) {
        val startJs = System.currentTimeMillis()
        val determineBasalResult = DetermineBasalAdapterAutoISFJS(ScriptReader(), injector)
        determineBasalResult.profile = input.getJSONObject("profile")
        determineBasalResult.glucoseStatus = input.getJSONObject("glucoseStatus")
        determineBasalResult.iobData = input.getJSONArray("iob_data")
        determineBasalResult.mealData = input.getJSONObject("meal_data")
        determineBasalResult.currentTemp = input.getJSONObject("currenttemp")
        determineBasalResult.autosensData = input.getJSONObject("autosens_data")
        determineBasalResult.microBolusAllowed = input.getBoolean("microBolusAllowed")
        determineBasalResult.currentTime = input.getLong("currentTime")
        determineBasalResult.flatBGsDetected = input.getBoolean("flatBGsDetected")
        val result = determineBasalResult.invoke()
        val varSens = result?.variableSens!!
        val endJs = System.currentTimeMillis()
        jsTime += (endJs - startJs)

        aapsLogger.info(LTag.APS, "Expected --> $output")
        assertThat(result).isNotNull()
        JSONAssert.assertEquals(
            "Error in file $filename",
            output.toString(),
            result.json()?.apply {
                // this is added afterwards to json. Copy from original
                put("timestamp", output.getString("timestamp"))
            }.toString(),
            CustomComparator(JSONCompareMode.LENIENT, Customization("tick") { o1: Any?, o2: Any? -> o1.toString() == o2.toString() })
        )

        // Exclude these with whole number delta as the alg is producing different results
        // on inputs like 2.0 which are evaluated as Int 2
        val delta = determineBasalResult.glucoseStatus.getDouble("delta")
        if (floor(delta) == delta) return
        // Pass to DetermineBasalSMB

        if (determineBasalResult.profile.optString("out_units") == "mmol/L")
            preferences.put(StringKey.GeneralUnits, GlucoseUnit.MMOL.asText)
        else
            preferences.put(StringKey.GeneralUnits, GlucoseUnit.MGDL.asText)

        val startKt = System.currentTimeMillis()
        val glucoseStatus = GlucoseStatusAutoIsf(
            glucose = determineBasalResult.glucoseStatus.getDouble("glucose"),
            noise = determineBasalResult.glucoseStatus.getDouble("noise"),
            delta = determineBasalResult.glucoseStatus.getDouble("delta"),
            shortAvgDelta = determineBasalResult.glucoseStatus.getDouble("short_avgdelta"),
            longAvgDelta = determineBasalResult.glucoseStatus.getDouble("long_avgdelta"),
            date = determineBasalResult.glucoseStatus.getLong("date"),
            duraISFminutes = determineBasalResult.glucoseStatus.getDouble("dura_ISF_minutes"),
            duraISFaverage = determineBasalResult.glucoseStatus.getDouble("dura_ISF_average"),
            a0 = determineBasalResult.glucoseStatus.getDouble("parabola_fit_a0"),
            a1 = determineBasalResult.glucoseStatus.getDouble("parabola_fit_a1"),
            a2 = determineBasalResult.glucoseStatus.getDouble("parabola_fit_a2"),
            bgAcceleration = determineBasalResult.glucoseStatus.getDouble("bg_acceleration"),
            corrSqu = determineBasalResult.glucoseStatus.getDouble("parabola_fit_correlation")
        )
        val currentTemp = CurrentTemp(
            duration = determineBasalResult.currentTemp.getInt("duration"),
            rate = determineBasalResult.currentTemp.getDouble("rate"),
            minutesrunning = null
        )
        val autosensData = AutosensResult(
            ratio = determineBasalResult.autosensData.getDouble("ratio")
        )

        fun JSONObject.toIob(): IobTotal =
            IobTotal(
                time = dateUtil.fromISODateString(this.getString("time")),
                iob = this.getDouble("iob"),
                basaliob = this.getDouble("basaliob"),
                bolussnooze = this.getDouble("bolussnooze"),
                activity = this.getDouble("activity"),
                lastBolusTime = this.getLong("lastBolusTime"),
                iobWithZeroTemp = this.optJSONObject("iobWithZeroTemp")?.toIob()
            )

        val iobData = arrayListOf<IobTotal>()
        for (i in 0 until determineBasalResult.iobData!!.length())
            iobData.add(determineBasalResult.iobData!!.getJSONObject(i).toIob())
        val currentTime = determineBasalResult.currentTime
        val profile = OapsProfileAutoIsf(
            dia = 0.0,
            min_5m_carbimpact = 0.0,
            max_iob = determineBasalResult.profile.getDouble("max_iob"),
            max_daily_basal = determineBasalResult.profile.getDouble("max_daily_basal"),
            max_basal = determineBasalResult.profile.getDouble("max_basal"),
            min_bg = determineBasalResult.profile.getDouble("min_bg"),
            max_bg = determineBasalResult.profile.getDouble("max_bg"),
            target_bg = determineBasalResult.profile.getDouble("target_bg"),
            carb_ratio = determineBasalResult.profile.getDouble("carb_ratio"),
            sens = determineBasalResult.profile.getDouble("sens"),
            autosens_adjust_targets = false,
            max_daily_safety_multiplier = determineBasalResult.profile.getDouble("max_daily_safety_multiplier"),
            current_basal_safety_multiplier = determineBasalResult.profile.getDouble("current_basal_safety_multiplier"),
            lgsThreshold = null,
            high_temptarget_raises_sensitivity = determineBasalResult.profile.getBoolean("high_temptarget_raises_sensitivity"),
            low_temptarget_lowers_sensitivity = determineBasalResult.profile.getBoolean("low_temptarget_lowers_sensitivity"),
            sensitivity_raises_target = determineBasalResult.profile.getBoolean("sensitivity_raises_target"),
            resistance_lowers_target = determineBasalResult.profile.getBoolean("resistance_lowers_target"),
            adv_target_adjustments = determineBasalResult.profile.getBoolean("adv_target_adjustments"),
            exercise_mode = determineBasalResult.profile.getBoolean("exercise_mode"),
            half_basal_exercise_target = determineBasalResult.profile.getInt("half_basal_exercise_target"),
            maxCOB = determineBasalResult.profile.getInt("maxCOB"),
            skip_neutral_temps = determineBasalResult.profile.getBoolean("skip_neutral_temps"),
            remainingCarbsCap = determineBasalResult.profile.getInt("remainingCarbsCap"),
            enableUAM = determineBasalResult.profile.getBoolean("enableUAM"),
            A52_risk_enable = determineBasalResult.profile.getBoolean("A52_risk_enable"),
            SMBInterval = determineBasalResult.profile.getInt("SMBInterval"),
            enableSMB_with_COB = determineBasalResult.profile.getBoolean("enableSMB_with_COB"),
            enableSMB_with_temptarget = determineBasalResult.profile.getBoolean("enableSMB_with_temptarget"),
            allowSMB_with_high_temptarget = determineBasalResult.profile.getBoolean("allowSMB_with_high_temptarget"),
            enableSMB_always = determineBasalResult.profile.getBoolean("enableSMB_always"),
            enableSMB_after_carbs = determineBasalResult.profile.getBoolean("enableSMB_after_carbs"),
            maxSMBBasalMinutes = determineBasalResult.profile.getInt("maxSMBBasalMinutes"),
            maxUAMSMBBasalMinutes = determineBasalResult.profile.getInt("maxUAMSMBBasalMinutes"),
            bolus_increment = determineBasalResult.profile.getDouble("bolus_increment"),
            carbsReqThreshold = determineBasalResult.profile.getInt("carbsReqThreshold"),
            current_basal = determineBasalResult.profile.getDouble("current_basal"),
            temptargetSet = determineBasalResult.profile.getBoolean("temptargetSet"),
            autosens_max = determineBasalResult.profile.getDouble("autosens_max"),
            out_units = determineBasalResult.profile.optString("out_units"),
            variable_sens = varSens, // TODO only available in result.variableSens? , not in determineBasalResult.profile.getDouble("variable_sens"),
            autoISF_version = determineBasalResult.profile.optString("autoISF_version"),
            enable_autoISF = determineBasalResult.profile.getBoolean("enable_autoISF"),
            autoISF_max = determineBasalResult.profile.getDouble("autoISF_max"),
            autoISF_min = determineBasalResult.profile.getDouble("autoISF_min"),
            bgAccel_ISF_weight = determineBasalResult.profile.getDouble("bgAccel_ISF_weight"),
            bgBrake_ISF_weight = determineBasalResult.profile.getDouble("bgBrake_ISF_weight"),
            pp_ISF_weight = determineBasalResult.profile.getDouble("pp_ISF_weight"),
            lower_ISFrange_weight = determineBasalResult.profile.getDouble("lower_ISFrange_weight"),
            higher_ISFrange_weight = determineBasalResult.profile.getDouble("higher_ISFrange_weight"),
            dura_ISF_weight = determineBasalResult.profile.getDouble("dura_ISF_weight"),
            smb_delivery_ratio = determineBasalResult.profile.getDouble("smb_delivery_ratio"),
            smb_delivery_ratio_min = determineBasalResult.profile.getDouble("smb_delivery_ratio_min"),
            smb_delivery_ratio_max = determineBasalResult.profile.getDouble("smb_delivery_ratio_max"),
            smb_delivery_ratio_bg_range = determineBasalResult.profile.getDouble("smb_delivery_ratio_bg_range"),
            smb_max_range_extension = determineBasalResult.profile.getDouble("smb_max_range_extension"),
            enableSMB_EvenOn_OddOff_always = determineBasalResult.profile.getBoolean("enableSMB_EvenOn_OddOff_always"),
            iob_threshold_percent = determineBasalResult.profile.getInt("iob_threshold_percent"),
            profile_percentage = determineBasalResult.profile.getInt("profile_percentage")
        )
        val meatData = MealData(
            carbs = determineBasalResult.mealData.getDouble("carbs"),
            mealCOB = determineBasalResult.mealData.getDouble("mealCOB"),
            slopeFromMaxDeviation = determineBasalResult.mealData.getDouble("slopeFromMaxDeviation"),
            slopeFromMinDeviation = determineBasalResult.mealData.getDouble("slopeFromMinDeviation"),
            lastBolusTime = determineBasalResult.mealData.getLong("lastBolusTime"),
            lastCarbTime = determineBasalResult.mealData.getLong("lastCarbTime")
        )
        val resultKt = determineBasalAutoISF.determine_basal(
            glucose_status = glucoseStatus,
            currenttemp = currentTemp,
            iob_data_array = iobData.toTypedArray(),
            profile = profile,
            autosens_data = autosensData,
            meal_data = meatData,
            microBolusAllowed = determineBasalResult.microBolusAllowed,
            currentTime = currentTime,
            flatBGsDetected = determineBasalResult.flatBGsDetected,
            autoIsfMode = true, //preferences.get(BooleanKey.ApsUseAutoIsf),
            iob_threshold_percent = preferences.get(IntKey.ApsAutoIsfIobThPercent),
            smb_max_range_extension = preferences.get(DoubleKey.ApsAutoIsfSmbMaxRangeExtension),
            profile_percentage = profile.profile_percentage, // 100,
            smb_ratio = profile.smb_delivery_ratio, // 0.5,
            loop_wanted_smb = "dummy",
            auto_isf_consoleLog = mutableListOf<String>("end AutoISF"),
            auto_isf_consoleError = mutableListOf<String>("start AutoISF")
        )
        val endKt = System.currentTimeMillis()
        ktTime += (endKt - startKt)
//
        // aapsLogger.info(LTag.APS, resultKt.toString())
//
        // aapsLogger.debug(LTag.APS, result?.json()?.getString("reason") ?: "")
        // aapsLogger.debug(LTag.APS, resultKt.reason.toString())
        aapsLogger.debug(LTag.APS, "File: $filename")
//      //   assertThat(resultKt.reason.toString()).isEqualTo(result?.json?.getString("reason"))
        assertThat(resultKt.tick ?: "").isEqualTo(result.json()?.optString("tick"))
        assertThat(resultKt.eventualBG ?: 0.0).isWithin(1.0).of(result.json()?.optDouble("eventualBG") ?: 0.0)
        assertThat(resultKt.targetBG ?: Double.NaN).isEqualTo(result.json()?.optDouble("targetBG"))
        assertThat(resultKt.insulinReq ?: Double.NaN).isEqualTo(result.json()?.optDouble("insulinReq"))
        assertThat(resultKt.carbsReq ?: 0).isEqualTo(result.json()?.optInt("carbsReq"))
        assertThat(resultKt.carbsReqWithin ?: 0).isEqualTo(result.json()?.optInt("carbsReqWithin"))
        assertThat(resultKt.units ?: Double.NaN).isEqualTo(result.json()?.optDouble("units"))
        assertThat(resultKt.sensitivityRatio ?: Double.NaN).isEqualTo(result.json()?.optDouble("sensitivityRatio"))
        assertThat(resultKt.duration ?: 0).isEqualTo(result.json()?.optInt("duration"))
        assertThat(resultKt.rate ?: Double.NaN).isEqualTo(result.json()?.optDouble("rate"))
        assertThat(resultKt.COB ?: Double.NaN).isEqualTo(result.json()?.optDouble("COB"))
        assertThat(resultKt.IOB ?: Double.NaN).isEqualTo(result.json()?.optDouble("IOB"))
        assertThat(resultKt.variable_sens ?: Double.NaN).isEqualTo(result.json()?.optDouble("variable_sens"))
    }

    enum class TestSource { ASSET, FILE }
    data class TestFile(val source: TestSource, val path: String, val name: String)

    private fun readResultFiles(): MutableList<TestFile> {
        val apsResults = mutableListOf<TestFile>()

        // look for results in filesystem
        fileListProvider.resultPath.walk().maxDepth(1)
            .filter { it.isFile && it.name.endsWith(".json") }
            .forEach {
                // val contents = storage.getFileContents(it)
                // apsResults.add(JSONObject(contents).apply { put("filename", it.name) })
                apsResults.add(TestFile(TestSource.FILE, it.path, it.name))
            }

        // look for results in assets
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val assetFiles = assets.list("results") ?: arrayOf()
        for (assetFileName in assetFiles) {
            if (assetFileName.endsWith(".json")) {
                // val contents = assets.open("results/$assetFileName").readBytes().toString(StandardCharsets.UTF_8)
                // apsResults.add(JSONObject(contents).apply { put("filename", assetFileName) })
                apsResults.add(TestFile(TestSource.ASSET, assetFileName, assetFileName))
            }
        }
        return apsResults
    }

    private fun TestFile.readContent(): JSONObject {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return when (source) {
            TestSource.ASSET -> JSONObject(assets.open("results/$name").readBytes().toString(StandardCharsets.UTF_8)).apply { put("filename", name) }
            TestSource.FILE  -> JSONObject(storage.getFileContents(File(path))).apply { put("filename", name) }
        }
    }
}
