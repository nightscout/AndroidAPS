package app.aaps.di.metro

import app.aaps.core.interfaces.sharedPreferences.KeyValueStore

/**
 * A [KeyValueStore] kept in a map, for the app graph in plain JVM tests.
 *
 * Not a Mockito mock on purpose. The store is opened once per test run (see `AndroidStorage.useForTests`),
 * and Mockito clears its mocks after each test. A mock store then failed every later test whose graph
 * saves a setting while it is built ("Mock accessed after inline mocks were cleared").
 */
internal class MapKeyValueStore : KeyValueStore {

    private val values = mutableMapOf<String, Any>()

    private val editor = object : KeyValueStore.Editor {
        override fun clear() = values.clear()
        override fun remove(key: String) {
            values.remove(key)
        }

        override fun putBoolean(key: String, value: Boolean) {
            values[key] = value
        }

        override fun putDouble(key: String, value: Double) {
            values[key] = value
        }

        override fun putLong(key: String, value: Long) {
            values[key] = value
        }

        override fun putInt(key: String, value: Int) {
            values[key] = value
        }

        override fun putString(key: String, value: String) {
            values[key] = value
        }
    }

    @Synchronized override fun edit(commit: Boolean, block: KeyValueStore.Editor.() -> Unit) = editor.block()
    @Synchronized override fun getAll(): Map<String, *> = values.toMap()
    @Synchronized override fun clear() = values.clear()
    @Synchronized override fun contains(key: String): Boolean = key in values
    @Synchronized override fun remove(key: String) {
        values.remove(key)
    }

    @Synchronized override fun getString(key: String, defaultValue: String): String = values[key] as? String ?: defaultValue
    @Synchronized override fun getStringOrNull(key: String, defaultValue: String?): String? = values[key] as? String ?: defaultValue
    @Synchronized override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue
    @Synchronized override fun getDouble(key: String, defaultValue: Double): Double = values[key] as? Double ?: defaultValue
    @Synchronized override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue
    @Synchronized override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue

    @Synchronized override fun putString(key: String, value: String) = editor.putString(key, value)
    @Synchronized override fun putBoolean(key: String, value: Boolean) = editor.putBoolean(key, value)
    @Synchronized override fun putDouble(key: String, value: Double) = editor.putDouble(key, value)
    @Synchronized override fun putInt(key: String, value: Int) = editor.putInt(key, value)
    @Synchronized override fun putLong(key: String, value: Long) = editor.putLong(key, value)

    @Synchronized override fun incInt(key: String) = putInt(key, getInt(key, 0) + 1)
    @Synchronized override fun incLong(key: String) = putLong(key, getLong(key, 0L) + 1)
}
