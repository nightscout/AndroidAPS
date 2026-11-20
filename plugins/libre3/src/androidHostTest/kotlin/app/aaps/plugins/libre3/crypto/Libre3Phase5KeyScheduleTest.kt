package app.aaps.plugins.libre3.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The Phase 5 key schedule, against the published vectors.
 *
 * From LibreCRKit `Tests/LibreCRKitTests/Phase5KeyScheduleTests.swift` at pin `a86b92f`.
 */
class Libre3Phase5KeyScheduleTest {

    private fun bytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun hex(data: ByteArray): String = data.joinToString("") { "%02x".format(it) }

    @Test
    fun `all six published vectors are produced byte for byte`() {
        val vectors = listOf(
            "00".repeat(66) to "4facb8db3692f2714ebaea5f9ff22de6",
            ("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
                "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f4041") to
                "56120a7e63561935008ef24e76f45d2a",
            ("050404000106040200060403000402060006030600010305070302070500030500020" +
                "605020406000607050606000507060103030207040705040406030200060507") to
                "3b16168843c299ad7fa311ba2440d58a",
            ("02040702070305000006040006040206030003020605000105000301050000010202" +
                "0605060500000207040000060507020702060304070705050502060603000407") to
                "3b16168843c299ad7fa311ba2440d58a",
            ("070705010506010205030100000305050600030300060304010205050201050606000407" +
                "010102020704030606050705010404060101060507010404000200000204") to
                "3e2199e34b872cec7ea8b621542c77ff",
            ("040407050401050502030004070502010204000007030103030500070704060304" +
                "040404000206070605020103020500020404000203010107040404070403050004") to
                "83f168a697970f7288c8a0abd0d83fee",
        )

        for ((sourceHex, expected) in vectors) {
            val key = Libre3Phase5KeySchedule.deriveRawKey(bytes(sourceHex))

            assertThat(hex(key)).isEqualTo(expected)
        }
    }

    @Test
    fun `the key it makes is the size the block maker needs`() {
        val key = Libre3Phase5KeySchedule.deriveRawKey(ByteArray(66))

        assertThat(key).hasLength(16)
    }

    @Test
    fun `a source of the wrong size is refused`() {
        assertThrows<Libre3CryptoException> { Libre3Phase5KeySchedule.deriveRawKey(ByteArray(65)) }
        assertThrows<Libre3CryptoException> { Libre3Phase5KeySchedule.deriveRawKey(ByteArray(67)) }
    }

    @Test
    fun `the key it makes really drives the pairing block maker`() {
        // The third vector is the key of the live capture, so the two ports meet here: this
        // schedule makes the key, and the block maker turns it into the message that was seen.
        val source = bytes(
            "050404000106040200060403000402060006030600010305070302070500030500020" +
                "605020406000607050606000507060103030207040705040406030200060507"
        )
        val key = Libre3Phase5KeySchedule.deriveRawKey(source)
        val nonce = bytes("210400008f8c4b")
        val plaintext = bytes("8d2f296f882c1c0991d0e38c097892288c5b0b7441a7486d930806db08acdf1e3225ec72")

        val sealed = Libre3AesCcm.encrypt(nonce, plaintext, tagLength = 4, aes = Libre3LibAes.blockMaker(key))

        assertThat(hex(sealed.ciphertext + sealed.tag))
            .isEqualTo("49e3d257fb4fe91267cd1303cfab012ca215375f94040f8e9340a139de69720a88dc15dd50d3931a")
    }
}
