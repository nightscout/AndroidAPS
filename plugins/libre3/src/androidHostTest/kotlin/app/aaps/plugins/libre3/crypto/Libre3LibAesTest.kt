package app.aaps.plugins.libre3.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The pairing block maker, against the published vectors.
 *
 * From LibreCRKit `Tests/LibreCRKitTests/LibAESTests.swift` at pin `a86b92f`. The last one is a
 * real capture from a live sensor, so it proves the whole chain and not just the arithmetic.
 */
class Libre3LibAesTest {

    private fun bytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun hex(data: ByteArray): String = data.joinToString("") { "%02x".format(it) }

    @Test
    fun `the working area has the size the routine expects`() {
        val context = Libre3LibAes.keySetup(ByteArray(16))

        assertThat(context).hasLength(Libre3LibAes.CONTEXT_SIZE)
    }

    @Test
    fun `a key of the wrong size is refused`() {
        assertThrows<Libre3CryptoException> { Libre3LibAes.keySetup(ByteArray(15)) }
        assertThrows<Libre3CryptoException> { Libre3LibAes.keySetup(ByteArray(32)) }
    }

    @Test
    fun `the three published block vectors are produced byte for byte`() {
        val vectors = listOf(
            Triple(ByteArray(16), ByteArray(16), "6b9bddb402786cba9adac3304b86028b"),
            Triple(
                ByteArray(16) { it.toByte() },
                ByteArray(16) { it.toByte() },
                "aa4454e26d649350498357b4ce2596ed",
            ),
            Triple(
                bytes("3b16168843c299ad7fa311ba2440d58a"),
                bytes("07210400008f8c4b0000000000000001"),
                "c4ccfb387363f51bf61df08fc6d39304",
            ),
        )

        for ((key, plaintext, expected) in vectors) {
            val context = Libre3LibAes.keySetup(key)

            assertThat(hex(Libre3LibAes.encryptBlock(plaintext, context))).isEqualTo(expected)
        }
    }

    @Test
    fun `a live capture is reproduced exactly`() {
        // A real Phase 5 message: the key, the nonce and the plain text of a sensor that paired.
        val key = bytes("3b16168843c299ad7fa311ba2440d58a")
        val nonce = bytes("210400008f8c4b")
        val plaintext = bytes("8d2f296f882c1c0991d0e38c097892288c5b0b7441a7486d930806db08acdf1e3225ec72")

        val sealed = Libre3AesCcm.encrypt(nonce, plaintext, tagLength = 4, aes = Libre3LibAes.blockMaker(key))

        assertThat(hex(sealed.ciphertext + sealed.tag))
            .isEqualTo("49e3d257fb4fe91267cd1303cfab012ca215375f94040f8e9340a139de69720a88dc15dd50d3931a")
    }

    @Test
    fun `what this block maker sealed can be read back with the same key`() {
        val key = bytes("3b16168843c299ad7fa311ba2440d58a")
        val nonce = bytes("210400008f8c4b")
        val plaintext = bytes("8d2f296f882c1c0991d0e38c097892288c5b0b7441a7486d930806db08acdf1e3225ec72")
        val aes = Libre3LibAes.blockMaker(key)

        val sealed = Libre3AesCcm.encrypt(nonce, plaintext, tagLength = 4, aes = aes)
        val recovered = Libre3AesCcm.decrypt(nonce, sealed.ciphertext, sealed.tag, aes = aes)

        assertThat(hex(recovered)).isEqualTo(hex(plaintext))
    }

    @Test
    fun `this is not ordinary AES, and using ordinary AES would give a different answer`() {
        // The whole reason this port exists. A driver that quietly fell back to ordinary AES would
        // build pairing messages the sensor cannot read.
        val key = bytes("3b16168843c299ad7fa311ba2440d58a")
        val block = bytes("07210400008f8c4b0000000000000001")

        val fromSensorRoutine = Libre3LibAes.blockMaker(key).encryptBlock(block)
        val fromOrdinaryAes = Libre3AesCcm.standardAes(key).encryptBlock(block)

        assertThat(hex(fromSensorRoutine)).isNotEqualTo(hex(fromOrdinaryAes))
    }

    @Test
    fun `a block of the wrong size is refused`() {
        val context = Libre3LibAes.keySetup(ByteArray(16))

        assertThrows<Libre3CryptoException> { Libre3LibAes.encryptBlock(ByteArray(15), context) }
    }
}
