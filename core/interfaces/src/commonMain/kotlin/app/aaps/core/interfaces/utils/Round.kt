package app.aaps.core.interfaces.utils

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Created by mike on 20.06.2016.
 */
object Round {

    fun roundTo(x: Double, step: Double): Double {
        require(!x.isNaN()) { "Parameter is NaN" }
        if (x == 0.0) return 0.0
        val n = (x / step).roundToLong()
        return exactTimes(n, step) ?: (n * step)
    }

    fun floorTo(x: Double, step: Double): Double {
        if (x == 0.0) return 0.0
        val q = x / step
        // IEEE-754: dividing an on-grid value can land a hair BELOW the integer
        // (0.15 / 0.05 == 2.9999999999999996), so a naive floor() drops a whole step.
        // Snap to the grid when the quotient is within tolerance of an integer, else floor.
        // The snap can return the grid value a float tail above x (x was meant to be on the grid),
        // never a step above it, so the "must not deliver more than asked / must not exceed the
        // max constraint" invariant in SafetyPlugin still holds.
        val n = if (abs(q - q.roundToLong()) < 1e-9) q.roundToLong() else floor(q).toLong()
        // The plain product is not exact and can end above x, so it is clamped to x.
        return exactTimes(n, step) ?: (n * step).coerceAtMost(x)
    }

    fun ceilTo(x: Double, step: Double): Double {
        if (x == 0.0) return 0.0
        val q = x / step
        // IEEE-754 mirror of floorTo: dividing an on-grid value can land a hair ABOVE the integer
        // (0.07 / 0.01 == 7.000000000000000888), so a naive ceil() adds a whole step. Snap to the
        // grid when the quotient is within tolerance of an integer, else ceil. Never a step below x,
        // so callers that round UP to stay within a limit keep that guarantee.
        val n = if (abs(q - q.roundToLong()) < 1e-9) q.roundToLong() else ceil(q).toLong()
        // The plain product is not exact and can end below x, so it is clamped to x.
        return exactTimes(n, step) ?: (n * step).coerceAtLeast(x)
    }

    /**
     * `x * step`, giving the [Double] nearest to the **decimal** product.
     *
     * A plain `x * step` is not good enough here: `3 * 0.05` is `0.15000000000000002` and
     * `12 * 0.05` is `0.6000000000000001`. Those are dose and rate values, so the extra digits
     * travel into pump commands, comparisons and the Nightscout payload.
     *
     * Integer arithmetic is what makes it exact: `step.toString()` spells an exact decimal, so reading
     * that text back as `unscaled / 10^scale` loses nothing, the multiply is an exact [Long] one, and
     * the single closing division is correctly rounded. The result is the nearest double to the true
     * decimal product, by construction rather than by approximation.
     *
     * The integer product is exact only while it stays under 2^53 (about 9.0e15). A clean step keeps it far
     * below: at 0.0001 (scale 4) values up to about 9e11 fit. A step with a float tail does not: 0.05 * 0.2 is
     * `0.010000000000000002`, an 18 digit number, and a dose of 10 U then overflowed [Long] into a negative
     * value. Past that limit this returns null, and the caller uses the plain product, which is off by a float
     * tail at most.
     */
    private fun exactTimes(x: Long, step: Double): Double? {
        // Shortest round-trip text, the same source BigDecimal.valueOf(double) reads. Below 1e-3 it
        // arrives in scientific notation ("1.0E-4" for 0.0001, a step that is really used), so the
        // exponent has to be folded into the scale rather than assumed absent.
        val text = step.toString()
        val e = text.indexOf('E')
        val mantissa = if (e < 0) text else text.substring(0, e)
        val exponent = if (e < 0) 0 else text.substring(e + 1).toInt()
        val point = mantissa.indexOf('.')
        val unscaled = (if (point < 0) mantissa else mantissa.substring(0, point) + mantissa.substring(point + 1)).toLong()
        val scale = (if (point < 0) 0 else mantissa.length - point - 1) - exponent

        if (unscaled != 0L && abs(x) > MAX_EXACT / abs(unscaled)) return null
        val product = x * unscaled
        return if (scale >= 0) product / 10.0.pow(scale) else product * 10.0.pow(-scale)
    }

    /** 2^53: the largest integer below which a [Double] still holds every whole number. */
    private const val MAX_EXACT = 1L shl 53

    fun isSame(d1: Double, d2: Double): Boolean =
        abs(d1 - d2) <= 0.000001

    /**
     * True when [given] differs from [asked] by a whole [step] or more: a limit cut it, it was not only rounded
     * to the step. A cut of exactly one step counts too, although 1.2 - 1.1 is 0.0999... as a Double, below 0.1.
     */
    fun isMoreThanRounding(asked: Double, given: Double, step: Double): Boolean =
        abs(asked - given) >= step - 1e-9
}
