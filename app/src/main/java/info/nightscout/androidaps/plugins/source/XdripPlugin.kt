package info.nightscout.androidaps.plugins.source

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.services.Intents
import info.nightscout.androidaps.utils.resources.ResourceHelper
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

    private fun setSource(source: String) {
        advancedFiltering = source.contains("G5 Native") || source.contains("G6 Native")
    }

    // cannot be inner class because of needed injection
    class XdripWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {

        @Inject lateinit var xdripPlugin: XdripPlugin

        init {
            (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        }

        override fun doWork(): Result {
            if (!xdripPlugin.isEnabled(PluginType.BGSOURCE)) return Result.failure()
            xdripPlugin.aapsLogger.debug(LTag.BGSOURCE, "Received xDrip data: $inputData")
            val bgReading = BgReading()
            bgReading.value = inputData.getDouble(Intents.EXTRA_BG_ESTIMATE, 0.0)
            bgReading.direction = inputData.getString(Intents.EXTRA_BG_SLOPE_NAME)
            bgReading.date = inputData.getLong(Intents.EXTRA_TIMESTAMP, 0)
            bgReading.raw = inputData.getDouble(Intents.EXTRA_RAW, 0.0)
            xdripPlugin.sensorBatteryLevel = inputData.getInt(Intents.EXTRA_SENSOR_BATTERY, -1)
            val source = inputData.getString(Intents.XDRIP_DATA_SOURCE_DESCRIPTION) ?: ""
            xdripPlugin.setSource(source)
            MainApp.getDbHelper().createIfNotExists(bgReading, "XDRIP")
            return Result.success()
        }
    }
}