package info.nightscout.androidaps.plugins.source

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
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
import info.nightscout.androidaps.utils.JsonHelper.safeGetString
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoctechPlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(R.drawable.ic_poctech)
    .pluginName(R.string.poctech)
    .preferencesId(R.xml.pref_bgsource)
    .description(R.string.description_source_poctech),
    aapsLogger, rh, injector
), BgSource {

    // cannot be inner class because of needed injection
    class PoctechWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var poctechPlugin: PoctechPlugin
        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var xDripBroadcast: XDripBroadcast

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            var ret = Result.success()

            if (!poctechPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            aapsLogger.debug(LTag.BGSOURCE, "Received Poctech Data $inputData")
            try {
                val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
                val jsonArray = JSONArray(inputData.getString("data"))
                aapsLogger.debug(LTag.BGSOURCE, "Received Poctech Data size:" + jsonArray.length())
                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
                        timestamp = json.getLong("date"),
                        value = if (safeGetString(json, "units", Constants.MGDL) == "mmol/L") json.getDouble("current") * Constants.MMOLL_TO_MGDL
                        else json.getDouble("current"),
                        smoothed = null,
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
                            xDripBroadcast.send(it)
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

    override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean =
        glucoseValue.sourceSensor == GlucoseValue.SourceSensor.POCTECH_NATIVE && sp.getBoolean(R.string.key_dexcomg5_nsupload, false)

}