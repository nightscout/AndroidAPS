package app.aaps.core.main.graph

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import app.aaps.core.data.aps.AutosensData
import app.aaps.core.data.db.GV
import app.aaps.core.data.db.TT
import app.aaps.core.data.iob.CobInfo
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.interfaces.graph.data.DataPointWithLabelInterface
import app.aaps.interfaces.graph.data.DeviationDataPoint
import app.aaps.interfaces.graph.data.FixedLineGraphSeries
import app.aaps.interfaces.graph.data.PointsWithLabelGraphSeries
import app.aaps.interfaces.graph.data.Scale
import app.aaps.interfaces.graph.data.ScaledDataPoint
import com.jjoe64.graphview.series.BarGraphSeries
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries

interface OverviewData {

    var rangeToDisplay: Int // for graph
    var toTime: Long  // current time rounded up to 1 hour
    var fromTime: Long // toTime - range
    var endTime: Long // toTime + predictions

    fun reset()
    fun initRange()
    /*
     * PUMP STATUS
     */

    var pumpStatus: String

    /*
     * CALC PROGRESS
     */

    var calcProgressPct: Int

    /*
     * BG
     */

    /**
     * Get newest glucose value from bucketed data.
     * If there are less than 3 glucose values, bucketed data is not created.
     * In this case take newest [app.aaps.core.data.db.GV] from db and convert it to [InMemoryGlucoseValue]
     *
     * Intended for display on screen only
     *
     * @return newest glucose value
     */
    fun lastBg(): InMemoryGlucoseValue?
    fun isLow(): Boolean
    fun isHigh(): Boolean
    @ColorInt fun lastBgColor(context: Context?): Int
    fun lastBgDescription(): String
    fun isActualBg(): Boolean
    /*
     * TEMPORARY BASAL
     */

    fun temporaryBasalText(): String
    fun temporaryBasalDialogText(): String
    @DrawableRes fun temporaryBasalIcon(): Int
    @AttrRes fun temporaryBasalColor(context: Context?): Int

    /*
     * EXTENDED BOLUS
    */
    fun extendedBolusText(): String
    fun extendedBolusDialogText(): String

    /*
     * IOB, COB
     */
    fun cobInfo(): CobInfo

    val lastCarbsTime: Long
    fun iobText(): String
    fun iobDialogText(): String

    /*
     * TEMP TARGET
     */
    val temporaryTarget: TT?

    /*
     * SENSITIVITY
     */
    fun lastAutosensData(): AutosensData?
    /*
     * Graphs
     */

    var bgReadingsArray: List<GV>
    var maxBgValue: Double
    var bucketedGraphSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface>
    var bgReadingGraphSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface>
    var predictionsGraphSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface>

    val basalScale: Scale
    var baseBasalGraphSeries: LineGraphSeries<ScaledDataPoint>
    var tempBasalGraphSeries: LineGraphSeries<ScaledDataPoint>
    var basalLineGraphSeries: LineGraphSeries<ScaledDataPoint>
    var absoluteBasalGraphSeries: LineGraphSeries<ScaledDataPoint>

    var temporaryTargetSeries: LineGraphSeries<DataPoint>

    var maxIAValue: Double
    val actScale: Scale
    var activitySeries: FixedLineGraphSeries<ScaledDataPoint>
    var activityPredictionSeries: FixedLineGraphSeries<ScaledDataPoint>

    var maxEpsValue: Double
    val epsScale: Scale
    var epsSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface>
    var maxTreatmentsValue: Double
    var treatmentsSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface>
    var maxTherapyEventValue: Double
    var therapyEventSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface>

    var maxIobValueFound: Double
    val iobScale: Scale
    var iobSeries: FixedLineGraphSeries<ScaledDataPoint>
    var absIobSeries: FixedLineGraphSeries<ScaledDataPoint>
    var iobPredictions1Series: PointsWithLabelGraphSeries<DataPointWithLabelInterface>

    var maxBGIValue: Double
    val bgiScale: Scale
    var minusBgiSeries: FixedLineGraphSeries<ScaledDataPoint>
    var minusBgiHistSeries: FixedLineGraphSeries<ScaledDataPoint>

    var maxCobValueFound: Double
    val cobScale: Scale
    var cobSeries: FixedLineGraphSeries<ScaledDataPoint>
    var cobMinFailOverSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface>

    var maxDevValueFound: Double
    val devScale: Scale
    var deviationsSeries: BarGraphSeries<DeviationDataPoint>

    var maxRatioValueFound: Double                    //even if sens data equals 0 for all the period, minimum scale is between 95% and 105%
    var minRatioValueFound: Double
    val ratioScale: Scale
    var ratioSeries: LineGraphSeries<ScaledDataPoint>

    var maxFromMaxValueFound: Double
    var maxFromMinValueFound: Double
    val dsMaxScale: Scale
    val dsMinScale: Scale
    var dsMaxSeries: LineGraphSeries<ScaledDataPoint>
    var dsMinSeries: LineGraphSeries<ScaledDataPoint>
    var heartRateScale: Scale
    var heartRateGraphSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface>

}
