package app.aaps.plugins.source

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.main.utils.worker.LoggingWorker
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
class PathedOTAppPlugin @Inject constructor(injector: HasAndroidInjector, rh: ResourceHelper, aapsLogger: AAPSLogger, )
    : PluginBase(PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .preferencesId(R.xml.pref_bgsource)
        .pluginIcon(app.aaps.core.ui.R.mipmap.ottai_icon)
        .pluginName(R.string.patched_ottai_app)
        .description(R.string.description_source_patched_ottai_app),
    aapsLogger, rh, injector), BgSource {

    // cannot be inner class because of needed injection
    class PathedOTAppWorker(context: Context, params: WorkerParameters) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var mOTAppPlugin: PathedOTAppPlugin
        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var repository: AppRepository

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()
            if (!mOTAppPlugin.isEnabled()) return Result.success()
            val collection = inputData.getString("collection") ?: return Result.failure(workDataOf("Error" to "missing collection"))
            if (collection == "entries") {
                val data = inputData.getString("data")
                aapsLogger.debug(LTag.BGSOURCE, "Received SI App Data: $data")
                if (!data.isNullOrEmpty()) {
                    try {
                        val glucoseValues = mutableListOf<TransactionGlucoseValue>()
                        val jsonArray = JSONArray(data)
                        for (i in 0 until jsonArray.length()) {
                            val jsonObject = jsonArray.getJSONObject(i)
                            when (val type = jsonObject.getString("type")) {
                                "sgv" ->{
                                    glucoseValues += TransactionGlucoseValue(
                                        timestamp = jsonObject.getLong("date"),
                                        value = jsonObject.getDouble("sgv"),
                                        raw = jsonObject.getDouble("sgv"),
                                        noise = null,
                                        trendArrow = GlucoseValue.TrendArrow.fromString(jsonObject.getString("direction")),
                                        sourceSensor = GlucoseValue.SourceSensor.OTApp)
                                    }
                                    else  -> aapsLogger.debug(LTag.BGSOURCE, "Unknown entries type: $type")
                                }
                            }
                            repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
                            .doOnError {
                                aapsLogger.error(LTag.DATABASE, "Error while saving values from Ottai App", it)
                                ret = Result.failure(workDataOf("Error" to it.toString()))
                            }
                            .blockingGet()
                            .also { savedValues ->
                                savedValues.all().forEach {
                                    aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                                }
                            }
                    } catch (e: JSONException) {
                        aapsLogger.error("Exception: ", e)
                        ret = Result.failure(workDataOf("Error" to e.toString()))
                    }
                }
            }
            return ret
        }
    }
}