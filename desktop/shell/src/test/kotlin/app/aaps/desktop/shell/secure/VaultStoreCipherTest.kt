package app.aaps.desktop.shell.secure

import app.aaps.core.interfaces.sharedPreferences.SealedDataUnreadableException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VaultStoreCipherTest {

    private val plain = "units=mmol".toByteArray()

    @Test fun `what is encrypted decrypts again and the key is made once`() {
        val vault = FakeKeyVault()
        val cipher = VaultStoreCipher(vault, "preferences")

        val first = cipher.encrypt(plain)
        val second = cipher.encrypt(plain)

        assertArrayEquals(plain, cipher.decrypt(first))
        assertArrayEquals(plain, VaultStoreCipher(vault, "preferences").decrypt(second))
        assertEquals(setOf("preferences"), vault.keys.keys)
        assertFalse(first.contentEquals(second), "every encryption needs its own IV")
    }

    @Test fun `reading with no key reports unreadable and makes no key`() {
        val sealed = VaultStoreCipher(FakeKeyVault(), "preferences").encrypt(plain)
        val emptyVault = FakeKeyVault()

        assertThrows<SealedDataUnreadableException> { VaultStoreCipher(emptyVault, "preferences").decrypt(sealed) }
        assertNull(emptyVault.keys["preferences"])
    }

    @Test fun `reading with another key reports unreadable`() {
        val sealed = VaultStoreCipher(FakeKeyVault(), "preferences").encrypt(plain)
        val otherVault = FakeKeyVault().apply { VaultStoreCipher(this, "preferences").encrypt(plain) }

        assertThrows<SealedDataUnreadableException> { VaultStoreCipher(otherVault, "preferences").decrypt(sealed) }
    }

    @Test fun `changed data reports unreadable`() {
        val vault = FakeKeyVault()
        val sealed = VaultStoreCipher(vault, "preferences").encrypt(plain)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()

        assertThrows<SealedDataUnreadableException> { VaultStoreCipher(vault, "preferences").decrypt(sealed) }
    }

    @Test fun `a key store that does not answer is passed on and is not called unreadable`() {
        val vault = FakeKeyVault()
        val sealed = VaultStoreCipher(vault, "preferences").encrypt(plain)
        vault.failure = KeyVaultUnavailableException("locked")

        assertThrows<KeyVaultUnavailableException> { VaultStoreCipher(vault, "preferences").decrypt(sealed) }
    }
}
