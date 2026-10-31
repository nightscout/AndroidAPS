package app.aaps.desktop.shell.secure

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * All keys of a client in one item of the key store, so that macOS asks once per update instead of once
 * per key.
 */
class OneItemKeyVaultTest {

    private val inner = FakeKeyVault(promptsPerItem = true)
    private val vault = OneItemKeyVault(inner)
    private val storeKey = ByteArray(32) { it.toByte() }
    private val pairingKey = ByteArray(32) { (100 + it).toByte() }

    @Test fun `every key goes into one item of the key store`() {
        vault.store("preferences", storeKey)
        vault.store("NsClientControlSecret", pairingKey)

        assertEquals(setOf(OneItemKeyVault.ITEM_ALIAS), inner.keys.keys)
        val reopened = OneItemKeyVault(inner)
        assertArrayEquals(storeKey, reopened.load("preferences"))
        assertArrayEquals(pairingKey, reopened.load("NsClientControlSecret"))
    }

    @Test fun `the item is read from the key store once`() {
        vault.store("preferences", storeKey)
        vault.store("NsClientControlSecret", pairingKey)
        val reopened = OneItemKeyVault(inner)
        inner.loads = 0

        reopened.load("preferences")
        reopened.load("NsClientControlSecret")
        reopened.load("preferences")

        assertEquals(1, inner.loads)
    }

    @Test fun `a key an earlier build stored in its own item is moved in`() {
        inner.keys["preferences"] = storeKey.copyOf()
        inner.keys["NsClientControlSecret"] = pairingKey.copyOf()

        assertArrayEquals(storeKey, vault.load("preferences"))
        assertArrayEquals(pairingKey, vault.load("NsClientControlSecret"))

        assertEquals(setOf(OneItemKeyVault.ITEM_ALIAS), inner.keys.keys)
        assertArrayEquals(pairingKey, OneItemKeyVault(inner).load("NsClientControlSecret"))
    }

    @Test fun `a missing key is null and makes no item`() {
        assertNull(vault.load("preferences"))
        assertTrue(inner.keys.isEmpty())
    }

    @Test fun `a deleted key stays deleted, also when an earlier build left its own item`() {
        vault.store("preferences", storeKey)
        vault.store("NsClientControlSecret", pairingKey)
        inner.keys["preferences"] = byteArrayOf(9, 9, 9)

        assertTrue(vault.delete("preferences"))

        assertNull(vault.load("preferences"))
        assertNull(OneItemKeyVault(inner).load("preferences"))
        assertArrayEquals(pairingKey, OneItemKeyVault(inner).load("NsClientControlSecret"))
        assertFalse(vault.delete("preferences"))
    }

    @Test fun `a key store that does not answer is passed on, not taken for no keys`() {
        inner.failure = KeyVaultUnavailableException("the user denied access")

        assertThrows<KeyVaultUnavailableException> { vault.load("preferences") }
    }

    @Test fun `an item it cannot read is an error, not an empty vault`() {
        inner.keys[OneItemKeyVault.ITEM_ALIAS] = "something else".encodeToByteArray()

        assertThrows<KeyVaultUnavailableException> { vault.load("preferences") }
    }

    @Test fun `an alias that would break the item is refused`() {
        assertThrows<IllegalArgumentException> { vault.store("a=b", storeKey) }
        assertThrows<IllegalArgumentException> { vault.store(OneItemKeyVault.ITEM_ALIAS, storeKey) }
    }
}
