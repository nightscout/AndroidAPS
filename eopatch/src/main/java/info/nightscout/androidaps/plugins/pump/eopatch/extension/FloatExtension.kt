package info.nightscout.androidaps.plugins.pump.eopatch.extension

import kotlin.math.abs

fun Double.nearlyEqual(b: Double, epsilon: Double): Boolean {
    val absA = abs(this)
    val absB = abs(b)
    val diff = abs(this - b)
    return if (this == b) {
        true
    } else if (this == 0.0 || b == 0.0 || absA + absB < java.lang.Float.MIN_NORMAL) {
        diff < epsilon * java.lang.Double.MIN_NORMAL
    } else {
        diff / (absA + absB).coerceAtMost(Double.MAX_VALUE) < epsilon
    }
}