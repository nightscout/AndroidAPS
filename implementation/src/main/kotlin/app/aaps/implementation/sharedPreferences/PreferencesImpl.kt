package app.aaps.implementation.sharedPreferences

import app.aaps.core.interfaces.configuration.Config
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
    config: Config
) : Preferences {

    override val simpleMode: Boolean get() = sp.getBoolean(BooleanKeys.GeneralSimpleMode.key, BooleanKeys.GeneralSimpleMode.defaultValue)
    override val apsMode: Boolean = config.APS
    override val nsclientMode: Boolean = config.NSCLIENT
    override val pumpControlMode: Boolean = config.PUMPCONTROL

    override fun get(key: BooleanKeys): Boolean =
        if (simpleMode) key.defaultValue
        else sp.getBoolean(key.key, key.defaultValue)

    override fun getIfExists(key: BooleanKeys): Boolean? =
        if (sp.contains(key.key)) sp.getBoolean(key.key, key.defaultValue) else null

    override fun put(key: BooleanKeys, value: Boolean) {
        sp.putBoolean(key.key, value)
    }

    override fun get(key: StringKeys): String =
        if (simpleMode) key.defaultValue
        else sp.getString(key.key, key.defaultValue)

    override fun getIfExists(key: StringKeys): String? =
        if (sp.contains(key.key)) sp.getString(key.key, key.defaultValue) else null

    override fun put(key: StringKeys, value: String) {
        sp.putString(key.key, value)
    }

    override fun get(key: DoubleKeys): Double =
        if (simpleMode) key.defaultValue
        else sp.getDouble(key.key, key.defaultValue)

    override fun getIfExists(key: DoubleKeys): Double? =
        if (sp.contains(key.key)) sp.getDouble(key.key, key.defaultValue) else null

    override fun put(key: DoubleKeys, value: Double) {
        sp.putDouble(key.key, value)
    }

    override fun get(key: IntKeys): Int =
        if (simpleMode) key.defaultValue
        else sp.getInt(key.key, key.defaultValue)

    override fun getIfExists(key: IntKeys): Int? =
        if (sp.contains(key.key)) sp.getInt(key.key, key.defaultValue) else null

    override fun put(key: IntKeys, value: Int) {
        sp.putInt(key.key, value)
    }

    override fun remove(key: Keys) {
        sp.remove(key.key)
    }
}