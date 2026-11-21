package app.aaps.plugins.eversense

import app.aaps.plugins.eversense.storage.EversenseStore
import java.util.concurrent.ConcurrentHashMap

/** The driver's store, in memory. A commit writes the changes of one editor in the order they were made. */
internal class MemoryStore : EversenseStore {

    val values: MutableMap<String, Any> = ConcurrentHashMap()

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
}
