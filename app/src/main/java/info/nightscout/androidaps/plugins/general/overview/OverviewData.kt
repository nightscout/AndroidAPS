package info.nightscout.androidaps.plugins.general.overview

import android.content.Context
import com.jjoe64.graphview.series.BarGraphSeries
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.database.entities.ExtendedBolus
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.extensions.convertedToPercent
import info.nightscout.androidaps.extensions.toStringFull
import info.nightscout.androidaps.extensions.toStringShort
import info.nightscout.androidaps.extensions.valueToUnits
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.FixedLineGraphSeries
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.Scale
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.ScaledDataPoint
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.ArrayList

@Singleton
class OverviewData @Inject constructor(
    private val resourceHelper: ResourceHelper,
    private val dateUtil: DateUtil,
    private val sp: SP,
    private val activePlugin: ActivePlugin,
    private val defaultValueHelper: DefaultValueHelper,
    private val profileFunction: ProfileFunction,
) {

    enum class Property {
        TIME,
        CALC_PROGRESS,
        PROFILE,
        TEMPORARY_BASAL,
        EXTENDED_BOLUS,
        TEMPORARY_TARGET,
        BG,
        IOB_COB,
        SENSITIVITY,
        GRAPH
    }

    var rangeToDisplay = 6 // for graph
    var toTime: Long = 0
    var fromTime: Long = 0
    var endTime: Long = 0

    fun initRange() {
        rangeToDisplay = sp.getInt(R.string.key_rangetodisplay, 6)

        val calendar = Calendar.getInstance().also {
            it.timeInMillis = System.currentTimeMillis()
            it[Calendar.MILLISECOND] = 0
            it[Calendar.SECOND] = 0
            it[Calendar.MINUTE] = 0
            it.add(Calendar.HOUR, 1)
        }

        toTime = calendar.timeInMillis + 100000 // little bit more to avoid wrong rounding - GraphView specific
        fromTime = toTime - T.hours(rangeToDisplay.toLong()).msecs()
        endTime = toTime
    }

    /*
     * PROFILE
     */
    var profile: Profile? = null
    var profileName: String? = null
    var profileNameWithRemainingTime: String? = null

    val profileBackgroundColor: Int
        get() =
            profile?.let { profile ->
                if (profile.percentage != 100 || profile.timeshift != 0) resourceHelper.getAttributeColor( null ,R.attr.ribbonWarning )
                else resourceHelper.getAttributeColor( null ,R.attr.ribbonDefault )
            } ?: resourceHelper.getAttributeColor( null ,R.attr.ribbonDefault )

    val profileTextColor: Int
        get() =
            profile?.let { profile ->
                if (profile.percentage != 100 || profile.timeshift != 0) resourceHelper.getAttributeColor( null ,R.attr.ribbonTextWarning )
                else resourceHelper.getAttributeColor( null ,R.attr.defaultPillTextColor )
            } ?: resourceHelper.getAttributeColor( null ,R.attr.defaultPillTextColor )

    /*
     * CALC PROGRESS
     */

    var calcProgress: String = ""

    /*
     * BG
     */

    var lastBg: GlucoseValue? = null

    val lastBgColor: Int
        get() = lastBg?.let { lastBg ->
            when {
                lastBg.valueToUnits(profileFunction.getUnits()) < defaultValueHelper.determineLowLine()  -> resourceHelper.getAttributeColor( null ,R.attr.bgLow )
                lastBg.valueToUnits(profileFunction.getUnits()) > defaultValueHelper.determineHighLine() -> resourceHelper.getAttributeColor( null ,R.attr.bgHigh )
                else                                                                                     -> resourceHelper.getAttributeColor( null ,R.attr.bgInRange )
            }
        } ?: resourceHelper.getAttributeColor( null ,R.attr.bgInRange )

    val isActualBg: Boolean
        get() =
            lastBg?.let { lastBg ->
                lastBg.timestamp > dateUtil.now() - T.mins(9).msecs()
            } ?: false

    /*
     * TEMPORARY BASAL
     */

    var temporaryBasal: TemporaryBasal? = null

    val temporaryBasalText: String
        get() =
            profile?.let { profile ->
                temporaryBasal?.let { "T:" + it.toStringShort() }
                    ?: resourceHelper.gs(R.string.pump_basebasalrate, profile.getBasal())
            } ?: resourceHelper.gs(R.string.notavailable)

    val temporaryBasalDialogText: String
        get() = profile?.let { profile ->
            temporaryBasal?.let { temporaryBasal ->
                "${resourceHelper.gs(R.string.basebasalrate_label)}: ${resourceHelper.gs(R.string.pump_basebasalrate, profile.getBasal())}" +
                    "\n" + resourceHelper.gs(R.string.tempbasal_label) + ": " + temporaryBasal.toStringFull(profile, dateUtil)
            }
                ?: "${resourceHelper.gs(R.string.basebasalrate_label)}: ${resourceHelper.gs(R.string.pump_basebasalrate, profile.getBasal())}"
        } ?: resourceHelper.gs(R.string.notavailable)

    val temporaryBasalIcon: Int
        get() =
            profile?.let { profile ->
                temporaryBasal?.let { temporaryBasal ->
                    val percentRate = temporaryBasal.convertedToPercent(dateUtil.now(), profile)
                    when {
                        percentRate > 100 -> R.drawable.ic_cp_basal_tbr_high
                        percentRate < 100 -> R.drawable.ic_cp_basal_tbr_low
                        else              -> R.drawable.ic_cp_basal_no_tbr
                    }
                }
            } ?: R.drawable.ic_cp_basal_no_tbr

    val temporaryBasalColor: Int
        get() = temporaryBasal?.let { resourceHelper.gc(R.color.basal) }
            ?: resourceHelper.gc(R.color.defaulttextcolor)

    /*
     * EXTENDED BOLUS
    */

    var extendedBolus: ExtendedBolus? = null

    val extendedBolusText: String
        get() =
            extendedBolus?.let { extendedBolus ->
                if (activePlugin.activePump.isFakingTempsByExtendedBoluses) resourceHelper.gs(R.string.pump_basebasalrate, extendedBolus.rate)
                else ""
            } ?: ""

    val extendedBolusDialogText: String
        get() = extendedBolus?.toStringFull(dateUtil) ?: ""

    /*
     * IOB, COB
     */

    var bolusIob: IobTotal? = null
    var basalIob: IobTotal? = null
    var cobInfo: CobInfo? = null
    var lastCarbsTime: Long = 0L

    val iobText: String
        get() =
            bolusIob?.let { bolusIob ->
                basalIob?.let { basalIob ->
                    resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob + basalIob.basaliob)
                } ?: resourceHelper.gs(R.string.value_unavailable_short)
            } ?: resourceHelper.gs(R.string.value_unavailable_short)

    val iobDialogText: String
        get() =
            bolusIob?.let { bolusIob ->
                basalIob?.let { basalIob ->
                    resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob + basalIob.basaliob) + "\n" +
                        resourceHelper.gs(R.string.bolus) + ": " + resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob) + "\n" +
                        resourceHelper.gs(R.string.basal) + ": " + resourceHelper.gs(R.string.formatinsulinunits, basalIob.basaliob)
                } ?: resourceHelper.gs(R.string.value_unavailable_short)
            } ?: resourceHelper.gs(R.string.value_unavailable_short)

    /*
     * TEMP TARGET
     */

    var temporarytarget: TemporaryTarget? = null

    /*
     * SENSITIVITY
     */

    var lastAutosensData: AutosensData? = null
    /*
     * Graphs
     */

    var bgReadingsArray: List<GlucoseValue> = ArrayList()
    var maxBgValue = Double.MIN_VALUE
    var bucketedGraphSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()
    var bgReadingGraphSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()
    var predictionsGraphSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()

    var maxBasalValueFound = 0.0
    val basalScale = Scale()
    var baseBasalGraphSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()
    var tempBasalGraphSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()
    var basalLineGraphSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()
    var absoluteBasalGraphSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()

    var temporaryTargetSeries: LineGraphSeries<DataPoint> = LineGraphSeries()

    var maxIAValue = 0.0
    val actScale = Scale()
    var activitySeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()
    var activityPredictionSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()

    var maxTreatmentsValue = 0.0
    var treatmentsSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()

    var maxIobValueFound = Double.MIN_VALUE
    val iobScale = Scale()
    var iobSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()
    var absIobSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()
    var iobPredictions1Series: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()
    var iobPredictions2Series: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()

    var maxBGIValue = Double.MIN_VALUE
    val bgiScale = Scale()
    var minusBgiSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()
    var minusBgiHistSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()

    var maxCobValueFound = Double.MIN_VALUE
    val cobScale = Scale()
    var cobSeries: FixedLineGraphSeries<ScaledDataPoint> = FixedLineGraphSeries()
    var cobMinFailOverSeries: PointsWithLabelGraphSeries<DataPointWithLabelInterface> = PointsWithLabelGraphSeries()

    var maxDevValueFound = Double.MIN_VALUE
    val devScale = Scale()
    var deviationsSeries: BarGraphSeries<OverviewPlugin.DeviationDataPoint> = BarGraphSeries()

    var maxRatioValueFound = 5.0                    //even if sens data equals 0 for all the period, minimum scale is between 95% and 105%
    var minRatioValueFound = -maxRatioValueFound
    val ratioScale = Scale()
    var ratioSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()

    var maxFromMaxValueFound = Double.MIN_VALUE
    var maxFromMinValueFound = Double.MIN_VALUE
    val dsMaxScale = Scale()
    val dsMinScale = Scale()
    var dsMaxSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()
    var dsMinSeries: LineGraphSeries<ScaledDataPoint> = LineGraphSeries()

}
