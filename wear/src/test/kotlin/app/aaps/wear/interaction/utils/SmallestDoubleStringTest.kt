package app.aaps.wear.interaction.utils

import app.aaps.wear.interaction.utils.SmallestDoubleString.Units
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SmallestDoubleString], the number-minimisation helper used to fit IOB/COB/delta
 * values into the very limited character budget of Wear OS complications.
 *
 * Coverage was lost when the wear test suite was dropped during the V4 AndroidX migration; this
 * restores direct coverage of the parser and the precision-reduction algorithm.
 */
class SmallestDoubleStringTest {

    // --- parsing of the input string into sign / decimal / fractional / extra / units ---

    @Test fun extractsParenthesizedExtraAndUnits() {
        val s = SmallestDoubleString("2(40)g", Units.USE)
        assertThat(s.extra).isEqualTo("40")
        assertThat(s.units).isEqualTo("g")
    }

    @Test fun noExtraWhenAbsent() {
        val s = SmallestDoubleString("50g", Units.USE)
        assertThat(s.extra).isEqualTo("")
        assertThat(s.units).isEqualTo("g")
    }

    @Test fun unitsSkippedByDefault() {
        // Default Units.SKIP drops the unit from the rendered output
        assertThat(SmallestDoubleString("3.50U").minimise(20)).isEqualTo("3.50")
        assertThat(SmallestDoubleString("3.50U", Units.USE).minimise(20)).isEqualTo("3.50U")
    }

    // --- normalisation that always happens, regardless of available width ---

    @Test fun stripsAllZeroFraction() {
        assertThat(SmallestDoubleString("1.0").minimise(20)).isEqualTo("1")
        assertThat(SmallestDoubleString("5.00").minimise(20)).isEqualTo("5")
        assertThat(SmallestDoubleString("-5.0").minimise(20)).isEqualTo("-5")
    }

    @Test fun stripsLeadingZeroOfPureFraction() {
        assertThat(SmallestDoubleString("0.7").minimise(20)).isEqualTo(".7")
        assertThat(SmallestDoubleString("0.87").minimise(20)).isEqualTo(".87")
        assertThat(SmallestDoubleString("0,87").minimise(20)).isEqualTo(",87")
    }

    @Test fun zeroValueStaysZero() {
        assertThat(SmallestDoubleString("0").minimise(20)).isEqualTo("0")
        assertThat(SmallestDoubleString("0.0").minimise(20)).isEqualTo("0")
    }

    @Test fun keepsValueUnchangedWhenItFits() {
        assertThat(SmallestDoubleString("1.2").minimise(20)).isEqualTo("1.2")
        assertThat(SmallestDoubleString("1.20").minimise(20)).isEqualTo("1.20")
        assertThat(SmallestDoubleString("1,2").minimise(20)).isEqualTo("1,2")
        // plus sign is preserved while it still fits
        assertThat(SmallestDoubleString("+2.5").minimise(20)).isEqualTo("+2.5")
    }

    // --- precision reduction (rounding) when the value does not fit ---

    @Test fun roundsFractionToFit() {
        assertThat(SmallestDoubleString("2.549").minimise(4)).isEqualTo("2.55")
        assertThat(SmallestDoubleString("-1.563").minimise(4)).isEqualTo("-1.6")
        assertThat(SmallestDoubleString("-1.78").minimise(3)).isEqualTo("-2")
    }

    @Test fun preservesSeparatorWhenRounding() {
        assertThat(SmallestDoubleString("+2,549").minimise(4)).isEqualTo("2,55")
        assertThat(SmallestDoubleString("-1,563").minimise(4)).isEqualTo("-1,6")
    }

    @Test fun dropsPlusSignBeforeReducingPrecision() {
        // "+1.2" is length 4; at budget 3 the plus is dropped first, leaving the value intact
        assertThat(SmallestDoubleString("+1.2").minimise(3)).isEqualTo("1.2")
    }
}
