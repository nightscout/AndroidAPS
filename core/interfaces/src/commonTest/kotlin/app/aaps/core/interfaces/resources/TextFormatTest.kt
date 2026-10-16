package app.aaps.core.interfaces.resources

import app.aaps.core.data.format.NumberFormat
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the non-Android platforms rely on to turn a format string into a sentence.
 *
 * Decimal separators are deliberately not asserted as literal text. [NumberFormat] uses the platform
 * separator, so a machine set to Czech renders `1,5` where an English one renders `1.5`, and a test
 * that hard coded either would pass on one developer's machine and fail on another's. The `%f` cases
 * compare against [NumberFormat] directly, which is what they are really checking: that the
 * precision reaches it.
 */
class TextFormatTest {

    @Test fun `substitutes a string argument`() {
        assertEquals("Hello Bob", formatTemplate("Hello %s", listOf("Bob")))
    }

    @Test fun `substitutes several arguments in order`() {
        assertEquals("a-b", formatTemplate("%s-%s", listOf("a", "b")))
    }

    @Test fun `honours an explicit argument index`() {
        assertEquals("second first", formatTemplate("%2\$s %1\$s", listOf("first", "second")))
    }

    @Test fun `an explicit index does not move the automatic one`() {
        // Java shares this rule: an indexed spec is a lookup, not a step through the arguments.
        assertEquals("b a b", formatTemplate("%2\$s %s %2\$s", listOf("a", "b")))
    }

    @Test fun `renders an integer without a decimal part`() {
        assertEquals("3 g", formatTemplate("%d g", listOf(3)))
    }

    @Test fun `renders a long`() {
        assertEquals("9000000000", formatTemplate("%d", listOf(9_000_000_000L)))
    }

    @Test fun `truncates a double given to a d conversion`() {
        // Java throws here. Showing the whole number is closer to the caller's intent than crashing
        // while drawing a label.
        assertEquals("2", formatTemplate("%d", listOf(2.9)))
    }

    @Test fun `passes the precision to the number formatter`() {
        val expected = NumberFormat(minFractionDigits = 1, maxFractionDigits = 1).format(1.25)
        assertEquals(expected, formatTemplate("%.1f", listOf(1.25)))
    }

    @Test fun `defaults a float to six decimals like java does`() {
        val expected = NumberFormat(minFractionDigits = 6, maxFractionDigits = 6).format(1.5)
        assertEquals(expected, formatTemplate("%f", listOf(1.5)))
    }

    @Test fun `keeps a literal per cent sign`() {
        assertEquals("100% done", formatTemplate("100%% done", listOf("ignored")))
    }

    @Test fun `leaves a placeholder visible when the argument is missing`() {
        // The alternative is Java's MissingFormatArgumentException, which is a crash while showing
        // a label. A visible placeholder is a bug report instead of a lost session.
        assertEquals("a %2\$s", formatTemplate("%s %2\$s", listOf("a")))
    }

    @Test fun `renders a null argument as null rather than dropping it`() {
        assertEquals("value: null", formatTemplate("value: %s", listOf(null)))
    }

    @Test fun `returns the template untouched when there are no arguments`() {
        assertEquals("%s stays", formatTemplate("%s stays", emptyList()))
    }

    @Test fun `pads to a width on the right by default`() {
        assertEquals("   ab", formatTemplate("%5s", listOf("ab")))
    }

    @Test fun `left aligns with the minus flag`() {
        assertEquals("ab   ", formatTemplate("%-5s", listOf("ab")))
    }

    @Test fun `zero fills after the sign`() {
        assertEquals("-001", formatTemplate("%04d", listOf(-1)))
    }

    @Test fun `uppercases for an uppercase conversion`() {
        assertEquals("LOUD", formatTemplate("%S", listOf("loud")))
    }

    @Test fun `leaves text with no placeholder alone`() {
        assertEquals("nothing to do", formatTemplate("nothing to do", listOf("unused")))
    }
}
