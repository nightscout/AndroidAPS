package app.aaps.desktop.shell.secure

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The choice between the OS key store, the file fallback, and refusing to start.
 *
 * The line that matters most: a key store that exists but does not answer stops the start. Falling
 * back to a file there would make a second key next to the real one.
 */
class KeyVaultsTest {

    @TempDir lateinit var dir: File
    private var savedProperty: String? = null

    // The test task forces the file vault for every test; these tests are about what happens without it.
    @BeforeEach fun clearOverride() {
        savedProperty = System.getProperty(KeyVaults.VAULT_PROPERTY)
        System.clearProperty(KeyVaults.VAULT_PROPERTY)
    }

    @AfterEach fun restoreOverride() {
        savedProperty?.let { System.setProperty(KeyVaults.VAULT_PROPERTY, it) }
    }

    @Test fun `a working OS key store is used`() {
        val vault = FakeKeyVault()

        val choice = KeyVaults.pick("Linux", dir, "client1") { vault }

        assertSame(vault, choice.vault)
        assertNull(choice.fallbackReason)
        assertTrue(vault.keys.isEmpty(), "the self-test must leave nothing behind")
    }

    @Test fun `the system property forces the file vault`() {
        System.setProperty(KeyVaults.VAULT_PROPERTY, "file")

        val choice = KeyVaults.pick("Linux", dir, "client1") { error("must not be asked") }

        assertTrue(choice.vault is FileKeyVault)
        assertNotNull(choice.fallbackReason)
    }

    @Test fun `a missing native library falls back to files with a reason`() {
        val choice = KeyVaults.pick("Linux", dir, "client1") { throw UnsatisfiedLinkError("libsecret-1.so.0: not found") }

        assertTrue(choice.vault is FileKeyVault)
        assertTrue(choice.fallbackReason!!.contains("libsecret"))
    }

    @Test fun `no key store running falls back to files with a reason`() {
        val vault = FakeKeyVault().apply { failure = KeyVaultAbsentException("org.freedesktop.secrets was not provided") }

        val choice = KeyVaults.pick("Linux", dir, "client1") { vault }

        assertTrue(choice.vault is FileKeyVault)
        assertNotNull(choice.fallbackReason)
    }

    @Test fun `an OS without a supported key store falls back to files`() {
        val choice = KeyVaults.pick("FreeBSD", dir, "client1") { null }

        assertTrue(choice.vault is FileKeyVault)
        assertNotNull(choice.fallbackReason)
    }

    @Test fun `a key store that does not answer stops the start`() {
        val vault = FakeKeyVault().apply { failure = KeyVaultUnavailableException("keyring is locked") }

        assertThrows<KeyVaultUnavailableException> { KeyVaults.pick("Linux", dir, "client1") { vault } }
    }

    @Test fun `a key store that fails in another way also stops the start`() {
        val vault = FakeKeyVault().apply { failure = IllegalStateException("unexpected") }

        assertThrows<KeyVaultUnavailableException> { KeyVaults.pick("Linux", dir, "client1") { vault } }
    }

    @Test fun `a key store that loses what it stores stops the start`() {
        assertThrows<KeyVaultUnavailableException> { KeyVaults.pick("Linux", dir, "client1") { FakeKeyVault().apply { forgetsWrites = true } } }
        assertThrows<KeyVaultUnavailableException> { KeyVaults.pick("Linux", dir, "client1") { FakeKeyVault().apply { returnsOtherBytes = true } } }
    }

    @Test fun `each OS gets its own vault`() {
        assertEquals(WindowsDpapiKeyVault::class, KeyVaults.osVaultFor("Windows 11", dir, "client1")?.let { it::class })
        assertNull(KeyVaults.osVaultFor("SunOS", dir, "client1"))
    }
}
