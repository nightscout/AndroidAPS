package info.nightscout.androidaps.plugins.treatments.fragments

import android.content.Context
import android.util.AttributeSet
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.utils.Round
import java.util.*

class ProfileGraph : GraphView {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    fun show(profile: Profile) {
        removeAllSeries()
        val basalArray: MutableList<DataPoint> = ArrayList()
        for (hour in 0..23) {
            basalArray.add(DataPoint(hour.toDouble(), profile.getBasalTimeFromMidnight(hour * 60 * 60)))
            basalArray.add(DataPoint((hour + 1).toDouble(), profile.getBasalTimeFromMidnight(hour * 60 * 60)))
        }
        val basalDataPoints: Array<DataPoint> = Array(basalArray.size){ i-> basalArray[i]}
        val basalSeries: LineGraphSeries<DataPoint> = LineGraphSeries(basalDataPoints)
        addSeries(basalSeries)
        basalSeries.thickness = 8
        basalSeries.isDrawBackground = true
        viewport.isXAxisBoundsManual = true
        viewport.setMinX(0.0)
        viewport.setMaxX(24.0)
        viewport.isYAxisBoundsManual = true
        viewport.setMinY(0.0)
        viewport.setMaxY(Round.ceilTo(profile.maxDailyBasal * 1.1, 0.5))
        gridLabelRenderer.numHorizontalLabels = 13
        gridLabelRenderer.verticalLabelsColor = basalSeries.color
    }
}