package info.nightscout.androidaps.utils.sharedPreferences

/**
 * Created by adrian on 2019-12-23.
 */

interface SP {
    fun getAll(): Map<String, *>
    fun clear()
    fun contains(key: String): Boolean
    fun contains(resourceId: Int): Boolean
    fun remove(resourceID: Int)
    fun remove(key: String)
    fun getString(resourceID: Int, defaultValue: String): String
    fun getString(key: String, defaultValue: String): String
    fun getBoolean(resourceID: Int, defaultValue: Boolean): Boolean
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getDouble(resourceID: Int, defaultValue: Double): Double
    fun getDouble(key: String, defaultValue: Double): Double
    fun getInt(resourceID: Int, defaultValue: Int): Int
    fun getInt(key: String, defaultValue: Int): Int
    fun getLong(resourceID: Int, defaultValue: Long): Long
    fun getLong(key: String, defaultValue: Long): Long
    fun putBoolean(key: String, value: Boolean)
    fun putBoolean(resourceID: Int, value: Boolean)
    fun putDouble(key: String, value: Double)
    fun putLong(key: String, value: Long)
    fun putLong(resourceID: Int, value: Long)
    fun putInt(key: String, value: Int)
    fun putInt(resourceID: Int, value: Int)
    fun incInt(resourceID: Int)
    fun putString(resourceID: Int, value: String)
    fun putString(key: String, value: String)
}