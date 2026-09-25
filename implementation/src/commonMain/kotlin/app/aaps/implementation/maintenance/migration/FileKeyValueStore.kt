package app.aaps.implementation.maintenance.migration

import app.aaps.core.interfaces.sharedPreferences.KeyValueStore

/**
 * A [KeyValueStore] over an import file, so [PreferenceMigrations] can run against it unchanged.
 *
 * This is the whole trick: the migrations already work: they just needed a second store to work on.
 * Start up hands them the device's store, an import hands them this one, and nothing about the
 * migrations differs between the two.
 *
 * Whatever is written here goes into the file's map and nowhere near the phone.
 *
 * ## Values arrive as text
 *
 * An export writes `value.toString()` for every entry, so a file holds `"true"` and `"45"` where the
 * device store holds a `Boolean` and an `Int`. The typed getters below therefore parse, and a value
 * that will not parse answers the caller's DEFAULT - which is indistinguishable from the key being
 * absent, because [KeyValueStore] has no way to say "present but unreadable".
 *
 * That is why the migrations read [getAll] and go through [LegacyPreferenceValue] for anything that
 * matters: it answers null for a value it cannot convert, so the caller skips the key and leaves it
 * alone. The typed getters are here to satisfy the interface and to serve presence checks, not to
 * make decisions.
 *
 * ## getAll returns a copy
 *
 * Each migration scans the store with [getAll] and writes to it while walking that scan, so the scan
 * has to be a copy. `SPImpl.getAll()` copies only because AOSP's `SharedPreferencesImpl` does; nothing
 * in the interface promises it. Copying here makes that a property of this class rather than a lucky
 * inheritance.
 */
class FileKeyValueStore(values: Map<String, Any?> = emptyMap()) : KeyValueStore {

    private val values: MutableMap<String, Any?> = values.toMutableMap()

    /** The file's contents after the migrations have run, ready to be applied. */
    fun asTextMap(): Map<String, String> =
        values.mapNotNull { (key, value) -> value?.let { key to it.toString() } }.toMap()

    override fun getAll(): Map<String, *> = values.toMap()
    override fun clear() = values.clear()
    override fun contains(key: String) = values.containsKey(key)
    override fun remove(key: String) { values.remove(key) }

    override fun getString(key: String, defaultValue: String): String = values[key]?.toString() ?: defaultValue
    override fun getStringOrNull(key: String, defaultValue: String?): String? = values[key]?.toString() ?: defaultValue
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = LegacyPreferenceValue.asBoolean(values[key]) ?: defaultValue
    override fun getDouble(key: String, defaultValue: Double): Double = LegacyPreferenceValue.asDouble(values[key]) ?: defaultValue
    override fun getInt(key: String, defaultValue: Int): Int = LegacyPreferenceValue.asLong(values[key])?.toInt() ?: defaultValue
    override fun getLong(key: String, defaultValue: Long): Long = LegacyPreferenceValue.asLong(values[key]) ?: defaultValue

    override fun putString(key: String, value: String) { values[key] = value }
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun putDouble(key: String, value: Double) { values[key] = value }
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun putLong(key: String, value: Long) { values[key] = value }

    override fun incInt(key: String) = putInt(key, getInt(key, 0) + 1)
    override fun incLong(key: String) = putLong(key, getLong(key, 0L) + 1L)

    override fun edit(commit: Boolean, block: KeyValueStore.Editor.() -> Unit) {
        object : KeyValueStore.Editor {
            override fun clear() = values.clear()
            override fun remove(key: String) { values.remove(key) }
            override fun putBoolean(key: String, value: Boolean) { values[key] = value }
            override fun putDouble(key: String, value: Double) { values[key] = value }
            override fun putLong(key: String, value: Long) { values[key] = value }
            override fun putInt(key: String, value: Int) { values[key] = value }
            override fun putString(key: String, value: String) { values[key] = value }
        }.block()
    }
}
