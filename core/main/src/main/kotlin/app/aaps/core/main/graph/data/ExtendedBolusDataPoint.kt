package app.aaps.core.main.graph.data

import android.content.Context
import android.graphics.Paint
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.database.entities.ExtendedBolus

class ExtendedBolusDataPoint(
    val data: ExtendedBolus,
    private val rh: ResourceHelper,
    private val decimalFormatter: DecimalFormatter
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

    private fun ExtendedBolus.toStringTotal(): String = "${decimalFormatter.to2Decimal(amount)}U ( ${decimalFormatter.to2Decimal(rate)} U/h )"
}