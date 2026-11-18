package app.aaps.plugins.eversense.util

import kotlin.math.min

data class RangeCalculation(val from: Int, val to: Int)

object RangeCalculator {
    fun calculateRange(rangeFrom: Int, rangeTo: Int): RangeCalculation {
        val count = min(rangeTo - rangeFrom, 20)
        val from = maxOf(rangeTo - count, rangeFrom)
        return RangeCalculation(from = from, to = rangeTo)
    }
}
