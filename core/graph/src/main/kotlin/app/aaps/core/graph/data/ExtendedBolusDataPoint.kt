package app.aaps.core.graph.data

import android.content.Context
import android.graphics.Paint
import app.aaps.core.data.model.EB
import app.aaps.core.interfaces.resources.ResourceHelper

class ExtendedBolusDataPoint(
    val data: EB,
    private val rh: ResourceHelper
) : DataPointWithLabelInterface {

    private var yValue = 0.0

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = yValue
    override val label get() = data.toStringTotal()
    override val duration get() = data.duration
    override val size = 10f
    override val shape = Shape.EXTENDEDBOLUS
    override val paintStyle: Paint.Style = Paint.Style.FILL // not used
    override fun color(context: Context?): Int {
        return rh.gac(context, app.aaps.core.ui.R.attr.extBolusColor)
    }

    override fun setY(y: Double) {
        yValue = y
    }

    private fun EB.toStringTotal(): String = rh.gs(app.aaps.core.ui.R.string.extended_bolus_data_point_graph, amount, rate)
}