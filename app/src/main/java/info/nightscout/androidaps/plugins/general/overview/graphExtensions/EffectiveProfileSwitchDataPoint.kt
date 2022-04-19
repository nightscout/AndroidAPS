package info.nightscout.androidaps.plugins.general.overview.graphExtensions

import android.content.Context
import android.graphics.Color
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.EffectiveProfileSwitch
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class EffectiveProfileSwitchDataPoint @Inject constructor(
    val data: EffectiveProfileSwitch,
    private val rh: ResourceHelper
) : DataPointWithLabelInterface {

    private var yValue = 0.0

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = yValue

    override fun setY(y: Double) {
        yValue = y
    }

    override val label get() = data.originalCustomizedName
    override val duration = 0L
    override val shape = PointsWithLabelGraphSeries.Shape.PROFILE
    override val size = 10f
    override fun color(context: Context?): Int {
        return rh.gac(context, R.attr.profileSwitchColor)
    }
}