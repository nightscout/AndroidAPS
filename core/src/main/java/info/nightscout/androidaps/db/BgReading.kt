package info.nightscout.androidaps.db

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class BgReading : DataPointWithLabelInterface {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var repository: AppRepository

    var data: GlucoseValue

    // Compatibility functions
    fun setDate(timeStamp: Long) {
        data.timestamp = timeStamp
    }

    fun getDate(): Long = data.timestamp
    fun getValue(): Double = data.value
    fun setValue(value: Double) {
        data.value = value
    }

    var isCOBPrediction = false // true when drawing predictions as bg points (COB)
    var isaCOBPrediction = false // true when drawing predictions as bg points (aCOB)
    var isIOBPrediction = false // true when drawing predictions as bg points (IOB)
    var isUAMPrediction = false // true when drawing predictions as bg points (UAM)
    var isZTPrediction = false // true when drawing predictions as bg points (ZT)

    @Deprecated("Create only with data")
    constructor(injector: HasAndroidInjector) {
        injector.androidInjector().inject(this)
        data = GlucoseValue(
            timestamp = 0,
            utcOffset = 0,
            raw = null,
            value = 0.0,
            trendArrow = GlucoseValue.TrendArrow.NONE,
            noise = null,
            sourceSensor = GlucoseValue.SourceSensor.UNKNOWN
        )
    }

    constructor(injector: HasAndroidInjector, glucoseValue: GlucoseValue) {
        injector.androidInjector().inject(this)
        data = glucoseValue
    }

    fun valueToUnits(units: String): Double =
        if (units == Constants.MGDL) data.value else data.value * Constants.MGDL_TO_MMOLL

    fun valueToUnitsToString(units: String): String =
        if (units == Constants.MGDL) DecimalFormatter.to0Decimal(data.value)
        else DecimalFormatter.to1Decimal(data.value * Constants.MGDL_TO_MMOLL)

    fun directionToSymbol(): String =
        if (data.trendArrow == GlucoseValue.TrendArrow.NONE) calculateDirection().symbol
        else data.trendArrow.symbol

    fun date(date: Long): BgReading {
        data.timestamp = date
        return this
    }

    fun value(value: Double): BgReading {
        data.value = value
        return this
    }

    // ------------------ DataPointWithLabelInterface ------------------
    override fun getX(): Double {
        return data.timestamp.toDouble()
    }

    override fun getY(): Double {
        return valueToUnits(profileFunction.getUnits())
    }

    override fun setY(y: Double) {}
    override fun getLabel(): String? = null
    override fun getDuration(): Long = 0
    override fun getShape(): PointsWithLabelGraphSeries.Shape =
        if (isPrediction) PointsWithLabelGraphSeries.Shape.PREDICTION
        else PointsWithLabelGraphSeries.Shape.BG

    override fun getSize(): Float = 1f

    override fun getColor(): Int {
        val units = profileFunction.getUnits()
        val lowLine = defaultValueHelper.determineLowLine()
        val highLine = defaultValueHelper.determineHighLine()
        return when {
            isPrediction                   -> predictionColor
            valueToUnits(units) < lowLine  -> resourceHelper.gc(R.color.low)
            valueToUnits(units) > highLine -> resourceHelper.gc(R.color.high)
            else                           -> resourceHelper.gc(R.color.inrange)
        }
    }

    val predictionColor: Int
        get() {
            return when {
                isIOBPrediction  -> resourceHelper.gc(R.color.iob)
                isCOBPrediction  -> resourceHelper.gc(R.color.cob)
                isaCOBPrediction -> -0x7f000001 and resourceHelper.gc(R.color.cob)
                isUAMPrediction  -> resourceHelper.gc(R.color.uam)
                isZTPrediction   -> resourceHelper.gc(R.color.zt)
                else             -> R.color.white
            }
        }

    private val isPrediction: Boolean
        get() = isaCOBPrediction || isCOBPrediction || isIOBPrediction || isUAMPrediction || isZTPrediction

    // Copied from xDrip+
    fun calculateDirection(): GlucoseValue.TrendArrow {
        // Rework to get bgreaings from internal DB and calculate on that base
        val bgReadingsList = repository.compatGetBgReadingsDataFromTime(data.timestamp - T.mins(10).msecs(), false)
            .blockingGet()
        if (bgReadingsList == null || bgReadingsList.size < 2) return GlucoseValue.TrendArrow.NONE
        var current = bgReadingsList[1]
        var previous = bgReadingsList[0]
        if (bgReadingsList[1].timestamp < bgReadingsList[0].timestamp) {
            current = bgReadingsList[0]
            previous = bgReadingsList[1]
        }
        val slope: Double

        // Avoid division by 0
        slope = if (current.timestamp == previous.timestamp) 0.0 else (previous.value - current.value) / (previous.timestamp - current.timestamp)
        aapsLogger.error(LTag.GLUCOSE, "Slope is :" + slope + " delta " + (previous.value - current.value) + " date difference " + (current.timestamp - previous.timestamp))
        val slope_by_minute = slope * 60000
        var arrow = GlucoseValue.TrendArrow.NONE
        if (slope_by_minute <= -3.5) {
            arrow = GlucoseValue.TrendArrow.DOUBLE_DOWN
        } else if (slope_by_minute <= -2) {
            arrow = GlucoseValue.TrendArrow.SINGLE_DOWN
        } else if (slope_by_minute <= -1) {
            arrow = GlucoseValue.TrendArrow.FORTY_FIVE_DOWN
        } else if (slope_by_minute <= 1) {
            arrow = GlucoseValue.TrendArrow.FLAT
        } else if (slope_by_minute <= 2) {
            arrow = GlucoseValue.TrendArrow.FORTY_FIVE_UP
        } else if (slope_by_minute <= 3.5) {
            arrow = GlucoseValue.TrendArrow.SINGLE_UP
        } else if (slope_by_minute <= 40) {
            arrow = GlucoseValue.TrendArrow.DOUBLE_UP
        }
        aapsLogger.error(LTag.GLUCOSE, "Direction set to: $arrow")
        return arrow
    }

    private fun isSlopeNameInvalid(direction: String?): Boolean {
        return direction!!.compareTo("NOT_COMPUTABLE") == 0 || direction.compareTo("NOT COMPUTABLE") == 0 || direction.compareTo("OUT_OF_RANGE") == 0 || direction.compareTo("OUT OF RANGE") == 0 || direction.compareTo("NONE") == 0 || direction.compareTo("NotComputable") == 0
    }
}