package info.nightscout.core.graph.data

import android.content.Context
import android.graphics.Paint
import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.shared.interfaces.ResourceHelper

class ExtendedBolusDataPoint(
    val data: ExtendedBolus,
    private val rh: ResourceHelper
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
        return rh.gac(context, info.nightscout.core.ui.R.attr.extBolusColor)
    }

    override fun setY(y: Double) {
        yValue = y
    }

    private fun ExtendedBolus.toStringTotal(): String = "${DecimalFormatter.to2Decimal(amount)}U ( ${DecimalFormatter.to2Decimal(rate)} U/h )"
}