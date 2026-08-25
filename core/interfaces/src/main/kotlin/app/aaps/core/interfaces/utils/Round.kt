package app.aaps.core.interfaces.utils

import java.math.BigDecimal
import java.security.InvalidParameterException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Created by mike on 20.06.2016.
 */
object Round {

    fun roundTo(x: Double, step: Double): Double {
        if (x.isNaN()) throw InvalidParameterException("Parameter is NaN")
        return if (x == 0.0) 0.0
        else BigDecimal.valueOf((x / step).roundToLong()).multiply(BigDecimal.valueOf(step)).toDouble()
    }

    fun floorTo(x: Double, step: Double): Double {
        if (x == 0.0) return 0.0
        val q = x / step
        // IEEE-754: dividing an on-grid value can land a hair BELOW the integer
        // (0.15 / 0.05 == 2.9999999999999996), so a naive floor() drops a whole step.
        // Snap to the grid when the quotient is within tolerance of an integer, else floor.
        // This never pushes the result above x, so the "must not deliver more than asked /
        // must not exceed the max constraint" invariant in SafetyPlugin still holds.
        val n = if (abs(q - q.roundToLong()) < 1e-9) q.roundToLong() else floor(q).toLong()
        return BigDecimal.valueOf(n).multiply(BigDecimal.valueOf(step)).toDouble()
    }

    fun ceilTo(x: Double, step: Double): Double {
        if (x == 0.0) return 0.0
        val q = x / step
        // IEEE-754 mirror of floorTo: dividing an on-grid value can land a hair ABOVE the integer
        // (0.07 / 0.01 == 7.000000000000000888), so a naive ceil() adds a whole step. Snap to the
        // grid when the quotient is within tolerance of an integer, else ceil. Never pushes the
        // result below x, so callers that round UP to stay within a limit keep that guarantee.
        val n = if (abs(q - q.roundToLong()) < 1e-9) q.roundToLong() else ceil(q).toLong()
        return BigDecimal.valueOf(n).multiply(BigDecimal.valueOf(step)).toDouble()
    }

    fun isSame(d1: Double, d2: Double): Boolean =
        abs(d1 - d2) <= 0.000001
}