package info.nightscout.androidaps.plugins.general.overview.graphExtensions

import android.content.Context
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.EffectiveProfileSwitch
import info.nightscout.androidaps.interfaces.ResourceHelper

class EffectiveProfileSwitchDataPoint(
    val data: EffectiveProfileSwitch,
    private val rh: ResourceHelper,
    private val scale: Scale
) : DataPointWithLabelInterface {

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = scale.transform(data.originalPercentage.toDouble())
    override fun setY(y: Double) {}
    override val label get() = if (data.originalPercentage != 100) data.originalPercentage.toString() + "%" else ""
    override val duration = 0L
    override val shape = PointsWithLabelGraphSeries.Shape.PROFILE
    override val size = 2f
    override fun color(context: Context?): Int {
        return rh.gac(context, R.attr.profileSwitchColor)
    }
}