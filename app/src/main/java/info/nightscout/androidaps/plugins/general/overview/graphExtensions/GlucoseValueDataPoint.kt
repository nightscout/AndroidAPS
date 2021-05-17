package info.nightscout.androidaps.plugins.general.overview.graphExtensions

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
    private val resourceHelper: ResourceHelper
) : DataPointWithLabelInterface {

    fun valueToUnits(units: GlucoseUnit): Double =
        if (units == GlucoseUnit.MGDL) data.value else data.value * Constants.MGDL_TO_MMOLL

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
            return when (data.sourceSensor) {
                GlucoseValue.SourceSensor.IOB_PREDICTION  -> resourceHelper.gc(R.color.iob)
                GlucoseValue.SourceSensor.COB_PREDICTION   -> resourceHelper.gc(R.color.cob)
                GlucoseValue.SourceSensor.A_COB_PREDICTION -> -0x7f000001 and resourceHelper.gc(R.color.cob)
                GlucoseValue.SourceSensor.UAM_PREDICTION   -> resourceHelper.gc(R.color.uam)
                GlucoseValue.SourceSensor.ZT_PREDICTION   -> resourceHelper.gc(R.color.zt)
                else                                      -> R.color.white
            }
        }

    private val isPrediction: Boolean
        get() = data.sourceSensor == GlucoseValue.SourceSensor.IOB_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.COB_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.A_COB_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.UAM_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.ZT_PREDICTION

}