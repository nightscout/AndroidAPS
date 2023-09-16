package info.nightscout.core.graph.data

import android.content.Context
import android.graphics.Paint
import info.nightscout.core.main.R
import info.nightscout.database.entities.Carbs
import info.nightscout.shared.interfaces.ResourceHelper

class CarbsDataPoint(
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
    override val paintStyle: Paint.Style = Paint.Style.FILL // not used

    override fun color(context: Context?): Int {
        return if (data.isValid) rh.gac(context, info.nightscout.core.ui.R.attr.cobColor) else rh.gac(context, info.nightscout.core.ui.R.attr.alarmColor)
    }

    override fun setY(y: Double) {
        yValue = y
    }
}