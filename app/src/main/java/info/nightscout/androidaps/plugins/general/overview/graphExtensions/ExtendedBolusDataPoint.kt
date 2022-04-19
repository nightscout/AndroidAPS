package info.nightscout.androidaps.plugins.general.overview.graphExtensions

import android.content.Context
import android.graphics.Color
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.ExtendedBolus
import info.nightscout.androidaps.extensions.toStringTotal
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class ExtendedBolusDataPoint @Inject constructor(
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
    override fun color(context: Context?): Int {
        return rh.gac(context, R.attr.extBolusColor)
    }

    override fun setY(y: Double) {
        yValue = y
    }
}