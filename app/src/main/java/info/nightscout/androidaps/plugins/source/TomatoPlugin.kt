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
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TomatoPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val sp: SP,
    private val nsUpload: NSUpload
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginIcon(R.drawable.ic_sensor)
    .pluginName(R.string.tomato)
    .preferencesId(R.xml.pref_bgsource)
    .shortName(R.string.tomato_short)
    .description(R.string.description_source_tomato),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    override fun advancedFilteringSupported(): Boolean {
        return false
    }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE)) return
        val bundle = intent.extras ?: return
        val bgReading = BgReading()
        aapsLogger.debug(LTag.BGSOURCE, "Received Tomato Data")
        bgReading.value = bundle.getDouble("com.fanqies.tomatofn.Extras.BgEstimate")
        bgReading.date = bundle.getLong("com.fanqies.tomatofn.Extras.Time")
        val isNew = MainApp.getDbHelper().createIfNotExists(bgReading, "Tomato")
        if (isNew && sp.getBoolean(R.string.key_dexcomg5_nsupload, false)) {
            nsUpload.uploadBg(bgReading, "AndroidAPS-Tomato")
        }
        if (isNew && sp.getBoolean(R.string.key_dexcomg5_xdripupload, false)) {
            nsUpload.sendToXdrip(bgReading)
        }
    }
}