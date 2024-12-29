package app.aaps.core.graph.data

import android.content.Context
import android.graphics.Paint
import app.aaps.core.data.model.CA
import app.aaps.core.interfaces.resources.ResourceHelper

class CarbsDataPoint(
    val data: CA,
    private val rh: ResourceHelper
) : DataPointWithLabelInterface {

    private var yValue = 0.0

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = yValue
    override val label get() = rh.gs(app.aaps.core.ui.R.string.format_carbs, data.amount.toInt())
    override val duration = 0L
    override val size = 2f
    override val shape = Shape.CARBS
    override val paintStyle: Paint.Style = Paint.Style.FILL // not used

    override fun color(context: Context?): Int {
        return if (data.isValid && data.amount > 0) rh.gac(context, app.aaps.core.ui.R.attr.cobColor) else rh.gac(context, app.aaps.core.ui.R.attr.alarmColor)
    }

    override fun setY(y: Double) {
        yValue = y
    }
}