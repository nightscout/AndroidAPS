package com.nightscout.eversense.util

import kotlin.math.min

data class RangeCalculation(val from: Int, val to: Int)

object RangeCalculator {
    fun calculateGlucoseRange(rangeFrom: Int, rangeTo: Int, lastGlucoseTimestampMs: Long): RangeCalculation {
        val timeDiffMs = System.currentTimeMillis() - lastGlucoseTimestampMs
        val fiveMinMs = 5 * 60 * 1000L
        val pageCount = min(((timeDiffMs / fiveMinMs) + 2).toInt(), 20)
        val from = maxOf(rangeTo - pageCount, rangeFrom)
        return RangeCalculation(from = from, to = rangeTo)
    }

    fun calculateRange(rangeFrom: Int, rangeTo: Int): RangeCalculation {
        val count = min(rangeTo - rangeFrom, 20)
        val from = maxOf(rangeTo - count, rangeFrom)
        return RangeCalculation(from = from, to = rangeTo)
    }
}
