package info.nightscout.androidaps.plugins.source

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.receivers.BundleStore
import info.nightscout.androidaps.receivers.DataReceiver
import info.nightscout.androidaps.services.Intents
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XdripPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon((R.drawable.ic_blooddrop_48))
    .pluginName(R.string.xdrip)
    .description(R.string.description_source_xdrip),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    private var advancedFiltering = false
    override var sensorBatteryLevel = -1

    override fun advancedFilteringSupported(): Boolean {
        return advancedFiltering
    }

    private fun detectSource(glucoseValue: GlucoseValue) {
        advancedFiltering = arrayOf(
            GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN,
            GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE,
            GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE,
            GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE_XDRIP,
            GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE_XDRIP
        ).any { it == glucoseValue.sourceSensor }
    }

    private val disposable = CompositeDisposable()

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    // cannot be inner class because of needed injection
    class XdripWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var xdripPlugin: XdripPlugin
        @Inject lateinit var repository: AppRepository
        @Inject lateinit var bundleStore: BundleStore

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            if (!xdripPlugin.isEnabled(PluginType.BGSOURCE)) return Result.failure()
            val bundle = bundleStore.pickup(inputData.getLong(DataReceiver.STORE_KEY, -1))
                ?: return Result.failure()
            
            xdripPlugin.aapsLogger.debug(LTag.BGSOURCE, "Received xDrip data: $bundle")
            val glucoseValues = mutableListOf<CgmSourceTransaction.TransactionGlucoseValue>()
            glucoseValues += CgmSourceTransaction.TransactionGlucoseValue(
                timestamp = bundle.getLong(Intents.EXTRA_TIMESTAMP, 0),
                value = bundle.getDouble(Intents.EXTRA_BG_ESTIMATE, 0.0),
                raw = bundle.getDouble(Intents.EXTRA_RAW, 0.0),
                noise = null,
                trendArrow = GlucoseValue.TrendArrow.fromString(bundle.getString(Intents.EXTRA_BG_SLOPE_NAME)),
                sourceSensor = GlucoseValue.SourceSensor.fromString(bundle.getString(Intents.XDRIP_DATA_SOURCE_DESCRIPTION)
                    ?: "")
            )
            xdripPlugin.disposable += repository.runTransactionForResult(CgmSourceTransaction(glucoseValues, emptyList(), null)).subscribe({ savedValues ->
                savedValues.all().forEach {
                    xdripPlugin.detectSource(it)
                }
            }, {
                xdripPlugin.aapsLogger.error(LTag.BGSOURCE, "Error while saving values from Eversense App", it)
            })
            xdripPlugin.sensorBatteryLevel = bundle.getInt(Intents.EXTRA_SENSOR_BATTERY, -1)
            return Result.success()
        }
    }
}