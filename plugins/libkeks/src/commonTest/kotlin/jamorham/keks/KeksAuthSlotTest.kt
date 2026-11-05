package jamorham.keks

import jamorham.keks.crypto.KeksReplay
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The display slot byte at the end of the auth request.
 *
 * The phone path must stay the upstream byte (0x02, the phone slot). Only a set
 * [Plugin.authSlotOverride] may change it, and then only that last byte.
 */
class KeksAuthSlotTest {

    @AfterTest
    fun tidy() = KeksReplay.clear()

    private fun authRequest(override: Int?): ByteArray {
        val plugin = Plugin("1234")
        plugin.authSlotOverride = override
        // A saved key sends the plugin straight to the auth request, without the key exchange
        plugin.setPersistence(2, ByteArray(16) { it.toByte() })
        plugin.amConnected()
        KeksReplay.randomKeys.addLast(hexBytes("00112233445566778899aabbccddeeff"))
        val packets = assertNotNull(plugin.aNext())
        return assertNotNull(packets[0])
    }

    @Test
    fun phonePathKeepsTheUpstreamSlotByte() {
        assertEquals("02001122334455667702", authRequest(null).hex())
    }

    @Test
    fun watchSlotChangesOnlyTheLastByte() {
        val phone = authRequest(null)
        val watch = authRequest(3)
        assertEquals(phone.size, watch.size)
        assertEquals(3, watch.last().toInt())
        assertEquals(phone.copyOf(phone.size - 1).hex(), watch.copyOf(watch.size - 1).hex())
    }
}
