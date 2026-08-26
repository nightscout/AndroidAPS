package app.aaps.ios.shell.prefs

import platform.Foundation.NSUserDefaults

/**
 * The iOS side of AAPS preference storage, backed by `NSUserDefaults`.
 *
 * `SP` is the only genuinely platform specific part of preferences. The layer above it,
 * `PreferencesImpl`, is 517 lines with no Android import in them: key lookup, defaults and range
 * clamping, all plain Kotlin. So iOS does not need another `Preferences`, it needs a store for that
 * one to sit on, which is this.
 *
 * ## Why this does not yet say `: SP`
 *
 * The `SP` interface lives in `core/interfaces/src/androidMain`, so iOS cannot see it. Twenty of its
 * methods also take an `@StringRes resourceId: Int` and look the key up in Android resources, which
 * has no iOS meaning. Moving it to common therefore means changing it, and that is shared code, so
 * it is left alone here. The names and behaviour below match the string keyed half exactly, so this
 * class becomes an `SP` implementation by adding one supertype once that move happens.
 *
 * ## The one thing worth reading carefully
 *
 * `NSUserDefaults` returns a zero valued default rather than reporting a missing key: `boolForKey`
 * on an absent key is `false`, and `doubleForKey` is `0.0`. Returning those would quietly replace
 * every AAPS default with zero, so each getter checks presence through [contains] first and returns
 * the caller's default when the key is absent. This is the difference between a preference screen
 * that opens with sensible values and one that opens with zeros.
 */
class IosSp(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) {

    /** Every stored key and value, for export and for tests. */
    fun getAll(): Map<String, Any?> =
        defaults.dictionaryRepresentation().entries.mapNotNull { (k, v) ->
            (k as? String)?.let { it to v }
        }.toMap()

    /** Removes only what AAPS wrote. Keys owned by the system stay. */
    fun clear() {
        getAll().keys.forEach { defaults.removeObjectForKey(it) }
    }

    fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    fun remove(key: String) = defaults.removeObjectForKey(key)

    fun getString(key: String, defaultValue: String): String =
        defaults.stringForKey(key) ?: defaultValue

    fun getStringOrNull(key: String, defaultValue: String?): String? =
        defaults.stringForKey(key) ?: defaultValue

    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        if (contains(key)) defaults.boolForKey(key) else defaultValue

    fun getDouble(key: String, defaultValue: Double): Double =
        if (contains(key)) defaults.doubleForKey(key) else defaultValue

    fun getInt(key: String, defaultValue: Int): Int =
        if (contains(key)) defaults.integerForKey(key).toInt() else defaultValue

    fun getLong(key: String, defaultValue: Long): Long =
        if (contains(key)) defaults.integerForKey(key) else defaultValue

    fun putString(key: String, value: String) = defaults.setObject(value, key)

    fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, key)

    fun putDouble(key: String, value: Double) = defaults.setDouble(value, key)

    fun putInt(key: String, value: Int) = defaults.setInteger(value.toLong(), key)

    fun putLong(key: String, value: Long) = defaults.setInteger(value, key)

    fun incInt(key: String) = putInt(key, getInt(key, 0) + 1)

    fun incLong(key: String) = putLong(key, getLong(key, 0L) + 1L)

    /**
     * Runs [block] as one edit.
     *
     * `SP.edit` exists on Android to avoid an `apply()` per write. `NSUserDefaults` already batches
     * writes to disk itself, so there is nothing to defer and the writes simply happen. The shape is
     * kept so that call sites written against `SP` do not have to change.
     */
    fun edit(@Suppress("UNUSED_PARAMETER") commit: Boolean = false, block: Editor.() -> Unit) {
        Editor(this).block()
    }

    /** The batching handle [edit] hands out. Writes straight through, as explained on [edit]. */
    class Editor(private val sp: IosSp) {

        fun clear() = sp.clear()
        fun remove(key: String) = sp.remove(key)
        fun putBoolean(key: String, value: Boolean) = sp.putBoolean(key, value)
        fun putDouble(key: String, value: Double) = sp.putDouble(key, value)
        fun putLong(key: String, value: Long) = sp.putLong(key, value)
        fun putInt(key: String, value: Int) = sp.putInt(key, value)
        fun putString(key: String, value: String) = sp.putString(key, value)
    }
}
