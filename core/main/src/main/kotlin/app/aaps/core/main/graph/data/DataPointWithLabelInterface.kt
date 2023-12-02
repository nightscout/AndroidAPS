package app.aaps.core.main.graph.data

import android.content.Context
import android.graphics.Paint
import androidx.annotation.ColorInt
import com.jjoe64.graphview.series.DataPointInterface

interface DataPointWithLabelInterface : DataPointInterface {

    override fun getX(): Double
    override fun getY(): Double
    fun setY(y: Double)

    val label: String
    val duration: Long
    val shape: PointsWithLabelGraphSeries.Shape
    val size: Float
    val paintStyle: Paint.Style
    @ColorInt fun color(context: Context?): Int
}