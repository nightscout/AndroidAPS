package info.nightscout.androidaps.plugins.general.overview.graphExtensions

import android.content.Context
import android.graphics.Color
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class BolusDataPoint @Inject constructor(
    val data: Bolus,
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val defaultValueHelper: DefaultValueHelper
) : DataPointWithLabelInterface {

    private var yValue = 0.0

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = if (data.type == Bolus.Type.SMB) defaultValueHelper.determineLowLine() else yValue
    override val label
        get() = DecimalFormatter.toPumpSupportedBolus(data.amount, activePlugin.activePump, rh)
    override val duration = 0L
    override val size = 2f

    override val shape
        get() = if (data.type == Bolus.Type.SMB) PointsWithLabelGraphSeries.Shape.SMB else PointsWithLabelGraphSeries.Shape.BOLUS

    override fun color(context: Context?): Int =
        if (data.type == Bolus.Type.SMB) rh.gac(context, R.attr.smbColor)
        else if (data.isValid) rh.gac(context,  R.attr.bolusDataPointColor)
        else rh.gac(context,  R.attr.alarmColor)


    override fun setY(y: Double) {
        yValue = y
    }
}