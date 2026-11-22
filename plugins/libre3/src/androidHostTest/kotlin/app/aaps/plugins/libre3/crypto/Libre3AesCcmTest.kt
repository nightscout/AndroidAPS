package app.aaps.plugins.libre3.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The published packet vector number one of RFC 3610, which is the same vector the upstream Swift
 * tests use (`Tests/LibreCRKitTests/AESCCMTests.swift`, pin `a86b92f`).
 */
class Libre3AesCcmTest {

    private fun bytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun hex(data: ByteArray): String = data.joinToString("") { "%02x".format(it) }

    private val key = bytes("c0c1c2c3c4c5c6c7c8c9cacbcccdcecf")
    private val nonce = bytes("00000003020100a0a1a2a3a4a5")
    private val aad = bytes("0001020304050607")
    private val plaintext = bytes("08090a0b0c0d0e0f101112131415161718191a1b1c1d1e")

    @Test
    fun `the published vector is produced byte for byte`() {
        val sealed = Libre3AesCcm.encrypt(nonce, plaintext, aad, tagLength = 8, aes = Libre3AesCcm.standardAes(key))

        assertThat(hex(sealed.ciphertext)).isEqualTo("588c979a61c663d2f066d0c2c0f989806d5f6b61dac384")
        assertThat(hex(sealed.tag)).isEqualTo("17e8d12cfdf926e0")
    }

    @Test
    fun `what was encrypted can be read back`() {
        val aes = Libre3AesCcm.standardAes(key)
        val sealed = Libre3AesCcm.encrypt(nonce, plaintext, aad, tagLength = 8, aes = aes)

        val recovered = Libre3AesCcm.decrypt(nonce, sealed.ciphertext, sealed.tag, aad, aes)

        assertThat(hex(recovered)).isEqualTo(hex(plaintext))
    }

    @Test
    fun `a changed message is refused`() {
        val aes = Libre3AesCcm.standardAes(key)
        val sealed = Libre3AesCcm.encrypt(nonce, plaintext, aad, tagLength = 8, aes = aes)
        val changed = sealed.ciphertext.copyOf()
        changed[0] = (changed[0].toInt() xor 1).toByte()

        assertThrows<Libre3CryptoException> { Libre3AesCcm.decrypt(nonce, changed, sealed.tag, aad, aes) }
    }

    @Test
    fun `a message read with the wrong key is refused`() {
        val sealed = Libre3AesCcm.encrypt(nonce, plaintext, aad, 8, Libre3AesCcm.standardAes(key))
        val otherKey = Libre3AesCcm.standardAes(ByteArray(16))

        assertThrows<Libre3CryptoException> { Libre3AesCcm.decrypt(nonce, sealed.ciphertext, sealed.tag, aad, otherKey) }
    }

    @Test
    fun `a tag length the standard does not allow is refused`() {
        assertThrows<Libre3CryptoException> {
            Libre3AesCcm.encrypt(nonce, plaintext, aad, tagLength = 5, aes = Libre3AesCcm.standardAes(key))
        }
    }

    @Test
    fun `the four byte tag used by this sensor works both ways`() {
        val aes = Libre3AesCcm.standardAes(key)
        val sealed = Libre3AesCcm.encrypt(nonce, plaintext, tagLength = 4, aes = aes)

        assertThat(sealed.tag).hasLength(4)
        assertThat(hex(Libre3AesCcm.decrypt(nonce, sealed.ciphertext, sealed.tag, aes = aes))).isEqualTo(hex(plaintext))
    }
}
