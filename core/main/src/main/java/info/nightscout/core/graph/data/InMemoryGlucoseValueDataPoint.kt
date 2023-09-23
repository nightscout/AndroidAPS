package info.nightscout.core.graph.data

import android.content.Context
import android.graphics.Paint
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.interfaces.profile.DefaultValueHelper
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.shared.interfaces.ResourceHelper

class InMemoryGlucoseValueDataPoint(
    val data: InMemoryGlucoseValue,
    private val defaultValueHelper: DefaultValueHelper,
    private val profileFunction: ProfileFunction,
    private val rh: ResourceHelper
) : DataPointWithLabelInterface {

    private fun valueToUnits(units: GlucoseUnit): Double =
         if (units == GlucoseUnit.MGDL) data.recalculated else data.recalculated * Constants.MGDL_TO_MMOLL

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = valueToUnits(profileFunction.getUnits())
    override fun setY(y: Double) {}
    override val label: String = ""
    override val duration = 0L
    override val shape = PointsWithLabelGraphSeries.Shape.BUCKETED_BG
    override val size = 1f
    override val paintStyle: Paint.Style = Paint.Style.FILL

    @ColorInt
    override fun color(context: Context?): Int {
        val units = profileFunction.getUnits()
        val lowLine = defaultValueHelper.determineLowLine()
        val highLine = defaultValueHelper.determineHighLine()
        val color = when {
            valueToUnits(units) < lowLine  -> rh.gac(context, info.nightscout.core.ui.R.attr.bgLow)
            valueToUnits(units) > highLine -> rh.gac(context, info.nightscout.core.ui.R.attr.highColor)
            else                           -> rh.gac(context, info.nightscout.core.ui.R.attr.bgInRange)
        }
        return if (data.filledGap) ColorUtils.setAlphaComponent(color, 128) else color
    }

}