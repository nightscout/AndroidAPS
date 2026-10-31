package app.aaps.core.interfaces.sharedPreferences

import androidx.annotation.StringRes

/**
 * Android's preference storage: everything in [KeyValueStore], plus naming a key by resource id.
 *
 * The string keyed half moved to [KeyValueStore] so the preference layer could become common. These
 * overloads stayed behind because a resource id only exists on Android, and because roughly three
 * hundred call sites are written this way - keeping them here means none of those had to change.
 */
interface SP : KeyValueStore {

    fun contains(resourceId: Int): Boolean
    fun remove(@StringRes resourceID: Int)

    fun getString(@StringRes resourceID: Int, defaultValue: String): String
    fun getStringOrNull(@StringRes resourceID: Int, defaultValue: String?): String?
    fun getBoolean(@StringRes resourceID: Int, defaultValue: Boolean): Boolean
    fun getDouble(@StringRes resourceID: Int, defaultValue: Double): Double
    fun getInt(@StringRes resourceID: Int, defaultValue: Int): Int
    fun getLong(@StringRes resourceID: Int, defaultValue: Long): Long

    fun putBoolean(@StringRes resourceID: Int, value: Boolean)
    fun putDouble(@StringRes resourceID: Int, value: Double)
    fun putLong(@StringRes resourceID: Int, value: Long)
    fun putInt(@StringRes resourceID: Int, value: Int)
    fun putString(@StringRes resourceID: Int, value: String)
}
