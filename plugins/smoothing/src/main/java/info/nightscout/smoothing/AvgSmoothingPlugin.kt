package info.nightscout.smoothing

import dagger.android.HasAndroidInjector
import info.nightscout.annotations.OpenForTesting
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.smoothing.Smoothing
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.T
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

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

    override fun smooth(data: MutableList<InMemoryGlucoseValue>): MutableList<InMemoryGlucoseValue> {
        if (data.lastIndex < 4) {
            aapsLogger.debug(LTag.GLUCOSE, "Not enough value's to smooth!")
            return data
        }

        for (i in data.lastIndex - 1 downTo 1) {
            // Check if value's are in a valid range
            // Bucketed is always calculated to 5 min, we still check if our data is evenly spaced with an allowance of 30 seconds
            if (isValid(data[i].value) && isValid(data[i - 1].value) && isValid(data[i + 1].value)
                && abs(data[i].timestamp - data[i - 1].timestamp - (data[i + 1].timestamp - data[i].timestamp)) < T.secs(30).msecs()
            ) {
                // We could further improve this by adding a weight to the neighbours, for simplicity this is not done.
                data[i].smoothed = ((data[i - 1].value + data[i].value + data[i + 1].value) / 3.0)
                data[i].trendArrow = GlucoseValue.TrendArrow.NONE
            } else {
                // data[i].smoothed = data[i].value
                val currentTime = data[i].timestamp
                val value = data[i].value
                aapsLogger.debug(LTag.GLUCOSE, "Value: $value at $currentTime not smoothed")
            }
        }
        // We leave the data we can not smooth as is, alternatively we could provide raw value's to the smoothed value's:
        // data[data.lastIndex].smoothed = data[data.lastIndex].value
        // data[0].smoothed = data[0].value
        return data
    }

    private fun isValid(n: Double): Boolean {
        // For Dexcom: Below 39 is LOW, above 401 Dexcom just says HI
        return n > 39 && n < 401
    }
}