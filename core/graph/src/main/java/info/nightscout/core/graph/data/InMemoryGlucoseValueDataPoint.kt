package info.nightscout.core.graph.data

import android.content.Context
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.shared.interfaces.ResourceHelper

class InMemoryGlucoseValueDataPoint(
    val data: InMemoryGlucoseValue,
    private val profileFunction: ProfileFunction,
    private val rh: ResourceHelper
) : DataPointWithLabelInterface {

    fun valueToUnits(units: GlucoseUnit): Double =
        if (units == GlucoseUnit.MGDL) data.value else data.value * Constants.MGDL_TO_MMOLL

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = valueToUnits(profileFunction.getUnits())
    override fun setY(y: Double) {}
    override val label: String = ""
    override val duration = 0L
    override val shape = PointsWithLabelGraphSeries.Shape.BUCKETED_BG
    override val size = 0.3f
    override fun color(context: Context?): Int {
        return rh.gac(context, info.nightscout.core.ui.R.attr.inMemoryColor)
    }
}