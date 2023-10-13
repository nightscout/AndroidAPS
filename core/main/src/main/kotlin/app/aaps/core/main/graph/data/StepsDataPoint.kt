package app.aaps.core.main.graph.data

import android.content.Context
import android.graphics.Paint
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.database.entities.StepsCount

class StepsDataPoint(
    private val data: StepsCount,
    private val rh: ResourceHelper,
) : DataPointWithLabelInterface {

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = data.steps5min.toDouble()
    override fun setY(y: Double) {}

    override val label: String = ""
    override val duration = 900000L
    override val shape = PointsWithLabelGraphSeries.Shape.STEPS
    override val size = 10f
    override val paintStyle: Paint.Style = Paint.Style.FILL

    override fun color(context: Context?): Int = rh.gac(context, app.aaps.core.ui.R.attr.stepsColor)
}
