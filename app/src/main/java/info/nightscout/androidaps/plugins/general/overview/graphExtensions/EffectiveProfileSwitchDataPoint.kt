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

    override fun getLabel(): String = data.originalCustomizedName
    override fun getDuration(): Long = 0
    override fun getShape(): PointsWithLabelGraphSeries.Shape = PointsWithLabelGraphSeries.Shape.PROFILE
    override fun getSize(): Float = 10f
    override fun getColor(): Int = Color.CYAN
}