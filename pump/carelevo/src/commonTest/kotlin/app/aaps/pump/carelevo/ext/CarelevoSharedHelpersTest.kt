package app.aaps.pump.carelevo.ext

import app.aaps.pump.carelevo.domain.ext.generateUUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The helpers that replaced `String.format`, `BigInteger` and `java.util.UUID` when the driver moved to shared code.
 * In commonTest, so they run on iOS as well as on the JVM.
 */
class CarelevoSharedHelpersTest {

    @Test
    fun `each byte prints as 0x and two lower case hex digits`() {
        val bytes = byteArrayOf(0x00, 0x0F, 0x7F, 0x80.toByte(), 0xFF.toByte())

        assertEquals("0x000x0f0x7f0x800xff", bytes.convertBytesToHex())
    }

    @Test
    fun `one byte prints the same way`() {
        assertEquals("0xa5", 0xA5.toByte().toHexText())
    }

    @Test
    fun `hex text reads back to the same bytes in either case`() {
        val expected = byteArrayOf(0x00, 0x0F, 0x80.toByte(), 0xFF.toByte())

        assertContentEquals(expected, "0x00 0x0f 0x80 0xFF".convertHexToByteArray())
        assertContentEquals(expected, byteArrayOf(0x00, 0x0F, 0x80.toByte(), 0xFF.toByte()).convertBytesToHex().convertHexToByteArray())
    }

    @Test
    fun `an odd number of hex digits gives no bytes`() {
        assertEquals(0, "0x0".convertHexToByteArray().size)
    }

    @Test
    fun `an alarm id is a new canonical uuid each time`() {
        val first = generateUUID()
        val second = generateUUID()

        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(first), first)
        assertNotEquals(first, second)
    }
}
