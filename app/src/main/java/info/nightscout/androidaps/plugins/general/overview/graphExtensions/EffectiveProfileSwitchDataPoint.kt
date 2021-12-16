package info.nightscout.androidaps.plugins.general.overview.graphExtensions

import android.graphics.Color
import info.nightscout.androidaps.database.entities.EffectiveProfileSwitch
import javax.inject.Inject

class EffectiveProfileSwitchDataPoint @Inject constructor(
    val data: EffectiveProfileSwitch
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
    override val color = Color.CYAN
}