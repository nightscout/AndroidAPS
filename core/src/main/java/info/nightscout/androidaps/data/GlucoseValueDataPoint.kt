package info.nightscout.androidaps.data

import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class GlucoseValueDataPoint @Inject constructor(
    val injector: HasAndroidInjector,
    val data: GlucoseValue
) : DataPointWithLabelInterface {

    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var resourceHelper: ResourceHelper

    init {
        injector.androidInjector().inject(this)
    }

    fun valueToUnits(units: String): Double =
        if (units == Constants.MGDL) data.value else data.value * Constants.MGDL_TO_MMOLL

    override fun getX(): Double {
        return data.timestamp.toDouble()
    }

    override fun getY(): Double {
        return valueToUnits(profileFunction.getUnits())
    }

    override fun setY(y: Double) {}
    override fun getLabel(): String? = null
    override fun getDuration(): Long = 0
    override fun getShape(): PointsWithLabelGraphSeries.Shape =
        if (isPrediction) PointsWithLabelGraphSeries.Shape.PREDICTION
        else PointsWithLabelGraphSeries.Shape.BG

    override fun getSize(): Float = 1f

    override fun getColor(context: Context): Int {
        val units = profileFunction.getUnits()
        val lowLine = defaultValueHelper.determineLowLine()
        val highLine = defaultValueHelper.determineHighLine()
        return when {
            isPrediction                   -> predictionColor
            valueToUnits(units) < lowLine  -> resourceHelper.getAttributeColor(context, R.attr.bgLow)
            valueToUnits(units) > highLine -> resourceHelper.getAttributeColor(context, R.attr.bgHigh)
            else                           -> resourceHelper.getAttributeColor(context, R.attr.bgInRange)
        }
    }

    val predictionColor: Int
        get() {
            return when (data.sourceSensor) {
                GlucoseValue.SourceSensor.IOB_PREDICTION -> resourceHelper.getAttributeColor(null, R.attr.iobColor)
                GlucoseValue.SourceSensor.COB_PREDICTION -> resourceHelper.getAttributeColor(null, R.attr.cobColor)
                GlucoseValue.SourceSensor.aCOB_PREDICTION -> -0x7f000001 and resourceHelper.getAttributeColor(null, R.attr.cobColor)
                GlucoseValue.SourceSensor.UAM_PREDICTION ->  resourceHelper.getAttributeColor(null, R.attr.uamColor)
                GlucoseValue.SourceSensor.ZT_PREDICTION -> resourceHelper.getAttributeColor(null, R.attr.ztColor)
                else                                      -> R.color.white
            }
        }

    private val isPrediction: Boolean
        get() = data.sourceSensor == GlucoseValue.SourceSensor.IOB_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.COB_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.aCOB_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.UAM_PREDICTION ||
            data.sourceSensor == GlucoseValue.SourceSensor.ZT_PREDICTION

}