package app.aaps.core.graph.data

import android.content.Context
import android.graphics.Paint
import app.aaps.core.data.model.SC
import app.aaps.core.interfaces.resources.ResourceHelper

class StepsDataPoint(
    private val data: SC,
    private val rh: ResourceHelper,
) : DataPointWithLabelInterface {

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = data.steps5min.toDouble()
    override fun setY(y: Double) {}

    override val label: String = ""
    override val duration = 300000L
    override val shape = Shape.STEPS
    override val size = 10f
    override val paintStyle: Paint.Style = Paint.Style.FILL

    override fun color(context: Context?): Int = rh.gac(context, app.aaps.core.ui.R.attr.stepsColor)
}
