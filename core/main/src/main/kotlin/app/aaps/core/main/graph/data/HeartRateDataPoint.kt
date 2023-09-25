package app.aaps.core.main.graph.data

import android.content.Context
import android.graphics.Paint
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.database.entities.HeartRate

class HeartRateDataPoint(
    private val data: HeartRate,
    private val rh: ResourceHelper,
) : DataPointWithLabelInterface {

    override fun getX(): Double = (data.timestamp - data.duration).toDouble()
    override fun getY(): Double = data.beatsPerMinute
    override fun setY(y: Double) {}

    override val label: String = ""
    override val duration = data.duration
    override val shape = PointsWithLabelGraphSeries.Shape.HEARTRATE
    override val size = 10f
    override val paintStyle: Paint.Style = Paint.Style.FILL

    override fun color(context: Context?): Int = rh.gac(context, app.aaps.core.ui.R.attr.heartRateColor)
}
