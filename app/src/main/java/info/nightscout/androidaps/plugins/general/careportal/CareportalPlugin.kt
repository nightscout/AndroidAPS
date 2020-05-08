package info.nightscout.androidaps.plugins.general.careportal

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CareportalPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    config: Config
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(CareportalFragment::class.java.name)
    .pluginName(R.string.careportal)
    .shortName(R.string.careportal_shortname)
    .visibleByDefault(config.NSCLIENT)
    .enableByDefault(config.NSCLIENT)
    .description(R.string.description_careportal),
    aapsLogger, resourceHelper, injector
)