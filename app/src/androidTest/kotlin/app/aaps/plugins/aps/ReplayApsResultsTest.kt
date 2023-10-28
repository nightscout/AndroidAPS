package app.aaps.plugins.aps

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.aaps.TestApplication
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.PrefFileListProvider
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class ReplayApsResultsTest @Inject constructor() {

    @Inject lateinit var prefFileListProvider: PrefFileListProvider
    @Inject lateinit var storage: Storage
    @Inject lateinit var aapsLogger: AAPSLogger

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
            val input = JsonHelper.safeGetString(result, "input") ?: error("Missing input")
            val output = JsonHelper.safeGetString(result, "output") ?: error("Missing output")
            val iJson = JSONObject(input)
            val oJson = JSONObject
            when (algorithm) {
                OpenAPSSMBPlugin::class.simpleName -> testOpenAPSSMB(input, output)
            }
        }
    }

    private fun testOpenAPSSMB(input: JSONObject, output: JSONObject) {

    }
    private fun testOpenAPSSMBDynamicISF(input: JSONObject, output: JSONObject) {

    }

    private fun readResultFiles(): MutableList<JSONObject> {
        val apsResults = mutableListOf<JSONObject>()

        // look for results in filesystem
        prefFileListProvider.resultPath.walk().maxDepth(1)
            .filter { it.isFile && it.name.endsWith(".json") }
            .forEach {
                val contents = storage.getFileContents(it)
                apsResults.add(JSONObject(contents))
            }

        // look for results in assets
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val assetFiles = assets.list("results") ?: arrayOf()
        for (assetFileName in assetFiles) {
            if (assetFileName.endsWith(".json")) {
                val contents = assets.open("results/$assetFileName").readBytes().toString(StandardCharsets.UTF_8)
                apsResults.add(JSONObject(contents))
            }
        }
        return apsResults
    }
}
