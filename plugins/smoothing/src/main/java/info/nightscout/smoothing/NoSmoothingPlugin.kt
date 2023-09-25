package info.nightscout.smoothing

import app.aaps.annotations.OpenForTesting
import app.aaps.core.interfaces.iob.InMemoryGlucoseValue
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smoothing.Smoothing
import dagger.android.HasAndroidInjector
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
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_timeline_24)
        .setDefault(true)
        .pluginName(R.string.no_smoothing_name)
        .shortName(R.string.smoothing_shortname)
        .description(R.string.description_no_smoothing),
    aapsLogger, rh, injector
), Smoothing {

    override fun smooth(data: MutableList<InMemoryGlucoseValue>): MutableList<InMemoryGlucoseValue> = data
}