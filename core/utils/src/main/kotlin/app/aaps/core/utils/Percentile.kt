package app.aaps.core.utils

import kotlin.math.floor

object Percentile {

    // Returns the value at a given percentile in a sorted numeric array, using the
    // "linear interpolation between closest ranks" method.
    // The rank index is arr.size * p, matching oref0 lib/percentile.js so that AndroidAPS autosens and
    // autotune reproduce OpenAPS dosing. This differs on purpose from the (arr.size - 1) * p variant in
    // the original https://gist.github.com/IceCreamYou/6ffa1b18c4c8f6aeaad2 - do NOT change it to n - 1,
    // it would shift every median and change autosens/autotune output.
    fun percentile(arr: Array<Double>, p: Double): Double = percentile(arr.toDoubleArray(), p)

    fun percentile(arr: DoubleArray, p: Double): Double {
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