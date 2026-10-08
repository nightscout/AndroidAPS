package app.aaps.ios.shell.prefs

import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

/**
 * The iOS side of AAPS preference storage, backed by `NSUserDefaults`.
 *
 * `SP` is the only genuinely platform specific part of preferences. The layer above it,
 * `PreferencesImpl`, is 517 lines with no Android import in them: key lookup, defaults and range
 * clamping, all plain Kotlin. So iOS does not need another `Preferences`, it needs a store for that
 * one to sit on, which is this.
 *
 * ## What it implements
 *
 * [KeyValueStore], the platform neutral half of Android's `SP`. `SP` itself keeps the overloads
 * that name a key by Android resource id, which have no meaning here. Everything above this - the
 * whole `Preferences` layer - is common code and talks to this interface.
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
    /**
     * The preference domain this store owns, by default the app's own.
     *
     * [getAll] and [clear] are scoped to it. Reading and writing single keys goes through
     * [defaults] as usual, but those two need to know which keys are AAPS's, and
     * `dictionaryRepresentation()` cannot tell them: it merges the app's keys with NSGlobalDomain
     * and the system defaults. A `clear` built on it would walk that merged map and delete the
     * device's own settings.
     */
    private val domain: String = NSBundle.mainBundle.bundleIdentifier ?: FALLBACK_DOMAIN,
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : KeyValueStore {

    /** Every key and value this app has stored. System owned keys are not included. */
    override fun getAll(): Map<String, Any?> =
        defaults.persistentDomainForName(domain).orEmpty().entries.mapNotNull { (k, v) ->
            (k as? String)?.let { it to v }
        }.toMap()

    /** Removes only what AAPS wrote. Keys owned by the system stay. */
    override fun clear() = defaults.removePersistentDomainForName(domain)

    override fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    override fun remove(key: String) = defaults.removeObjectForKey(key)

    override fun getString(key: String, defaultValue: String): String =
        defaults.stringForKey(key) ?: defaultValue

    override fun getStringOrNull(key: String, defaultValue: String?): String? =
        defaults.stringForKey(key) ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        if (contains(key)) defaults.boolForKey(key) else defaultValue

    override fun getDouble(key: String, defaultValue: Double): Double =
        if (contains(key)) defaults.doubleForKey(key) else defaultValue

    override fun getInt(key: String, defaultValue: Int): Int =
        if (contains(key)) defaults.integerForKey(key).toInt() else defaultValue

    override fun getLong(key: String, defaultValue: Long): Long =
        if (contains(key)) defaults.integerForKey(key) else defaultValue

    override fun putString(key: String, value: String) = defaults.setObject(value, key)

    override fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, key)

    override fun putDouble(key: String, value: Double) = defaults.setDouble(value, key)

    override fun putInt(key: String, value: Int) = defaults.setInteger(value.toLong(), key)

    override fun putLong(key: String, value: Long) = defaults.setInteger(value, key)

    override fun incInt(key: String) = putInt(key, getInt(key, 0) + 1)

    override fun incLong(key: String) = putLong(key, getLong(key, 0L) + 1L)

    /**
     * Runs [block] as one edit.
     *
     * `SP.edit` exists on Android to avoid an `apply()` per write. `NSUserDefaults` already batches
     * writes to disk itself, so there is nothing to defer and the writes simply happen. The shape is
     * kept so that call sites written against `SP` do not have to change.
     */
    override fun edit(commit: Boolean, block: KeyValueStore.Editor.() -> Unit) {
        Editor(this).block()
    }

    /** The batching handle [edit] hands out. Writes straight through, as explained on [edit]. */
    class Editor(private val sp: IosSp) : KeyValueStore.Editor {

        override fun clear() = sp.clear()
        override fun remove(key: String) = sp.remove(key)
        override fun putBoolean(key: String, value: Boolean) = sp.putBoolean(key, value)
        override fun putDouble(key: String, value: Double) = sp.putDouble(key, value)
        override fun putLong(key: String, value: Long) = sp.putLong(key, value)
        override fun putInt(key: String, value: Int) = sp.putInt(key, value)
        override fun putString(key: String, value: String) = sp.putString(key, value)
    }

    companion object {

        /** Used only if the bundle has no identifier, which should not happen in a real app. */
        const val FALLBACK_DOMAIN: String = "app.aaps"
    }
}
