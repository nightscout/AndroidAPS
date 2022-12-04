package info.nightscout.core.graph.data

import android.content.Context
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.profile.DefaultValueHelper
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.shared.interfaces.ResourceHelper

class GlucoseValueDataPoint(
    val data: GlucoseValue,
    private val defaultValueHelper: DefaultValueHelper,
    private val profileFunction: ProfileFunction,
    private val rh: ResourceHelper
) : DataPointWithLabelInterface {

    fun valueToUnits(units: GlucoseUnit): Double =
        if (units == GlucoseUnit.MGDL) data.value else data.value * Constants.MGDL_TO_MMOLL

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = valueToUnits(profileFunction.getUnits())

    override fun setY(y: Double) {}
    override val label: String = Profile.toCurrentUnitsString(profileFunction, data.value)
    override val duration = 0L
    override val shape get() = if (isPrediction) PointsWithLabelGraphSeries.Shape.PREDICTION else PointsWithLabelGraphSeries.Shape.BG
    override val size = 1f
    override fun color(context: Context?): Int {
        val units = profileFunction.getUnits()
        val lowLine = defaultValueHelper.determineLowLine()
        val highLine = defaultValueHelper.determineHighLine()
        return when {
            isPrediction                   -> predictionColor(context)
            valueToUnits(units) < lowLine  -> rh.gac(context, info.nightscout.core.ui.R.attr.bgLow)
            valueToUnits(units) > highLine -> rh.gac(context, info.nightscout.core.ui.R.attr.highColor)
            else                           -> rh.gac(context, info.nightscout.core.ui.R.attr.bgInRange)
        }
    }

    private fun predictionColor(context: Context?): Int {
        return when (data.sourceSensor) {
            GlucoseValue.SourceSensor.IOB_PREDICTION   -> rh.gac(context, info.nightscout.core.ui.R.attr.iobColor)
            GlucoseValue.SourceSensor.COB_PREDICTION   -> rh.gac(context, info.nightscout.core.ui.R.attr.cobColor)
            GlucoseValue.SourceSensor.A_COB_PREDICTION -> -0x7f000001 and rh.gac(context, info.nightscout.core.ui.R.attr.cobColor)
            GlucoseValue.SourceSensor.UAM_PREDICTION   -> rh.gac(context, info.nightscout.core.ui.R.attr.uamColor)
            GlucoseValue.SourceSensor.ZT_PREDICTION    -> rh.gac(context, info.nightscout.core.ui.R.attr.ztColor)
            else                                       -> rh.gac(context, info.nightscout.core.ui.R.attr.defaultTextColor)
        }
    }

    private val isPrediction: Boolean
        get() = data.sourceSensor == GlucoseValue.SourceSensor.IOB_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.COB_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.A_COB_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.UAM_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.ZT_PREDICTION

}