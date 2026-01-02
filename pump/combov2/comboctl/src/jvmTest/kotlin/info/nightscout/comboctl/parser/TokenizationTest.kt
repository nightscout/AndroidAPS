package info.nightscout.comboctl.parser

import app.aaps.shared.tests.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenizationTest : TestBase() {

    @Test
    fun checkBasicPatternMatch() {
        // Try to match the LARGE_BASAL symbol pattern in the testFrameMainScreenWithTimeSeparator.
        // That symbol is present at position (0,9).
        // Trying to match it at those coordinates is expected to succeed,
        // while trying to match it slightly to the right should fail.

        val largeBasalGlyphPattern = glyphPatterns.getValue(Glyph.LargeSymbol(LargeSymbol.BASAL))

        val result1 = checkIfPatternMatchesAt(
            testFrameMainScreenWithTimeSeparator,
            largeBasalGlyphPattern,
            0, 8
        )
        assertTrue(result1)

        val result2 = checkIfPatternMatchesAt(
            testFrameMainScreenWithTimeSeparator,
            largeBasalGlyphPattern,
            1, 8
        )
        assertFalse(result2)
    }

    @Test
    fun checkMainScreenTokenization() {
        // Look for tokens in the main menu display frame.
        // The pattern matching algorithm scans the frame
        // left to right, top to bottom, and tries the
        // large patterns first.
        // The main screen contains symbols that yield
        // ambiguities due to overlapping tokens. For
        // example, the basal icon contains sub-patterns
        // that match the cyrillic letter "п". These must
        // be filtered out by findTokens().

        val tokens = findTokens(testFrameMainScreenWithTimeSeparator)

        assertEquals(13, tokens.size)

        val iterator = tokens.iterator()

        assertEquals(Glyph.SmallSymbol(SmallSymbol.CLOCK), iterator.next().glyph)

        assertEquals(Glyph.SmallDigit(1), iterator.next().glyph)
        assertEquals(Glyph.SmallDigit(0), iterator.next().glyph)
        assertEquals(Glyph.SmallSymbol(SmallSymbol.SEPARATOR), iterator.next().glyph)
        assertEquals(Glyph.SmallDigit(2), iterator.next().glyph)
        assertEquals(Glyph.SmallDigit(0), iterator.next().glyph)

        assertEquals(Glyph.LargeSymbol(LargeSymbol.BASAL), iterator.next().glyph)

        assertEquals(Glyph.LargeDigit(0), iterator.next().glyph)
        assertEquals(Glyph.LargeSymbol(LargeSymbol.DOT), iterator.next().glyph)
        assertEquals(Glyph.LargeDigit(2), iterator.next().glyph)
        assertEquals(Glyph.LargeDigit(0), iterator.next().glyph)

        assertEquals(Glyph.LargeSymbol(LargeSymbol.UNITS_PER_HOUR), iterator.next().glyph)

        assertEquals(Glyph.SmallDigit(1), iterator.next().glyph)
    }

    @Test
    fun checkStandardBolusTokenization() {
        // Look for tokens in the standard bolus display frame.
        // The pattern matching algorithm scans the frame
        // left to right, top to bottom, and tries the
        // large patterns first.
        // The standard bolus screen contains mostly letters,
        // but also a LARGE_BOLUS symbol at the very bottom of
        // the screen, thus testing that patterns are also properly
        // matched if they are at a border.

        val tokens = findTokens(testFrameStandardBolusMenuScreen)

        assertEquals(14, tokens.size)

        val iterator = tokens.iterator()

        assertEquals(Glyph.SmallCharacter('S'), iterator.next().glyph)
        assertEquals(Glyph.SmallCharacter('T'), iterator.next().glyph)
        assertEquals(Glyph.SmallCharacter('A'), iterator.next().glyph)
        assertEquals(Glyph.SmallCharacter('N'), iterator.next().glyph)
        assertEquals(Glyph.SmallCharacter('D'), iterator.next().glyph)
        assertEquals(Glyph.SmallCharacter('A'), iterator.next().glyph)
        assertEquals(Glyph.SmallCharacter('R'), iterator.next().glyph)
        assertEquals(Glyph.SmallCharacter('D'), iterator.next().glyph)
        assertEquals(Glyph.SmallCharacter('B'), iterator.next().glyph)
        assertEquals(Glyph.SmallCharacter('O'), iterator.next().glyph)
        assertEquals(Glyph.SmallCharacter('L'), iterator.next().glyph)
        assertEquals(Glyph.SmallCharacter('U'), iterator.next().glyph)
        assertEquals(Glyph.SmallCharacter('S'), iterator.next().glyph)

        assertEquals(Glyph.LargeSymbol(LargeSymbol.BOLUS), iterator.next().glyph)
    }
}
