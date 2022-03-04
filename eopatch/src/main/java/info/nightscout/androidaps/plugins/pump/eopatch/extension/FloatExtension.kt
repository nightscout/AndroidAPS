package info.nightscout.androidaps.plugins.pump.eopatch.extension

import kotlin.math.abs

fun Float.nearlyEqual(b: Float, epsilon: Float): Boolean {
    val absA = abs(this)
    val absB = abs(b)
    val diff = abs(this - b)
    return if (this == b) {
        true
    } else if (this == 0f || b == 0f || absA + absB < java.lang.Float.MIN_NORMAL) {
        diff < epsilon * java.lang.Float.MIN_NORMAL
    } else {
        diff / (absA + absB).coerceAtMost(Float.MAX_VALUE) < epsilon
    }
}

fun Float.nearlyNotEqual(b: Float, epsilon: Float): Boolean {
    return !nearlyEqual(b, epsilon)
}
