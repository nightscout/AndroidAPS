package info.nightscout.androidaps.plugins.general.overview.graphExtensions

import android.content.Context
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.extensions.rawOrSmoothed
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.shared.sharedPreferences.SP

class GlucoseValueDataPoint(
    val data: GlucoseValue,
    private val defaultValueHelper: DefaultValueHelper,
    private val profileFunction: ProfileFunction,
    private val rh: ResourceHelper,
    private val sp: SP
) : DataPointWithLabelInterface {

    fun valueToUnits(units: GlucoseUnit): Double =
        if (units == GlucoseUnit.MGDL) data.rawOrSmoothed(sp) else data.rawOrSmoothed(sp) * Constants.MGDL_TO_MMOLL

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = valueToUnits(profileFunction.getUnits())

    override fun setY(y: Double) {}
    override val label: String = Profile.toCurrentUnitsString(profileFunction, data.rawOrSmoothed(sp))
    override val duration = 0L
    override val shape get() = if (isPrediction) PointsWithLabelGraphSeries.Shape.PREDICTION else PointsWithLabelGraphSeries.Shape.BG
    override val size = 1f
    override fun color(context: Context?): Int {
        val units = profileFunction.getUnits()
        val lowLine = defaultValueHelper.determineLowLine()
        val highLine = defaultValueHelper.determineHighLine()
        return when {
            isPrediction                   -> predictionColor(context)
            valueToUnits(units) < lowLine  -> rh.gac(context, R.attr.bgLow)
            valueToUnits(units) > highLine -> rh.gac(context, R.attr.highColor)
            else                           -> rh.gac(context, R.attr.bgInRange)
        }
    }

    private fun predictionColor(context: Context?): Int {
        return when (data.sourceSensor) {
            GlucoseValue.SourceSensor.IOB_PREDICTION   -> rh.gac(context, R.attr.iobColor)
            GlucoseValue.SourceSensor.COB_PREDICTION   -> rh.gac(context, R.attr.cobColor)
            GlucoseValue.SourceSensor.A_COB_PREDICTION -> -0x7f000001 and rh.gac(context, R.attr.cobColor)
            GlucoseValue.SourceSensor.UAM_PREDICTION   -> rh.gac(context, R.attr.uamColor)
            GlucoseValue.SourceSensor.ZT_PREDICTION    -> rh.gac(context, R.attr.ztColor)
            else                                       -> rh.gac(context, R.attr.defaultTextColor)
        }
    }

    private val isPrediction: Boolean
        get() = data.sourceSensor == GlucoseValue.SourceSensor.IOB_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.COB_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.A_COB_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.UAM_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.ZT_PREDICTION

}