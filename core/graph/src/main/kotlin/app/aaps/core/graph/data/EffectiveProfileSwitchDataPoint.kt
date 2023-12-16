package app.aaps.core.graph.data

import android.content.Context
import android.graphics.Paint
import app.aaps.core.data.model.EPS
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.graph.Scale
import app.aaps.core.interfaces.resources.ResourceHelper

class EffectiveProfileSwitchDataPoint(
    val data: EPS,
    private val rh: ResourceHelper,
    private val scale: Scale
) : DataPointWithLabelInterface {

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = scale.transform(data.originalPercentage.toDouble())
    override fun setY(y: Double) {}
    override val label
        get() = "" +
            (if (data.originalPercentage != 100) "${data.originalPercentage}%" else "") +
            (if (data.originalPercentage != 100 && data.originalTimeshift != 0L) "," else "") +
            (if (data.originalTimeshift != 0L) (T.msecs(data.originalTimeshift).hours().toString() + rh.gs(app.aaps.core.interfaces.R.string.shorthour)) else "")
    override val duration = 0L
    override val shape = Shape.PROFILE
    override val size = 2f
    override val paintStyle: Paint.Style = Paint.Style.FILL // not used
    override fun color(context: Context?): Int {
        return rh.gac(context, app.aaps.core.ui.R.attr.profileSwitchColor)
    }
}