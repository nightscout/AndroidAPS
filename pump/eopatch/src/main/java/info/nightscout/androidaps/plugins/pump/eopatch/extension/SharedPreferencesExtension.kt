package info.nightscout.androidaps.plugins.pump.eopatch.extension

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * puts a key value pair in shared prefs if doesn't exists, otherwise updates value on given [key]
 */
operator fun SharedPreferences.set(key: String, commit: Boolean = false, value: Any?) {
    when (value) {
        is String? -> edit(commit) { putString(key, value) }
        is Int -> edit(commit) { putInt(key, value) }
        is Long -> edit(commit) { putLong(key, value) }
        is Float -> edit(commit) { putFloat(key, value) }
        is Boolean -> edit(commit) { putBoolean(key, value) }
        else -> throw UnsupportedOperationException("Not yet implemented")
    }
}

/**
 * finds value on given key.
 * [T] is the type of value
 * @param defaultValue optional default value - will take null for strings, false for bool and -1 for numeric values if [defaultValue] is not specified
 */
inline operator fun <reified T : Any> SharedPreferences.get(key: String, defaultValue: T? = null): T? {
    return when (T::class) {
        String::class -> getString(key, defaultValue as? String) as T?
        Int::class -> getInt(key, defaultValue as? Int ?: -1) as T?
        Long::class -> getLong(key, defaultValue as? Long ?: -1) as T?
        Float::class -> getFloat(key, defaultValue as? Float ?: -1f) as T?
        Boolean::class -> getBoolean(key, defaultValue as? Boolean ?: false) as T?
        else -> throw UnsupportedOperationException("Not yet implemented")
    }
}

fun SharedPreferences.getString(key: String): String? = this[key]
fun SharedPreferences.getInt(key: String): Int? = this[key]
fun SharedPreferences.getFloat(key: String): Float? = this[key]
fun SharedPreferences.getLong(key: String): Long? = this[key]
fun SharedPreferences.getBoolean(key: String): Boolean? = this[key]
