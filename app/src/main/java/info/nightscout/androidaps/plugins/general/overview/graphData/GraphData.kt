package info.nightscout.androidaps.plugins.general.overview.graphData

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import com.jjoe64.graphview.series.Series
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.general.overview.OverviewData
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.AreaGraphSeries
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DoubleDataPoint
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.TimeAsXAxisLabelFormatter
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.*
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max

class GraphData(
    injector: HasAndroidInjector,
    private val graph: GraphView,
    private val overviewData: OverviewData
) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var defaultValueHelper: DefaultValueHelper

    private var maxY = Double.MIN_VALUE
    private var minY = Double.MAX_VALUE
    private val units: GlucoseUnit
    private val series: MutableList<Series<*>> = ArrayList()

    init {
        injector.androidInjector().inject(this)
        units = profileFunction.getUnits()
    }

    fun addBucketedData() {
        addSeries(overviewData.bucketedGraphSeries)
    }

    fun addBgReadings(addPredictions: Boolean) {
        maxY = if (overviewData.bgReadingsArray.isEmpty()) {
            if (units == GlucoseUnit.MGDL) 180.0 else 10.0
        } else overviewData.maxBgValue
        minY = 0.0
        addSeries(overviewData.bgReadingGraphSeries)
        if (addPredictions) addSeries(overviewData.predictionsGraphSeries)
    }

    fun addInRangeArea(fromTime: Long, toTime: Long, lowLine: Double, highLine: Double) {
        val inRangeAreaDataPoints = arrayOf(
            DoubleDataPoint(fromTime.toDouble(), lowLine, highLine),
            DoubleDataPoint(toTime.toDouble(), lowLine, highLine)
        )
        addSeries(AreaGraphSeries(inRangeAreaDataPoints).also {
            it.color = 0
            it.isDrawBackground = true
            it.backgroundColor = resourceHelper.gc(R.color.inrangebackground)
        })
    }

    fun addBasals() {
        val scale = defaultValueHelper.determineLowLine() / maxY / 1.2
        addSeries(overviewData.baseBasalGraphSeries)
        addSeries(overviewData.tempBasalGraphSeries)
        addSeries(overviewData.basalLineGraphSeries)
        addSeries(overviewData.absoluteBasalGraphSeries)
        overviewData.basalScale.multiplier = maxY * scale / overviewData.maxBasalValueFound
    }

    fun addTargetLine() {
        addSeries(overviewData.temporaryTargetSeries)
    }

    fun addTreatments() {
        maxY = maxOf(maxY, overviewData.maxTreatmentsValue)
        addSeries(overviewData.treatmentsSeries)
    }

    fun addActivity(scale: Double) {
        addSeries(overviewData.activitySeries)
        addSeries(overviewData.activityPredictionSeries)
        overviewData.actScale.multiplier = maxY * scale / overviewData.maxIAValue
    }

    //Function below show -BGI to be able to compare curves with deviations
    fun addMinusBGI(useForScale: Boolean, scale: Double) {
        if (useForScale) {
            maxY = overviewData.maxBGIValue
            minY = -overviewData.maxBGIValue
        }
        overviewData.bgiScale.multiplier = maxY * scale / overviewData.maxBGIValue
        addSeries(overviewData.minusBgiSeries)
        addSeries(overviewData.minusBgiHistSeries)
    }

    // scale in % of vertical size (like 0.3)
    fun addIob(useForScale: Boolean, scale: Double) {
        if (useForScale) {
            maxY = overviewData.maxIobValueFound
            minY = -overviewData.maxIobValueFound
        }
        overviewData.iobScale.multiplier = maxY * scale / overviewData.maxIobValueFound
        addSeries(overviewData.iobSeries)
        addSeries(overviewData.iobPredictions1Series)
        addSeries(overviewData.iobPredictions2Series)
    }

    // scale in % of vertical size (like 0.3)
    fun addAbsIob(useForScale: Boolean, scale: Double) {
        if (useForScale) {
            maxY = overviewData.maxIobValueFound
            minY = -overviewData.maxIobValueFound
        }
        overviewData.iobScale.multiplier = maxY * scale / overviewData.maxIobValueFound
        addSeries(overviewData.absIobSeries)
    }

    // scale in % of vertical size (like 0.3)
    fun addCob(useForScale: Boolean, scale: Double) {
        if (useForScale) {
            maxY = overviewData.maxCobValueFound
            minY = 0.0
        }
        overviewData.cobScale.multiplier = maxY * scale / overviewData.maxCobValueFound
        addSeries(overviewData.cobSeries)
        addSeries(overviewData.cobMinFailOverSeries)
    }

    // scale in % of vertical size (like 0.3)
    fun addDeviations(useForScale: Boolean, scale: Double) {
        if (useForScale) {
            maxY = overviewData.maxDevValueFound
            minY = -maxY
        }
        overviewData.devScale.multiplier = maxY * scale / overviewData.maxDevValueFound
        addSeries(overviewData.deviationsSeries)
    }

    // scale in % of vertical size (like 0.3)
    fun addRatio(useForScale: Boolean, scale: Double) {
        if (useForScale) {
            maxY = 100.0 + max(overviewData.maxRatioValueFound, abs(overviewData.minRatioValueFound))
            minY = 100.0 - max(overviewData.maxRatioValueFound, abs(overviewData.minRatioValueFound))
            overviewData.ratioScale.multiplier = 1.0
            overviewData.ratioScale.shift = 100.0
        } else {
            overviewData.ratioScale.multiplier = maxY * scale / max(overviewData.maxRatioValueFound, abs(overviewData.minRatioValueFound))
            overviewData.ratioScale.shift = 0.0
        }
        addSeries(overviewData.ratioSeries)
    }

    // scale in % of vertical size (like 0.3)
    fun addDeviationSlope(useForScale: Boolean, scale: Double, isRatioScale: Boolean = false) {
        if (useForScale) {
            maxY = max(overviewData.maxFromMaxValueFound, overviewData.maxFromMinValueFound)
            minY = -maxY
        }
        var graphMaxY = maxY
        if (isRatioScale) {
            graphMaxY = maxY - 100.0
            overviewData.dsMinScale.shift = 100.0
            overviewData.dsMaxScale.shift = 100.0
        } else {
            overviewData.dsMinScale.shift = 0.0
            overviewData.dsMaxScale.shift = 0.0
        }
        overviewData.dsMaxScale.multiplier = graphMaxY * scale / overviewData.maxFromMaxValueFound
        overviewData.dsMinScale.multiplier = graphMaxY * scale / overviewData.maxFromMinValueFound
        addSeries(overviewData.dsMaxSeries)
        addSeries(overviewData.dsMinSeries)
    }

    // scale in % of vertical size (like 0.3)
    fun addNowLine(now: Long) {
        val nowPoints = arrayOf(
            DataPoint(now.toDouble(), 0.0),
            DataPoint(now.toDouble(), maxY)
        )
        addSeries(LineGraphSeries(nowPoints).also {
            it.isDrawDataPoints = false
            // custom paint to make a dotted line
            it.setCustomPaint(Paint().also { paint ->
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.pathEffect = DashPathEffect(floatArrayOf(10f, 20f), 0f)
                paint.color = Color.WHITE
            })
        })
    }

    fun setNumVerticalLabels() {
        graph.gridLabelRenderer.numVerticalLabels = if (units == GlucoseUnit.MGDL) (maxY / 40 + 1).toInt() else (maxY / 2 + 1).toInt()
    }

    fun formatAxis(fromTime: Long, endTime: Long) {
        graph.viewport.setMaxX(endTime.toDouble())
        graph.viewport.setMinX(fromTime.toDouble())
        graph.viewport.isXAxisBoundsManual = true
        graph.gridLabelRenderer.labelFormatter = TimeAsXAxisLabelFormatter("HH")
        graph.gridLabelRenderer.numHorizontalLabels = 7 // only 7 because of the space
    }

    private fun addSeries(s: Series<*>) = series.add(s)

    fun performUpdate() {
        // clear old data
        graph.series.clear()

        // add pre calculated series
        for (s in series) {
            if (!s.isEmpty) {
                s.onGraphViewAttached(graph)
                graph.series.add(s)
            }
        }
        var step = 1.0
        if (maxY < 1) step = 0.1
        graph.viewport.setMaxY(Round.ceilTo(maxY, step))
        graph.viewport.setMinY(Round.floorTo(minY, step))
        graph.viewport.isYAxisBoundsManual = true

        // draw it
        graph.onDataChanged(false, false)
    }
}

