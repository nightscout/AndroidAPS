package info.nightscout.core.utils

import kotlin.math.floor

object Percentile {

    // From https://gist.github.com/IceCreamYou/6ffa1b18c4c8f6aeaad2
    // Returns the value at a given percentile in a sorted numeric array.
    // "Linear interpolation between closest ranks" method
    fun percentile(arr: Array<Double>, p: Double): Double {
        if (arr.isEmpty()) return 0.0
        if (p <= 0) return arr[0]
        if (p >= 1) return arr[arr.size - 1]
        val index = arr.size * p
        val lower = floor(index)
        val upper = lower + 1
        val weight = index % 1
        return if (upper >= arr.size) arr[lower.toInt()] else arr[lower.toInt()] * (1 - weight) + arr[upper.toInt()] * weight
    }
}