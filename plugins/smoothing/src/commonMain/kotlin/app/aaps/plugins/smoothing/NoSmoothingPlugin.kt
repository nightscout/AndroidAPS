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