package info.nightscout.androidaps.plugins.general.overview.graphExtensions

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.data.InMemoryGlucoseValue
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class InMemoryGlucoseValueDataPoint @Inject constructor(
    val data: InMemoryGlucoseValue,
    private val profileFunction: ProfileFunction,
    private val resourceHelper: ResourceHelper
) : DataPointWithLabelInterface {

    fun valueToUnits(units: GlucoseUnit): Double =
        if (units == GlucoseUnit.MGDL) data.value else data.value * Constants.MGDL_TO_MMOLL

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = valueToUnits(profileFunction.getUnits())
    override fun setY(y: Double) {}
    override fun getLabel(): String? = null
    override fun getDuration(): Long = 0
    override fun getShape(): PointsWithLabelGraphSeries.Shape = PointsWithLabelGraphSeries.Shape.BUCKETED_BG
    override fun getSize(): Float = 0.3f
    override fun getColor(): Int = resourceHelper.gc(R.color.white)
}