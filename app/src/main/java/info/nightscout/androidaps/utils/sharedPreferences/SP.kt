package info.nightscout.androidaps.utils.sharedPreferences

import android.content.SharedPreferences
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.utils.SafeParse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by mike on 17.02.2017.
 */
@Singleton
class SP @Inject constructor(private val sharedPreferences: SharedPreferences) {

    fun getAll(): Map<String, *> = sharedPreferences.all

    fun clear() = sharedPreferences.edit().clear().apply()

    fun contains(key: String): Boolean = sharedPreferences.contains(key)

    fun contains(resourceId: Int): Boolean = sharedPreferences.contains(MainApp.gs(resourceId))

    fun remove(resourceID: Int) =
            sharedPreferences.edit().remove(MainApp.gs(resourceID)).apply()

    fun remove(key: String) =
            sharedPreferences.edit().remove(key).apply()

    fun getString(resourceID: Int, defaultValue: String): String =
            sharedPreferences.getString(MainApp.gs(resourceID), defaultValue)

    fun getString(key: String, defaultValue: String): String =
            sharedPreferences.getString(key, defaultValue)

    fun getBoolean(resourceID: Int, defaultValue: Boolean): Boolean {
        return try {
            sharedPreferences.getBoolean(MainApp.gs(resourceID), defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return try {
            sharedPreferences.getBoolean(key, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }

    fun getDouble(resourceID: Int, defaultValue: Double): Double =
            SafeParse.stringToDouble(sharedPreferences.getString(MainApp.gs(resourceID), defaultValue.toString()))

    fun getDouble(key: String, defaultValue: Double): Double =
            SafeParse.stringToDouble(sharedPreferences.getString(key, defaultValue.toString()))

    fun getInt(resourceID: Int, defaultValue: Int): Int {
        return try {
            sharedPreferences.getInt(MainApp.gs(resourceID), defaultValue)
        } catch (e: Exception) {
            SafeParse.stringToInt(sharedPreferences.getString(MainApp.gs(resourceID), defaultValue.toString()))
        }
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return try {
            sharedPreferences.getInt(key, defaultValue)
        } catch (e: Exception) {
            SafeParse.stringToInt(sharedPreferences.getString(key, defaultValue.toString()))
        }
    }

    fun getLong(resourceID: Int, defaultValue: Long): Long {
        return try {
            sharedPreferences.getLong(MainApp.gs(resourceID), defaultValue)
        } catch (e: Exception) {
            SafeParse.stringToLong(sharedPreferences.getString(MainApp.gs(resourceID), defaultValue.toString()))
        }
    }

    fun getLong(key: String, defaultValue: Long): Long {
        return try {
            sharedPreferences.getLong(key, defaultValue)
        } catch (e: Exception) {
            SafeParse.stringToLong(sharedPreferences.getString(key, defaultValue.toString()))
        }
    }

    fun putBoolean(key: String, value: Boolean) = sharedPreferences.edit().putBoolean(key, value).apply()

    fun putBoolean(resourceID: Int, value: Boolean) =
            sharedPreferences.edit().putBoolean(MainApp.gs(resourceID), value).apply()


    fun putDouble(key: String, value: Double) =
            sharedPreferences.edit().putString(key, java.lang.Double.toString(value)).apply()


    fun putLong(key: String, value: Long) =
            sharedPreferences.edit().putLong(key, value).apply()

    fun putLong(resourceID: Int, value: Long) =
            sharedPreferences.edit().putLong(MainApp.gs(resourceID), value).apply()

    fun putInt(key: String, value: Int) =
            sharedPreferences.edit().putInt(key, value).apply()

    fun putInt(resourceID: Int, value: Int) =
            sharedPreferences.edit().putInt(MainApp.gs(resourceID), value).apply()

    fun incInt(resourceID: Int) {
        val value = getInt(resourceID, 0) + 1
        sharedPreferences.edit().putInt(MainApp.gs(resourceID), value).apply()
    }

    fun putString(resourceID: Int, value: String) =
            sharedPreferences.edit().putString(MainApp.gs(resourceID), value).apply()

    fun putString(key: String, value: String) =
            sharedPreferences.edit().putString(key, value).apply()

}