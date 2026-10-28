package app.aaps.plugins.dexcomoneplus.identity

/**
 * The small private key-value file one sensor slot keeps for itself.
 *
 * It is deliberately not the application's own settings: the pairing code and the KEKS key stay
 * local and never leave in an exported preference file. On Android it is a private
 * `SharedPreferences` file, which is what this shape is drawn from; another platform may store it
 * however it likes as long as a value written is the same value read back.
 *
 * Writing a null string removes the key, exactly as `SharedPreferences` does, and
 * [OnePlusSensorStore] relies on that when a sensor has no serial.
 */
interface OnePlusSensorPrefs {

    fun getString(key: String): String?

    fun getLong(key: String, defaultValue: Long): Long

    fun getInt(key: String, defaultValue: Int): Int

    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    fun contains(key: String): Boolean

    /**
     * Applies every change made in [block] together.
     *
     * One call, one write: several keys that belong to the same decision must not be able to land
     * half way, which has already cost a sensor its correct age once.
     */
    fun edit(block: Editor.() -> Unit)

    /** Collects the changes of one [edit] call. */
    interface Editor {

        /** A null [value] removes the key. */
        fun putString(key: String, value: String?)

        fun putLong(key: String, value: Long)

        fun putInt(key: String, value: Int)

        fun putBoolean(key: String, value: Boolean)

        fun remove(key: String)

        fun clear()
    }
}
