package app.aaps.core.main.graph.data

import com.jjoe64.graphview.series.DataPointInterface

class DoubleDataPoint(private val x: Double, val y1: Double, val y2: Double) : DataPointInterface {

    override fun getX(): Double = x
    override fun getY(): Double = y1
}