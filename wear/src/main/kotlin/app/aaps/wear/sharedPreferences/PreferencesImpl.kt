package app.aaps.wear.sharedPreferences

import android.content.Context
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.PreferenceKey
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesImpl @Inject constructor(
    private val sp: SP,
    private val context: Context
) : Preferences {

    override val simpleMode: Boolean = false
    override val apsMode: Boolean = false
    override val nsclientMode: Boolean = false
    override val pumpControlMode: Boolean = false

    override fun get(key: BooleanKey): Boolean = sp.getBoolean(key.key, key.defaultValue)

    override fun getIfExists(key: BooleanKey): Boolean? =
        if (sp.contains(key.key)) sp.getBoolean(key.key, key.defaultValue) else null

    override fun put(key: BooleanKey, value: Boolean) {
        sp.putBoolean(key.key, value)
    }

    override fun get(key: StringKey): String = sp.getString(key.key, key.defaultValue)

    override fun getIfExists(key: StringKey): String? =
        if (sp.contains(key.key)) sp.getString(key.key, key.defaultValue) else null

    override fun put(key: StringKey, value: String) {
        sp.putString(key.key, value)
    }

    override fun get(key: DoubleKey): Double = sp.getDouble(key.key, key.defaultValue)

    override fun getIfExists(key: DoubleKey): Double? =
        if (sp.contains(key.key)) sp.getDouble(key.key, key.defaultValue) else null

    override fun put(key: DoubleKey, value: Double) {
        sp.putDouble(key.key, value)
    }

    override fun get(key: UnitDoubleKey): Double =
        error("Not implemented")
    //profileUtil.valueInCurrentUnitsDetect(sp.getDouble(key.key, key.defaultValue))

    override fun getIfExists(key: UnitDoubleKey): Double? =
        if (sp.contains(key.key)) sp.getDouble(key.key, key.defaultValue) else null

    override fun put(key: UnitDoubleKey, value: Double) {
        sp.putDouble(key.key, value)
    }

    override fun get(key: IntKey): Int = sp.getInt(key.key, key.defaultValue)

    override fun getIfExists(key: IntKey): Int? =
        if (sp.contains(key.key)) sp.getInt(key.key, key.defaultValue) else null

    override fun put(key: IntKey, value: Int) {
        sp.putInt(key.key, value)
    }

    override fun remove(key: PreferenceKey) {
        sp.remove(key.key)
    }

    override fun isUnitDependent(key: String): Boolean =
        UnitDoubleKey.entries.any { context.getString(it.key) == key }

    override fun get(key: String): PreferenceKey =
        BooleanKey.entries.find { context.getString(it.key) == key }
            ?: StringKey.entries.find { context.getString(it.key) == key }
            ?: IntKey.entries.find { context.getString(it.key) == key }
            ?: DoubleKey.entries.find { context.getString(it.key) == key }
            ?: UnitDoubleKey.entries.find { context.getString(it.key) == key }
            ?: error("Key $key not found")

}