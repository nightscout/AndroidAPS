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
    private val resourceHelper: ResourceHelper
) : DataPointWithLabelInterface {

    private var yValue = 0.0

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = yValue
    override fun getLabel(): String = data.toStringTotal()
    override fun getDuration(): Long = data.duration
    override fun getSize(): Float = 10f
    override fun getShape(): PointsWithLabelGraphSeries.Shape = PointsWithLabelGraphSeries.Shape.EXTENDEDBOLUS
    override fun getColor(context: Context): Int =  resourceHelper.getAttributeColor(context, R.attr.smbColor)

    override fun setY(y: Double) {
        yValue = y
    }
}