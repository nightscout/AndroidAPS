package app.aaps.plugins.eversense.storage

/** Device state storage. The host owns persistence, encryption and settings import rules. */
interface EversenseStore {
    fun getString(key: String, defaultValue: String?): String?
    fun getLong(key: String, defaultValue: Long): Long
    fun getInt(key: String, defaultValue: Int): Int
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun contains(key: String): Boolean
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putLong(key: String, value: Long): Editor
        fun putInt(key: String, value: Int): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun commit(): Boolean
        fun apply()
    }
}

inline fun EversenseStore.edit(commit: Boolean = false, block: EversenseStore.Editor.() -> Unit) {
    val editor = edit()
    editor.block()
    if (commit) editor.commit() else editor.apply()
}
