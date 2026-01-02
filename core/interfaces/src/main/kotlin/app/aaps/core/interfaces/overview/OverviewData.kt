package app.aaps.core.interfaces.overview

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.graph.Scale
import app.aaps.core.interfaces.graph.SeriesData

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
     * Graphs
     */

    var bgReadingsArray: List<GV>
    var maxBgValue: Double
    var bucketedGraphSeries: SeriesData
    var bgReadingGraphSeries: SeriesData
    var predictionsGraphSeries: SeriesData

    val basalScale: Scale
    var baseBasalGraphSeries: SeriesData
    var tempBasalGraphSeries: SeriesData
    var basalLineGraphSeries: SeriesData
    var absoluteBasalGraphSeries: SeriesData

    var temporaryTargetSeries: SeriesData
    var runningModesSeries: SeriesData

    var maxIAValue: Double
    val actScale: Scale
    var activitySeries: SeriesData
    var activityPredictionSeries: SeriesData

    var maxEpsValue: Double
    val epsScale: Scale
    var epsSeries: SeriesData
    var maxTreatmentsValue: Double
    var treatmentsSeries: SeriesData
    var maxTherapyEventValue: Double
    var therapyEventSeries: SeriesData

    var maxIobValueFound: Double
    val iobScale: Scale
    var iobSeries: SeriesData
    var absIobSeries: SeriesData
    var iobPredictions1Series: SeriesData

    var maxBGIValue: Double
    val bgiScale: Scale
    var minusBgiSeries: SeriesData
    var minusBgiHistSeries: SeriesData

    var maxCobValueFound: Double
    val cobScale: Scale
    var cobSeries: SeriesData
    var cobMinFailOverSeries: SeriesData

    var maxDevValueFound: Double
    val devScale: Scale
    var deviationsSeries: SeriesData

    var maxRatioValueFound: Double                    //even if sens data equals 0 for all the period, minimum scale is between 95% and 105%
    var minRatioValueFound: Double
    val ratioScale: Scale
    var ratioSeries: SeriesData

    var maxVarSensValueFound: Double
    var minVarSensValueFound: Double
    val varSensScale: Scale
    var varSensSeries: SeriesData

    var maxFromMaxValueFound: Double
    var maxFromMinValueFound: Double
    val dsMaxScale: Scale
    val dsMinScale: Scale
    var dsMaxSeries: SeriesData
    var dsMinSeries: SeriesData
    var heartRateScale: Scale
    var heartRateGraphSeries: SeriesData
    var stepsForScale: Scale
    var stepsCountGraphSeries: SeriesData

}
