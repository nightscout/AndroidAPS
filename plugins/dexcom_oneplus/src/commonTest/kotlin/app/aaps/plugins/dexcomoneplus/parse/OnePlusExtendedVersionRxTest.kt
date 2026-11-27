package app.aaps.plugins.dexcomoneplus.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The sensor's own session and warm-up length (0x52). The two answers are real ones, seen from a
 * 10-day and a 15-day G7 (AAPS PR 5397).
 *
 * Test names here carry no comma: Kotlin/Native rejects one inside a backtick quoted name.
 */
class OnePlusExtendedVersionRxTest {

    private fun hex(text: String): ByteArray =
        text.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `a 10-day sensor reads 10 and a half days and 27 minutes`() {
        val info = assertNotNull(OnePlusExtendedVersionRx.parse(hex("52 00 c0d70d00 5406 00020404 ff 0c00")))
        assertEquals(907_200L, info.sessionLengthSeconds)
        assertEquals(1_620, info.warmupSeconds)
    }

    @Test
    fun `a 15-day sensor reads 15 and a half days and 62 minutes`() {
        val info = assertNotNull(OnePlusExtendedVersionRx.parse(hex("52 00 406f1400 880e 00010a04 ff 1100")))
        assertEquals(1_339_200L, info.sessionLengthSeconds)
        assertEquals(3_720, info.warmupSeconds)
        assertEquals(1_339_200_000L, info.sessionLengthMs)
    }

    @Test
    fun `another opcode is not an answer`() {
        assertNull(OnePlusExtendedVersionRx.parse(hex("4e 00 406f1400 880e 00010a04 ff 1100")))
    }

    @Test
    fun `a short packet is not an answer`() {
        assertNull(OnePlusExtendedVersionRx.parse(hex("52 00 406f1400 880e")))
    }

    @Test
    fun `a session length out of range is a misread`() {
        // 1 day: no sensor runs that short.
        assertNull(OnePlusExtendedVersionRx.parse(hex("52 00 80510100 880e 00010a04 ff 1100")))
    }

    @Test
    fun `a warm-up of zero is a misread`() {
        assertNull(OnePlusExtendedVersionRx.parse(hex("52 00 406f1400 0000 00010a04 ff 1100")))
    }

    @Test
    fun `the request is the opcode alone`() {
        assertEquals(listOf<Byte>(0x52), OnePlusExtendedVersionRx.request().toList())
    }
}
