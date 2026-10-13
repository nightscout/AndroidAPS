package app.aaps.core.graph

import android.content.Context
import android.util.AttributeSet
import app.aaps.core.graph.data.GraphViewWithCleanup
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.utils.Round
import com.jjoe64.graphview.DefaultLabelFormatter
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.min

class IcProfileGraph @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : GraphViewWithCleanup(context, attrs, defStyle) {

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
        gridLabelRenderer.labelFormatter = DefaultLabelFormatter(nf, nf)
    }

    fun show(profile1: Profile, profile2: Profile) {
        removeAllSeries()

        var minIc = 1000.0
        var maxIc = 0.0
        // ic 1
        val icArray1: MutableList<DataPoint> = ArrayList()
        for (hour in 0..23) {
            val ic = profile1.getIcTimeFromMidnight(hour * 60 * 60)
            minIc = min(minIc, ic)
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
            minIc = min(minIc, ic)
            maxIc = max(maxIc, ic)
            icArray2.add(DataPoint(hour.toDouble(), ic))
            icArray2.add(DataPoint((hour + 1).toDouble(), ic))
        }
        val icSeries2: LineGraphSeries<DataPoint> = LineGraphSeries(Array(icArray2.size) { i -> icArray2[i] })
        addSeries(icSeries2)
        icSeries2.thickness = 8
        icSeries2.isDrawBackground = false
        icSeries2.color = context.getColor(app.aaps.core.ui.R.color.examinedProfile)

        viewport.isXAxisBoundsManual = true
        viewport.setMinX(0.0)
        viewport.setMaxX(24.0)
        viewport.isYAxisBoundsManual = true
        viewport.setMinY(Round.floorTo(minIc / 1.1, 0.5))
        viewport.setMaxY(Round.ceilTo(maxIc * 1.1, 0.5))
        gridLabelRenderer.numHorizontalLabels = 13

        val nf: NumberFormat = NumberFormat.getInstance()
        nf.maximumFractionDigits = 1
        gridLabelRenderer.labelFormatter = DefaultLabelFormatter(nf, nf)
    }
}