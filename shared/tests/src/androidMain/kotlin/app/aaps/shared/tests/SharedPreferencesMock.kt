package app.aaps.shared.tests

import android.content.SharedPreferences

class SharedPreferencesMock : SharedPreferences {

    private val editor = EditorInternals()

    internal class EditorInternals : SharedPreferences.Editor {

        var innerMap: MutableMap<String, Any?> = HashMap()
        override fun putString(k: String, v: String?): SharedPreferences.Editor {
            innerMap[k] = v
            return this
        }

        override fun putStringSet(k: String, set: Set<String>?): SharedPreferences.Editor {
            innerMap[k] = set
            return this
        }

        override fun putInt(k: String, i: Int): SharedPreferences.Editor {
            innerMap[k] = i
            return this
        }

        override fun putLong(k: String, l: Long): SharedPreferences.Editor {
            innerMap[k] = l
            return this
        }

        override fun putFloat(k: String, v: Float): SharedPreferences.Editor {
            innerMap[k] = v
            return this
        }

        override fun putBoolean(k: String, b: Boolean): SharedPreferences.Editor {
            innerMap[k] = b
            return this
        }

        override fun remove(k: String): SharedPreferences.Editor {
            innerMap.remove(k)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            innerMap.clear()
            return this
        }

        override fun commit(): Boolean {
            return true
        }

        override fun apply() {}
    }

    override fun getAll(): Map<String, *> {
        return editor.innerMap
    }

    override fun getString(k: String, s: String?): String? {
        return if (editor.innerMap.containsKey(k)) {
            editor.innerMap[k] as String?
        } else {
            s
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(k: String, set: Set<String>?): Set<String>? {
        return if (editor.innerMap.containsKey(k)) {
            editor.innerMap[k] as Set<String>?
        } else {
            set
        }
    }

    override fun getInt(k: String, i: Int): Int {
        return if (editor.innerMap.containsKey(k)) {
            editor.innerMap[k] as Int
        } else {
            i
        }
    }

    override fun getLong(k: String, l: Long): Long {
        return if (editor.innerMap.containsKey(k)) {
            editor.innerMap[k] as Long
        } else {
            l
        }
    }

    override fun getFloat(k: String, v: Float): Float {
        return if (editor.innerMap.containsKey(k)) {
            editor.innerMap[k] as Float
        } else {
            v
        }
    }

    override fun getBoolean(k: String, b: Boolean): Boolean {
        return if (editor.innerMap.containsKey(k)) {
            editor.innerMap[k] as Boolean
        } else {
            b
        }
    }

    override fun contains(k: String): Boolean {
        return editor.innerMap.containsKey(k)
    }

    override fun edit(): SharedPreferences.Editor {
        return editor
    }

    override fun registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener) {}
}