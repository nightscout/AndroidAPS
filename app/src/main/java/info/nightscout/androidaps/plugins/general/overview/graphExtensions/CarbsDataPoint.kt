package info.nightscout.androidaps.plugins.general.overview.graphExtensions

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class CarbsDataPoint @Inject constructor(
    val data: Carbs,
    private val rh: ResourceHelper
) : DataPointWithLabelInterface {

    private var yValue = 0.0

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = yValue
    override val label get() = rh.gs(R.string.format_carbs, data.amount.toInt())
    override val duration = 0L
    override val size = 2f
    override val shape = PointsWithLabelGraphSeries.Shape.CARBS
    override val color get() = if (data.isValid) rh.gc(R.color.carbs) else rh.gc(android.R.color.holo_red_light)

    override fun setY(y: Double) {
        yValue = y
    }
}