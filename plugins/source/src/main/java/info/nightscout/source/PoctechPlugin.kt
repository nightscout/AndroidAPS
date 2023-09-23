package info.nightscout.source

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.JsonHelper.safeGetString
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.CgmSourceTransaction
import info.nightscout.database.transactions.TransactionGlucoseValue
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.source.BgSource
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
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
        .pluginIcon(info.nightscout.core.main.R.drawable.ic_poctech)
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