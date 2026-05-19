package app.aaps.plugins.main.general.nfcCommands

import android.content.SharedPreferences
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.shared.tests.SharedPreferencesMock

/** Minimal SP backed by SharedPreferencesMock for NfcTagStore unit tests. */
internal class TestSp(private val prefs: SharedPreferences = SharedPreferencesMock()) : SP {
    override fun getString(key: String, defaultValue: String) = prefs.getString(key, defaultValue) ?: defaultValue
    override fun edit(commit: Boolean, block: SP.Editor.() -> Unit) {
        val spEditor = prefs.edit()
        val editor = object : SP.Editor {
            override fun clear() { spEditor.clear() }
            override fun remove(key: String) { spEditor.remove(key) }
            override fun remove(resourceID: Int) = throw UnsupportedOperationException()
            override fun putBoolean(key: String, value: Boolean) { spEditor.putBoolean(key, value) }
            override fun putBoolean(resourceID: Int, value: Boolean) = throw UnsupportedOperationException()
            override fun putDouble(key: String, value: Double) { spEditor.putString(key, value.toString()) }
            override fun putDouble(resourceID: Int, value: Double) = throw UnsupportedOperationException()
            override fun putLong(key: String, value: Long) { spEditor.putLong(key, value) }
            override fun putLong(resourceID: Int, value: Long) = throw UnsupportedOperationException()
            override fun putInt(key: String, value: Int) { spEditor.putInt(key, value) }
            override fun putInt(resourceID: Int, value: Int) = throw UnsupportedOperationException()
            override fun putString(key: String, value: String) { spEditor.putString(key, value) }
            override fun putString(resourceID: Int, value: String) = throw UnsupportedOperationException()
        }
        block(editor)
        if (commit) spEditor.commit() else spEditor.apply()
    }

    override fun getAll(): Map<String, *> = prefs.all
    override fun clear() { prefs.edit().clear().apply() }
    override fun contains(key: String) = prefs.contains(key)
    override fun contains(resourceId: Int) = throw UnsupportedOperationException()
    override fun remove(resourceID: Int) = throw UnsupportedOperationException()
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
    override fun getString(resourceID: Int, defaultValue: String) = throw UnsupportedOperationException()
    override fun getStringOrNull(resourceID: Int, defaultValue: String?) = throw UnsupportedOperationException()
    override fun getStringOrNull(key: String, defaultValue: String?) = prefs.getString(key, defaultValue)
    override fun getBoolean(resourceID: Int, defaultValue: Boolean) = throw UnsupportedOperationException()
    override fun getBoolean(key: String, defaultValue: Boolean) = prefs.getBoolean(key, defaultValue)
    override fun getDouble(resourceID: Int, defaultValue: Double) = throw UnsupportedOperationException()
    override fun getDouble(key: String, defaultValue: Double) = prefs.getString(key, null)?.toDoubleOrNull() ?: defaultValue
    override fun getInt(resourceID: Int, defaultValue: Int) = throw UnsupportedOperationException()
    override fun getInt(key: String, defaultValue: Int) = prefs.getInt(key, defaultValue)
    override fun getLong(resourceID: Int, defaultValue: Long) = throw UnsupportedOperationException()
    override fun getLong(key: String, defaultValue: Long) = prefs.getLong(key, defaultValue)
    override fun incLong(key: String) = throw UnsupportedOperationException()
    override fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    override fun putBoolean(resourceID: Int, value: Boolean) = throw UnsupportedOperationException()
    override fun putDouble(key: String, value: Double) { prefs.edit().putString(key, value.toString()).apply() }
    override fun putDouble(resourceID: Int, value: Double) = throw UnsupportedOperationException()
    override fun putLong(key: String, value: Long) { prefs.edit().putLong(key, value).apply() }
    override fun putLong(resourceID: Int, value: Long) = throw UnsupportedOperationException()
    override fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    override fun putInt(resourceID: Int, value: Int) = throw UnsupportedOperationException()
    override fun incInt(key: String) = throw UnsupportedOperationException()
    override fun putString(resourceID: Int, value: String) = throw UnsupportedOperationException()
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
}
