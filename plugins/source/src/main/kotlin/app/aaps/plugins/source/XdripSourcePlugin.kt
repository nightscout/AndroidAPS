package app.aaps.plugins.source

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.main.utils.worker.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.CgmSourceTransaction
import app.aaps.database.transactions.TransactionGlucoseValue
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XdripSourcePlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .fragmentClass(BGSourceFragment::class.java.name)
        .pluginIcon((app.aaps.core.main.R.drawable.ic_blooddrop_48))
        .preferencesId(R.xml.pref_bgsource)
        .pluginName(R.string.source_xdrip)
        .description(R.string.description_source_xdrip),
    aapsLogger, rh, injector
), BgSource, XDripSource {

    private var advancedFiltering = false
    override var sensorBatteryLevel = -1

    override fun advancedFilteringSupported(): Boolean = advancedFiltering

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
    class XdripSourceWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var xdripSourcePlugin: XdripSourcePlugin
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var dataWorkerStorage: DataWorkerStorage

        override suspend fun doWorkAndLog(): Result {
            var ret = Result.success()

            if (!xdripSourcePlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            val bundle = dataWorkerStorage.pickupBundle(inputData.getLong(DataWorkerStorage.STORE_KEY, -1))
                ?: return Result.failure(workDataOf("Error" to "missing input data"))

            aapsLogger.debug(LTag.BGSOURCE, "Received xDrip data: $bundle")
            val glucoseValues = mutableListOf<TransactionGlucoseValue>()
            glucoseValues += TransactionGlucoseValue(
                timestamp = bundle.getLong(Intents.EXTRA_TIMESTAMP, 0),
                value = bundle.getDouble(Intents.EXTRA_BG_ESTIMATE, 0.0),
                raw = bundle.getDouble(Intents.EXTRA_RAW, 0.0),
                noise = null,
                trendArrow = GlucoseValue.TrendArrow.fromString(bundle.getString(Intents.EXTRA_BG_SLOPE_NAME)),
                sourceSensor = GlucoseValue.SourceSensor.fromString(
                    bundle.getString(Intents.XDRIP_DATA_SOURCE_DESCRIPTION)
                        ?: ""
                )
            )
            repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null))
                .doOnError {
                    aapsLogger.error(LTag.DATABASE, "Error while saving values from Xdrip", it)
                    ret = Result.failure(workDataOf("Error" to it.toString()))
                }
                .blockingGet()
                .also { savedValues ->
                    savedValues.all().forEach {
                        xdripSourcePlugin.detectSource(it)
                        aapsLogger.debug(LTag.DATABASE, "Inserted bg $it")
                    }
                }
            xdripSourcePlugin.sensorBatteryLevel = bundle.getInt(Intents.EXTRA_SENSOR_BATTERY, -1)
            return ret
        }
    }
}
