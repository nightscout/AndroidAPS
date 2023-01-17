package info.nightscout.source

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.CgmSourceTransaction
import info.nightscout.database.transactions.TransactionGlucoseValue
import info.nightscout.interfaces.XDripBroadcast
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.source.BgSource
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MM640gPlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP
) : PluginBase(
    PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(info.nightscout.core.main.R.drawable.ic_generic_cgm)
    .pluginName(R.string.mm640g)
    .description(R.string.description_source_mm640g),
    aapsLogger, rh, injector
), BgSource {

    // cannot be inner class because of needed injection
    class MM640gWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var mM640gPlugin: MM640gPlugin
        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var dataWorkerStorage: DataWorkerStorage
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var xDripBroadcast: XDripBroadcast

        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()

            if (!mM640gPlugin.isEnabled()) return Result.success()
            val collection = inputData.getString("collection") ?: return Result.failure(workDataOf("Error" to "missing collection"))
            if (collection == "entries") {
                val data = inputData.getString("data")
                aapsLogger.debug(LTag.BGSOURCE, "Received MM640g Data: $data")
                if (!data.isNullOrEmpty()) {
                    try {
                        val glucoseValues = mutableListOf<TransactionGlucoseValue>()
                        val jsonArray = JSONArray(data)
                        for (i in 0 until jsonArray.length()) {
                            val jsonObject = jsonArray.getJSONObject(i)
                            when (val type = jsonObject.getString("type")) {
                                "sgv" ->
                                    glucoseValues += TransactionGlucoseValue(
                                        timestamp = jsonObject.getLong("date"),
                                        value = jsonObject.getDouble("sgv"),
                                        raw = jsonObject.getDouble("sgv"),
                                        noise = null,
                                        trendArrow = GlucoseValue.TrendArrow.fromString(jsonObject.getString("direction")),
                                        sourceSensor = GlucoseValue.SourceSensor.MM_600_SERIES
                                    )
                                else  -> aapsLogger.debug(LTag.BGSOURCE, "Unknown entries type: $type")
                            }
                        }
                        repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
                            .doOnError {
                                aapsLogger.error(LTag.DATABASE, "Error while saving values from Eversense App", it)
                                ret = Result.failure(workDataOf("Error" to it.toString()))
                            }
                            .blockingGet()
                            .also { savedValues ->
                                savedValues.all().forEach {
                                    xDripBroadcast.sendIn640gMode(it)
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

    override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean =
        glucoseValue.sourceSensor == GlucoseValue.SourceSensor.MM_600_SERIES && sp.getBoolean(info.nightscout.core.utils.R.string.key_do_ns_upload, false)

}