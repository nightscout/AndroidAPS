package app.aaps.wear.sharedPreferences

import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.BooleanKeys
import app.aaps.core.keys.DoubleKeys
import app.aaps.core.keys.IntKeys
import app.aaps.core.keys.Keys
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringKeys
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesImpl @Inject constructor(
    private val sp: SP,
) : Preferences {

    override val simpleMode: Boolean = false
    override val apsMode: Boolean = false
    override val nsclientMode: Boolean = false
    override val pumpControlMode: Boolean = false

    override fun get(key: BooleanKeys): Boolean = sp.getBoolean(key.key, key.defaultValue)

    override fun getIfExists(key: BooleanKeys): Boolean? =
        if (sp.contains(key.key)) sp.getBoolean(key.key, key.defaultValue) else null

    override fun put(key: BooleanKeys, value: Boolean) {
        sp.putBoolean(key.key, value)
    }

    override fun get(key: StringKeys): String = sp.getString(key.key, key.defaultValue)

    override fun getIfExists(key: StringKeys): String? =
        if (sp.contains(key.key)) sp.getString(key.key, key.defaultValue) else null

    override fun put(key: StringKeys, value: String) {
        sp.putString(key.key, value)
    }

    override fun get(key: DoubleKeys): Double = sp.getDouble(key.key, key.defaultValue)

    override fun getIfExists(key: DoubleKeys): Double? =
        if (sp.contains(key.key)) sp.getDouble(key.key, key.defaultValue) else null

    override fun put(key: DoubleKeys, value: Double) {
        sp.putDouble(key.key, value)
    }

    override fun get(key: IntKeys): Int = sp.getInt(key.key, key.defaultValue)

    override fun getIfExists(key: IntKeys): Int? =
        if (sp.contains(key.key)) sp.getInt(key.key, key.defaultValue) else null

    override fun put(key: IntKeys, value: Int) {
        sp.putInt(key.key, value)
    }

    override fun remove(key: Keys) {
        sp.remove(key.key)
    }
}