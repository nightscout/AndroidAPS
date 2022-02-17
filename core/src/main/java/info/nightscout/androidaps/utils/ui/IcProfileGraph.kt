package info.nightscout.androidaps.utils.ui

import android.content.Context
import android.util.AttributeSet
import com.jjoe64.graphview.DefaultLabelFormatter
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.utils.Round
import java.text.NumberFormat
import java.util.*
import kotlin.math.max

class IcProfileGraph : GraphView {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    fun show(profile: Profile) {
        removeAllSeries()
        val icArray: MutableList<DataPoint> = ArrayList()
        var maxIc = 0.0
        for (hour in 0..23) {
            val ic = profile.getIcTimeFromMidnight(hour * 60 * 60)
            maxIc = max(maxIc, ic)
            icArray.add(DataPoint(hour.toDouble(), ic))
            icArray.add(DataPoint((hour + 1).toDouble(), ic))
        }
        val icDataPoints: Array<DataPoint> = Array(icArray.size) { i -> icArray[i] }
        val icSeries: LineGraphSeries<DataPoint> = LineGraphSeries(icDataPoints)
        addSeries(icSeries)
        icSeries.thickness = 8
        icSeries.isDrawBackground = false
        viewport.isXAxisBoundsManual = true
        viewport.setMinX(0.0)
        viewport.setMaxX(24.0)
        viewport.isYAxisBoundsManual = true
        viewport.setMinY(0.0)
        viewport.setMaxY(Round.ceilTo(maxIc * 1.1, 0.5))
        gridLabelRenderer.numHorizontalLabels = 13
        gridLabelRenderer.verticalLabelsColor = icSeries.color

        val nf: NumberFormat = NumberFormat.getInstance()
        nf.maximumFractionDigits = 1
        gridLabelRenderer.setLabelFormatter(DefaultLabelFormatter(nf, nf))
    }

    fun show(profile1: Profile, profile2: Profile) {
        removeAllSeries()

        var maxIc = 0.0
        // ic 1
        val icArray1: MutableList<DataPoint> = ArrayList()
        for (hour in 0..23) {
            val ic = profile1.getIcTimeFromMidnight(hour * 60 * 60)
            maxIc = max(maxIc, ic)
            icArray1.add(DataPoint(hour.toDouble(), ic))
            icArray1.add(DataPoint((hour + 1).toDouble(), ic))
        }
        val icSeries1: LineGraphSeries<DataPoint> = LineGraphSeries(Array(icArray1.size) { i -> icArray1[i] })
        addSeries(icSeries1)
        icSeries1.thickness = 8
        icSeries1.isDrawBackground = false

        // ic 2
        val icArray2: MutableList<DataPoint> = ArrayList()
        for (hour in 0..23) {
            val ic = profile2.getIcTimeFromMidnight(hour * 60 * 60)
            maxIc = max(maxIc, ic)
            icArray2.add(DataPoint(hour.toDouble(), ic))
            icArray2.add(DataPoint((hour + 1).toDouble(), ic))
        }
        val icSeries2: LineGraphSeries<DataPoint> = LineGraphSeries(Array(icArray2.size) { i -> icArray2[i] })
        addSeries(icSeries2)
        icSeries2.thickness = 8
        icSeries2.isDrawBackground = false
        icSeries2.color = context.getColor(R.color.examinedProfile)

        viewport.isXAxisBoundsManual = true
        viewport.setMinX(0.0)
        viewport.setMaxX(24.0)
        viewport.isYAxisBoundsManual = true
        viewport.setMinY(0.0)
        viewport.setMaxY(Round.ceilTo(maxIc * 1.1, 0.5))
        gridLabelRenderer.numHorizontalLabels = 13
    }
}