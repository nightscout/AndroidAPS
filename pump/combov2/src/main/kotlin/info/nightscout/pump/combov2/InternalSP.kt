package info.nightscout.pump.combov2

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import info.nightscout.shared.SafeParse
import info.nightscout.shared.sharedPreferences.SP

// This is a copy of the AAPS SPImplementation. We keep this to be able
// to set up a custom internal SP store for the Combo pump state.
class InternalSP(
    private val sharedPreferences: SharedPreferences,
    private val context: Context
) : SP {
    @SuppressLint("ApplySharedPref")
    override fun edit(commit: Boolean, block: SP.Editor.() -> Unit) {
        val spEdit = sharedPreferences.edit()

        val edit = object : SP.Editor {
            override fun clear() {
                spEdit.clear()
            }

            override fun remove(@StringRes resourceID: Int) {
                spEdit.remove(context.getString(resourceID))
            }
            override fun remove(key: String) {
                spEdit.remove(key)
            }

            override fun putBoolean(key: String, value: Boolean) {
                spEdit.putBoolean(key, value)
            }
            override fun putBoolean(@StringRes resourceID: Int, value: Boolean) {
                spEdit.putBoolean(context.getString(resourceID), value)
            }
            override fun putDouble(key: String, value: Double) {
                spEdit.putString(key, value.toString())
            }
            override fun putDouble(@StringRes resourceID: Int, value: Double) {
                spEdit.putString(context.getString(resourceID), value.toString())
            }
            override fun putLong(key: String, value: Long) {
                spEdit.putLong(key, value)
            }
            override fun putLong(@StringRes resourceID: Int, value: Long) {
                spEdit.putLong(context.getString(resourceID), value)
            }
            override fun putInt(key: String, value: Int) {
                spEdit.putInt(key, value)
            }
            override fun putInt(@StringRes resourceID: Int, value: Int) {
                spEdit.putInt(context.getString(resourceID), value)
            }
            override fun putString(key: String, value: String) {
                spEdit.putString(key, value)
            }
            override fun putString(@StringRes resourceID: Int, value: String) {
                spEdit.putString(context.getString(resourceID), value)
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
        sharedPreferences.getString(context.getString(resourceID), defaultValue) ?: defaultValue

    override fun getStringOrNull(resourceID: Int, defaultValue: String?): String? =
        sharedPreferences.getString(context.getString(resourceID), defaultValue) ?: defaultValue

    override fun getStringOrNull(key: String, defaultValue: String?): String? =
        sharedPreferences.getString(key, defaultValue)

    override fun getString(key: String, defaultValue: String): String =
        sharedPreferences.getString(key, defaultValue) ?: defaultValue

    override fun getBoolean(resourceID: Int, defaultValue: Boolean): Boolean {
        return try {
            sharedPreferences.getBoolean(context.getString(resourceID), defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return try {
            sharedPreferences.getBoolean(key, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }

    override fun getDouble(resourceID: Int, defaultValue: Double): Double =
        SafeParse.stringToDouble(sharedPreferences.getString(context.getString(resourceID), defaultValue.toString()))

    override fun getDouble(key: String, defaultValue: Double): Double =
        SafeParse.stringToDouble(sharedPreferences.getString(key, defaultValue.toString()))

    override fun getInt(resourceID: Int, defaultValue: Int): Int {
        return try {
            sharedPreferences.getInt(context.getString(resourceID), defaultValue)
        } catch (e: Exception) {
            SafeParse.stringToInt(sharedPreferences.getString(context.getString(resourceID), defaultValue.toString()))
        }
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return try {
            sharedPreferences.getInt(key, defaultValue)
        } catch (e: Exception) {
            SafeParse.stringToInt(sharedPreferences.getString(key, defaultValue.toString()))
        }
    }

    override fun getLong(resourceID: Int, defaultValue: Long): Long {
        return try {
            sharedPreferences.getLong(context.getString(resourceID), defaultValue)
        } catch (e: Exception) {
            try {
                SafeParse.stringToLong(sharedPreferences.getString(context.getString(resourceID), defaultValue.toString()))
            } catch (e: Exception) {
                defaultValue
            }
        }
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return try {
            sharedPreferences.getLong(key, defaultValue)
        } catch (e: Exception) {
            try {
                SafeParse.stringToLong(sharedPreferences.getString(key, defaultValue.toString()))
            } catch (e: Exception) {
                defaultValue
            }
        }
    }

    override fun incLong(resourceID: Int) {
        val value = getLong(resourceID, 0) + 1L
        sharedPreferences.edit().putLong(context.getString(resourceID), value).apply()
    }

    override fun putBoolean(key: String, value: Boolean) = sharedPreferences.edit().putBoolean(key, value).apply()

    override fun putBoolean(resourceID: Int, value: Boolean) =
        sharedPreferences.edit().putBoolean(context.getString(resourceID), value).apply()

    override fun putDouble(key: String, value: Double) =
        sharedPreferences.edit().putString(key, value.toString()).apply()

    override fun putDouble(resourceID: Int, value: Double) {
        sharedPreferences.edit().putString(context.getString(resourceID), value.toString()).apply()
    }

    override fun putLong(key: String, value: Long) =
        sharedPreferences.edit().putLong(key, value).apply()

    override fun putLong(resourceID: Int, value: Long) =
        sharedPreferences.edit().putLong(context.getString(resourceID), value).apply()

    override fun putInt(key: String, value: Int) =
        sharedPreferences.edit().putInt(key, value).apply()

    override fun putInt(resourceID: Int, value: Int) =
        sharedPreferences.edit().putInt(context.getString(resourceID), value).apply()

    override fun incInt(resourceID: Int) {
        val value = getInt(resourceID, 0) + 1
        sharedPreferences.edit().putInt(context.getString(resourceID), value).apply()
    }

    override fun putString(resourceID: Int, value: String) =
        sharedPreferences.edit().putString(context.getString(resourceID), value).apply()

    override fun putString(key: String, value: String) =
        sharedPreferences.edit().putString(key, value).apply()
}