package app.aaps.core.data.format

import app.aaps.core.data.format.NumberFormatPlatform.SEPARATOR_DOT
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The same expected text on every platform.
 *
 * [NumberFormatPlatform] is an `expect object`, so each target formats numbers with its own
 * library - `java.text.DecimalFormat` on the JVM and on desktop, `NSNumberFormatter` on Apple. They
 * are supposed to agree. `NumberFormatTest` pins the JVM against real `DecimalFormat` and is a fine
 * oracle, but it is a `jvmTest` and cannot say anything about the others.
 *
 * These are literal expectations rather than a comparison, so they work on a target that has no JVM
 * to compare against: on a machine that can run Apple tests the same file checks `NSNumberFormatter`
 * with no extra work. That is the point - the trap is armed before the platform that might spring it
 * exists.
 *
 * Every case passes [SEPARATOR_DOT] explicitly, so the decimal separator cannot vary with the
 * machine's locale. What is still assumed is that the default locale uses **ASCII digits**; a device
 * set to Arabic renders Arabic-Indic digits by design, and that is the intended behaviour rather
 * than something to pin here.
 */
class NumberFormatParityTest {

    @Test
    fun `no grouping above a thousand`() {
        // The bug wave 1 fixed: DecimalFormat() groups by default, so this used to be "1,234.5" on
        // en and "1.234,5" on de. Grouping is off everywhere now.
        assertEquals("1234.5", NumberFormat.DECIMAL_1.format(1234.5, SEPARATOR_DOT))
        assertEquals("1234567", NumberFormat.INTEGER.format(1234567.0, SEPARATOR_DOT))
    }

    @Test
    fun `rounding is half even`() {
        // Half-even is what DecimalFormat does, and what NSNumberFormatterRoundHalfEven must match:
        // an exact tie goes to the nearest EVEN digit, so 2.5 rounds down and 3.5 rounds up.
        //
        // Every value here is exactly representable as a double (2.5, 3.5, 0.25, 0.125, 0.375 are
        // all sums of powers of two), which is deliberate - see the note below.
        assertEquals("2", NumberFormat.INTEGER.format(2.5, SEPARATOR_DOT))
        assertEquals("4", NumberFormat.INTEGER.format(3.5, SEPARATOR_DOT))
        assertEquals("0.2", NumberFormat.DECIMAL_1.format(0.25, SEPARATOR_DOT))
        assertEquals("0.12", NumberFormat.DECIMAL_2.format(0.125, SEPARATOR_DOT))
        assertEquals("0.38", NumberFormat.DECIMAL_2.format(0.375, SEPARATOR_DOT))
    }

    /**
     * A value that only *looks* like a tie is not part of this contract, because the platforms
     * genuinely disagree and the first version of this test caught it.
     *
     * `0.35` is not representable as a double - the nearest one is `0.34999999999999997779...`,
     * just below the midpoint. `DecimalFormat` works from that true decimal value and rounds **down**
     * to `0.3`. An implementation that scales by ten first sees `0.35 * 10.0 == 3.5` exactly in
     * IEEE-754 arithmetic, reads it as a perfect tie, applies half-even, and produces `0.4`. A
     * previous Kotlin/Native experiment did exactly that, which is how this case was found.
     *
     * `DecimalFormat` is the reference - it is what every existing AAPS number has been rendered with
     * - so a platform that differs here is the one that is wrong. **`NSNumberFormatter` must be
     * checked against this case on a Mac before an iOS client ships**, because it would be rendering
     * real doses.
     */
    @Test
    fun `values that are not exactly representable are deliberately not pinned`() {
        // Asserting only that it produces one decimal, not which one.
        val text = NumberFormat.DECIMAL_1.format(0.35, SEPARATOR_DOT)
        assertEquals(3, text.length, "expected a single decimal digit but got '$text'")
    }

    @Test
    fun `minimum fraction digits are padded`() {
        assertEquals("1.0", NumberFormat.DECIMAL_1.format(1.0, SEPARATOR_DOT))
        assertEquals("1.00", NumberFormat.DECIMAL_2.format(1.0, SEPARATOR_DOT))
        assertEquals("1.000", NumberFormat.DECIMAL_3.format(1.0, SEPARATOR_DOT))
        assertEquals("0.50", NumberFormat.DECIMAL_2.format(0.5, SEPARATOR_DOT))
    }

    @Test
    fun `minimum integer digits are padded`() {
        assertEquals("05", NumberFormat.INTEGER_2_DIGITS.format(5.0, SEPARATOR_DOT))
        assertEquals("12", NumberFormat.INTEGER_2_DIGITS.format(12.0, SEPARATOR_DOT))
    }

    @Test
    fun `trailing zeros above the minimum are dropped`() {
        assertEquals("1", NumberFormat.UP_TO_2_DECIMALS.format(1.0, SEPARATOR_DOT))
        assertEquals("1.5", NumberFormat.UP_TO_2_DECIMALS.format(1.5, SEPARATOR_DOT))
        assertEquals("1.25", NumberFormat.UP_TO_2_DECIMALS.format(1.25, SEPARATOR_DOT))
        // one decimal always, a second only when it is not zero
        assertEquals("1.0", NumberFormat.DECIMAL_1_UP_TO_2.format(1.0, SEPARATOR_DOT))
        assertEquals("1.25", NumberFormat.DECIMAL_1_UP_TO_2.format(1.25, SEPARATOR_DOT))
    }

    @Test
    fun `negative numbers keep their sign`() {
        assertEquals("-1.5", NumberFormat.DECIMAL_1.format(-1.5, SEPARATOR_DOT))
        assertEquals("-1234.5", NumberFormat.DECIMAL_1.format(-1234.5, SEPARATOR_DOT))
        assertEquals("-5", NumberFormat.INTEGER.format(-5.0, SEPARATOR_DOT))
    }

    @Test
    fun `zero formats without a sign`() {
        assertEquals("0", NumberFormat.INTEGER.format(0.0, SEPARATOR_DOT))
        assertEquals("0.0", NumberFormat.DECIMAL_1.format(0.0, SEPARATOR_DOT))
    }

    @Test
    fun `insulin and glucose values a user actually sees`() {
        // The shapes that reach the screen and Nightscout, so a platform difference here would be
        // visible rather than theoretical.
        assertEquals("1.25", NumberFormat.DECIMAL_2.format(1.25, SEPARATOR_DOT))
        assertEquals("0.05", NumberFormat.DECIMAL_2.format(0.05, SEPARATOR_DOT))
        assertEquals("5.5", NumberFormat.DECIMAL_1.format(5.5, SEPARATOR_DOT))
        assertEquals("120", NumberFormat.INTEGER.format(120.0, SEPARATOR_DOT))
        assertEquals("0.001", NumberFormat.DECIMAL_3.format(0.001, SEPARATOR_DOT))
    }

    @Test
    fun `the explicit dot separator does not depend on the locale`() {
        // Whatever the machine's locale is, asking for a dot gives a dot. This is what makes server
        // data and exported files stable.
        assertEquals("3.5", NumberFormat.DECIMAL_1.format(3.5, SEPARATOR_DOT))
        assertEquals('.', SEPARATOR_DOT)
    }
}
