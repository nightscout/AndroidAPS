package app.aaps.implementation.sharedPreferences

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
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
    private val rh: ResourceHelper,
    private val profileUtil: ProfileUtil,
    config: Config
) : Preferences {

    override val simpleMode: Boolean get() = sp.getBoolean(BooleanKey.GeneralSimpleMode.key, BooleanKey.GeneralSimpleMode.defaultValue)
    override val apsMode: Boolean = config.APS
    override val nsclientMode: Boolean = config.NSCLIENT
    override val pumpControlMode: Boolean = config.PUMPCONTROL

    override fun get(key: BooleanKey): Boolean =
        if (simpleMode && key.defaultedBySM) key.defaultValue
        else sp.getBoolean(key.key, key.defaultValue)

    override fun getIfExists(key: BooleanKey): Boolean? =
        if (sp.contains(key.key)) sp.getBoolean(key.key, key.defaultValue) else null

    override fun put(key: BooleanKey, value: Boolean) {
        sp.putBoolean(key.key, value)
    }

    override fun get(key: StringKey): String =
        if (simpleMode && key.defaultedBySM) key.defaultValue
        else sp.getString(key.key, key.defaultValue)

    override fun getIfExists(key: StringKey): String? =
        if (sp.contains(key.key)) sp.getString(key.key, key.defaultValue) else null

    override fun put(key: StringKey, value: String) {
        sp.putString(key.key, value)
    }

    override fun get(key: DoubleKey): Double =
        if (simpleMode && key.defaultedBySM) key.defaultValue
        else sp.getDouble(key.key, key.defaultValue)

    override fun getIfExists(key: DoubleKey): Double? =
        if (sp.contains(key.key)) sp.getDouble(key.key, key.defaultValue) else null

    override fun put(key: DoubleKey, value: Double) {
        sp.putDouble(key.key, value)
    }

    override fun get(key: UnitDoubleKey): Double =
        if (simpleMode && key.defaultedBySM) key.defaultValue
        else profileUtil.valueInCurrentUnitsDetect(sp.getDouble(key.key, key.defaultValue))

    override fun getIfExists(key: UnitDoubleKey): Double? =
        if (sp.contains(key.key)) sp.getDouble(key.key, key.defaultValue) else null

    override fun put(key: UnitDoubleKey, value: Double) {
        sp.putDouble(key.key, value)
    }

    override fun get(key: IntKey): Int =
        if (simpleMode && key.defaultedBySM) key.defaultValue
        else sp.getInt(key.key, key.defaultValue)

    override fun getIfExists(key: IntKey): Int? =
        if (sp.contains(key.key)) sp.getInt(key.key, key.defaultValue) else null

    override fun put(key: IntKey, value: Int) {
        sp.putInt(key.key, value)
    }

    override fun remove(key: PreferenceKey) {
        sp.remove(key.key)
    }

    override fun isUnitDependent(key: String): Boolean =
        UnitDoubleKey.entries.any { rh.gs(it.key) == key }

    override fun get(key: String): PreferenceKey =
        BooleanKey.entries.find { rh.gs(it.key) == key }
            ?: StringKey.entries.find { rh.gs(it.key) == key }
            ?: IntKey.entries.find { rh.gs(it.key) == key }
            ?: DoubleKey.entries.find { rh.gs(it.key) == key }
            ?: UnitDoubleKey.entries.find { rh.gs(it.key) == key }
            ?: error("Key $key not found")

}