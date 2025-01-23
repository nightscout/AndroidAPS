package app.aaps.core.graph.data

import android.content.Context
import android.graphics.Paint
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences

class InMemoryGlucoseValueDataPoint(
    val data: InMemoryGlucoseValue,
    private val preferences: Preferences,
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
    override val shape = Shape.BUCKETED_BG
    override val size = 1f
    override val paintStyle: Paint.Style = Paint.Style.FILL

    @ColorInt
    override fun color(context: Context?): Int {
        val units = profileFunction.getUnits()
        val lowLine = preferences.get(UnitDoubleKey.OverviewLowMark)
        val highLine = preferences.get(UnitDoubleKey.OverviewHighMark)
        val color = when {
            valueToUnits(units) < lowLine  -> rh.gac(context, app.aaps.core.ui.R.attr.bgLow)
            valueToUnits(units) > highLine -> rh.gac(context, app.aaps.core.ui.R.attr.highColor)
            else                           -> rh.gac(context, app.aaps.core.ui.R.attr.bgInRange)
        }
        return if (data.filledGap) ColorUtils.setAlphaComponent(color, 128) else color
    }

}