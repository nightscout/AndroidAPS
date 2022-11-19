package info.nightscout.androidaps.plugins.source

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.interfaces.BgSource
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PathedSIAppPlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(R.drawable.ic_generic_cgm)
    .pluginName(R.string.patched_si_app)
    .description(R.string.description_source_patched_si_app),
    aapsLogger, rh, injector
), BgSource {

    // cannot be inner class because of needed injection
    class PathedSIAppWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var mSIAppPlugin: PathedSIAppPlugin
        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var dateUtil: DateUtil
        @Inject lateinit var dataWorker: DataWorker
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var xDripBroadcast: XDripBroadcast

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            var ret = Result.success()

            if (!mSIAppPlugin.isEnabled()) return Result.success()
            val collection = inputData.getString("collection") ?: return Result.failure(workDataOf("Error" to "missing collection"))
            if (collection == "entries") {
                val data = inputData.getString("data")
                aapsLogger.debug(LTag.BGSOURCE, "Received SI App Data: $data")
                if (data != null && data.isNotEmpty()) {
                    try {
                        val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
                        val jsonArray = JSONArray(data)
                        for (i in 0 until jsonArray.length()) {
                            val jsonObject = jsonArray.getJSONObject(i)
                            when (val type = jsonObject.getString("type")) {
                                "sgv" ->
                                    glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
                                        timestamp = jsonObject.getLong("date"),
                                        value = jsonObject.getDouble("sgv"),
                                        raw = jsonObject.getDouble("sgv"),
                                        noise = null,
                                        trendArrow = GlucoseValue.TrendArrow.fromString(jsonObject.getString("direction")),
                                        sourceSensor = GlucoseValue.SourceSensor.SIApp
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
                                    xDripBroadcast.send(it)
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
        glucoseValue.sourceSensor == GlucoseValue.SourceSensor.SIApp && sp.getBoolean(R.string.key_dexcomg5_nsupload, false)

}