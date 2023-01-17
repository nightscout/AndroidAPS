package info.nightscout.source

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.annotations.OpenForTesting
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
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class GlimpPlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP
) : PluginBase(
    PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(info.nightscout.core.main.R.drawable.ic_glimp)
    .pluginName(R.string.glimp)
    .preferencesId(R.xml.pref_bgsource)
    .description(R.string.description_source_glimp),
    aapsLogger, rh, injector
), BgSource {

    // cannot be inner class because of needed injection
    class GlimpWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var injector: HasAndroidInjector
        @Inject lateinit var glimpPlugin: GlimpPlugin
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var xDripBroadcast: XDripBroadcast

        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()

            if (!glimpPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            aapsLogger.debug(LTag.BGSOURCE, "Received Glimp Data: $inputData}")
            val glucoseValues = mutableListOf<TransactionGlucoseValue>()
            glucoseValues += TransactionGlucoseValue(
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
                        xDripBroadcast.sendIn640gMode(it)
                        aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                    }
                }
            return ret
        }
    }

    override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean =
        glucoseValue.sourceSensor == GlucoseValue.SourceSensor.GLIMP && sp.getBoolean(info.nightscout.core.utils.R.string.key_do_ns_upload, false)

}