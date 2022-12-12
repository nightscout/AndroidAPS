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
import kotlin.math.max
import kotlin.math.round

@OpenForTesting
@Singleton
class AvgSmoothingPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.SMOOTHING)
        .pluginIcon(info.nightscout.core.ui.R.drawable.ic_timeline_24)
        .pluginName(R.string.avg_smoothing_name)
        .shortName(R.string.smoothing_shortname)
        .description(R.string.description_avg_smoothing),
    aapsLogger, rh, injector
), Smoothing {

    @Suppress("LocalVariableName")
    override fun smooth(data: MutableList<InMemoryGlucoseValue>): MutableList<InMemoryGlucoseValue> {

        for (i in data.lastIndex -1 downTo 1) {
            // TODO: Bucketed is always calculated to 5 min (CHECK!), Maybe add a separate timecheck?
            // TODO: We could further improve this by adding a weight to the neighbours
            if (isValid(data[i].value) && isValid(data[i - 1].value) && isValid(data[i + 1].value))
            {
                var result = ((data[i - 1].value + data[i].value + data[i + 1].value) / 3.0)
                data[i].smoothed = result
                aapsLogger.debug("RESULT $result")
            }
            else
            {
                // TODO: Decide what to do here
                data[i].smoothed = data[i].value
            }
        }

        // append data we cannot smooth
        data[data.lastIndex].smoothed = data[data.lastIndex].value
        data[0].smoothed = data[0].value
        return data
    }
    private fun isValid(n: Double): Boolean {
        return n > 39 && n < 401
    }
}