package app.aaps.plugins.smoothing

import app.aaps.annotations.OpenForTesting
import app.aaps.core.interfaces.iob.InMemoryGlucoseValue
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.database.entities.GlucoseValue
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.round

@OpenForTesting
@Singleton
class ExponentialSmoothingPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.SMOOTHING)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_timeline_24)
        .pluginName(R.string.exponential_smoothing_name)
        .shortName(R.string.smoothing_shortname)
        .description(R.string.description_exponential_smoothing),
    aapsLogger, rh, injector
), Smoothing {

    @Suppress("LocalVariableName")
    override fun smooth(data: MutableList<InMemoryGlucoseValue>): MutableList<InMemoryGlucoseValue> {
        /**
         *  TSUNAMI DATA SMOOTHING CORE
         *
         *  Calculated a weighted average of 1st and 2nd order exponential smoothing functions
         *  to reduce the effect of sensor noise on APS performance. The weighted average
         *  is a compromise between the fast response to changing BGs at the cost of smoothness
         *  as offered by 1st order exponential smoothing, and the predictive, trend-sensitive but
         *  slower-to-respond smoothing as offered by 2nd order functions.
         *
         */
        val sizeRecords = data.size
        val o1_sBG: ArrayList<Double> = ArrayList() //MP array for 1st order Smoothed Blood Glucose
        val o2_sBG: ArrayList<Double> = ArrayList() //MP array for 2nd order Smoothed Blood Glucose
        val o2_sD: ArrayList<Double> = ArrayList() //MP array for 2nd order Smoothed delta
        val ssBG: ArrayList<Double> = ArrayList() //MP array for weighted averaged, doubly smoothed Blood Glucose
        //val ssD: ArrayList<Double> = ArrayList() //MP array for deltas of doubly smoothed Blood Glucose
        var windowSize = data.size //MP number of bg readings to include in smoothing window
        val o1_weight = 0.4
        val o1_a = 0.5
        val o2_a = 0.4
        val o2_b = 1.0
        var insufficientSmoothingData = false

        // ADJUST SMOOTHING WINDOW TO ONLY INCLUDE VALID READINGS
        // Valid readings include:
        // - Values that actually exist (windowSize may not be larger than sizeRecords)
        // - Values that come in approx. every 5 min. If the time gap between two readings is larger, this is likely due to a sensor error or warmup of a new sensor.d
        // - Values that are not 38 mg/dl; 38 mg/dl reflects an xDrip error state (according to a comment in determine-basal.js)

        //MP: Adjust smoothing window if database size is smaller than the default value + 1 (+1 because the reading before the oldest reading to be smoothed will be used in the calculations
        if (sizeRecords <= windowSize) { //MP standard smoothing window
            windowSize =
                (sizeRecords - 1).coerceAtLeast(0) //MP Adjust smoothing window to the size of database if it is smaller than the original window size; -1 to always have at least one older value to compare against as a buffer to prevent app crashes
        }

        //MP: Adjust smoothing window further if a gap in the BG database is detected, e.g. due to sensor errors of sensor swaps, or if 38 mg/dl are reported (xDrip error state)
        for (i in 0 until windowSize) {
            if (round((data[i].timestamp - data[i + 1].timestamp) / (1000.0 * 60)) >= 12) { //MP: 12 min because a missed reading (i.e. readings coming in after 10 min) can occur for various reasons, like walking away from the phone or reinstalling AAPS
                //if (Math.round((data.get(i).date - data.get(i + 1).date) / 60000L) <= 7) { //MP crashes the app, useful for testing
                windowSize =
                    i + 1 //MP: If time difference between two readings exceeds 7 min, adjust windowSize to *include* the more recent reading (i = reading; +1 because windowSize reflects number of valid readings);
                break
            } else if (data[i].value == 38.0) {
                windowSize = i //MP: 38 mg/dl reflects an xDrip error state; Chain of valid readings ends here, *exclude* this value (windowSize = i; i + 1 would include the current value)
                break
            }
        }

        // CALCULATE SMOOTHING WINDOW - 1st order exponential smoothing
        o1_sBG.clear() // MP reset smoothed bg array

        if (windowSize >= 4) { //MP: Require a valid windowSize of at least 4 readings
            o1_sBG.add(data[windowSize - 1].value) //MP: Initialise smoothing with the oldest valid data point
            for (i in 0 until windowSize) { //MP calculate smoothed bg window of valid readings
                o1_sBG.add(
                    0,
                    o1_sBG[0] + o1_a * (data[windowSize - 1 - i].value - o1_sBG[0])
                ) //MP build array of 1st order smoothed bgs
            }
        } else {
            insufficientSmoothingData = true
        }

        // CALCULATE SMOOTHING WINDOW - 2nd order exponential smoothing
        if (windowSize >= 4) { //MP: Require a valid windowSize of at least 4 readings
            o2_sBG.add(data[windowSize - 1].value) //MP Start 2nd order exponential data smoothing with the oldest valid bg
            o2_sD.add(data[windowSize - 2].value - data[windowSize - 1].value) //MP Start 2nd order exponential data smoothing with the oldest valid delta
            for (i in 0 until windowSize - 1) { //MP calculated smoothed bg window of last 1 h
                o2_sBG.add(
                    0,
                    o2_a * data[windowSize - 2 - i].value + (1 - o2_a) * (o2_sBG[0] + o2_sD[0])
                ) //MP build array of 2nd order smoothed bgs; windowSize-1 is the oldest valid bg value, so windowSize-2 is from when on the smoothing begins;
                o2_sD.add(
                    0,
                    o2_b * (o2_sBG[0] - o2_sBG[1]) + (1 - o2_b) * o2_sD[0]
                ) //MP build array of 1st order smoothed bgs
            }
        } else {
            insufficientSmoothingData = true
        }

        // CALCULATE WEIGHTED AVERAGES OF GLUCOSE & DELTAS
        //ssBG.clear() // MP reset doubly smoothed bg array
        //ssD.clear() // MP reset doubly smoothed delta array

        if (!insufficientSmoothingData) { //MP Build doubly smoothed array only if there is enough valid readings
            for (i in o2_sBG.indices) { //MP calculated doubly smoothed bg of all o1/o2 smoothed data available; o2 & o1 smooth bg array sizes are equal in size, so only one is used as a condition
                // here
                ssBG.add(o1_weight * o1_sBG[i] + (1 - o1_weight) * o2_sBG[i]) //MP build array of doubly smoothed bgs
            }
            for (i in 0 until minOf(ssBG.size, data.size)) { // noise at the beginning of the smoothing window is the greatest, so only include the 10 most recent values in the output
                data[i].smoothed = max(round(ssBG[i]), 39.0) //Make 39 the smallest value as smaller values trigger errors (xDrip error state = 38)
                data[i].trendArrow = GlucoseValue.TrendArrow.NONE
            }
        } else {
            for (i in 0 until data.size) { // noise at the beginning of the smoothing window is the greatest, so only include the 10 most recent values in the output
                data[i].smoothed = max(data[i].value, 39.0) // if insufficient smoothing data, copy 'value' into 'smoothed' data column so that it isn't empty; Make 39 the smallest value as smaller
                // values trigger errors (xDrip error state = 38)
                data[i].trendArrow = GlucoseValue.TrendArrow.NONE
            }
        }

        return data
    }
}