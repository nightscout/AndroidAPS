package app.aaps.core.nssdk.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ClientControlPairingCryptoTest {

    @Test
    fun wrapUnwrapRoundtripsWithCorrectPin() {
        val plaintext = "the-secret-payload".toByteArray()
        val salt = ClientControlPairingCrypto.newSalt()
        val iv = ClientControlPairingCrypto.newIv()
        val wrapped = ClientControlPairingCrypto.wrap(plaintext, "12345678", salt, iv)
        val recovered = ClientControlPairingCrypto.unwrap(wrapped, "12345678", salt, iv)
        assertThat(recovered).isEqualTo(plaintext)
    }

    @Test
    fun unwrapReturnsNullForWrongPin() {
        val salt = ClientControlPairingCrypto.newSalt()
        val iv = ClientControlPairingCrypto.newIv()
        val wrapped = ClientControlPairingCrypto.wrap("hi".toByteArray(), "11111111", salt, iv)
        assertThat(ClientControlPairingCrypto.unwrap(wrapped, "22222222", salt, iv)).isNull()
    }

    @Test
    fun unwrapReturnsNullForTamperedCiphertext() {
        val salt = ClientControlPairingCrypto.newSalt()
        val iv = ClientControlPairingCrypto.newIv()
        val wrapped = ClientControlPairingCrypto.wrap("hi".toByteArray(), "12345678", salt, iv).copyOf()
        wrapped[0] = (wrapped[0].toInt() xor 0x01).toByte() // flip a bit in the ciphertext
        assertThat(ClientControlPairingCrypto.unwrap(wrapped, "12345678", salt, iv)).isNull()
    }

    @Test
    fun unwrapReturnsNullForWrongSalt() {
        val saltA = ClientControlPairingCrypto.newSalt()
        val saltB = ClientControlPairingCrypto.newSalt()
        val iv = ClientControlPairingCrypto.newIv()
        val wrapped = ClientControlPairingCrypto.wrap("hi".toByteArray(), "12345678", saltA, iv)
        assertThat(ClientControlPairingCrypto.unwrap(wrapped, "12345678", saltB, iv)).isNull()
    }

    @Test
    fun newPinIsEightDigits() {
        repeat(50) {
            val pin = ClientControlPairingCrypto.newPin()
            assertThat(pin).hasLength(8)
            assertThat(pin.all(Char::isDigit)).isTrue()
        }
    }

    @Test
    fun newSaltIs16Bytes() {
        assertThat(ClientControlPairingCrypto.newSalt()).hasLength(16)
    }

    @Test
    fun newIvIs12Bytes() {
        assertThat(ClientControlPairingCrypto.newIv()).hasLength(12)
    }

    @Test
    fun newSaltsAreUnique() {
        val a = ClientControlPairingCrypto.newSalt()
        val b = ClientControlPairingCrypto.newSalt()
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun newIvsAreUnique() {
        val a = ClientControlPairingCrypto.newIv()
        val b = ClientControlPairingCrypto.newIv()
        assertThat(a).isNotEqualTo(b)
    }
}
