package info.nightscout.androidaps.plugins.general.overview.graphExtensions

import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class GlucoseValueDataPoint @Inject constructor(
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
    override val label: String? = null
    override val duration = 0L
    override val shape get() = if (isPrediction) PointsWithLabelGraphSeries.Shape.PREDICTION else PointsWithLabelGraphSeries.Shape.BG
    override val size = 1f

    override fun getColor(context: Context?): Int {
        val units = profileFunction.getUnits()
        val lowLine = defaultValueHelper.determineLowLine()
        val highLine = defaultValueHelper.determineHighLine()
        return when {
            isPrediction                   -> predictionColor
            valueToUnits(units) < lowLine  -> rh.gac(context, R.attr.bgLow)
            valueToUnits(units) > highLine -> rh.gac(context, R.attr.bgHigh)
            else                           -> rh.gac(context, R.attr.bgInRange)
        }
    }

    val predictionColor: Int
        get() {
            return when (data.sourceSensor) {
                GlucoseValue.SourceSensor.IOB_PREDICTION -> rh.gac( R.attr.iobColor)
                GlucoseValue.SourceSensor.COB_PREDICTION -> -0x7f000001 and rh.gac( R.attr.cobColor)
                GlucoseValue.SourceSensor.UAM_PREDICTION ->  rh.gac( R.attr.uamColor)
                GlucoseValue.SourceSensor.ZT_PREDICTION -> rh.gac( R.attr.ztColor)
                else                                      -> rh.gac( R.attr.defaultColor)
            }
        }

    private val isPrediction: Boolean
        get() = data.sourceSensor == GlucoseValue.SourceSensor.IOB_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.COB_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.A_COB_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.UAM_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.ZT_PREDICTION

}