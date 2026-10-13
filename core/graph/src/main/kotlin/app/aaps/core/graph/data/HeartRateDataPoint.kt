package app.aaps.core.graph.data

import android.content.Context
import android.graphics.Paint
import app.aaps.core.data.model.HR
import app.aaps.core.interfaces.resources.ResourceHelper

class HeartRateDataPoint(
    private val data: HR,
    private val rh: ResourceHelper,
) : DataPointWithLabelInterface {

    override fun getX(): Double = (data.timestamp - data.duration).toDouble()
    override fun getY(): Double = data.beatsPerMinute
    override fun setY(y: Double) {}

    override val label: String = ""
    override val duration = data.duration
    override val shape = Shape.HEART_RATE
    override val size = 10f
    override val paintStyle: Paint.Style = Paint.Style.FILL

    override fun color(context: Context?): Int = rh.gac(context, app.aaps.core.ui.R.attr.heartRateColor)
}
