package info.nightscout.androidaps.plugins.treatments.fragments

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.utils.Round
import java.util.*
import kotlin.math.max

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
        val basalDataPoints: Array<DataPoint> = Array(basalArray.size) { i -> basalArray[i] }
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

    fun show(profile1: Profile, profile2: Profile) {
        removeAllSeries()

        // profile 1
        val basalArray1: MutableList<DataPoint> = ArrayList()
        for (hour in 0..23) {
            basalArray1.add(DataPoint(hour.toDouble(), profile1.getBasalTimeFromMidnight(hour * 60 * 60)))
            basalArray1.add(DataPoint((hour + 1).toDouble(), profile1.getBasalTimeFromMidnight(hour * 60 * 60)))
        }
        val basalSeries1: LineGraphSeries<DataPoint> = LineGraphSeries(Array(basalArray1.size) { i -> basalArray1[i] })
        addSeries(basalSeries1)
        basalSeries1.thickness = 8
        basalSeries1.isDrawBackground = true

        // profile 2
        val basalArray2: MutableList<DataPoint> = ArrayList()
        for (hour in 0..23) {
            basalArray2.add(DataPoint(hour.toDouble(), profile2.getBasalTimeFromMidnight(hour * 60 * 60)))
            basalArray2.add(DataPoint((hour + 1).toDouble(), profile2.getBasalTimeFromMidnight(hour * 60 * 60)))
        }
        val basalSeries2: LineGraphSeries<DataPoint> = LineGraphSeries(Array(basalArray2.size) { i -> basalArray2[i] })
        addSeries(basalSeries2)
        basalSeries2.thickness = 8
        basalSeries2.isDrawBackground = false
        basalSeries2.color = context.getColor(R.color.examinedProfile)
        basalSeries2.backgroundColor = context.getColor(R.color.examinedProfile)

        viewport.isXAxisBoundsManual = true
        viewport.setMinX(0.0)
        viewport.setMaxX(24.0)
        viewport.isYAxisBoundsManual = true
        viewport.setMinY(0.0)
        viewport.setMaxY(Round.ceilTo(max(profile1.maxDailyBasal, profile2.maxDailyBasal) * 1.1, 0.5))
        gridLabelRenderer.numHorizontalLabels = 13
    }
}