package app.aaps.plugins.eversense

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.plugins.eversense.storage.EversenseStore
import kotlin.concurrent.Volatile

/** The driver's store, in memory. A commit writes the changes of one editor in the order they were made. */
internal class MemoryStore : EversenseStore {

    /** What is stored, by key. Safe from several threads: a test in real time writes it while the driver reads. */
    val values: MutableMap<String, Any> = LockedMap()

    /** How many edits were committed, for a test that checks that nothing was written. */
    @Volatile var commits = 0
        private set

    override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
    override fun getLong(key: String, defaultValue: Long) = (values[key] as? Number)?.toLong() ?: defaultValue
    override fun getInt(key: String, defaultValue: Int) = (values[key] as? Number)?.toInt() ?: defaultValue
    override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
    override fun contains(key: String) = key in values
    override fun edit(): EversenseStore.Editor = object : EversenseStore.Editor {
        private val changes = mutableListOf<() -> Unit>()
        override fun putString(key: String, value: String?) = apply { changes += { if (value == null) values.remove(key) else values[key] = value } }
        override fun putLong(key: String, value: Long) = apply { changes += { values[key] = value } }
        override fun putInt(key: String, value: Int) = apply { changes += { values[key] = value } }
        override fun putBoolean(key: String, value: Boolean) = apply { changes += { values[key] = value } }
        override fun remove(key: String) = apply { changes += { values.remove(key) } }
        override fun clear() = apply { changes += { values.clear() } }
        override fun commit(): Boolean {
            changes.forEach { it() }
            commits++
            return true
        }

        override fun apply() {
            commit()
        }
    }

    /** A map behind one lock; the views it hands out are copies, so a reader never sees a change half done. */
    private class LockedMap : MutableMap<String, Any> {

        private val lock = AapsLock()
        private val map = HashMap<String, Any>()

        override val size: Int get() = lock.withLock { map.size }
        override fun isEmpty(): Boolean = lock.withLock { map.isEmpty() }
        override fun containsKey(key: String): Boolean = lock.withLock { map.containsKey(key) }
        override fun containsValue(value: Any): Boolean = lock.withLock { map.containsValue(value) }
        override fun get(key: String): Any? = lock.withLock { map[key] }
        override val keys: MutableSet<String> get() = lock.withLock { map.keys.toMutableSet() }
        override val values: MutableCollection<Any> get() = lock.withLock { map.values.toMutableList() }
        override val entries: MutableSet<MutableMap.MutableEntry<String, Any>> get() = lock.withLock { HashMap(map).entries }
        override fun put(key: String, value: Any): Any? = lock.withLock { map.put(key, value) }
        override fun remove(key: String): Any? = lock.withLock { map.remove(key) }
        override fun putAll(from: Map<out String, Any>) = lock.withLock { map.putAll(from) }
        override fun clear() = lock.withLock { map.clear() }
        override fun toString(): String = lock.withLock { map.toString() }
    }
}
