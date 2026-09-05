package app.aaps.pump.eopatch.core.util

import kotlin.math.abs
import kotlin.math.min

object CommonUtils {

    fun nearlyEqual(a: Float, b: Float, epsilon: Float): Boolean {
        val absA = abs(a)
        val absB = abs(b)
        val diff = abs(a - b)

        return when {
            a == b                            -> true // shortcut, handles infinities
            a == 0f || b == 0f || absA + absB < Float.MIN_VALUE
                                              -> diff < epsilon * Float.MIN_VALUE
            else                              -> diff / min(absA + absB, Float.MAX_VALUE) < epsilon
        }
    }
}
