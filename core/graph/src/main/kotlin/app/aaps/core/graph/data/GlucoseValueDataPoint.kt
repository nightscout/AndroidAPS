package app.aaps.core.graph.data

import android.content.Context
import android.graphics.Paint
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil

class GlucoseValueDataPoint(
    val data: GV,
    private val profileUtil: ProfileUtil,
    private val rh: ResourceHelper,
    dateUtil: DateUtil
) : DataPointWithLabelInterface {

    private fun valueToUnits(units: GlucoseUnit): Double =
        if (units == GlucoseUnit.MGDL) data.value else data.value * Constants.MGDL_TO_MMOLL

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = valueToUnits(profileUtil.units)

    override fun setY(y: Double) {}
    override val label: String = dateUtil.timeString(data.timestamp) + " " + profileUtil.fromMgdlToStringInUnits(data.value)
    override val duration = 0L
    override val shape get() = if (isPrediction) Shape.PREDICTION else Shape.BG
    override val size = if (isPrediction) 1f else 0.6f
    override val paintStyle: Paint.Style = if (isPrediction) Paint.Style.FILL else Paint.Style.STROKE

    override fun color(context: Context?): Int {
        return when {
            isPrediction -> predictionColor(context)
            else         -> rh.gac(context, app.aaps.core.ui.R.attr.originalBgValueColor)
        }
    }

    private fun predictionColor(context: Context?): Int {
        return when (data.sourceSensor) {
            SourceSensor.IOB_PREDICTION   -> rh.gac(context, app.aaps.core.ui.R.attr.iobColor)
            SourceSensor.COB_PREDICTION   -> rh.gac(context, app.aaps.core.ui.R.attr.cobColor)
            SourceSensor.A_COB_PREDICTION -> -0x7f000001 and rh.gac(context, app.aaps.core.ui.R.attr.cobColor)
            SourceSensor.UAM_PREDICTION   -> rh.gac(context, app.aaps.core.ui.R.attr.uamColor)
            SourceSensor.ZT_PREDICTION    -> rh.gac(context, app.aaps.core.ui.R.attr.ztColor)
            else                          -> rh.gac(context, app.aaps.core.ui.R.attr.defaultTextColor)
        }
    }

    private val isPrediction: Boolean
        get() = data.sourceSensor == SourceSensor.IOB_PREDICTION ||
            data.sourceSensor == SourceSensor.COB_PREDICTION ||
            data.sourceSensor == SourceSensor.A_COB_PREDICTION ||
            data.sourceSensor == SourceSensor.UAM_PREDICTION ||
            data.sourceSensor == SourceSensor.ZT_PREDICTION

}