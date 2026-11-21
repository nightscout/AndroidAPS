package app.aaps.plugins.eversense.connection

import app.aaps.plugins.eversense.storage.EversenseStore
import kotlinx.coroutines.CancellationException

/**
 * The driver's store for work that can outlive its reason, as a cloud upload in flight while the user signs out: once
 * [valid] says no, every read and write fails, so the old work cannot bring back what the sign-out removed.
 */
internal class GuardedStore(private val delegate: EversenseStore, private val valid: () -> Boolean) : EversenseStore {

    private fun <T> guard(action: () -> T): T {
        if (!valid()) throw CancellationException("The Eversense work has ended")
        return action()
    }

    override fun getString(key: String, defaultValue: String?) = guard { delegate.getString(key, defaultValue) }
    override fun getLong(key: String, defaultValue: Long) = guard { delegate.getLong(key, defaultValue) }
    override fun getInt(key: String, defaultValue: Int) = guard { delegate.getInt(key, defaultValue) }
    override fun getBoolean(key: String, defaultValue: Boolean) = guard { delegate.getBoolean(key, defaultValue) }
    override fun contains(key: String) = guard { delegate.contains(key) }

    override fun edit(): EversenseStore.Editor {
        val editor = guard { delegate.edit() }
        return object : EversenseStore.Editor {
            override fun putString(key: String, value: String?) = apply { editor.putString(key, value) }
            override fun putLong(key: String, value: Long) = apply { editor.putLong(key, value) }
            override fun putInt(key: String, value: Int) = apply { editor.putInt(key, value) }
            override fun putBoolean(key: String, value: Boolean) = apply { editor.putBoolean(key, value) }
            override fun remove(key: String) = apply { editor.remove(key) }
            override fun clear() = apply { editor.clear() }
            override fun commit(): Boolean = guard { editor.commit() }
            override fun apply() {
                guard { editor.apply() }
            }
        }
    }
}
