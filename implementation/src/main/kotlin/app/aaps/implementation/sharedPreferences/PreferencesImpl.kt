package app.aaps.implementation.sharedPreferences

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntComposedKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.IntentKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.LongNonKey

import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.ComposedKey
import app.aaps.core.keys.interfaces.DoubleComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.IntComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.IntNonPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.LongComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.LongNonPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import dagger.Lazy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
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
    private val dateUtil: DateUtil,
) : Preferences {

    override val simpleMode: Boolean get() = sp.getBoolean(BooleanKey.GeneralSimpleMode.key, BooleanKey.GeneralSimpleMode.defaultValue)
    override val apsMode: Boolean = config.APS
    override val nsclientMode: Boolean = config.AAPSCLIENT
    override val pumpControlMode: Boolean = config.PUMPCONTROL

    private val prefsList: MutableList<Class<out NonPreferenceKey>> =
        mutableListOf(
            BooleanComposedKey::class.java,
            BooleanKey::class.java,
            BooleanNonKey::class.java,
            DoubleKey::class.java,
            IntentKey::class.java,
            IntKey::class.java,
            IntComposedKey::class.java,
            IntNonKey::class.java,
            LongComposedKey::class.java,
            LongNonKey::class.java,
            StringKey::class.java,
            StringNonKey::class.java,
            UnitDoubleKey::class.java,
        )

    private val booleanFlows = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()
    private val stringFlows = ConcurrentHashMap<String, MutableStateFlow<String>>()
    private val doubleFlows = ConcurrentHashMap<String, MutableStateFlow<Double>>()
    private val unitDoubleFlows = ConcurrentHashMap<String, MutableStateFlow<Double>>()
    private val intFlows = ConcurrentHashMap<String, MutableStateFlow<Int>>()
    private val longFlows = ConcurrentHashMap<String, MutableStateFlow<Long>>()

    private fun isHidden(key: PreferenceKey): Boolean =
        if (apsMode && key.showInApsMode == false) true
        else if (nsclientMode && key.showInNsClientMode == false) true
        else if (pumpControlMode && key.showInPumpControlMode == false) true
        else false

    override fun get(key: BooleanNonPreferenceKey): Boolean =
        sp.getBoolean(key.key, key.defaultValue)

    override fun getIfExists(key: BooleanNonPreferenceKey): Boolean? =
        if (sp.contains(key.key)) sp.getBoolean(key.key, key.defaultValue) else null

    override fun put(key: BooleanNonPreferenceKey, value: Boolean) {
        sp.putBoolean(key.key, value)
        booleanFlows[key.key]?.value = value

    }

    override fun observe(key: BooleanNonPreferenceKey): StateFlow<Boolean> =
        booleanFlows.computeIfAbsent(key.key) { MutableStateFlow(get(key)) }

    override fun get(key: BooleanPreferenceKey): Boolean =
        if (!config.isEngineeringMode() && key.engineeringModeOnly) key.defaultValue
        else if (simpleMode && key.defaultedBySM) calculatedDefaultValue(key)
        else if (key.calculatedDefaultValue && isHidden(key)) calculatedDefaultValue(key)
        else sp.getBoolean(key.key, calculatedDefaultValue(key))

    override fun get(key: StringNonPreferenceKey): String =
        sp.getString(key.key, key.defaultValue)

    override fun get(key: StringPreferenceKey): String =
        if (simpleMode && key.defaultedBySM) key.defaultValue
        else sp.getString(key.key, key.defaultValue)

    override fun getIfExists(key: StringNonPreferenceKey): String? =
        if (sp.contains(key.key)) sp.getString(key.key, key.defaultValue) else null

    override fun put(key: StringNonPreferenceKey, value: String) {
        sp.putString(key.key, value)
        stringFlows[key.key]?.value = value

    }

    override fun observe(key: StringNonPreferenceKey): StateFlow<String> =
        stringFlows.computeIfAbsent(key.key) { MutableStateFlow(get(key)) }

    override fun get(key: DoubleNonPreferenceKey): Double =
        sp.getDouble(key.key, key.defaultValue)

    override fun get(key: DoublePreferenceKey): Double =
        if (simpleMode && key.calculatedBySM) calculatePreference(key)
        else if (simpleMode && key.defaultedBySM) key.defaultValue
        else sp.getDouble(key.key, key.defaultValue)

    override fun getIfExists(key: DoublePreferenceKey): Double? =
        if (sp.contains(key.key)) sp.getDouble(key.key, key.defaultValue) else null

    override fun put(key: DoubleNonPreferenceKey, value: Double) {
        sp.putDouble(key.key, value)
        doubleFlows[key.key]?.value = value

    }

    override fun observe(key: DoubleNonPreferenceKey): StateFlow<Double> =
        doubleFlows.computeIfAbsent(key.key) { MutableStateFlow(get(key)) }

    override fun get(key: DoubleComposedNonPreferenceKey, vararg arguments: Any): Double =
        sp.getDouble(key.composeKey(*arguments), key.defaultValue)

    override fun getIfExists(key: DoubleComposedNonPreferenceKey, vararg arguments: Any): Double? =
        if (sp.contains(key.composeKey(*arguments))) sp.getDouble(key.composeKey(*arguments), key.defaultValue) else null

    override fun put(key: DoubleComposedNonPreferenceKey, vararg arguments: Any, value: Double) {
        val composedKey = key.composeKey(*arguments)
        sp.putDouble(composedKey, value)
        doubleFlows[composedKey]?.value = value

    }

    override fun observe(key: DoubleComposedNonPreferenceKey, vararg arguments: Any): StateFlow<Double> {
        val composedKey = key.composeKey(*arguments)
        return doubleFlows.computeIfAbsent(composedKey) { MutableStateFlow(get(key, *arguments)) }
    }

    override fun get(key: UnitDoublePreferenceKey): Double =
        if (simpleMode && key.defaultedBySM) profileUtil.get().valueInCurrentUnitsDetect(key.defaultValue)
        else profileUtil.get().valueInCurrentUnitsDetect(sp.getDouble(key.key, key.defaultValue))

    override fun getIfExists(key: UnitDoublePreferenceKey): Double? =
        if (sp.contains(key.key)) profileUtil.get().valueInCurrentUnitsDetect(sp.getDouble(key.key, key.defaultValue)) else null

    override fun put(key: UnitDoublePreferenceKey, value: Double) {
        sp.putDouble(key.key, value)
        unitDoubleFlows[key.key]?.value = get(key)

    }

    override fun observe(key: UnitDoublePreferenceKey): StateFlow<Double> =
        unitDoubleFlows.computeIfAbsent(key.key) { MutableStateFlow(get(key)) }

    override fun get(key: IntNonPreferenceKey): Int =
        sp.getInt(key.key, key.defaultValue)

    override fun getIfExists(key: IntNonPreferenceKey): Int? =
        if (sp.contains(key.key)) sp.getInt(key.key, key.defaultValue) else null

    override fun put(key: IntNonPreferenceKey, value: Int) {
        sp.putInt(key.key, value)
        intFlows[key.key]?.value = value

    }

    override fun observe(key: IntNonPreferenceKey): StateFlow<Int> =
        intFlows.computeIfAbsent(key.key) { MutableStateFlow(get(key)) }

    override fun inc(key: IntNonPreferenceKey) {
        sp.incInt(key.key)
        intFlows[key.key]?.let { it.value = get(key) }

    }

    override fun get(key: IntPreferenceKey): Int =
        if (!config.isEngineeringMode() && key.engineeringModeOnly) key.defaultValue
        else if (simpleMode && key.defaultedBySM) calculatedDefaultValue(key)
        else if (key.calculatedDefaultValue && isHidden(key)) calculatedDefaultValue(key)
        else sp.getInt(key.key, calculatedDefaultValue(key))

    override fun get(key: IntComposedNonPreferenceKey, vararg arguments: Any): Int =
        sp.getInt(key.composeKey(*arguments), key.defaultValue)

    override fun put(key: IntComposedNonPreferenceKey, vararg arguments: Any, value: Int) {
        val composedKey = key.composeKey(*arguments)
        sp.putInt(composedKey, value)
        intFlows[composedKey]?.value = value

    }

    override fun observe(key: IntComposedNonPreferenceKey, vararg arguments: Any): StateFlow<Int> {
        val composedKey = key.composeKey(*arguments)
        return intFlows.computeIfAbsent(composedKey) { MutableStateFlow(get(key, *arguments)) }
    }

    override fun get(key: LongNonPreferenceKey): Long =
        sp.getLong(key.key, key.defaultValue)

    override fun inc(key: LongNonPreferenceKey) {
        sp.incLong(key.key)
        longFlows[key.key]?.let { it.value = get(key) }

    }

    override fun getIfExists(key: LongNonPreferenceKey): Long? =
        if (sp.contains(key.key)) sp.getLong(key.key, key.defaultValue) else null

    override fun put(key: LongNonPreferenceKey, value: Long) {
        sp.putLong(key.key, value)
        longFlows[key.key]?.value = value

    }

    override fun observe(key: LongNonPreferenceKey): StateFlow<Long> =
        longFlows.computeIfAbsent(key.key) { MutableStateFlow(get(key)) }

    override fun get(key: LongPreferenceKey): Long =
        if (!config.isEngineeringMode() && key.engineeringModeOnly) key.defaultValue
        else if (simpleMode && key.defaultedBySM) calculatedDefaultValue(key)
        else if (key.calculatedDefaultValue && isHidden(key)) calculatedDefaultValue(key)
        else sp.getLong(key.key, calculatedDefaultValue(key))

    override fun remove(key: NonPreferenceKey) {
        sp.remove(key.key)
    }

    override fun get(key: LongComposedNonPreferenceKey, vararg arguments: Any): Long =
        sp.getLong(key.composeKey(*arguments), key.defaultValue)

    override fun getIfExists(key: LongComposedNonPreferenceKey, vararg arguments: Any): Long? =
        if (sp.contains(key.composeKey(*arguments))) sp.getLong(key.composeKey(*arguments), key.defaultValue) else null

    override fun put(key: LongComposedNonPreferenceKey, vararg arguments: Any, value: Long) {
        val composedKey = key.composeKey(*arguments)
        sp.putLong(composedKey, value)
        longFlows[composedKey]?.value = value

    }

    override fun observe(key: LongComposedNonPreferenceKey, vararg arguments: Any): StateFlow<Long> {
        val composedKey = key.composeKey(*arguments)
        return longFlows.computeIfAbsent(composedKey) { MutableStateFlow(get(key, *arguments)) }
    }

    override fun remove(key: ComposedKey, vararg arguments: Any) {
        sp.remove(key.composeKey(*arguments))
    }

    override fun isUnitDependent(key: String): Boolean =
        UnitDoubleKey.entries.any { it.key == key }

    override fun get(key: String): NonPreferenceKey? =
        prefsList
            .flatMap { it.enumConstants!!.asIterable() }
            .find { it.key == key }

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

    override fun get(key: BooleanComposedNonPreferenceKey, vararg arguments: Any): Boolean =
        sp.getBoolean(key.composeKey(*arguments), key.defaultValue)

    override fun get(key: BooleanComposedNonPreferenceKey, vararg arguments: Any, defaultValue: Boolean): Boolean =
        sp.getBoolean(key.composeKey(*arguments), defaultValue)

    override fun getIfExists(key: BooleanComposedNonPreferenceKey, vararg arguments: Any): Boolean? =
        if (sp.contains(key.composeKey(*arguments))) sp.getBoolean(key.composeKey(*arguments), key.defaultValue) else null

    override fun put(key: BooleanComposedNonPreferenceKey, vararg arguments: Any, value: Boolean) {
        val composedKey = key.composeKey(*arguments)
        sp.putBoolean(composedKey, value)
        booleanFlows[composedKey]?.value = value

    }

    override fun observe(key: BooleanComposedNonPreferenceKey, vararg arguments: Any): StateFlow<Boolean> {
        val composedKey = key.composeKey(*arguments)
        return booleanFlows.computeIfAbsent(composedKey) { MutableStateFlow(get(key, *arguments)) }
    }

    override fun get(key: StringComposedNonPreferenceKey, vararg arguments: Any): String =
        sp.getString(key.composeKey(*arguments), key.defaultValue)

    override fun getIfExists(key: StringComposedNonPreferenceKey, vararg arguments: Any): String? =
        if (sp.contains(key.composeKey(*arguments))) sp.getString(key.composeKey(*arguments), key.defaultValue) else null

    override fun put(key: StringComposedNonPreferenceKey, vararg arguments: Any, value: String) {
        val composedKey = key.composeKey(*arguments)
        sp.putString(composedKey, value)
        stringFlows[composedKey]?.value = value

    }

    override fun observe(key: StringComposedNonPreferenceKey, vararg arguments: Any): StateFlow<String> {
        val composedKey = key.composeKey(*arguments)
        return stringFlows.computeIfAbsent(composedKey) { MutableStateFlow(get(key, *arguments)) }
    }

    override fun registerPreferences(clazz: Class<out NonPreferenceKey>) {
        if (clazz !in prefsList) prefsList.add(clazz)
    }

    override fun allMatchingStrings(key: ComposedKey): List<String> =
        mutableListOf<String>().also {
            assert(key.format == "%s")
            val keys: Map<String, *> = sp.getAll()
            for ((singleKey, _) in keys)
                if (singleKey.startsWith(key.key)) it.add(singleKey.split(key.key)[1])
        }

    override fun allMatchingInts(key: ComposedKey): List<Int> =
        mutableListOf<Int>().also {
            assert(key.format == "%d")
            val keys: Map<String, *> = sp.getAll()
            for ((singleKey, _) in keys)
                if (singleKey.startsWith(key.key)) it.add(SafeParse.stringToInt(singleKey.split(key.key)[1]))
        }

    override fun isExportableKey(key: String): Boolean {
        prefsList
            .flatMap { it.enumConstants!!.asIterable() }
            .forEach {
                if (it.key == key && it.exportable) return true
                if (it is ComposedKey && key.startsWith(it.key) && it.exportable) return true
            }
        return false
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
        limit(
            key, when (key) {
                DoubleKey.ApsMaxBasal  -> runBlocking { profileFunction.get().getProfile() }?.getMaxDailyBasal()?.let { it * 3 } ?: key.defaultValue
                DoubleKey.ApsSmbMaxIob -> recentMaxBolus() + (runBlocking { profileFunction.get().getProfile() }?.getMaxDailyBasal()?.let { it * 3 } ?: key.defaultValue)
                DoubleKey.ApsAmaMaxIob -> runBlocking { profileFunction.get().getProfile() }?.getMaxDailyBasal()?.let { it * 3 } ?: key.defaultValue
                else                   -> error("Unsupported key calculation")
            })

    private fun limit(key: DoublePreferenceKey, calculated: Double) = min(key.max, max(key.min, calculated))
    private fun recentMaxBolus(): Double =
        runBlocking {
            persistenceLayer
                .getBolusesFromTime(dateUtil.now() - T.days(7).msecs(), true)
                .maxOfOrNull { it.amount }
                ?: hardLimits.get().maxBolus()
        }

    override fun getAllPreferenceKeys(): List<PreferenceKey> =
        prefsList
            .filter { PreferenceKey::class.java.isAssignableFrom(it) }
            .flatMap { it.enumConstants!!.asIterable() }
            .filterIsInstance<PreferenceKey>()
}