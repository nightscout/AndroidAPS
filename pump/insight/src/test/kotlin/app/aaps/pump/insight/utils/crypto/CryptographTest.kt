package app.aaps.pump.insight.utils.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.spongycastle.crypto.encodings.OAEPEncoding
import org.spongycastle.crypto.engines.RSAEngine

/**
 * Covers [Cryptograph]: the pure hashing / key-derivation / Twofish-CTR-CCM / CRC helpers plus an
 * RSA generate→encrypt→decrypt round-trip. All deterministic (except RSA keygen), no Android deps.
 */
class CryptographTest {

    @Test
    fun combine_concatenatesArrays() {
        assertThat(Cryptograph.combine(byteArrayOf(1, 2), byteArrayOf(3, 4, 5)))
            .isEqualTo(byteArrayOf(1, 2, 3, 4, 5))
        // empty operands
        assertThat(Cryptograph.combine(byteArrayOf(), byteArrayOf(7))).isEqualTo(byteArrayOf(7))
    }

    @Test
    fun calculateCRC_emptyIsSeed_nonEmptyIsDeterministic() {
        assertThat(Cryptograph.calculateCRC(byteArrayOf())).isEqualTo(0xffff)
        val a = Cryptograph.calculateCRC(byteArrayOf(1, 2, 3, 4))
        assertThat(a).isEqualTo(Cryptograph.calculateCRC(byteArrayOf(1, 2, 3, 4))) // deterministic
        assertThat(a).isNotEqualTo(Cryptograph.calculateCRC(byteArrayOf(4, 3, 2, 1)))
    }

    @Test
    fun getServicePasswordHash_is16BytesAndDeterministic() {
        val salt = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val h1 = Cryptograph.getServicePasswordHash("password", salt)
        assertThat(h1.size).isEqualTo(16)
        assertThat(h1).isEqualTo(Cryptograph.getServicePasswordHash("password", salt))
        assertThat(h1).isNotEqualTo(Cryptograph.getServicePasswordHash("other", salt))
    }

    @Test
    fun deriveKeys_splitsKeysAndFormatsVerificationString() {
        val secret = ByteArray(32) { it.toByte() }
        val dk = Cryptograph.deriveKeys(
            verificationSeed = byteArrayOf(9, 8, 7, 6),
            secret = secret,
            random = byteArrayOf(1, 1, 1, 1),
            peerRandom = byteArrayOf(2, 2, 2, 2)
        )
        assertThat(dk.incomingKey.size).isEqualTo(16)
        assertThat(dk.outgoingKey.size).isEqualTo(16)
        // 10 table chars grouped 3-3-4 with two separating spaces (indices 3 and 7)
        assertThat(dk.verificationString.length).isEqualTo(12)
        assertThat(dk.verificationString[3]).isEqualTo(' ')
        assertThat(dk.verificationString[7]).isEqualTo(' ')
        // deterministic
        assertThat(dk.verificationString).isEqualTo(
            Cryptograph.deriveKeys(byteArrayOf(9, 8, 7, 6), secret, byteArrayOf(1, 1, 1, 1), byteArrayOf(2, 2, 2, 2)).verificationString
        )
    }

    @Test
    fun encryptDataCTR_keepsLengthAndIsDeterministic() {
        val key = ByteArray(16) { it.toByte() }
        val nonce = ByteArray(13) { (it + 1).toByte() }
        val data = byteArrayOf(10, 20, 30, 40, 50)
        val enc = Cryptograph.encryptDataCTR(data, key, nonce)
        assertThat(enc.size).isEqualTo(data.size)
        assertThat(enc).isEqualTo(Cryptograph.encryptDataCTR(data, key, nonce))
        assertThat(enc).isNotEqualTo(data) // actually transformed
    }

    @Test
    fun produceCCMTag_is8BytesAndDeterministic() {
        val key = ByteArray(16) { it.toByte() }
        val nonce = ByteArray(13) { (it + 1).toByte() }
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val header = byteArrayOf(9, 9)
        val tag = Cryptograph.produceCCMTag(nonce, payload, header, key)
        assertThat(tag.size).isEqualTo(8)
        assertThat(tag).isEqualTo(Cryptograph.produceCCMTag(nonce, payload, header, key))
    }

    @Test
    fun rsa_generateEncryptDecryptRoundTrip() {
        val kp = Cryptograph.generateRSAKey()
        assertThat(kp.publicKeyBytes.size).isEqualTo(256)
        val plain = "hello insight".toByteArray()
        val cipher = OAEPEncoding(RSAEngine()).apply { init(true, kp.publicKey) }
        val ciphertext = cipher.processBlock(plain, 0, plain.size)
        assertThat(Cryptograph.decryptRSA(kp.privateKey, ciphertext)).isEqualTo(plain)
    }
}
