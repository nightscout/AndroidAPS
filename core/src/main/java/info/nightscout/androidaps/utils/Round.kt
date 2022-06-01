package info.nightscout.androidaps.utils

import android.os.Bundle
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Created by mike on 20.06.2016.
 */
object Round {

    fun roundTo(x: Double, step: Double, fabricPrivacy: FabricPrivacy? = null): Double = try {
        if (x == 0.0) 0.0
        else BigDecimal.valueOf((x / step).roundToLong()).multiply(BigDecimal.valueOf(step)).toDouble()
    } catch (e: Exception) {
        fabricPrivacy?.logCustom("Error_roundTo", Bundle().apply {
            putDouble("x", x)
            putDouble("step", step)
            putString("stacktrace", e.stackTraceToString())
        })
        0.0
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