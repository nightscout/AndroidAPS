package app.aaps.shared.impl.sharedPreferences

import androidx.datastore.core.DataStore
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Where [EncryptedKeyValueStore] keeps its values on disk: [DataStoreBackend] in the app.
 *
 * An interface only because `DataStore` is an `expect` interface, which common test code cannot
 * safely implement. The tests use a map in memory instead.
 */
internal interface StoreBackend {

    suspend fun read(): Map<String, Any>
    suspend fun write(values: Map<String, Any>)
}

internal class DataStoreBackend(private val dataStore: DataStore<Map<String, Any>>) : StoreBackend {

    override suspend fun read(): Map<String, Any> = dataStore.data.first()

    // DataStore writes only when the value differs from what it holds, so saving an unchanged map
    // costs nothing.
    override suspend fun write(values: Map<String, Any>) {
        dataStore.updateData { values }
    }
}

/**
 * The preference store, kept in one encrypted file (see [StoreCodec]). Replaces `DesktopSp` and
 * `IosSp`, and is meant to replace `SPImpl` later.
 *
 * ## Reads and writes
 *
 * `Preferences.get()` is synchronous and called everywhere, so every value is held in memory and a read
 * never touches the disk. A write changes the memory at once and asks a background saver to write the
 * whole map. Requests are merged: ten writes in a row may become one save, and the order cannot go
 * wrong because a save always writes what is in memory at that moment. `edit(commit = true)` saves
 * before it returns, for values that must be on the disk before the caller goes on.
 *
 * ## A save that fails
 *
 * It is tried again, three times with a short wait. On Windows the virus scanner or the search indexer
 * often holds a file that was just written, and the move that replaces it fails for a few milliseconds
 * (`DesktopSp` hit this on 2026-10-14). A save that still fails is reported through [onSaveFailure].
 * The memory is right either way, and the next save writes everything again.
 *
 * ## A file that cannot be read
 *
 * [open] throws, and nothing is written. A store that started empty instead would overwrite the file
 * on its first save, and every setting would be gone without a word.
 */
