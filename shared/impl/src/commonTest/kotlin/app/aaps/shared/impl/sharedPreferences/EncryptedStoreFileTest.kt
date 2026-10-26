package app.aaps.shared.impl.sharedPreferences

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The store on a real file, through DataStore's file storage - the path every platform runs, with only
 * the cipher replaced by [FakeStoreCipher]. Runs on the JVM, on Android and on the iOS simulator.
 */
@OptIn(ExperimentalAtomicApi::class)
class EncryptedStoreFileTest {

    private val fs = FileSystem.SYSTEM
    private val dir: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "aaps-store-test-${Random.nextLong().toULong()}"
    private val file: Path = dir / "preferences.store"
    private val failures = AtomicInt(0)
    private var job: Job? = null

    @AfterTest fun cleanUp() {
        close()
        fs.deleteRecursively(dir)
    }

    private fun open(cipher: FakeStoreCipher): EncryptedKeyValueStore {
        close()
        fs.createDirectories(dir)
        val newJob = SupervisorJob().also { job = it }
        return try {
            openEncryptedStore(file.toString(), cipher, emptyList(), CoroutineScope(Dispatchers.Default + newJob)) { failures.incrementAndFetch() }
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

    private fun bytes(): ByteArray = fs.read(file) { readByteArray() }

    private fun Throwable.hasCause(type: (Throwable) -> Boolean): Boolean = generateSequence(this) { it.cause }.any(type)

    @Test fun `values and their types survive a reload`() {
        val cipher = FakeStoreCipher()
        open(cipher).apply {
            putString("units", "mmol")
            putInt("dia", 5)
            putLong("since", 1_793_008_500_000L)
            putDouble("isf", 0.1)
            putBoolean("closed", true)
            flush()
        }

        val reloaded = open(cipher)

        assertEquals(mapOf("units" to "mmol", "dia" to 5, "since" to 1_793_008_500_000L, "isf" to 0.1, "closed" to true), reloaded.getAll())
    }

    @Test fun `no file opens as an empty store`() {
        assertEquals(emptyMap<String, Any>(), open(FakeStoreCipher()).getAll())
        assertFalse(fs.exists(file))
    }

    @Test fun `the file holds neither key names nor values in clear`() {
        open(FakeStoreCipher()).apply {
            putString("nsclient_token", "secret-token-value")
            flush()
        }

        val text = bytes().decodeToString()

        assertFalse(text.contains("nsclient_token"))
        assertFalse(text.contains("secret-token-value"))
    }

    @Test fun `a store made with another key is refused and left untouched`() {
        open(FakeStoreCipher(key = 0x11)).apply {
            putString("units", "mmol")
            flush()
        }
        close()
        val before = bytes()

        val thrown = assertFailsWith<Exception> { open(FakeStoreCipher(key = 0x22)) }

        assertTrue(thrown.hasCause { it is CorruptionException }, "was $thrown")
        assertContentEquals(before, bytes())
    }

    @Test fun `a cipher that cannot reach its key is not mistaken for an unreadable file`() {
        val cipher = FakeStoreCipher()
        open(cipher).apply {
            putString("units", "mmol")
            flush()
        }
        close()
        cipher.unavailable = IllegalStateException("the key store is locked")

        val thrown = assertFailsWith<Exception> { open(cipher) }

        assertFalse(thrown.hasCause { it is CorruptionException }, "was $thrown")
        assertTrue(thrown.hasCause { it.message == "the key store is locked" }, "was $thrown")
    }

    /**
     * What "Start fresh" does in the running app: the open fails, its DataStore is shut down, the file is
     * moved aside, and the same path is opened again in the same process. DataStore refuses a second
     * instance on a file while the first is alive, so this fails if the shut down is not complete.
     */
    @Test fun `after a failed open the same file can be opened again in the same process`() {
        open(FakeStoreCipher(key = 0x11)).apply {
            putString("units", "mmol")
            flush()
        }
        assertFailsWith<Exception> { open(FakeStoreCipher(key = 0x22)) }
        fs.atomicMove(file, dir / "preferences.store.unreadable")

        val fresh = open(FakeStoreCipher(key = 0x22))
        fresh.putString("units", "mgdl")
        fresh.flush()

        assertEquals(mapOf("units" to "mgdl"), fresh.getAll())
        assertTrue(fs.exists(file))
    }

    @Test fun `a save that cannot replace the file leaves it untouched and is reported`() {
        val cipher = FakeStoreCipher()
        val store = open(cipher)
        store.putString("units", "mmol")
        store.flush()
        val before = bytes()

        // A non-empty directory where DataStore writes its new content: every attempt fails with an
        // IOException, the same shape as a file that Windows keeps locked for a moment.
        val blocked = dir / "preferences.store.tmp"
        fs.createDirectories(blocked)
        fs.write(blocked / "occupied") { writeUtf8("x") }
        store.putString("units", "mgdl")
        store.flush()

        // At least once: the background save and this flush may both have run into the block.
        assertTrue(failures.load() > 0)
        assertContentEquals(before, bytes())
        assertEquals("mgdl", store.getString("units", ""))

        fs.deleteRecursively(blocked)
        store.putInt("dia", 5)
        store.flush()
        assertEquals(mapOf("units" to "mgdl", "dia" to 5), open(cipher).getAll())
    }
}
