package info.nightscout.androidaps.plugins.general.overview.graphExtensions

import android.content.Context
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.data.InMemoryGlucoseValue
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP

class InMemoryGlucoseValueDataPoint(
    val data: InMemoryGlucoseValue,
    private val profileFunction: ProfileFunction,
    private val rh: ResourceHelper,
    private val sp: SP
) : DataPointWithLabelInterface {

    fun valueToUnits(units: GlucoseUnit): Double =
        if (units == GlucoseUnit.MGDL) data.rawOrSmoothed(sp)
        else data.rawOrSmoothed(sp) * Constants.MGDL_TO_MMOLL

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = valueToUnits(profileFunction.getUnits())
    override fun setY(y: Double) {}
    override val label: String = ""
    override val duration = 0L
    override val shape = PointsWithLabelGraphSeries.Shape.BUCKETED_BG
    override val size = 0.3f
    override fun color(context: Context?): Int {
        return rh.gac(context, R.attr.inMemoryColor)
    }
}