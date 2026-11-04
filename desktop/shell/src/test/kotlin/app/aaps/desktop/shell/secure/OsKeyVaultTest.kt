package app.aaps.desktop.shell.secure

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID

/**
 * The real OS key stores, which the other tests replace with [FakeKeyVault].
 *
 * DPAPI runs on every Windows machine: it only writes a blob into a temporary folder. The Keychain and
 * the Secret Service write into the real login keychain or keyring of whoever runs the test, so they
 * only run when asked for: `AAPS_KEYVAULT_IT=1 ./gradlew :desktop:shell:test --tests "*OsKeyVaultTest*"`.
 * Each run uses its own namespace and deletes what it stored.
 */
class OsKeyVaultTest {

    @TempDir lateinit var dir: File
    private val os = System.getProperty("os.name").orEmpty()
    private val key = ByteArray(32) { (it * 7).toByte() }

    @Test fun `DPAPI keeps a key and gives it back`() {
        assumeTrue(os.lowercase().contains("win"), "Windows only")
        val vault = WindowsDpapiKeyVault(dir)

        vault.store("preferences", key)

        assertArrayEquals(key, vault.load("preferences"))
        val blob = File(dir, "preferences${WindowsDpapiKeyVault.BLOB_SUFFIX}").readBytes()
        assertFalse(blob.toList().windowed(key.size).any { it == key.toList() }, "the blob must not contain the key")
        assertTrue(vault.delete("preferences"))
        assertNull(vault.load("preferences"))
    }

    @Test fun `DPAPI answers no key for a blob made elsewhere`() {
        assumeTrue(os.lowercase().contains("win"), "Windows only")
        File(dir, "preferences${WindowsDpapiKeyVault.BLOB_SUFFIX}").writeBytes(ByteArray(200) { 1 })

        assertNull(WindowsDpapiKeyVault(dir).load("preferences"))
    }

    @Test fun `the key store of this OS keeps a key and gives it back`() {
        assumeTrue(System.getenv("AAPS_KEYVAULT_IT") == "1", "set AAPS_KEYVAULT_IT=1 to use the real key store")
        val vault = KeyVaults.osVaultFor(os, dir, "test-${UUID.randomUUID()}")
        assumeTrue(vault != null, "no key store for $os")
        vault!!

        try {
            assertNull(vault.load("preferences"))
            vault.store("preferences", key)
            assertArrayEquals(key, vault.load("preferences"))
            vault.store("preferences", key.reversedArray())
            assertArrayEquals(key.reversedArray(), vault.load("preferences"), "a second store must replace the first")
        } finally {
            vault.delete("preferences")
        }
        assertNull(vault.load("preferences"))
    }
}
