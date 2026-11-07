package app.aaps.plugins.eversense.connection

import app.aaps.plugins.eversense.storage.EversenseStore

/** Rejects state writes from HTTP/BLE work belonging to a closed session. */
internal class SessionStore(
    private val controller: EversenseConnectionController,
    private val session: Long,
    private val delegate: EversenseStore,
    private val valid: () -> Boolean = { true },
    private val cloud: Boolean = false
) : EversenseStore {
    fun restrict(valid: () -> Boolean) = SessionStore(controller, session, this, valid, cloud)

    private fun <T> guard(action: () -> T): T {
        val checked = {
            if (!valid()) throw java.util.concurrent.CancellationException("Eversense link has ended")
            action()
        }
        return if (cloud) controller.withCloudSession(session, checked) else controller.withSession(session, checked)
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
            override fun apply() { guard { editor.apply() } }
        }
    }
}
