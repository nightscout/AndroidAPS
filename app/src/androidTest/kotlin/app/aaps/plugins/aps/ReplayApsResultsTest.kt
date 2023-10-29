package app.aaps.plugins.aps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.aaps.TestApplication
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.PrefFileListProvider
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.aps.openAPSAMA.DetermineBasalAdapterAMAJS
import app.aaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalAdapterSMBJS
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import app.aaps.plugins.aps.openAPSSMBDynamicISF.DetermineBasalAdapterSMBDynamicISFJS
import app.aaps.plugins.aps.openAPSSMBDynamicISF.OpenAPSSMBDynamicISFPlugin
import app.aaps.plugins.aps.utils.ScriptReader
import com.google.common.truth.Truth.assertThat
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class ReplayApsResultsTest @Inject constructor() {

    @Inject lateinit var prefFileListProvider: PrefFileListProvider
    @Inject lateinit var storage: Storage
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var injector: HasAndroidInjector

    private val context = ApplicationProvider.getApplicationContext<TestApplication>()

    @get:Rule
    var runtimePermissionRule = GrantPermissionRule.grant(android.Manifest.permission.READ_EXTERNAL_STORAGE)!!

    @Before
    fun inject() {
        context.androidInjector().inject(this)
    }

    @Test
    fun replayTest() {
        val results = readResultFiles()
        assertThat(results.size).isGreaterThan(0)
        results.forEach { result ->
            val algorithm = JsonHelper.safeGetString(result, "algorithm")
            val inputString = JsonHelper.safeGetString(result, "input") ?: error("Missing input")
            val outputString = JsonHelper.safeGetString(result, "output") ?: error("Missing output")
            val filename = JsonHelper.safeGetString(result, "filename") ?: "Unknown filename"
            val input = JSONObject(inputString)
            val output = JSONObject(outputString)
            when (algorithm) {
                OpenAPSSMBPlugin::class.simpleName           -> testOpenAPSSMB(filename, input, output, context, injector)
                OpenAPSSMBDynamicISFPlugin::class.simpleName -> testOpenAPSSMBDynamicISF(filename, input, output, context, injector)
                OpenAPSAMAPlugin::class.simpleName           -> testOpenAPSAMA(filename, input, output, context, injector)
            }
        }
    }

    private fun testOpenAPSSMB(filename: String, input: JSONObject, output: JSONObject, context: Context, injector: HasAndroidInjector) {
        val determineBasalResult = DetermineBasalAdapterSMBJS(ScriptReader(context), injector)
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
        aapsLogger.info(LTag.APS, "Expected --> $output")
        assertThat(result).isNotNull()
        JSONAssert.assertEquals(
            "Error in file $filename",
            output,
            result?.json()?.apply {
                // this is added afterwards to json. Copy from original
                put("timestamp", output.getString("timestamp"))
            }, false
        )
    }

    private fun testOpenAPSSMBDynamicISF(filename: String, input: JSONObject, output: JSONObject, context: Context, injector: HasAndroidInjector) {
        val determineBasalResult = DetermineBasalAdapterSMBDynamicISFJS(ScriptReader(context), injector)
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
        aapsLogger.info(LTag.APS, "Expected --> $output")
        assertThat(result).isNotNull()
        JSONAssert.assertEquals(
            "Error in file $filename",
            output,
            result?.json()?.apply {
                // this is added afterwards to json. Copy from original
                put("timestamp", output.getString("timestamp"))
            }, false
        )
    }

    private fun testOpenAPSAMA(filename: String, input: JSONObject, output: JSONObject, context: Context, injector: HasAndroidInjector) {
        val determineBasalResult = DetermineBasalAdapterAMAJS(ScriptReader(context), injector)
        determineBasalResult.profile = input.getJSONObject("profile")
        determineBasalResult.glucoseStatus = input.getJSONObject("glucoseStatus")
        determineBasalResult.iobData = input.getJSONArray("iob_data")
        determineBasalResult.mealData = input.getJSONObject("meal_data")
        determineBasalResult.currentTemp = input.getJSONObject("currenttemp")
        determineBasalResult.autosensData = input.getJSONObject("autosens_data")

        val result = determineBasalResult.invoke()
        aapsLogger.info(LTag.APS, "Expected --> $output")
        assertThat(result).isNotNull()
        JSONAssert.assertEquals(
            "Error in file $filename",
            output,
            result?.json()?.apply {
                // this is added afterwards to json. Copy from original
                put("timestamp", output.getString("timestamp"))
            }, false
        )
    }

    private fun readResultFiles(): MutableList<JSONObject> {
        val apsResults = mutableListOf<JSONObject>()

        // look for results in filesystem
        prefFileListProvider.resultPath.walk().maxDepth(1)
            .filter { it.isFile && it.name.endsWith(".json") }
            .forEach {
                val contents = storage.getFileContents(it)
                apsResults.add(JSONObject(contents).apply { put("filename", it.name) })
            }

        // look for results in assets
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val assetFiles = assets.list("results") ?: arrayOf()
        for (assetFileName in assetFiles) {
            if (assetFileName.endsWith(".json")) {
                val contents = assets.open("results/$assetFileName").readBytes().toString(StandardCharsets.UTF_8)
                apsResults.add(JSONObject(contents).apply { put("filename", assetFileName) })
            }
        }
        return apsResults
    }
}
