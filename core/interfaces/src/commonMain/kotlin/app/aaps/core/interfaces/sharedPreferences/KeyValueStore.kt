package app.aaps.core.interfaces.sharedPreferences

/**
 * Typed key/value storage, keyed by string.
 *
 * This is the half of `SP` that has no Android in it. `PreferencesImpl` is built on exactly these
 * calls and never names a resource id, so pulling them out is what lets that class - and the
 * preference layer above it - be common code.
 *
 * Android's `SP` extends this and adds overloads that take an `@StringRes` id, so the three hundred
 * odd call sites written that way keep compiling untouched. iOS implements this interface directly,
 * since a resource id means nothing there.
 *
 * Distinct from [app.aaps.core.keys.interfaces.Preferences], which is the layer above: `Preferences`
 * knows about typed keys, defaults and ranges, while this only stores values under names.
 */
interface KeyValueStore {

    // A helper Editor interface to distinguish its methods from the store's. The latter always
    // apply immediately. The whole point of edit() is to avoid unnecessary apply()/commit() calls,
    // so it cannot use the store's own put* methods.
    interface Editor {

        fun clear()
        fun remove(key: String)
        fun putBoolean(key: String, value: Boolean)
        fun putDouble(key: String, value: Double)
        fun putLong(key: String, value: Long)
        fun putInt(key: String, value: Int)
        fun putString(key: String, value: String)
    }

    fun edit(commit: Boolean = false, block: Editor.() -> Unit)

    fun getAll(): Map<String, *>
    fun clear()
    fun contains(key: String): Boolean
    fun remove(key: String)

    fun getString(key: String, defaultValue: String): String
    fun getStringOrNull(key: String, defaultValue: String?): String?
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getDouble(key: String, defaultValue: Double): Double
    fun getInt(key: String, defaultValue: Int): Int
    fun getLong(key: String, defaultValue: Long): Long

    fun putString(key: String, value: String)
    fun putBoolean(key: String, value: Boolean)
    fun putDouble(key: String, value: Double)
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)

    fun incInt(key: String)
    fun incLong(key: String)
}
