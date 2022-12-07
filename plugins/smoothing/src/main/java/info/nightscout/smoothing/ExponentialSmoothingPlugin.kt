package info.nightscout.smoothing

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.smoothing.Smoothing
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class ExponentialSmoothingPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    activePlugin: ActivePlugin,
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.SMOOTHING)
        .pluginIcon(info.nightscout.core.ui.R.drawable.ic_timeline_24)
        .pluginName(R.string.exponential_smoothing_name)
        .shortName(R.string.smoothing_shortname)
        .description(R.string.description_exponential_smoothing),
    aapsLogger, rh, injector
), Smoothing {
}