package app.aaps.desktop.shell.secure

import app.aaps.core.utils.toHex
import app.aaps.implementation.logging.AAPSLoggerDesktop
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Moving the old plain key files into the OS vault. A key file is deleted only when the vault is known
 * to hold exactly the same key; in every other case it stays.
 */
class LegacyKeyFilesTest {

    @TempDir lateinit var dir: File
    private val log = AAPSLoggerDesktop()
    private val key = ByteArray(32) { it.toByte() }

    private fun keyFile(alias: String, bytes: ByteArray = key) = File(dir, alias).apply { writeText(bytes.toHex()) }

    @Test fun `a key file is moved into the vault and deleted`() {
        val file = keyFile("NsClientControlSecret")
        val vault = FakeKeyVault()

        LegacyKeyFiles.migrate(dir, vault, log)

        assertArrayEquals(key, vault.keys["NsClientControlSecret"])
        assertFalse(file.exists())
    }

    @Test fun `a key the vault already holds is only deleted from the folder`() {
        val file = keyFile("preferences")
        val vault = FakeKeyVault().apply { keys["preferences"] = key.copyOf() }

        LegacyKeyFiles.migrate(dir, vault, log)

        assertFalse(file.exists())
    }

    @Test fun `a different key in the vault keeps the file`() {
        val file = keyFile("preferences")
        val vault = FakeKeyVault().apply { keys["preferences"] = ByteArray(32) { 7 } }

        LegacyKeyFiles.migrate(dir, vault, log)

        assertTrue(file.exists())
        assertArrayEquals(ByteArray(32) { 7 }, vault.keys["preferences"])
    }

    @Test fun `a vault that does not keep the key keeps the file`() {
        val file = keyFile("preferences")

        LegacyKeyFiles.migrate(dir, FakeKeyVault().apply { forgetsWrites = true }, log)

        assertTrue(file.exists())
    }

    @Test fun `a vault that does not answer keeps the file`() {
        val file = keyFile("preferences")

        LegacyKeyFiles.migrate(dir, FakeKeyVault().apply { failure = KeyVaultUnavailableException("locked") }, log)

        assertTrue(file.exists())
    }

    @Test fun `nothing moves when the vault is the file vault`() {
        val file = keyFile("preferences")

        LegacyKeyFiles.migrate(dir, FakeKeyVault(isOsBacked = false), log)

        assertTrue(file.exists())
    }

    @Test fun `DPAPI blobs and half written files are left alone`() {
        val blob = File(dir, "preferences${WindowsDpapiKeyVault.BLOB_SUFFIX}").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val temp = File(dir, "preferences.new").apply { writeText(key.toHex()) }
        val vault = FakeKeyVault()

        LegacyKeyFiles.migrate(dir, vault, log)

        assertTrue(blob.exists())
        assertTrue(temp.exists())
        assertTrue(vault.keys.isEmpty())
    }
}
