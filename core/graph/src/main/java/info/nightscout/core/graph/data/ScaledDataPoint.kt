package info.nightscout.core.graph.data

import com.jjoe64.graphview.series.DataPointInterface

open class ScaledDataPoint : DataPointInterface {

    private val x: Double
    private val y: Double
    private val scale: Scale

    constructor(x: Double, y: Double, scale: Scale) {
        this.x = x
        this.y = y
        this.scale = scale
    }

    constructor(x: Long, y: Double, scale: Scale) {
        this.x = x.toDouble()
        this.y = y
        this.scale = scale
    }

    override fun getX(): Double = x
    override fun getY(): Double = scale.transform(y)
    override fun toString(): String = "[$x/$y]"
}