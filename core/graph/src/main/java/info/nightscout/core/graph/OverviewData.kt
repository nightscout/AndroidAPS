package info.nightscout.core.graph

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.jjoe64.graphview.series.BarGraphSeries
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import info.nightscout.core.graph.data.DataPointWithLabelInterface
import info.nightscout.core.graph.data.DeviationDataPoint
import info.nightscout.core.graph.data.FixedLineGraphSeries
import info.nightscout.core.graph.data.PointsWithLabelGraphSeries
import info.nightscout.core.graph.data.Scale
import info.nightscout.core.graph.data.ScaledDataPoint
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.interfaces.aps.AutosensData
import info.nightscout.interfaces.aps.AutosensDataStore
import info.nightscout.interfaces.iob.CobInfo
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.iob.IobTotal

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

    fun lastBg(autosensDataStore: AutosensDataStore): InMemoryGlucoseValue?
    fun isLow(autosensDataStore: AutosensDataStore): Boolean
    fun isHigh(autosensDataStore: AutosensDataStore): Boolean
    @ColorInt fun lastBgColor(context: Context?, autosensDataStore: AutosensDataStore): Int
    fun lastBgDescription(autosensDataStore: AutosensDataStore): String
    fun isActualBg(autosensDataStore: AutosensDataStore): Boolean
    /*
     * TEMPORARY BASAL
     */

    fun temporaryBasalText(iobCobCalculator: IobCobCalculator): String
    fun temporaryBasalDialogText(iobCobCalculator: IobCobCalculator): String
    @DrawableRes fun temporaryBasalIcon(iobCobCalculator: IobCobCalculator): Int
    @AttrRes fun temporaryBasalColor(context: Context?, iobCobCalculator: IobCobCalculator): Int

    /*
     * EXTENDED BOLUS
    */
    fun extendedBolusText(iobCobCalculator: IobCobCalculator): String
    fun extendedBolusDialogText(iobCobCalculator: IobCobCalculator): String

    /*
     * IOB, COB
     */
    fun bolusIob(iobCobCalculator: IobCobCalculator): IobTotal
    fun basalIob(iobCobCalculator: IobCobCalculator): IobTotal
    fun cobInfo(iobCobCalculator: IobCobCalculator): CobInfo

    val lastCarbsTime: Long
    fun iobText(iobCobCalculator: IobCobCalculator): String
    fun iobDialogText(iobCobCalculator: IobCobCalculator): String

    /*
     * TEMP TARGET
     */
    val temporaryTarget: TemporaryTarget?

    /*
     * SENSITIVITY
     */
    fun lastAutosensData(iobCobCalculator: IobCobCalculator): AutosensData?
    /*
     * Graphs
     */

    var bgReadingsArray: List<GlucoseValue>
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
}