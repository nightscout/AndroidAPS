package app.aaps.shared.impl.sharedPreferences

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedKeyValueStoreTest {

    /** The disk, in memory. [failNext] makes that many writes throw, the way a locked file does on Windows. */
    private class MemoryBackend(var saved: Map<String, Any> = emptyMap()) : StoreBackend {

        var writes = 0
        var failNext = 0

        override suspend fun read(): Map<String, Any> = saved

        override suspend fun write(values: Map<String, Any>) {
            writes++
            if (failNext > 0) {
                failNext--
                throw IllegalStateException("file is locked")
            }
            saved = values
        }
    }

    private val failures = mutableListOf<Throwable>()

    private fun TestScope.store(backend: MemoryBackend) = EncryptedKeyValueStore.open(backend, backgroundScope) { failures += it }

    /**
     * Lets the background saver run, retries included. Not `advanceUntilIdle`: that one ignores work
     * started in `backgroundScope`, which is where the saver lives. A bounded window of virtual time
     * instead, so a saver that never stops cannot hang the test.
     */
    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    @Test
    fun `a value can be read at once and reaches the disk in the background`() = runTest {
        val backend = MemoryBackend()
        val store = store(backend)

        store.putString("units", "mmol")
        assertEquals("mmol", store.getString("units", "mgdl"))

        settle()
        assertEquals(mapOf<String, Any>("units" to "mmol"), backend.saved)
    }

    @Test
    fun `open reads what is on the disk`() = runTest {
        val store = store(MemoryBackend(mapOf("dia" to 5, "closed" to true)))

        assertEquals(5, store.getInt("dia", 0))
        assertTrue(store.getBoolean("closed", false))
        assertTrue(store.contains("dia"))
        assertFalse(store.contains("missing"))
    }

    @Test
    fun `commit is on the disk before edit returns`() = runTest {
        val backend = MemoryBackend()
        val store = store(backend)

        store.edit(commit = true) { putString("nonce", "42") }

        assertEquals(mapOf<String, Any>("nonce" to "42"), backend.saved)
    }

    @Test
    fun `an edit is applied whole and in order`() = runTest {
        val backend = MemoryBackend(mapOf("old" to "x"))
        val store = store(backend)

        store.edit {
            clear()
            putInt("dia", 5)
            putLong("since", 7L)
            putDouble("isf", 0.1)
            putBoolean("closed", true)
            putString("units", "mmol")
            remove("units")
        }
        settle()

        assertEquals(mapOf<String, Any>("dia" to 5, "since" to 7L, "isf" to 0.1, "closed" to true), backend.saved)
    }

    @Test
    fun `values written as text read as the type asked for`() = runTest {
        val store = store(MemoryBackend(mapOf("dia" to "5", "since" to "7", "isf" to "0.1", "closed" to "true")))

        assertEquals(5, store.getInt("dia", 0))
        assertEquals(7L, store.getLong("since", 0L))
        assertEquals(0.1, store.getDouble("isf", 0.0))
        assertTrue(store.getBoolean("closed", false))
    }

    @Test
    fun `text with a decimal comma or a Unicode minus reads the way the phone's old store read it`() = runTest {
        // The phone's old settings screens saved decimals as text, and a comma keyboard gave "0,5".
        val store = store(MemoryBackend(mapOf("maxBolus" to "0,5", "offset" to "−2", "dia" to "−3", "since" to "−7")))

        assertEquals(0.5, store.getDouble("maxBolus", 9.0))
        assertEquals(-2.0, store.getDouble("offset", 9.0))
        assertEquals(-3, store.getInt("dia", 9))
        assertEquals(-7L, store.getLong("since", 9L))
    }

    @Test
    fun `a value that does not convert gives the default and not zero`() = runTest {
        val store = store(MemoryBackend(mapOf("dia" to "not a number", "big" to Long.MAX_VALUE)))

        assertEquals(5, store.getInt("dia", 5))
        assertEquals(3, store.getInt("big", 3))
        assertEquals(1.5, store.getDouble("dia", 1.5))
        assertFalse(store.getBoolean("dia", false))
    }

    @Test
    fun `numbers read as other number types where nothing is lost`() = runTest {
        val store = store(MemoryBackend(mapOf("int" to 5, "long" to 6L)))

        assertEquals(5L, store.getLong("int", 0L))
        assertEquals(6, store.getInt("long", 0))
        assertEquals(5.0, store.getDouble("int", 0.0))
        assertEquals("5", store.getString("int", ""))
    }

    @Test
    fun `getStringOrNull gives the default only for a missing key`() = runTest {
        val store = store(MemoryBackend(mapOf("units" to "mmol")))

        assertEquals("mmol", store.getStringOrNull("units", null))
        assertNull(store.getStringOrNull("missing", null))
    }

    @Test
    fun `increments count from zero and keep counting`() = runTest {
        val store = store(MemoryBackend())

        store.incInt("starts")
        store.incInt("starts")
        store.incLong("counter")

        assertEquals(2, store.getInt("starts", 0))
        assertEquals(1L, store.getLong("counter", 0L))
    }

    @Test
    fun `remove and clear take values away`() = runTest {
        val backend = MemoryBackend(mapOf("a" to 1, "b" to 2))
        val store = store(backend)

        store.remove("a")
        assertEquals(mapOf<String, Any>("b" to 2), store.getAll())

        store.clear()
        settle()
        assertEquals(emptyMap<String, Any>(), store.getAll())
        assertEquals(emptyMap(), backend.saved)
    }

    @Test
    fun `a save that fails is tried again and is not reported when a retry works`() = runTest {
        val backend = MemoryBackend().apply { failNext = EncryptedKeyValueStore.SAVE_ATTEMPTS - 1 }
        val store = store(backend)

        store.putString("units", "mmol")
        settle()

        assertEquals(mapOf<String, Any>("units" to "mmol"), backend.saved)
        assertEquals(EncryptedKeyValueStore.SAVE_ATTEMPTS, backend.writes)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `a save that keeps failing is reported once and the next change saves everything`() = runTest {
        val backend = MemoryBackend().apply { failNext = EncryptedKeyValueStore.SAVE_ATTEMPTS }
        val store = store(backend)

        store.putString("units", "mmol")
        settle()
        assertEquals(1, failures.size)
        assertEquals(emptyMap(), backend.saved)
        // The memory is still right.
        assertEquals("mmol", store.getString("units", ""))

        store.putInt("dia", 5)
        settle()
        assertEquals(mapOf<String, Any>("units" to "mmol", "dia" to 5), backend.saved)
    }
}
