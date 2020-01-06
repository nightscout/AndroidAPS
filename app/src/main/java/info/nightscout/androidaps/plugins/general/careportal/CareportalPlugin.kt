package info.nightscout.androidaps.plugins.general.careportal

import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CareportalPlugin @Inject constructor(rxBus: RxBusWrapper, aapsLogger: AAPSLogger
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(CareportalFragment::class.java.name)
    .pluginName(R.string.careportal)
    .shortName(R.string.careportal_shortname)
    .visibleByDefault(Config.NSCLIENT)
    .enableByDefault(Config.NSCLIENT)
    .description(R.string.description_careportal),
    rxBus, aapsLogger

) {

    override fun specialEnableCondition(): Boolean {
        return Config.NSCLIENT
    }
}