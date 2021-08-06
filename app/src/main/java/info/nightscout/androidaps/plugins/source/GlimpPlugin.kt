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
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlimpPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(R.drawable.ic_glimp)
    .pluginName(R.string.Glimp)
    .preferencesId(R.xml.pref_bgsource)
    .description(R.string.description_source_glimp),
    aapsLogger, resourceHelper, injector
), BgSource {

    // cannot be inner class because of needed injection
    class GlimpWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var glimpPlugin: GlimpPlugin
        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var broadcastToXDrip: XDripBroadcast

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            var ret = Result.success()

            if (!glimpPlugin.isEnabled(PluginType.BGSOURCE)) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            aapsLogger.debug(LTag.BGSOURCE, "Received Glimp Data: $inputData}")
            val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
            glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
                timestamp = inputData.getLong("myTimestamp", 0),
                value = inputData.getDouble("mySGV", 0.0),
                raw = inputData.getDouble("mySGV", 0.0),
                noise = null,
                trendArrow = GlucoseValue.TrendArrow.fromString(inputData.getString("myTrend")),
                sourceSensor = GlucoseValue.SourceSensor.GLIMP
            )
            repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving values from Glimp App", it)
                    ret = Result.failure(workDataOf("Error" to it.toString()))
                }
                .blockingGet()
                .also { savedValues ->
                    savedValues.inserted.forEach {
                        broadcastToXDrip(it)
                        aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                    }
                }
            return ret
        }
    }

    override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean =
        glucoseValue.sourceSensor == GlucoseValue.SourceSensor.GLIMP && sp.getBoolean(R.string.key_dexcomg5_nsupload, false)

}