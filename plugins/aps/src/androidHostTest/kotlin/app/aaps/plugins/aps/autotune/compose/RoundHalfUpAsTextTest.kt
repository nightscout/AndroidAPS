package app.aaps.plugins.aps.autotune.compose

import app.aaps.core.data.format.NumberFormat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * [roundHalfUpAsText] written by [NumberFormat] gives the same text as `String.format("%.Nf")`, which
 * the Autotune results used before they moved to common code.
 */
class RoundHalfUpAsTextTest {

    private val special = listOf(
        0.0, 0.15, 0.25, 0.35, 1.005, 2.675, 1.115, 9.995, 99.995, 0.0005, 0.00049, 1.0E-5, 0.5, 1.5, 2.5, 45.25, 45.35,
        123456.789, 16.951, 18.185, 0.1 + 0.2, 2.0 / 3.0, 999.9995, 5.0E-4, 1.0E7, 86.0 / 18.0
    )

    @Test
    fun `same text as String format`() {
        val values = special + (0..30000).map { it / 1000.0 } + (0..2000).map { it * 0.005 } + (1..500).map { it / 7.0 }
        for (decimals in 0..3) {
            val format = NumberFormat.withDecimals(decimals)
            for (value in values) {
                assertWithMessage("$value with $decimals decimals")
                    .that(format.format(roundHalfUpAsText(value, decimals), '.'))
                    .isEqualTo(String.format(Locale.US, "%.${decimals}f", value))
            }
        }
    }

    @Test
    fun `the phone's separator is used`() {
        val old = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertWithMessage("1.005").that(NumberFormat.withDecimals(2).format(roundHalfUpAsText(1.005, 2))).isEqualTo(String.format(Locale.GERMANY, "%.2f", 1.005))
        } finally {
            Locale.setDefault(old)
        }
    }
}
