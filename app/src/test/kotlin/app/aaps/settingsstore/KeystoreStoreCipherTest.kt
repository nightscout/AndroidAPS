package app.aaps.settingsstore

import app.aaps.core.interfaces.sharedPreferences.SealedDataUnreadableException
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.KeyStoreException

class KeystoreStoreCipherTest {

    private val keys = MemoryKeys()
    private val sut = KeystoreStoreCipher(keys)

    @Test
    fun `what is encrypted opens again`() {
        val plain = "settings".encodeToByteArray()

        assertThat(sut.decrypt(sut.encrypt(plain))).isEqualTo(plain)
    }

    @Test
    fun `the sealed data starts with a fresh IV each time`() {
        val plain = "settings".encodeToByteArray()

        assertThat(sut.encrypt(plain).copyOf(12)).isNotEqualTo(sut.encrypt(plain).copyOf(12))
    }

    @Test
    fun `a write makes the key when there is none`() {
        sut.encrypt(byteArrayOf(1))

        assertThat(keys.key).isNotNull()
    }

    @Test
    fun `a read without a key is unreadable and makes no key`() {
        val sealed = KeystoreStoreCipher(MemoryKeys()).encrypt(byteArrayOf(1, 2, 3))

        assertThrows<SealedDataUnreadableException> { sut.decrypt(sealed) }
        assertThat(keys.key).isNull()
    }

    @Test
    fun `data sealed with another key is unreadable`() {
        val sealed = KeystoreStoreCipher(MemoryKeys()).encrypt(byteArrayOf(1, 2, 3))
        sut.encrypt(byteArrayOf(0))

        assertThrows<SealedDataUnreadableException> { sut.decrypt(sealed) }
    }

    @Test
    fun `changed data is unreadable`() {
        val sealed = sut.encrypt(byteArrayOf(1, 2, 3))
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()

        assertThrows<SealedDataUnreadableException> { sut.decrypt(sealed) }
    }

    @Test
    fun `data too short to hold an IV and a tag is unreadable`() {
        assertThrows<SealedDataUnreadableException> { sut.decrypt(ByteArray(20)) }
    }

    @Test
    fun `a Keystore that does not answer is not unreadable, and is tried three times`() {
        val sealed = KeystoreStoreCipher(keys).encrypt(byteArrayOf(1, 2, 3))
        keys.unavailable = KeyStoreException("busy")
        keys.loads = 0

        val thrown = assertThrows<KeyStoreException> { KeystoreStoreCipher(keys).decrypt(sealed) }

        assertThat(thrown.message).isEqualTo("busy")
        assertThat(keys.loads).isEqualTo(3)
    }

    @Test
    fun `a Keystore that answers on the second try is fine`() {
        val sealed = KeystoreStoreCipher(keys).encrypt(byteArrayOf(1, 2, 3))
        val flaky = object : KeystoreStoreCipher.Keys by keys {
            var failed = false
            override fun load() = if (failed) keys.load() else { failed = true; throw KeyStoreException("busy") }
        }

        assertThat(KeystoreStoreCipher(flaky).decrypt(sealed)).isEqualTo(byteArrayOf(1, 2, 3))
    }
}