@OptIn(ExperimentalAtomicApi::class)
class EncryptedKeyValueStore internal constructor(
    private val backend: StoreBackend,
    initial: Map<String, Any>,
    scope: CoroutineScope,
    private val onSaveFailure: (Throwable) -> Unit
) : KeyValueStore {

    private val values = AtomicReference(initial)
    private val saveRequests = Channel<Unit>(Channel.CONFLATED)
    private val saveMutex = Mutex()

    init {
        scope.launch { saveRequests.consumeEach { save() } }
    }

    /** Writes what is in memory now and returns when it is on the disk, or the save has failed. */
    fun flush() = runBlocking { save() }

    private suspend fun save() = saveMutex.withLock {
        var attempt = 0
        while (true) {
            try {
                backend.write(values.load())
                return@withLock
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (++attempt >= SAVE_ATTEMPTS) {
                    onSaveFailure(e)
                    return@withLock
                }
                // Doubling, and only between attempts, so the worst case stays about 60 ms.
                delay(RETRY_DELAY_MS shl (attempt - 1))
            }
        }
    }

    private fun change(transform: (Map<String, Any>) -> Map<String, Any>) {
        // A compare-and-set loop rather than a lock: `synchronized` is JVM only. Two writers racing on
        // the same moment both land; the one that lost the race recomputes from the winner's map.
        while (true) {
            val old = values.load()
            if (values.compareAndSet(old, transform(old))) break
        }
        saveRequests.trySend(Unit)
    }

    private fun raw(key: String): Any? = values.load()[key]

    override fun edit(commit: Boolean, block: KeyValueStore.Editor.() -> Unit) {
        // Collected first and applied as one change, so a reader never sees half of an edit.
        val operations = mutableListOf<(MutableMap<String, Any>) -> Unit>()
        object : KeyValueStore.Editor {
            override fun clear() { operations += { it.clear() } }
            override fun remove(key: String) { operations += { it.remove(key) } }
            override fun putBoolean(key: String, value: Boolean) { operations += { it[key] = value } }
            override fun putDouble(key: String, value: Double) { operations += { it[key] = value } }
            override fun putLong(key: String, value: Long) { operations += { it[key] = value } }
            override fun putInt(key: String, value: Int) { operations += { it[key] = value } }
            override fun putString(key: String, value: String) { operations += { it[key] = value } }
        }.block()
        change { old -> old.toMutableMap().also { map -> operations.forEach { it(map) } } }
        if (commit) flush()
    }

    override fun getAll(): Map<String, *> = values.load().toMap()
    override fun clear() = change { emptyMap() }
    override fun contains(key: String): Boolean = values.load().containsKey(key)
    override fun remove(key: String) = change { it - key }

    // Lenient on purpose, the way `DesktopSp` was: a value stored as text (every value a properties file
    // migrated) still reads as the type asked for, and a value that will not convert answers the
    // caller's default instead of zero.
    override fun getString(key: String, defaultValue: String): String = raw(key)?.toString() ?: defaultValue
    override fun getStringOrNull(key: String, defaultValue: String?): String? = raw(key)?.toString() ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = when (val value = raw(key)) {
        is Boolean -> value
        is String  -> value.toBooleanStrictOrNull() ?: defaultValue
        else       -> defaultValue
    }

    override fun getDouble(key: String, defaultValue: Double): Double = when (val value = raw(key)) {
        is Double -> value
        is Int    -> value.toDouble()
        is Long   -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: defaultValue
        else      -> defaultValue
    }

    override fun getInt(key: String, defaultValue: Int): Int = when (val value = raw(key)) {
        is Int    -> value
        is Long   -> if (value in Int.MIN_VALUE..Int.MAX_VALUE) value.toInt() else defaultValue
        is String -> value.toIntOrNull() ?: defaultValue
        else      -> defaultValue
    }

    override fun getLong(key: String, defaultValue: Long): Long = when (val value = raw(key)) {
        is Long   -> value
        is Int    -> value.toLong()
        is String -> value.toLongOrNull() ?: defaultValue
        else      -> defaultValue
    }

    override fun putString(key: String, value: String) = change { it + (key to value) }
    override fun putBoolean(key: String, value: Boolean) = change { it + (key to value) }
    override fun putDouble(key: String, value: Double) = change { it + (key to value) }
    override fun putInt(key: String, value: Int) = change { it + (key to value) }
    override fun putLong(key: String, value: Long) = change { it + (key to value) }

    // Read and write in one change, so two increments at the same moment both count.
    override fun incInt(key: String) = change { it + (key to (intOf(it[key]) + 1)) }
    override fun incLong(key: String) = change { it + (key to (longOf(it[key]) + 1L)) }

    private fun intOf(value: Any?): Int = when (value) {
        is Int    -> value
        is String -> value.toIntOrNull() ?: 0
        else      -> 0
    }

    private fun longOf(value: Any?): Long = when (value) {
        is Long   -> value
        is Int    -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else      -> 0L
    }

    companion object {

        /** How many times a save is tried before it is reported as failed. */
        const val SAVE_ATTEMPTS = 3

        /** The wait before the second attempt, doubled for each one after it. */
        const val RETRY_DELAY_MS = 20L

        /**
         * Reads [dataStore] once and returns the store over it.
         *
         * Throws what the read throws: a `CorruptionException` when the file cannot be read with this
         * device's key (see [StoreCodec.decode]), anything else when the key could not be reached.
         */
        fun open(dataStore: DataStore<Map<String, Any>>, scope: CoroutineScope, onSaveFailure: (Throwable) -> Unit): EncryptedKeyValueStore =
            open(DataStoreBackend(dataStore), scope, onSaveFailure)

        internal fun open(backend: StoreBackend, scope: CoroutineScope, onSaveFailure: (Throwable) -> Unit): EncryptedKeyValueStore =
            EncryptedKeyValueStore(backend, runBlocking { backend.read() }, scope, onSaveFailure)
    }
}
