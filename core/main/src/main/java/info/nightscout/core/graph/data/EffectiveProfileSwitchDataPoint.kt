package info.nightscout.core.graph.data

import android.content.Context
import android.graphics.Paint
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.T

class EffectiveProfileSwitchDataPoint(
    val data: EffectiveProfileSwitch,
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
            (if (data.originalTimeshift != 0L) (T.msecs(data.originalTimeshift).hours().toString() + rh.gs(info.nightscout.interfaces.R.string.shorthour)) else "")
    override val duration = 0L
    override val shape = PointsWithLabelGraphSeries.Shape.PROFILE
    override val size = 2f
    override val paintStyle: Paint.Style = Paint.Style.FILL // not used
    override fun color(context: Context?): Int {
        return rh.gac(context, info.nightscout.core.ui.R.attr.profileSwitchColor)
    }
}