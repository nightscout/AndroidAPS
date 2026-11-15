package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock

/**
 * A buffer of records waiting to be written to the database, which guards its own mutations.
 *
 * This replaces `synchronized(theList) { theList.add(x) }`, which is JVM only. Each buffer keeps its
 * **own** lock rather than sharing one: incoming glucose values, treatments and foods arrive on
 * different websocket events and are drained by different pipelines, so a single shared lock would
 * make a long treatments sync block BG ingest. That is a real behaviour change, not a tidy-up, which
 * is why the granularity is preserved exactly.
 *
 * The lock is deliberately not exposed. Callers cannot hold it across two operations, so there is no
 * way to reintroduce the "check then act" race that a bare `MutableList` invites.
 */
internal class SyncedList<T> {

    private val lock = AapsLock()
    private val items: MutableList<T> = mutableListOf()

    fun add(item: T): Boolean = lock.withLock { items.add(item) }

    fun addAll(payload: Collection<T>): Boolean = lock.withLock { items.addAll(payload) }

    fun isEmpty(): Boolean = lock.withLock { items.isEmpty() }

    /**
     * A copy of what is in the buffer right now, without emptying it.
     *
     * A copy rather than the list itself, so a caller cannot read it while another thread is midway
     * through adding to it. Only tests need this - production code drains with [snapshotAndClear].
     */
    fun snapshot(): List<T> = lock.withLock { items.toList() }

    /**
     * Atomically copies the buffer and empties it, or returns null when there was nothing in it.
     *
     * One operation rather than two on purpose: anything arriving between a separate read and clear
     * would be dropped without ever reaching the database.
     */
    fun snapshotAndClear(): List<T>? = lock.withLock {
        if (items.isEmpty()) null
        else items.toList().also { items.clear() }
    }
}

/**
 * Per-type counts of what was stored, shown in the Nightscout client log.
 *
 * Replaces a `HashMap` guarded by `synchronized(this)` plus `Map.merge`, which is a JVM method with
 * no multiplatform equivalent. `add` accumulates exactly as `merge(key, amount, Int::plus)` did.
 */
internal class SyncedCounters {

    private val lock = AapsLock()
    private val values: MutableMap<String, Int> = mutableMapOf()

    fun add(key: String, amount: Int) = lock.withLock {
        values[key] = (values[key] ?: 0) + amount
    }

    operator fun get(key: String): Int? = lock.withLock { values[key] }

    fun removeClass(key: String) = lock.withLock { values.remove(key) }
}
