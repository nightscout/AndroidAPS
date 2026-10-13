package app.aaps.core.graph.data

import android.content.Context
import android.graphics.Paint
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.R

class RunningModeDataPoint(
    val mode: RM.Mode,
    val startTime: Long,
    val endTime: Long,
    private val rh: ResourceHelper
) : DataPointWithLabelInterface {

    private var yValue = 0.0

    override fun getX(): Double = startTime.toDouble()

    override fun getY(): Double = 0.0

    override fun setY(y: Double) {
        yValue = y
    }

    override val label get() = mode.name
    override val duration get() = endTime - startTime
    override val shape = Shape.RUNNING_MODE
    override val paintStyle: Paint.Style = Paint.Style.FILL // not used
    override val size get() = 10.0f // not used
    override fun color(context: Context?): Int {
        return when (mode) {
            RM.Mode.OPEN_LOOP         -> rh.gac(context, R.attr.loopOpened)
            RM.Mode.CLOSED_LOOP       -> android.R.color.transparent //rh.gac(context, R.attr.loopClosed)
            RM.Mode.CLOSED_LOOP_LGS   -> rh.gac(context, R.attr.loopLgs)
            RM.Mode.DISABLED_LOOP     -> rh.gac(context, R.attr.loopDisabled)
            RM.Mode.SUPER_BOLUS       -> rh.gac(context, R.attr.loopSuperBolus)
            RM.Mode.DISCONNECTED_PUMP -> rh.gac(context, R.attr.loopDisconnected)
            RM.Mode.SUSPENDED_BY_PUMP -> rh.gac(context, R.attr.loopSuspended)
            RM.Mode.SUSPENDED_BY_USER -> rh.gac(context, R.attr.loopSuspended)
            RM.Mode.SUSPENDED_BY_DST -> rh.gac(context, R.attr.loopSuspended)
            RM.Mode.RESUME            -> 0
        }
    }

}