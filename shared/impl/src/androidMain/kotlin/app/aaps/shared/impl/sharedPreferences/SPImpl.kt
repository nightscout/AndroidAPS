package app.aaps.shared.impl.sharedPreferences

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.SafeParse

/**
 * The preferences file of the watch, and the phone's old settings file.
 *
 * The phone keeps its settings in the encrypted store (`AndroidStorage`) since 2026-10. It only reads this
 * file once, to move the settings over, and keeps it for one release as a way back for a downgrade.
 */
fun preferencesFileName(context: Context): String = "${context.packageName}_preferences"

fun defaultPreferences(context: Context): SharedPreferences =
    context.getSharedPreferences(preferencesFileName(context), Context.MODE_PRIVATE)

/**
 * The SharedPreferences store. Only wear uses it now; the phone moved to the encrypted store in 2026-10.
 *
 * To remove from here in the release after that move: delete the phone's old settings file (the move in
 * `SharedPreferencesMigration` keeps it for one release), then move this class, the two functions above and
 * the `SP` interface into the wear module.
 */
class SPImpl(
    private val sharedPreferences: SharedPreferences,
    private val context: Context
) : SP {

    @SuppressLint("ApplySharedPref")
    override fun edit(commit: Boolean, block: KeyValueStore.Editor.() -> Unit) {
        val spEdit = sharedPreferences.edit()

        val edit = object : KeyValueStore.Editor {
            override fun clear() {
                spEdit.clear()
            }


            override fun remove(key: String) {
                spEdit.remove(key)
            }

            override fun putBoolean(key: String, value: Boolean) {
                spEdit.putBoolean(key, value)
            }


            override fun putDouble(key: String, value: Double) {
                spEdit.putString(key, value.toString())
            }


            override fun putLong(key: String, value: Long) {
                spEdit.putLong(key, value)
            }


            override fun putInt(key: String, value: Int) {
                spEdit.putInt(key, value)
            }


            override fun putString(key: String, value: String) {
                spEdit.putString(key, value)
            }

        }

        block(edit)

        if (commit)
            spEdit.commit()
        else
            spEdit.apply()
    }

    override fun getAll(): Map<String, *> = sharedPreferences.all

    override fun clear() = sharedPreferences.edit().clear().apply()

    override fun contains(key: String): Boolean = sharedPreferences.contains(key)

    override fun contains(resourceId: Int): Boolean = sharedPreferences.contains(context.getString(resourceId))

    override fun remove(resourceID: Int) =
        sharedPreferences.edit().remove(context.getString(resourceID)).apply()

    override fun remove(key: String) =
        sharedPreferences.edit().remove(key).apply()

    override fun getString(resourceID: Int, defaultValue: String): String =
        getString(context.getString(resourceID), defaultValue)

    override fun getString(key: String, defaultValue: String): String =
        getStringOrNull(key, defaultValue) ?: defaultValue

    override fun getStringOrNull(resourceID: Int, defaultValue: String?): String? =
        getStringOrNull(context.getString(resourceID), defaultValue)

    override fun getStringOrNull(key: String, defaultValue: String?): String? =
        try {
            sharedPreferences.getString(key, defaultValue)
        } catch (_: Exception) {
            storedValue(key)?.toString() ?: defaultValue
        }

    override fun getBoolean(resourceID: Int, defaultValue: Boolean): Boolean =
        sharedPreferences.getBoolean(context.getString(resourceID), defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        try {
            sharedPreferences.getBoolean(key, defaultValue)
        } catch (_: Exception) {
            defaultValue
        }

    override fun getDouble(resourceID: Int, defaultValue: Double): Double =
        getDouble(context.getString(resourceID), defaultValue)

    override fun getDouble(key: String, defaultValue: Double): Double =
        try {
            sharedPreferences.getFloat(key, defaultValue.toFloat()).toDouble()
        } catch (_: Exception) {
            doubleOf(storedValue(key), defaultValue)
        }

    override fun getInt(resourceID: Int, defaultValue: Int): Int =
        getInt(context.getString(resourceID), defaultValue)

    override fun getInt(key: String, defaultValue: Int): Int =
        try {
            sharedPreferences.getInt(key, defaultValue)
        } catch (_: Exception) {
            intOf(storedValue(key), defaultValue)
        }

    override fun getLong(resourceID: Int, defaultValue: Long): Long =
        getLong(context.getString(resourceID), defaultValue)

    override fun getLong(key: String, defaultValue: Long): Long =
        try {
            sharedPreferences.getLong(key, defaultValue)
        } catch (_: Exception) {
            longOf(storedValue(key), defaultValue)
        }

    /**
     * The value as it is stored, for a key whose type is not the one the getter asked for.
     *
     * Older builds wrote the same setting as text, as an Int or as a Float, so a getter cannot assume
     * that "not my type" means text. Reading it as text threw a second ClassCastException, this time
     * inside the catch, where nothing caught it: opening the settings killed the app for anyone whose
     * file still held an Int under a decimal setting.
     *
     * Only the fallback reads the whole map, which SharedPreferences copies on every call, so the
     * normal path stays as cheap as it was. The same conversion is in `EncryptedKeyValueStore`, which
     * holds the phone's settings now.
     */
    private fun storedValue(key: String): Any? =
        try {
            sharedPreferences.all[key]
        } catch (_: Exception) {
            null
        }

    private fun doubleOf(value: Any?, defaultValue: Double): Double = when (value) {
        is Number -> value.toDouble()
        is String -> SafeParse.stringToDouble(value, defaultValue)
        else      -> defaultValue
    }

    private fun intOf(value: Any?, defaultValue: Int): Int = when (value) {
        is Long   -> if (value in Int.MIN_VALUE..Int.MAX_VALUE) value.toInt() else defaultValue
        is Number -> value.toInt()
        is String -> SafeParse.stringToInt(value, defaultValue)
        else      -> defaultValue
    }

    private fun longOf(value: Any?, defaultValue: Long): Long = when (value) {
        is Number -> value.toLong()
        is String -> SafeParse.stringToLong(value, defaultValue)
        else      -> defaultValue
    }

    override fun incLong(key: String) {
        val value = getLong(key, 0) + 1L
        sharedPreferences.edit().putLong(key, value).apply()
    }

    override fun putBoolean(key: String, value: Boolean) = sharedPreferences.edit().putBoolean(key, value).apply()

    override fun putBoolean(resourceID: Int, value: Boolean) =
        sharedPreferences.edit().putBoolean(context.getString(resourceID), value).apply()

    override fun putDouble(key: String, value: Double) =
        sharedPreferences.edit().putFloat(key, value.toFloat()).apply()

    override fun putDouble(resourceID: Int, value: Double) =
        sharedPreferences.edit().putFloat(context.getString(resourceID), value.toFloat()).apply()

    override fun putLong(key: String, value: Long) =
        sharedPreferences.edit().putLong(key, value).apply()

    override fun putLong(resourceID: Int, value: Long) =
        sharedPreferences.edit().putLong(context.getString(resourceID), value).apply()

    override fun putInt(key: String, value: Int) =
        sharedPreferences.edit().putInt(key, value).apply()

    override fun putInt(resourceID: Int, value: Int) =
        sharedPreferences.edit().putInt(context.getString(resourceID), value).apply()

    override fun incInt(key: String) {
        val value = getInt(key, 0) + 1
        sharedPreferences.edit().putInt(key, value).apply()
    }

    override fun putString(resourceID: Int, value: String) =
        sharedPreferences.edit().putString(context.getString(resourceID), value).apply()

    override fun putString(key: String, value: String) =
        sharedPreferences.edit().putString(key, value).apply()

}