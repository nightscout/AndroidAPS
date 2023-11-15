package app.aaps.core.main.graph.data

import android.content.Context
import android.graphics.Paint
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.database.entities.ExtendedBolus

class ExtendedBolusDataPoint(
    val data: ExtendedBolus,
    private val rh: ResourceHelper,
) : DataPointWithLabelInterface {

    private var yValue = 0.0

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = yValue
    override val label get() = data.toStringTotal()
    override val duration get() = data.duration
    override val size = 10f
    override val shape = PointsWithLabelGraphSeries.Shape.EXTENDEDBOLUS
    override val paintStyle: Paint.Style = Paint.Style.FILL // not used
    override fun color(context: Context?): Int {
        return rh.gac(context, app.aaps.core.ui.R.attr.extBolusColor)
    }

    override fun setY(y: Double) {
        yValue = y
    }

    private fun ExtendedBolus.toStringTotal(): String = "${rh.gs(app.aaps.core.ui.R.string.format_insulin_units, amount)} ( ${rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, rate)} )"
}