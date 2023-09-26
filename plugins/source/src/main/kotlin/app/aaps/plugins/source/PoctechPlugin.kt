package app.aaps.plugins.source

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.configuration.Constants
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.main.utils.worker.LoggingWorker
import app.aaps.core.utils.JsonHelper.safeGetString
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.CgmSourceTransaction
import app.aaps.database.transactions.TransactionGlucoseValue
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoctechPlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon(app.aaps.core.main.R.drawable.ic_poctech)
        .preferencesId(R.xml.pref_bgsource)
        .pluginName(R.string.poctech)
        .description(R.string.description_source_poctech),
    aapsLogger, rh, injector
), BgSource {

    // cannot be inner class because of needed injection
    class PoctechWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var poctechPlugin: PoctechPlugin
        @Inject lateinit var repository: AppRepository

        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()

            if (!poctechPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            aapsLogger.debug(LTag.BGSOURCE, "Received Poctech Data $inputData")
            try {
                val glucoseValues = mutableListOf<TransactionGlucoseValue>()
                val jsonArray = JSONArray(inputData.getString("data"))
                aapsLogger.debug(LTag.BGSOURCE, "Received Poctech Data size:" + jsonArray.length())
                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    glucoseValues += TransactionGlucoseValue(
                        timestamp = json.getLong("date"),
                        value = if (safeGetString(json, "units", GlucoseUnit.MGDL.asText) == "mmol/L") json.getDouble("current") * Constants.MMOLL_TO_MGDL
                        else json.getDouble("current"),
                        raw = json.getDouble("raw"),
                        noise = null,
                        trendArrow = GlucoseValue.TrendArrow.fromString(json.getString("direction")),
                        sourceSensor = GlucoseValue.SourceSensor.POCTECH_NATIVE
                    )
                }
                repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
                    .doOnError {
                        aapsLogger.error(LTag.DATABASE, "Error while saving values from Poctech App", it)
                        ret = Result.failure(workDataOf("Error" to it.toString()))
                    }
                    .blockingGet()
                    .also { savedValues ->
                        savedValues.inserted.forEach {
                            aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                        }
                    }
            } catch (e: JSONException) {
                aapsLogger.error("Exception: ", e)
                ret = Result.failure(workDataOf("Error" to e.toString()))
            }
            return ret
        }
    }
}