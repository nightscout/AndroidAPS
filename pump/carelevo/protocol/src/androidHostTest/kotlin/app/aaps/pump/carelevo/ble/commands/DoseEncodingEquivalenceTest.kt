package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Random
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.math.roundToInt

/**
 * The dose rounding without `BigDecimal` ([hundredthsHalfUp]) gives the bytes the driver sent before, on every value
 * a dose can be. [oldEncode] and [oldSentSpeed] are the code the driver ran until the protocol moved to Kotlin
 * Multiplatform, kept here as the reference: a wrong byte is a wrong dose.
 *
 * JVM only, because the reference needs `BigDecimal`. The common `DoseEncodingTest` runs the hard values on every
 * platform.
 */
internal class DoseEncodingEquivalenceTest {

    /** Every value the patch can take in bytes: whole units up to 255. */
    private val maxHundredths = 25_599

    @Test
    fun `every hundredth gives the same bytes`() {
        for (k in 0..maxHundredths) check(k / 100.0)
    }

    @Test
    fun `every thousandth gives the same bytes - these include every half way point`() {
        for (k in 0..maxHundredths * 10 + 9) check(k / 1000.0)
    }

    /** A dose AAPS computes is a sum or a product of doubles, not a clean decimal. */
    @Test
    fun `sums and products of steps give the same bytes`() {
        var sumOfHundredths = 0.0
        var sumOfTwentieths = 0.0
        for (k in 0..maxHundredths) {
            check(k * 0.01)
            check(sumOfHundredths)
            sumOfHundredths += 0.01
            if (k <= maxHundredths / 5) {
                check(k * 0.05)
                check(sumOfTwentieths)
                sumOfTwentieths += 0.05
            }
            check(k * 0.1 / 10)
        }
    }

    /** The doubles next to each half way point are where a rounding without care goes wrong. */
    @Test
    fun `the doubles around every half way point give the same bytes`() {
        for (k in 0..maxHundredths) {
            var below = (2 * k + 1) / 200.0
            var above = below
            check(below)
            repeat(4) {
                below = below.nextDown()
                above = above.nextUp()
                check(below)
                check(above)
            }
        }
    }

    @Test
    fun `random values give the same bytes`() {
        val random = Random(20261117)
        repeat(2_000_000) { check(random.nextDouble() * 256) }
    }

    private fun check(value: Double) {
        val now = encodeUnitCenti(value)
        val before = oldEncode(value)
        assertWithMessage("encodeUnitCenti($value)").that(now.toList()).isEqualTo(before.toList())
        assertWithMessage("sentSpeed($value)").that(BasalProgramCommand.sentSpeed(value)).isEqualTo(oldSentSpeed(value))
        if (value > 0.0) {
            val bolus = ImmediateBolusCommand(actionId = 1, volume = value).encode()
            assertWithMessage("bolus $value").that(bolus.copyOfRange(2, 4).toList()).isEqualTo(before.toList())
        }
    }

    /** The encoding before: `encodeUnitCenti` and `ImmediateBolusCommand.encode` both rounded this way. */
    private fun oldEncode(value: Double): ByteArray {
        val rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()
        val whole = rounded.toInt()
        val centi = ((rounded - whole) * 100).roundToInt()
        return byteArrayOf(whole.toByte(), centi.toByte())
    }

    private fun oldSentSpeed(value: Double): Double =
        oldEncode(value).let { bytes ->
            BigDecimal(bytes[0].toUByte().toInt()).add(BigDecimal(bytes[1].toUByte().toInt()).movePointLeft(2)).toDouble()
        }
}
