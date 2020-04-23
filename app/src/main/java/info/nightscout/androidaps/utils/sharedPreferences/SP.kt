package info.nightscout.androidaps.utils.sharedPreferences

import androidx.annotation.StringRes

/**
 * Created by adrian on 2019-12-23.
 */

interface SP {

    fun getAll(): Map<String, *>
    fun clear()
    fun contains(key: String): Boolean
    fun contains(resourceId: Int): Boolean
    fun remove(@StringRes resourceID: Int)
    fun remove(key: String)
    fun getString(@StringRes resourceID: Int, defaultValue: String): String
    fun getStringOrNull(@StringRes resourceID: Int, defaultValue: String?): String?
    fun getStringOrNull(key: String, defaultValue: String?): String?
    fun getString(key: String, defaultValue: String): String
    fun getBoolean(@StringRes resourceID: Int, defaultValue: Boolean): Boolean
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getDouble(@StringRes resourceID: Int, defaultValue: Double): Double
    fun getDouble(key: String, defaultValue: Double): Double
    fun getInt(@StringRes resourceID: Int, defaultValue: Int): Int
    fun getInt(key: String, defaultValue: Int): Int
    fun getLong(@StringRes resourceID: Int, defaultValue: Long): Long
    fun getLong(key: String, defaultValue: Long): Long
    fun putBoolean(key: String, value: Boolean)
    fun putBoolean(@StringRes resourceID: Int, value: Boolean)
    fun putDouble(key: String, value: Double)
    fun putDouble(@StringRes resourceID: Int, value: Double)
    fun putLong(key: String, value: Long)
    fun putLong(@StringRes resourceID: Int, value: Long)
    fun putInt(key: String, value: Int)
    fun putInt(@StringRes resourceID: Int, value: Int)
    fun incInt(@StringRes resourceID: Int)
    fun putString(@StringRes resourceID: Int, value: String)
    fun putString(key: String, value: String)
}