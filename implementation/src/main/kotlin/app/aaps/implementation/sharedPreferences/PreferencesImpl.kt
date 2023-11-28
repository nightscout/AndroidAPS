package app.aaps.implementation.sharedPreferences

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.PreferenceKey
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class PreferencesImpl @Inject constructor(
    private val sp: SP,
    private val rh: ResourceHelper,
    private val profileUtil: Lazy<ProfileUtil>,
    private val profileFunction: Lazy<ProfileFunction>,
    private val hardLimits: Lazy<HardLimits>,
    private val persistenceLayer: PersistenceLayer,
    private val config: Config,
    private val dateUtil: DateUtil
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
        if (simpleMode && key.calculatedBySM) calculatePreference(key)
        else if (simpleMode && key.defaultedBySM) key.defaultValue
        else sp.getDouble(key.key, key.defaultValue)

    override fun getIfExists(key: DoubleKey): Double? =
        if (sp.contains(key.key)) sp.getDouble(key.key, key.defaultValue) else null

    override fun put(key: DoubleKey, value: Double) {
        sp.putDouble(key.key, value)
    }

    override fun get(key: UnitDoubleKey): Double =
        if (simpleMode && key.defaultedBySM) key.defaultValue
        else profileUtil.get().valueInCurrentUnitsDetect(sp.getDouble(key.key, key.defaultValue))

    override fun getIfExists(key: UnitDoubleKey): Double? =
        if (sp.contains(key.key)) sp.getDouble(key.key, key.defaultValue) else null

    override fun put(key: UnitDoubleKey, value: Double) {
        sp.putDouble(key.key, value)
    }

    override fun get(key: IntKey): Int =
        if (simpleMode && key.defaultedBySM) calculatedDefaultValue(key)
        else if (key.engineeringModeOnly && !config.isEngineeringMode()) calculatedDefaultValue(key)
        else sp.getInt(key.key, calculatedDefaultValue(key))

    override fun getIfExists(key: IntKey): Int? =
        if (sp.contains(key.key)) sp.getInt(key.key, calculatedDefaultValue(key)) else null

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

    private fun calculatedDefaultValue(key: IntKey): Int =
        if (key.calculatedDefaultValue)
            when (key) {
                IntKey.AutosensPeriod ->
                    when (get(StringKey.SafetyAge)) {
                        rh.gs(app.aaps.core.utils.R.string.key_teenage) -> 4
                        rh.gs(app.aaps.core.utils.R.string.key_child)   -> 4
                        else                                            -> 24
                    }

                else                  -> error("Unsupported default value calculation")
            }
        else key.defaultValue

    private fun calculatePreference(key: DoubleKey): Double =
        limit(key, when (key) {
            DoubleKey.ApsMaxBasal -> profileFunction.get().getProfile()?.getMaxDailyBasal()?.let { it * 3 } ?: key.defaultValue
            DoubleKey.ApsSmbMaxIob -> recentMaxBolus() + (profileFunction.get().getProfile()?.getMaxDailyBasal()?.let { it * 3 } ?: key.defaultValue)
            DoubleKey.ApsAmaMaxIob -> profileFunction.get().getProfile()?.getMaxDailyBasal()?.let { it * 3 } ?: key.defaultValue
            else -> error("Unsupported key calculation")
        })

    private fun limit(key: DoubleKey, calculated: Double) = min(key.max, max(key.min, calculated))
    private fun recentMaxBolus(): Double =
        persistenceLayer
            .getBolusesFromTime(dateUtil.now() - T.days(7).msecs(), true)
            .blockingGet()
            .maxOfOrNull { it.amount }
            ?: hardLimits.get().maxBolus()
}