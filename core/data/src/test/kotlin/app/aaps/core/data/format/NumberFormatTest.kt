package app.aaps.core.data.format

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.text.DecimalFormat
import java.util.Locale

/**
 * Proves that [NumberFormat] gives exactly the same text as the `java.text.DecimalFormat` patterns
 * it replaces.
 *
 * Every pattern listed here was found in the app before the change. If a pattern is added back
 * somewhere, add it here too.
 *
 * The app test suite sets the locale to English for the whole JVM, so a locale problem would not
 * be seen anywhere else. That is why these tests set the locale themselves and restore it after.
 */
class NumberFormatTest {

    private val originalLocale: Locale = Locale.getDefault()

    @AfterEach
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    /** Every pattern used in the app, with the [NumberFormat] that replaces it. */
    private val patterns: List<Pair<String, NumberFormat>> = listOf(
        "0" to NumberFormat.INTEGER,
        "#0" to NumberFormat.INTEGER,
        "#" to NumberFormat.INTEGER,
        "00" to NumberFormat.INTEGER_2_DIGITS,
        "0.0" to NumberFormat.DECIMAL_1,
        "#0.0" to NumberFormat.DECIMAL_1,
        "0.00" to NumberFormat.DECIMAL_2,
        "#0.00" to NumberFormat.DECIMAL_2,
        "##0.00" to NumberFormat.DECIMAL_2,
        "0.000" to NumberFormat.DECIMAL_3,
        "#0.000" to NumberFormat.DECIMAL_3,
        "0.000000" to NumberFormat.DECIMAL_6,
        "#.#" to NumberFormat.UP_TO_1_DECIMAL,
        "0.##" to NumberFormat.UP_TO_2_DECIMALS,
        "0.0#" to NumberFormat.DECIMAL_1_UP_TO_2,
        "0.00#" to NumberFormat.DECIMAL_2_UP_TO_3,
        "0.000#" to NumberFormat.DECIMAL_3_UP_TO_4
    )

    private val values: List<Double> = listOf(
        0.0, 1.0, -1.0,
        // half way values, they show whether rounding is half-even
        0.5, 1.5, 2.5, 3.5, -0.5, -1.5, -2.5,
        0.05, 0.15, 0.25, 1.05, 1.15, 1.25,
        0.005, 0.015, 0.025,
        // ordinary values from the app: insulin, carbs, glucose, basal rates
        1.33, 1.3333, 0.1, 0.05, 30.0, 5.5, 10.0, 18.0, 180.0, 0.025,
        2.75, 99.999, 100.0, 123.456, 1234.5, 12345.678,
        -1.33, -0.05, -99.999,
        // very small and very large
        0.0001, 0.000001, 1000000.0
    )

    @Test
    fun `matches DecimalFormat in English locale`() {
        assertSamePatternOutput(Locale.ENGLISH)
    }

    @Test
    fun `matches DecimalFormat in a comma locale`() {
        // German uses a comma as the decimal separator. This is what most AAPS users in Europe see.
        assertSamePatternOutput(Locale.GERMAN)
    }

    @Test
    fun `matches DecimalFormat in Czech locale`() {
        assertSamePatternOutput(Locale.forLanguageTag("cs-CZ"))
    }

    private fun assertSamePatternOutput(locale: Locale) {
        Locale.setDefault(locale)
        for ((pattern, format) in patterns)
            for (value in values) {
                val expected = DecimalFormat(pattern).format(value)
                assertWithMessage("pattern \"$pattern\" value $value locale $locale")
                    .that(format.format(value))
                    .isEqualTo(expected)
            }
    }

    @Test
    fun `explicit dot separator does not depend on locale`() {
        Locale.setDefault(Locale.GERMAN)
        assertThat(NumberFormat.DECIMAL_1.format(1.5, NumberFormatPlatform.SEPARATOR_DOT)).isEqualTo("1.5")
        assertThat(NumberFormat.DECIMAL_3.format(0.025, NumberFormatPlatform.SEPARATOR_DOT)).isEqualTo("0.025")
    }

    @Test
    fun `locale separator is used by default`() {
        Locale.setDefault(Locale.GERMAN)
        assertThat(NumberFormat.DECIMAL_1.format(1.5)).isEqualTo("1,5")
        Locale.setDefault(Locale.ENGLISH)
        assertThat(NumberFormat.DECIMAL_1.format(1.5)).isEqualTo("1.5")
    }

    @Test
    fun `no grouping separator for large numbers`() {
        Locale.setDefault(Locale.ENGLISH)
        assertThat(NumberFormat.INTEGER.format(1234567.0)).isEqualTo("1234567")
        assertThat(NumberFormat.DECIMAL_2.format(1234567.891)).isEqualTo("1234567.89")
    }

    @Test
    fun `withDecimals gives the same result as the constants`() {
        Locale.setDefault(Locale.ENGLISH)
        assertThat(NumberFormat.withDecimals(0).format(1.55)).isEqualTo(NumberFormat.INTEGER.format(1.55))
        assertThat(NumberFormat.withDecimals(1).format(1.55)).isEqualTo(NumberFormat.DECIMAL_1.format(1.55))
        assertThat(NumberFormat.withDecimals(2).format(1.555)).isEqualTo(NumberFormat.DECIMAL_2.format(1.555))
        assertThat(NumberFormat.withDecimals(3).format(1.5555)).isEqualTo(NumberFormat.DECIMAL_3.format(1.5555))
    }

    @Test
    fun `format of Long and Int gives the same result as Double`() {
        Locale.setDefault(Locale.ENGLISH)
        assertThat(NumberFormat.DECIMAL_1.format(5L)).isEqualTo("5.0")
        assertThat(NumberFormat.DECIMAL_1.format(5)).isEqualTo("5.0")
        assertThat(NumberFormat.INTEGER_2_DIGITS.format(5)).isEqualTo("05")
    }
}
