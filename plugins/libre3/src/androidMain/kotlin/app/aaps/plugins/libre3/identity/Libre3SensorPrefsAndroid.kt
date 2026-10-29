package app.aaps.plugins.libre3.identity

import android.content.Context
import android.content.SharedPreferences
import app.aaps.plugins.libre3.Libre3AndroidPlatform
import app.aaps.plugins.libre3.sensorStore

/**
 * [Libre3SensorPrefs] on Android: one private `SharedPreferences` file, exactly the file and the
 * keys this driver has always used.
 *
 * `commit` is what [editAndWait] means here and `apply` is what [edit] means. That difference is
 * the one the driver leans on, see the note on the interface.
 */
class Libre3SensorPrefsAndroid(context: Context, fileName: String) : Libre3SensorPrefs {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)

    override fun editAndWait(block: Libre3SensorPrefs.Editor.() -> Unit): Boolean =
        prefs.edit().also { AndroidEditor(it).block() }.commit()

    override fun edit(block: Libre3SensorPrefs.Editor.() -> Unit) {
        prefs.edit().also { AndroidEditor(it).block() }.apply()
    }

    private class AndroidEditor(private val editor: SharedPreferences.Editor) : Libre3SensorPrefs.Editor {

        // Writing a null string removes the key, which is what SharedPreferences does and what the
        // store relies on.
        override fun putString(key: String, value: String?) {
            editor.putString(key, value)
        }

        override fun putInt(key: String, value: Int) {
            editor.putInt(key, value)
        }

        override fun putLong(key: String, value: Long) {
            editor.putLong(key, value)
        }

        override fun putBoolean(key: String, value: Boolean) {
            editor.putBoolean(key, value)
        }

        override fun remove(key: String) {
            editor.remove(key)
        }

        override fun clear() {
            editor.clear()
        }
    }
}

/**
 * Builds a store for one slot from an Android context.
 *
 * A top level function with the same name as the class, so every existing
 * `Libre3SensorStore(context)` and `Libre3SensorStore(context, "staging")` call keeps compiling
 * while the class itself no longer knows what a `Context` is.
 *
 * @param namespace null or blank is the production slot. A name gives the pre-soak slot its own
 *   file. The receiver id always comes from the production file, because it is the identity of
 *   this phone and not of a sensor.
 */
fun Libre3SensorStore(context: Context, namespace: String? = null): Libre3SensorStore =
    Libre3AndroidPlatform(context).sensorStore(namespace)
