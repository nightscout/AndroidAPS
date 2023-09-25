package app.aaps.core.main.graph.data

import android.content.Context
import android.graphics.Paint
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.main.R
import app.aaps.database.entities.Carbs

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
        return if (data.isValid) rh.gac(context, app.aaps.core.ui.R.attr.cobColor) else rh.gac(context, app.aaps.core.ui.R.attr.alarmColor)
    }

    override fun setY(y: Double) {
        yValue = y
    }
}