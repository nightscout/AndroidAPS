package app.aaps.desktop.shell.secure

import androidx.datastore.core.DataMigration
import app.aaps.implementation.logging.AAPSLoggerDesktop
import app.aaps.shared.impl.sharedPreferences.EncryptedKeyValueStore
import app.aaps.shared.impl.sharedPreferences.StoreUnreadableException
import app.aaps.shared.impl.sharedPreferences.openEncryptedStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The encrypted store on a real file with the desktop cipher, with only the key store replaced by
 * [FakeKeyVault]. What every platform shares - a reload, a refused file, a failed save - is tested in
 * `shared/impl` (`EncryptedStoreFileTest` there); this covers the desktop parts on top of it: how a
 * failed open is told apart for the start screen, and the move from the old properties file.
 */
class EncryptedStoreFileTest {

    @TempDir lateinit var dir: File
    private val log = AAPSLoggerDesktop()
    private val file get() = File(dir, "preferences.store")
    private val properties get() = File(dir, "preferences.properties")
    private var job: Job? = null

    @AfterEach fun closeStore() = close()

    private fun open(vault: KeyVault, migrations: List<DataMigration<Map<String, Any>>> = emptyList()): EncryptedKeyValueStore {
        close()
        val newJob = SupervisorJob().also { job = it }
        return try {
            openEncryptedStore(file.absolutePath, VaultStoreCipher(vault, "preferences"), migrations, CoroutineScope(Dispatchers.IO + newJob)) { }
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    /** DataStore allows one instance per file, so a store is shut down completely before the file is opened again. */
    private fun close() = runBlocking {
        job?.cancelAndJoin()
        job = null
    }

    private fun migration(vault: KeyVault) = PropertiesFileMigration(properties, file, VaultStoreCipher(vault, "preferences"), log)

    @Test fun `values and their types survive a reload with the real cipher`() {
        val vault = FakeKeyVault()
        open(vault).apply {
            putString("units", "mmol")
            putInt("dia", 5)
            putLong("since", 1_793_008_500_000L)
            putDouble("isf", 0.1)
            putBoolean("closed", true)
            flush()
        }

        val reloaded = open(vault)

        assertEquals(mapOf("units" to "mmol", "dia" to 5, "since" to 1_793_008_500_000L, "isf" to 0.1, "closed" to true), reloaded.getAll())
    }

    @Test fun `a store made with another key is shown as unreadable`() {
        open(FakeKeyVault()).apply {
            putString("units", "mmol")
            flush()
        }
        close()

        val thrown = assertThrows<Exception> { open(FakeKeyVault()) }

        assertTrue(DesktopStorage.classify(thrown) is StoreUnreadableException, "was $thrown")
    }

    @Test fun `a key store that does not answer is not mistaken for an unreadable file`() {
        val vault = FakeKeyVault()
        open(vault).apply {
            putString("units", "mmol")
            flush()
        }
        close()
        vault.failure = KeyVaultUnavailableException("keyring is locked")

        val thrown = assertThrows<Exception> { open(vault) }

        assertTrue(DesktopStorage.classify(thrown) is KeyVaultUnavailableException, "was $thrown")
    }

    @Test fun `the old properties file is moved in and deleted`() {
        properties.writeText("units=mmol\ndia=5\nclosed=true\n")
        val vault = FakeKeyVault()

        val store = open(vault, listOf(migration(vault)))

        assertEquals("mmol", store.getString("units", ""))
        assertEquals(5, store.getInt("dia", 0))
        assertTrue(store.getBoolean("closed", false))
        assertFalse(properties.exists())
        assertEquals("mmol", open(vault).getString("units", ""))
    }

    @Test fun `values already in the store win over the properties file`() {
        val vault = FakeKeyVault()
        open(vault).apply {
            putString("units", "mmol")
            flush()
        }
        // A start that was cut short after the store was written but before the old file was deleted.
        properties.writeText("units=mgdl\ndia=5\n")

        val store = open(vault, listOf(migration(vault)))

        assertEquals("mmol", store.getString("units", ""))
        assertEquals(5, store.getInt("dia", 0))
        assertFalse(properties.exists())
    }
}
