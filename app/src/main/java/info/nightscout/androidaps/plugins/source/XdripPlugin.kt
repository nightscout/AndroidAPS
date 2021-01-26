package info.nightscout.androidaps.plugins.source

import android.content.Intent
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.BundleLogger
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
    private var sensorBatteryLevel = -1

    override fun advancedFilteringSupported(): Boolean {
        return advancedFiltering
    }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE)) return
        val bundle = intent.extras ?: return
        aapsLogger.debug(LTag.BGSOURCE, "Received xDrip data: " + BundleLogger.log(intent.extras))
        val bgReading = BgReading()
        bgReading.value = bundle.getDouble(Intents.EXTRA_BG_ESTIMATE)
        bgReading.direction = bundle.getString(Intents.EXTRA_BG_SLOPE_NAME)
        bgReading.date = bundle.getLong(Intents.EXTRA_TIMESTAMP)
        bgReading.raw = bundle.getDouble(Intents.EXTRA_RAW)
        if (bundle.containsKey(Intents.EXTRA_SENSOR_BATTERY)) sensorBatteryLevel = bundle.getInt(Intents.EXTRA_SENSOR_BATTERY)
        val source = bundle.getString(Intents.XDRIP_DATA_SOURCE_DESCRIPTION, "no Source specified")
        setSource(source)
        MainApp.getDbHelper().createIfNotExists(bgReading, "XDRIP")
    }

    private fun setSource(source: String) {
        advancedFiltering = source.contains("G5 Native") || source.contains("G6 Native")
    }

    override fun getSensorBatteryLevel(): Int {
        return sensorBatteryLevel
    }
}