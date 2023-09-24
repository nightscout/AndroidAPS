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

    fun floorTo(x: Double, step: Double): Double =
        if (x != 0.0) floor(x / step) * step
        else 0.0

    fun ceilTo(x: Double, step: Double): Double =
        if (x != 0.0) ceil(x / step) * step
        else 0.0

    fun isSame(d1: Double, d2: Double): Boolean =
        abs(d1 - d2) <= 0.000001
}