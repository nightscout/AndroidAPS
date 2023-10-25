package app.aaps.core.graph.data

import android.content.Context
import android.graphics.Paint
import app.aaps.core.data.db.BS
import app.aaps.core.interfaces.profile.DefaultValueHelper
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter

class BolusDataPoint(
    val data: BS,
    private val rh: ResourceHelper,
    private val bolusStep: Double,
    private val defaultValueHelper: DefaultValueHelper,
    private val decimalFormatter: DecimalFormatter
) : DataPointWithLabelInterface {

    private var yValue = 0.0

    override fun getX(): Double = data.timestamp.toDouble()
    override fun getY(): Double = if (data.type == BS.Type.SMB) defaultValueHelper.determineLowLine() else yValue
    override val label
        get() = decimalFormatter.toPumpSupportedBolus(data.amount, bolusStep)
    override val duration = 0L
    override val size = 2f
    override val paintStyle: Paint.Style = Paint.Style.FILL // not used
    override val shape
        get() = if (data.type == BS.Type.SMB) Shape.SMB else Shape.BOLUS

    override fun color(context: Context?): Int =
        if (data.type == BS.Type.SMB) rh.gac(context, app.aaps.core.ui.R.attr.smbColor)
        else if (data.isValid) rh.gac(context, app.aaps.core.ui.R.attr.bolusDataPointColor)
        else rh.gac(context, app.aaps.core.ui.R.attr.alarmColor)

    override fun setY(y: Double) {
        yValue = y
    }
}
