package app.aaps.core.graph

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.insulin.Insulin
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlin.math.floor

class ActivityGraph : GraphView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    fun show(insulin: Insulin, iCfgSample: ICfg? = null) {
        val iCfg = iCfgSample ?:insulin.iCfg
        removeAllSeries()
        mSecondScale = null
        val hours = floor(iCfg.getDia() + 1).toLong()
        val bolus = BS(
            timestamp = 0,
            amount = 1.0,
            type = BS.Type.NORMAL
        )
        val activityArray: MutableList<DataPoint> = ArrayList()
        val iobArray: MutableList<DataPoint> = ArrayList()
        var time: Long = 0
        while (time <= T.hours(hours).msecs()) {
            val iob = insulin.iobCalcForTreatment(bolus, time, iCfg)
            activityArray.add(DataPoint(T.msecs(time).mins().toDouble(), iob.activityContrib))
            iobArray.add(DataPoint(T.msecs(time).mins().toDouble(), iob.iobContrib))
            time += T.mins(5).msecs()
        }
        addSeries(LineGraphSeries(Array(activityArray.size) { i -> activityArray[i] }).also {
            it.thickness = 8
            gridLabelRenderer.verticalLabelsColor = it.color
        })
        viewport.isXAxisBoundsManual = true
        viewport.setMinX(0.0)
        viewport.setMaxX((hours * 60).toDouble())
        viewport.isYAxisBoundsManual = true
        viewport.setMinY(0.0)
        viewport.setMaxY(0.01)
        //gridLabelRenderer.numHorizontalLabels = (hours + 1).toInt()
        gridLabelRenderer.horizontalAxisTitle = "[min]"
        secondScale.addSeries(LineGraphSeries(Array(iobArray.size) { i -> iobArray[i] }).also {
            it.isDrawBackground = true
            it.color = Color.MAGENTA
            it.backgroundColor = Color.argb(70, 255, 0, 255)
        })
        secondScale.minY = 0.0
        secondScale.maxY = 1.0
        gridLabelRenderer.verticalLabelsSecondScaleColor = Color.MAGENTA
    }
}