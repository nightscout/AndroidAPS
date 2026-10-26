package app.aaps.desktop.shell.secure

import androidx.datastore.core.DataMigration
import app.aaps.implementation.logging.AAPSLoggerDesktop
import app.aaps.shared.impl.sharedPreferences.EncryptedKeyValueStore
import app.aaps.shared.impl.sharedPreferences.openEncryptedStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The encrypted store on a real file, through DataStore's file storage and the desktop cipher - the
 * whole path the desktop runs, with only the key store replaced by [FakeKeyVault].
 */
class EncryptedStoreFileTest {

    @TempDir lateinit var dir: File
    private val log = AAPSLoggerDesktop()
    private val file get() = File(dir, "preferences.store")
    private val properties get() = File(dir, "preferences.properties")
    private val failures = mutableListOf<Throwable>()
    private var job: Job? = null

    @AfterEach fun closeStore() = close()

    private fun open(vault: KeyVault, migrations: List<DataMigration<Map<String, Any>>> = emptyList()): EncryptedKeyValueStore {
        close()
        val newJob = SupervisorJob().also { job = it }
        return try {
            openEncryptedStore(file, VaultStoreCipher(vault, "preferences"), migrations, CoroutineScope(Dispatchers.IO + newJob)) { failures += it }
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

    @Test fun `values and their types survive a reload`() {
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

    @Test fun `the file holds neither key names nor values in clear`() {
        open(FakeKeyVault()).apply {
            putString("nsclient_token", "secret-token-value")
            flush()
        }

        val text = file.readBytes().toString(Charsets.ISO_8859_1)

        assertFalse(text.contains("nsclient_token"))
        assertFalse(text.contains("secret-token-value"))
    }

    @Test fun `a store made with another key is refused and left untouched`() {
        open(FakeKeyVault()).apply {
            putString("units", "mmol")
            flush()
        }
        close()
        val before = file.readBytes()

        val thrown = assertThrows<Exception> { open(FakeKeyVault()) }

        assertTrue(DesktopStorage.classify(thrown) is StoreUnreadableException, "was $thrown")
        assertArrayEquals(before, file.readBytes())
    }

    /**
     * What "Start fresh" does in the running app: the open fails, its DataStore is shut down, the file is
     * moved aside, and the same path is opened again in the same process. DataStore refuses a second
     * instance on a file while the first is alive, so this fails if the shut down is not complete.
     */
    @Test fun `after a failed open the same file can be opened again in the same process`() {
        open(FakeKeyVault()).apply {
            putString("units", "mmol")
            flush()
        }
        assertThrows<Exception> { open(FakeKeyVault()) }
        assertTrue(file.renameTo(File(dir, "preferences.store.unreadable")))

        val fresh = open(FakeKeyVault())
        fresh.putString("units", "mgdl")
        fresh.flush()

        assertEquals(mapOf("units" to "mgdl"), fresh.getAll())
        assertTrue(file.exists())
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

    @Test fun `a save that cannot replace the file leaves it untouched and is reported`() {
        val vault = FakeKeyVault()
        val store = open(vault)
        store.putString("units", "mmol")
        store.flush()
        val before = file.readBytes()

        // A non-empty directory where DataStore writes its new content: every attempt fails with an
        // IOException, the same shape as a file that Windows keeps locked for a moment.
        val blocked = File(file.absolutePath + ".tmp").apply { mkdirs() }
        File(blocked, "occupied").writeText("x")
        store.putString("units", "mgdl")
        store.flush()

        // At least once: the background save and this flush may both have run into the block.
        assertTrue(failures.isNotEmpty())
        assertArrayEquals(before, file.readBytes())
        assertEquals("mgdl", store.getString("units", ""))

        blocked.deleteRecursively()
        store.putInt("dia", 5)
        store.flush()
        assertEquals(mapOf("units" to "mgdl", "dia" to 5), open(vault).getAll())
    }
}
