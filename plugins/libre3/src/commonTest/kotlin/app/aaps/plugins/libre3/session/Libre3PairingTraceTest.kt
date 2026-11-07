package app.aaps.plugins.libre3.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The pairing trace writes to `AndroidAPS.log`, which users send to other people, so in a user
 * build it must never write raw pairing bytes, only their length.
 */
class Libre3PairingTraceTest {

    private val value = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9A.toByte())

    @Test
    fun secretsAreOffInThisBuild() {
        assertFalse(Libre3PairingTrace.TRACE_SECRETS, "TRACE_SECRETS must stay false in any build given to users")
    }

    @Test
    fun radioBytesAreWrittenAsLengthOnly() {
        val lines = mutableListOf<String>()
        Libre3PairingTrace(nowMs = { 0L }, write = { lines += it }).bytes("sensor R1", value)
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("sensor R1 5 bytes, not shown"), lines[0])
        assertFalse(lines[0].contains("123456789a"), lines[0])
    }

    @Test
    fun secretBytesAreWrittenAsLengthOnly() {
        val lines = mutableListOf<String>()
        Libre3PairingTrace(nowMs = { 0L }, write = { lines += it }).secret("pairing key", value)
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("pairing key 5 bytes, not shown"), lines[0])
        assertFalse(lines[0].contains("123456789a"), lines[0])
    }
}
