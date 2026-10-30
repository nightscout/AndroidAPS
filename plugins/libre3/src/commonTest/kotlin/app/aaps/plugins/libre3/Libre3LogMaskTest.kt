package app.aaps.plugins.libre3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Serials and Bluetooth addresses are cut down before they reach `AndroidAPS.log`, which users
 * send to other people. Only the last few characters may stay.
 */
class Libre3LogMaskTest {

    @Test
    fun macKeepsOnlyTheLastFiveCharacters() {
        assertEquals("***EE:FF", Libre3LogMask.mac("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun macIsUpperCasedAndTrimmedLikeTheArbiterKey() {
        assertEquals("***EE:FF", Libre3LogMask.mac(" aa:bb:cc:dd:ee:ff "))
    }

    @Test
    fun serialKeepsOnlyTheLastFourCharacters() {
        val masked = Libre3LogMask.serial("0D00012AB")
        assertEquals("***12AB", masked)
        assertFalse(masked.contains("0D000"))
    }

    @Test
    fun aValueTooShortToCutIsHiddenWhole() {
        assertEquals("***", Libre3LogMask.serial("1234"))
        assertEquals("***", Libre3LogMask.mac("EE:FF"))
    }

    @Test
    fun aMissingValueSaysNone() {
        assertEquals("none", Libre3LogMask.serial(null))
        assertEquals("none", Libre3LogMask.serial(""))
        assertEquals("none", Libre3LogMask.mac(null))
        assertEquals("none", Libre3LogMask.mac("  "))
    }
}
