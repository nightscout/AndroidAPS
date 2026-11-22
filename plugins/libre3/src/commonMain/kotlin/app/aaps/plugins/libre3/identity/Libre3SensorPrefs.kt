package app.aaps.plugins.libre3.identity

/**
 * The small private key-value file one sensor slot keeps for itself.
 *
 * It is deliberately not the application's own settings: the PIN and the pairing key stay local
 * and never leave in an exported preference file. On Android it is a private `SharedPreferences`
 * file, which is what this shape is drawn from; another platform may store it however it likes as
 * long as a value written is the same value read back.
 *
 * Writing a null string removes the key, exactly as `SharedPreferences` does.
 *
 * ⚠️ The difference between [editAndWait] and [edit] is a safety rule of this driver, not a
 * performance choice. The PIN and the pairing key must really be on the disk before a connect
 * starts, so a crash can never leave a sensor that was taken over but whose key is lost. A
 * platform that made [editAndWait] return true without waiting would break that silently.
 */
interface Libre3SensorPrefs {

    fun getString(key: String): String?

    fun getInt(key: String, defaultValue: Int): Int

    fun getLong(key: String, defaultValue: Long): Long

    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    /**
     * Applies every change made in [block] together and waits for them to reach storage.
     *
     * One call, one write: several keys that belong to the same decision must not be able to land
     * half way.
     *
     * @return true only when the write really finished.
     */
    fun editAndWait(block: Editor.() -> Unit): Boolean

    /**
     * Applies every change made in [block] together, without waiting for the disk.
     *
     * Only for values where a lost write costs nothing. Everything a connect depends on uses
     * [editAndWait].
     */
    fun edit(block: Editor.() -> Unit)

    /** Collects the changes of one edit call. */
    interface Editor {

        /** A null [value] removes the key. */
        fun putString(key: String, value: String?)

        fun putInt(key: String, value: Int)

        fun putLong(key: String, value: Long)

        fun putBoolean(key: String, value: Boolean)

        fun remove(key: String)

        fun clear()
    }
}
