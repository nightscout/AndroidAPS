package app.aaps.core.graph

import android.content.Context
import android.util.AttributeSet
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.graph.data.AreaGraphSeries
import app.aaps.core.graph.data.DoubleDataPoint
import app.aaps.core.graph.data.GraphViewWithCleanup
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.utils.Round
import com.jjoe64.graphview.DefaultLabelFormatter
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.min

class TargetBgProfileGraph @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : GraphViewWithCleanup(context, attrs, defStyle) {

    private fun fromMgdlToUnits(value: Double, units: GlucoseUnit): Double =
        if (units == GlucoseUnit.MGDL) value else value * Constants.MGDL_TO_MMOLL

    fun show(profile: Profile) {

        removeAllSeries()
        val targetArray: MutableList<DoubleDataPoint> = ArrayList()
        var maxValue = 0.0
        val units = profile.units
        for (hour in 0..23) {
            val valueLow = fromMgdlToUnits(profile.getTargetLowMgdlTimeFromMidnight(hour * 60 * 60), units)
            val valueHigh = fromMgdlToUnits(profile.getTargetHighMgdlTimeFromMidnight(hour * 60 * 60), units)
            maxValue = max(maxValue, valueHigh)
            targetArray.add(DoubleDataPoint(hour.toDouble(), valueLow, valueHigh))
            targetArray.add(DoubleDataPoint((hour + 1).toDouble(), valueLow, valueHigh))
        }
        val targetDataPoints: Array<DoubleDataPoint> = Array(targetArray.size) { i -> targetArray[i] }
        val targetSeries: AreaGraphSeries<DoubleDataPoint> = AreaGraphSeries(targetDataPoints)
        addSeries(targetSeries)
        targetSeries.isDrawBackground = true

        viewport.isXAxisBoundsManual = true
        viewport.setMinX(0.0)
        viewport.setMaxX(24.0)
        viewport.isYAxisBoundsManual = true
        viewport.setMinY(0.0)
        viewport.setMaxY(Round.ceilTo(maxValue * 1.1, 0.5))
        gridLabelRenderer.numHorizontalLabels = 13
        gridLabelRenderer.verticalLabelsColor = targetSeries.color

        val nf: NumberFormat = NumberFormat.getInstance()
        nf.maximumFractionDigits = if (units == GlucoseUnit.MMOL) 1 else 0
        gridLabelRenderer.labelFormatter = DefaultLabelFormatter(nf, nf)
    }

    fun show(profile1: Profile, profile2: Profile) {

        removeAllSeries()
        val targetArray1: MutableList<DoubleDataPoint> = ArrayList()
        var minValue = 1000.0
        var maxValue = 0.0
        val units = profile1.units
        for (hour in 0..23) {
            val valueLow = fromMgdlToUnits(profile1.getTargetLowMgdlTimeFromMidnight(hour * 60 * 60), units)
            val valueHigh = fromMgdlToUnits(profile1.getTargetHighMgdlTimeFromMidnight(hour * 60 * 60), units)
            minValue = min(minValue, valueLow)
            maxValue = max(maxValue, valueHigh)
            targetArray1.add(DoubleDataPoint(hour.toDouble(), valueLow, valueHigh))
            targetArray1.add(DoubleDataPoint((hour + 1).toDouble(), valueLow, valueHigh))
        }
        val targetDataPoints1: Array<DoubleDataPoint> = Array(targetArray1.size) { i -> targetArray1[i] }
        val targetSeries1: AreaGraphSeries<DoubleDataPoint> = AreaGraphSeries(targetDataPoints1)
        addSeries(targetSeries1)
        targetSeries1.isDrawBackground = true

        val targetArray2: MutableList<DoubleDataPoint> = ArrayList()
        for (hour in 0..23) {
            val valueLow = fromMgdlToUnits(profile2.getTargetLowMgdlTimeFromMidnight(hour * 60 * 60), units)
            val valueHigh = fromMgdlToUnits(profile2.getTargetHighMgdlTimeFromMidnight(hour * 60 * 60), units)
            minValue = min(minValue, valueLow)
            maxValue = max(maxValue, valueHigh)
            targetArray2.add(DoubleDataPoint(hour.toDouble(), valueLow, valueHigh))
            targetArray2.add(DoubleDataPoint((hour + 1).toDouble(), valueLow, valueHigh))
        }
        val targetDataPoints2: Array<DoubleDataPoint> = Array(targetArray2.size) { i -> targetArray2[i] }
        val targetSeries2: AreaGraphSeries<DoubleDataPoint> = AreaGraphSeries(targetDataPoints2)
        addSeries(targetSeries2)
        targetSeries2.isDrawBackground = false
        targetSeries2.color = context.getColor(app.aaps.core.ui.R.color.examinedProfile)

        viewport.isXAxisBoundsManual = true
        viewport.setMinX(0.0)
        viewport.setMaxX(24.0)
        viewport.isYAxisBoundsManual = true
        viewport.setMinY(Round.floorTo(minValue / 1.1, 0.5))
        viewport.setMaxY(Round.ceilTo(maxValue * 1.1, 0.5))
        gridLabelRenderer.numHorizontalLabels = 13
        gridLabelRenderer.verticalLabelsColor = targetSeries1.color

        val nf: NumberFormat = NumberFormat.getInstance()
        nf.maximumFractionDigits = if (units == GlucoseUnit.MMOL) 1 else 0
        gridLabelRenderer.labelFormatter = DefaultLabelFormatter(nf, nf)
    }
}
