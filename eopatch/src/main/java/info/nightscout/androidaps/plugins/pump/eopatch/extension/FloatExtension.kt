package info.nightscout.androidaps.plugins.pump.eopatch.extension

fun Float.nearlyEqual(b: Float, epsilon: Float): Boolean {
    val absA = Math.abs(this)
    val absB = Math.abs(b)
    val diff = Math.abs(this - b)
    return if (this == b) {
        true
    } else if (this == 0f || b == 0f || absA + absB < java.lang.Float.MIN_NORMAL) {
        diff < epsilon * java.lang.Float.MIN_NORMAL
    } else {
        diff / Math.min(absA + absB, Float.MAX_VALUE) < epsilon
    }
}

fun Float.nearlyNotEqual(b: Float, epsilon: Float): Boolean {
    return !nearlyEqual(b, epsilon)
}
