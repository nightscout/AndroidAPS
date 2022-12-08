package info.nightscout.smoothing

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
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
class NoSmoothingPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.SMOOTHING)
        .pluginIcon(info.nightscout.core.ui.R.drawable.ic_timeline_24)
        .setDefault(true)
        .pluginName(R.string.no_smoothing_name)
        .shortName(R.string.smoothing_shortname)
        .description(R.string.description_no_smoothing),
    aapsLogger, rh, injector
), Smoothing {

    override fun smooth(data: MutableList<InMemoryGlucoseValue>): MutableList<InMemoryGlucoseValue> = data
}