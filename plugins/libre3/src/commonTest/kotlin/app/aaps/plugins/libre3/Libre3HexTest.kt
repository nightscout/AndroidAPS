package app.aaps.plugins.libre3

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The text helpers that replaced `String.format` and the JVM charset decoder.
 *
 * These lines reach the driver log and the pairing trace, so the text has to stay byte for byte
 * what it was. They run on every target, because a per byte format is exactly the kind of thing
 * that quietly differs.
 */
class Libre3HexTest {

    @Test
    fun oneByteIsAlwaysTwoDigits() {
        assertEquals("00", 0x00.toByte().libre3HexLower())
        assertEquals("0f", 0x0F.toByte().libre3HexLower())
        assertEquals("ff", 0xFF.toByte().libre3HexLower())
        assertEquals("80", 0x80.toByte().libre3HexLower())
    }

    @Test
    fun theUpperCaseFormIsTheSameDigitsInUpperCase() {
        assertEquals("0F", 0x0F.toByte().libre3HexUpper())
        assertEquals("AB", 0xAB.toByte().libre3HexUpper())
        assertEquals("FF", 0xFF.toByte().libre3HexUpper())
    }

    @Test
    fun anArrayIsEveryByteWithNothingBetween() {
        assertEquals("", ByteArray(0).libre3HexLower())
        assertEquals("000110ff", byteArrayOf(0x00, 0x01, 0x10, 0xFF.toByte()).libre3HexLower())
    }

    @Test
    fun asciiTextReadsBackAsItself() {
        assertEquals("3MH00KD7XW4", byteArrayOf(51, 77, 72, 48, 48, 75, 68, 55, 88, 87, 52).libre3AsciiToString())
    }

    @Test
    fun aByteThatIsNotAsciiBecomesTheReplacementCharacter() {
        // Same answer the JVM US-ASCII decoder gave, so a damaged frame cannot read as plausible text.
        assertEquals("A�B", byteArrayOf(0x41, 0x80.toByte(), 0x42).libre3AsciiToString())
    }
}
