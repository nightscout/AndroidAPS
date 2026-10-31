package app.aaps.plugins.dexcomoneplus.identity

import android.content.Context
import android.content.SharedPreferences

/**
 * Android [OnePlusSensorPrefs]: the same private `SharedPreferences` file the store always used,
 * under the same name, so an install keeps its stored sensor across this change.
 */
internal class OnePlusSensorPrefsAndroid(context: Context, prefsName: String) : OnePlusSensorPrefs {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)

    override fun contains(key: String): Boolean = prefs.contains(key)

    override fun edit(block: OnePlusSensorPrefs.Editor.() -> Unit) {
        val editor = prefs.edit()
        AndroidEditor(editor).block()
        editor.apply()
    }

    private class AndroidEditor(private val editor: SharedPreferences.Editor) : OnePlusSensorPrefs.Editor {

        override fun putString(key: String, value: String?) {
            editor.putString(key, value)
        }

        override fun putLong(key: String, value: Long) {
            editor.putLong(key, value)
        }

        override fun putInt(key: String, value: Int) {
            editor.putInt(key, value)
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
 * Builds a store on this phone's private file for [namespace].
 *
 * Named like the class on purpose: every caller still writes `OnePlusSensorStore(context)`, and the
 * class itself no longer knows anything about Android.
 */
fun OnePlusSensorStore(context: Context, namespace: String? = null): OnePlusSensorStore =
    OnePlusSensorStore(OnePlusSensorPrefsAndroid(context, OnePlusSensorStore.prefsFileName(namespace)))
