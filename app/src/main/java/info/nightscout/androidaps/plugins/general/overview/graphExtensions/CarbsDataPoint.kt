package info.nightscout.androidaps.plugins.general.overview.graphExtensions

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class CarbsDataPoint @Inject constructor(
    val data: Carbs,
    private val resourceHelper: ResourceHelper
) : DataPointWithLabelInterface {

    private var yValue = 0.0

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = yValue
    override fun getLabel(): String = resourceHelper.gs(R.string.format_carbs, data.amount.toInt())
    override fun getDuration(): Long = 0
    override fun getSize(): Float = 2f

    override fun getShape(): PointsWithLabelGraphSeries.Shape = PointsWithLabelGraphSeries.Shape.CARBS

    override fun getColor(): Int =
        if (data.isValid) resourceHelper.gc(R.color.carbs)
        else resourceHelper.gc(android.R.color.holo_red_light)

    override fun setY(y: Double) {
        yValue = y
    }
}