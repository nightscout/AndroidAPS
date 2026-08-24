package app.aaps.plugins.smoothing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.smoothing.Smoothing
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding


@Inject
@SingleIn(AppScope::class)
// Bound as PluginBase, not implicitly: these classes have two supertypes (PluginBase and Smoothing),
// so Metro cannot pick one. The plugin list wants PluginBase.
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(600)
class NoSmoothingPlugin(
    aapsLogger: AAPSLogger,
    rh: TextResolver
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.SMOOTHING)
        .icon(Icons.Default.Timeline)
        .setDefault(true)
        .pluginName(SmoothingStrings.no_smoothing_name)
        .shortName(SmoothingStrings.smoothing_shortname)
        .description(SmoothingStrings.description_no_smoothing),
    aapsLogger, rh
), Smoothing {

    override fun smooth(data: MutableList<InMemoryGlucoseValue>): MutableList<InMemoryGlucoseValue> = data
}