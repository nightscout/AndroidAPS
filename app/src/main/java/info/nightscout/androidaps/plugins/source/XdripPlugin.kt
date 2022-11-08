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
import info.nightscout.androidaps.receivers.Intents
import info.nightscout.androidaps.interfaces.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XdripPlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon((R.drawable.ic_blooddrop_48))
    .pluginName(R.string.xdrip)
    .description(R.string.description_source_xdrip),
    aapsLogger, rh, injector
), BgSource {

    private var advancedFiltering = false
    override var sensorBatteryLevel = -1

    override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean  = false

    override fun advancedFilteringSupported(): Boolean {
        return advancedFiltering
    }

    private fun detectSource(glucoseValue: GlucoseValue) {
        advancedFiltering = arrayOf(
            GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN,
            GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE,
            GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE,
            GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE_XDRIP,
            GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE_XDRIP,
            GlucoseValue.SourceSensor.DEXCOM_G6_G5_NATIVE_XDRIP
        ).any { it == glucoseValue.sourceSensor }
    }

    // cannot be inner class because of needed injection
    class XdripWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var aapsLogger: AAPSLogger
        @Inject lateinit var xdripPlugin: XdripPlugin
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var dataWorker: DataWorker

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            var ret = Result.success()

            if (!xdripPlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val bundle = dataWorker.pickupBundle(inputData.getLong(DataWorker.STORE_KEY, -1))
                ?: return Result.failure(workDataOf("Error" to "missing input data"))

            aapsLogger.debug(LTag.BGSOURCE, "Received xDrip data: $bundle")
            val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
            glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
                timestamp = bundle.getLong(Intents.EXTRA_TIMESTAMP, 0),
                value = bundle.getDouble(Intents.EXTRA_BG_ESTIMATE, 0.0),
                raw = bundle.getDouble(Intents.EXTRA_RAW, 0.0),
                smoothed = null,
                noise = null,
                trendArrow = GlucoseValue.TrendArrow.fromString(bundle.getString(Intents.EXTRA_BG_SLOPE_NAME)),
                sourceSensor = GlucoseValue.SourceSensor.fromString(bundle.getString(Intents.XDRIP_DATA_SOURCE_DESCRIPTION)
                    ?: "")
            )
            repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving values from Xdrip", it)
                    ret = Result.failure(workDataOf("Error" to it.toString()))
                }
                .blockingGet()
                .also { savedValues ->
                    savedValues.all().forEach {
                        xdripPlugin.detectSource(it)
                        aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                    }
                }
            xdripPlugin.sensorBatteryLevel = bundle.getInt(Intents.EXTRA_SENSOR_BATTERY, -1)
            return ret
        }
    }
}
