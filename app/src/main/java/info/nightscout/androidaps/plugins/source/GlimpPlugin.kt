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
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlimpPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(R.drawable.ic_glimp)
    .pluginName(R.string.Glimp)
    .preferencesId(R.xml.pref_bgsource)
    .description(R.string.description_source_glimp),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    override fun advancedFilteringSupported(): Boolean {
        return false
    }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE)) return
        val bundle = intent.extras ?: return
        aapsLogger.debug(LTag.BGSOURCE, "Received Glimp Data: ${BundleLogger.log(bundle)}")
        val bgReading = BgReading()
        bgReading.value = bundle.getDouble("mySGV")
        bgReading.direction = bundle.getString("myTrend")
        bgReading.date = bundle.getLong("myTimestamp")
        bgReading.raw = 0.0
        MainApp.getDbHelper().createIfNotExists(bgReading, "GLIMP")
    }
}