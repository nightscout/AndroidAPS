package app.aaps.implementation.sharedPreferences

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanPreferenceKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.DoublePreferenceKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.IntPreferenceKey
import app.aaps.core.keys.IntentKey
import app.aaps.core.keys.LongPreferenceKey
import app.aaps.core.keys.NonPreferenceKey
import app.aaps.core.keys.PreferenceKey
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.String2PreferenceKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonPreferenceKey
import app.aaps.core.keys.StringPreferenceKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.UnitDoublePreferenceKey
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class PreferencesImpl @Inject constructor(
    private val sp: SP,
    private val profileUtil: Lazy<ProfileUtil>,
    private val profileFunction: Lazy<ProfileFunction>,
    private val hardLimits: Lazy<HardLimits>,
    private val persistenceLayer: PersistenceLayer,
    private val config: Config,
    private val dateUtil: DateUtil
) : Preferences {

    override val simpleMode: Boolean get() = sp.getBoolean(BooleanKey.GeneralSimpleMode.key, BooleanKey.GeneralSimpleMode.defaultValue)
    override val apsMode: Boolean = config.APS
    override val nsclientMode: Boolean = config.AAPSCLIENT
    override val pumpControlMode: Boolean = config.PUMPCONTROL

    private val prefsList: MutableList<Class<out NonPreferenceKey>> =
        mutableListOf(
            BooleanKey::class.java,
            IntKey::class.java,
            DoubleKey::class.java,
            UnitDoubleKey::class.java,
            StringKey::class.java,
            IntentKey::class.java,
        )

    private fun isHidden(key: PreferenceKey): Boolean =
        if (apsMode && key.showInApsMode == false) true
        else if (nsclientMode && key.showInNsClientMode == false) true
        else if (pumpControlMode && key.showInPumpControlMode == false) true
        else false

    override fun get(key: BooleanPreferenceKey): Boolean =
        if (!config.isEngineeringMode() && key.engineeringModeOnly) key.defaultValue
        else if (simpleMode && key.defaultedBySM) calculatedDefaultValue(key)
        else if (key.calculatedDefaultValue && isHidden(key)) calculatedDefaultValue(key)
        else sp.getBoolean(key.key, calculatedDefaultValue(key))

    override fun getIfExists(key: BooleanPreferenceKey): Boolean? =
        if (sp.contains(key.key)) sp.getBoolean(key.key, key.defaultValue) else null

    override fun put(key: BooleanPreferenceKey, value: Boolean) {
        sp.putBoolean(key.key, value)
    }

    override fun get(key: StringNonPreferenceKey): String =
        sp.getString(key.key, key.defaultValue)

    override fun get(key: StringPreferenceKey): String =
        if (simpleMode && key.defaultedBySM) key.defaultValue
        else sp.getString(key.key, key.defaultValue)

    override fun getIfExists(key: StringNonPreferenceKey): String? =
        if (sp.contains(key.key)) sp.getString(key.key, key.defaultValue) else null

    override fun put(key: StringNonPreferenceKey, value: String) {
        sp.putString(key.key, value)
    }

    override fun get(key: String2PreferenceKey, appendix: String): String =
        if (simpleMode && key.defaultedBySM) key.defaultValue
        else sp.getString(key.key + key.delimiter + appendix, key.defaultValue)

    override fun getIfExists(key: String2PreferenceKey, appendix: String): String? =
        if (sp.contains(key.key + key.delimiter + appendix)) sp.getString(key.key + key.delimiter + appendix, key.defaultValue) else null

    override fun put(key: String2PreferenceKey, appendix: String, value: String) {
        sp.putString(key.key + key.delimiter + appendix, value)
    }

    override fun get(key: DoublePreferenceKey): Double =
        if (simpleMode && key.calculatedBySM) calculatePreference(key)
        else if (simpleMode && key.defaultedBySM) key.defaultValue
        else sp.getDouble(key.key, key.defaultValue)

    override fun getIfExists(key: DoublePreferenceKey): Double? =
        if (sp.contains(key.key)) sp.getDouble(key.key, key.defaultValue) else null

    override fun put(key: DoublePreferenceKey, value: Double) {
        sp.putDouble(key.key, value)
    }

    override fun get(key: UnitDoublePreferenceKey): Double =
        if (simpleMode && key.defaultedBySM) key.defaultValue
        else profileUtil.get().valueInCurrentUnitsDetect(sp.getDouble(key.key, key.defaultValue))

    override fun getIfExists(key: UnitDoublePreferenceKey): Double? =
        if (sp.contains(key.key)) sp.getDouble(key.key, key.defaultValue) else null

    override fun put(key: UnitDoublePreferenceKey, value: Double) {
        sp.putDouble(key.key, value)
    }

    override fun get(key: IntPreferenceKey): Int =
        if (!config.isEngineeringMode() && key.engineeringModeOnly) key.defaultValue
        else if (simpleMode && key.defaultedBySM) calculatedDefaultValue(key)
        else if (key.calculatedDefaultValue && isHidden(key)) calculatedDefaultValue(key)
        else sp.getInt(key.key, calculatedDefaultValue(key))

    override fun getIfExists(key: IntPreferenceKey): Int? =
        if (sp.contains(key.key)) sp.getInt(key.key, calculatedDefaultValue(key)) else null

    override fun put(key: IntPreferenceKey, value: Int) {
        sp.putInt(key.key, value)
    }

    override fun get(key: LongPreferenceKey): Long =
        if (!config.isEngineeringMode() && key.engineeringModeOnly) key.defaultValue
        else if (simpleMode && key.defaultedBySM) calculatedDefaultValue(key)
        else if (key.calculatedDefaultValue && isHidden(key)) calculatedDefaultValue(key)
        else sp.getLong(key.key, calculatedDefaultValue(key))

    override fun getIfExists(key: LongPreferenceKey): Long? =
        if (sp.contains(key.key)) sp.getLong(key.key, calculatedDefaultValue(key)) else null

    override fun put(key: LongPreferenceKey, value: Long) {
        sp.putLong(key.key, value)
    }

    override fun remove(key: NonPreferenceKey) {
        sp.remove(key.key)
    }

    override fun remove(key: String2PreferenceKey, appendix: String) {
        sp.remove(key.key + key.delimiter + appendix)
    }

    override fun isUnitDependent(key: String): Boolean =
        UnitDoubleKey.entries.any { it.key == key }

    override fun get(key: String): NonPreferenceKey =
        prefsList
            .flatMap { it.enumConstants!!.asIterable() }
            .find { it.key == key }
            ?: error("Key $key not found")

    override fun getIfExists(key: String): NonPreferenceKey? =
        prefsList
            .flatMap { it.enumConstants!!.asIterable() }
            .find { it.key == key }

    override fun getDependingOn(key: String): List<PreferenceKey> =
        mutableListOf<PreferenceKey>().also { list ->
            prefsList.forEach { clazz ->
                if (PreferenceKey::class.java.isAssignableFrom(clazz))
                    clazz.enumConstants!!.filter {
                        (it as PreferenceKey).dependency != null && it.dependency!!.key == key || it.negativeDependency != null && it.negativeDependency!!.key == key
                    }.forEach {
                        list.add(it as PreferenceKey)
                    }
            }
        }

    override fun registerPreferences(clazz: Class<out NonPreferenceKey>) {
        if (clazz !in prefsList) prefsList.add(clazz)
    }

    private fun calculatedDefaultValue(key: IntPreferenceKey): Int =
        if (key.calculatedDefaultValue)
            when (key) {
                IntKey.AutosensPeriod ->
                    when (get(StringKey.SafetyAge)) {
                        hardLimits.get().ageEntryValues()[HardLimits.AgeType.TEENAGE.ordinal] -> 4
                        hardLimits.get().ageEntryValues()[HardLimits.AgeType.CHILD.ordinal]   -> 4
                        else                                                                  -> 24
                    }

                else                  -> error("Unsupported default value calculation")
            }
        else key.defaultValue

    private fun calculatedDefaultValue(key: LongPreferenceKey): Long =
        if (key.calculatedDefaultValue)
            when (key) {
                else -> error("Unsupported default value calculation")
            }
        else key.defaultValue

    private fun calculatedDefaultValue(key: BooleanPreferenceKey): Boolean =
        if (key.calculatedDefaultValue)
            when (key) {
                BooleanKey.OverviewKeepScreenOn                    -> config.AAPSCLIENT
                BooleanKey.NsClientNotificationsFromAlarms         -> config.AAPSCLIENT
                BooleanKey.NsClientNotificationsFromAnnouncements  -> config.AAPSCLIENT
                BooleanKey.NsClientLogAppStart                     -> config.APS
                BooleanKey.NsClientCreateAnnouncementsFromErrors   -> config.APS
                BooleanKey.NsClientCreateAnnouncementsFromCarbsReq -> config.APS
                else                                               -> error("Unsupported default value calculation")
            }
        else key.defaultValue

    private fun calculatePreference(key: DoublePreferenceKey): Double =
        limit(key, when (key) {
            DoubleKey.ApsMaxBasal  -> profileFunction.get().getProfile()?.getMaxDailyBasal()?.let { it * 3 } ?: key.defaultValue
            DoubleKey.ApsSmbMaxIob -> recentMaxBolus() + (profileFunction.get().getProfile()?.getMaxDailyBasal()?.let { it * 3 } ?: key.defaultValue)
            DoubleKey.ApsAmaMaxIob -> profileFunction.get().getProfile()?.getMaxDailyBasal()?.let { it * 3 } ?: key.defaultValue
            else                   -> error("Unsupported key calculation")
        })

    private fun limit(key: DoublePreferenceKey, calculated: Double) = min(key.max, max(key.min, calculated))
    private fun recentMaxBolus(): Double =
        persistenceLayer
            .getBolusesFromTime(dateUtil.now() - T.days(7).msecs(), true)
            .blockingGet()
            .maxOfOrNull { it.amount }
            ?: hardLimits.get().maxBolus()
}