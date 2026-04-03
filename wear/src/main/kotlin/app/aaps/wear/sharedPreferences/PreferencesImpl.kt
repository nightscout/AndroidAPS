package app.aaps.wear.sharedPreferences

import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.SafeParse
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesImpl @Inject constructor(
    private val sp: SP
) : Preferences {

    override val simpleMode: Boolean = false
    override val apsMode: Boolean = false
    override val nsclientMode: Boolean = false
    override val pumpControlMode: Boolean = false

    private val prefsList: MutableList<Class<out NonPreferenceKey>> =
        mutableListOf(
            BooleanKey::class.java,
            BooleanNonKey::class.java,
            IntKey::class.java,
            IntNonKey::class.java,
            IntComposedKey::class.java,
            LongNonKey::class.java,
            LongComposedKey::class.java,
            DoubleKey::class.java,
            UnitDoubleKey::class.java,
            StringKey::class.java,
            StringNonKey::class.java,
            IntentKey::class.java,
        )

    private val booleanFlows = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()
    private val stringFlows = ConcurrentHashMap<String, MutableStateFlow<String>>()
    private val doubleFlows = ConcurrentHashMap<String, MutableStateFlow<Double>>()
    private val intFlows = ConcurrentHashMap<String, MutableStateFlow<Int>>()
    private val longFlows = ConcurrentHashMap<String, MutableStateFlow<Long>>()

    override fun get(key: BooleanNonPreferenceKey): Boolean = sp.getBoolean(key.key, key.defaultValue)

    override fun getIfExists(key: BooleanNonPreferenceKey): Boolean? =
        if (sp.contains(key.key)) sp.getBoolean(key.key, key.defaultValue) else null

    override fun put(key: BooleanNonPreferenceKey, value: Boolean) {
        sp.putBoolean(key.key, value)
        booleanFlows[key.key]?.value = value
    }

    override fun observe(key: BooleanNonPreferenceKey): StateFlow<Boolean> =
        booleanFlows.getOrPut(key.key) { MutableStateFlow(get(key)) }

    override fun get(key: BooleanPreferenceKey): Boolean = sp.getBoolean(key.key, key.defaultValue)

    override fun get(key: StringNonPreferenceKey): String = sp.getString(key.key, key.defaultValue)

    override fun get(key: StringPreferenceKey): String = sp.getString(key.key, key.defaultValue)

    override fun getIfExists(key: StringNonPreferenceKey): String? =
        if (sp.contains(key.key)) sp.getString(key.key, key.defaultValue) else null

    override fun put(key: StringNonPreferenceKey, value: String) {
        sp.putString(key.key, value)
        stringFlows[key.key]?.value = value
    }

    override fun observe(key: StringNonPreferenceKey): StateFlow<String> =
        stringFlows.getOrPut(key.key) { MutableStateFlow(get(key)) }

    override fun get(key: DoublePreferenceKey): Double = sp.getDouble(key.key, key.defaultValue)

    override fun get(key: DoubleNonPreferenceKey): Double = sp.getDouble(key.key, key.defaultValue)

    override fun getIfExists(key: DoublePreferenceKey): Double? =
        if (sp.contains(key.key)) sp.getDouble(key.key, key.defaultValue) else null

    override fun put(key: DoubleNonPreferenceKey, value: Double) {
        sp.putDouble(key.key, value)
        doubleFlows[key.key]?.value = value
    }

    override fun observe(key: DoubleNonPreferenceKey): StateFlow<Double> =
        doubleFlows.getOrPut(key.key) { MutableStateFlow(get(key)) }

    override fun get(key: UnitDoublePreferenceKey): Double =
        error("Not implemented")
    //profileUtil.valueInCurrentUnitsDetect(sp.getDouble(key.key, key.defaultValue))

    override fun getIfExists(key: UnitDoublePreferenceKey): Double =
        error("Not implemented")
    //if (sp.contains(key.key)) sp.getDouble(key.key, key.defaultValue) else null

    override fun put(key: UnitDoublePreferenceKey, value: Double) {
        sp.putDouble(key.key, value)
    }

    override fun observe(key: UnitDoublePreferenceKey): StateFlow<Double> =
        MutableStateFlow(0.0) // Not used on wear

    override fun get(key: DoubleComposedNonPreferenceKey, vararg arguments: Any): Double =
        sp.getDouble(key.composeKey(*arguments), key.defaultValue)

    override fun getIfExists(key: DoubleComposedNonPreferenceKey, vararg arguments: Any): Double? =
        if (sp.contains(key.composeKey(*arguments))) sp.getDouble(key.composeKey(*arguments), key.defaultValue) else null

    override fun put(key: DoubleComposedNonPreferenceKey, vararg arguments: Any, value: Double) {
        sp.putDouble(key.composeKey(*arguments), value)
    }

    override fun observe(key: DoubleComposedNonPreferenceKey, vararg arguments: Any): StateFlow<Double> =
        doubleFlows.getOrPut(key.composeKey(*arguments)) { MutableStateFlow(get(key, *arguments)) }

    override fun get(key: IntNonPreferenceKey): Int = sp.getInt(key.key, key.defaultValue)

    override fun getIfExists(key: IntNonPreferenceKey): Int? =
        if (sp.contains(key.key)) sp.getInt(key.key, key.defaultValue) else null

    override fun put(key: IntNonPreferenceKey, value: Int) {
        sp.putInt(key.key, value)
        intFlows[key.key]?.value = value
    }

    override fun observe(key: IntNonPreferenceKey): StateFlow<Int> =
        intFlows.getOrPut(key.key) { MutableStateFlow(get(key)) }

    override fun inc(key: IntNonPreferenceKey) {
        sp.incInt(key.key)
        intFlows[key.key]?.let { it.value = get(key) }
    }

    override fun get(key: IntComposedNonPreferenceKey, vararg arguments: Any): Int =
        sp.getInt(key.composeKey(*arguments), key.defaultValue)

    override fun put(key: IntComposedNonPreferenceKey, vararg arguments: Any, value: Int) {
        sp.putInt(key.composeKey(*arguments), value)
    }

    override fun observe(key: IntComposedNonPreferenceKey, vararg arguments: Any): StateFlow<Int> =
        intFlows.getOrPut(key.composeKey(*arguments)) { MutableStateFlow(get(key, *arguments)) }

    override fun get(key: IntPreferenceKey): Int = sp.getInt(key.key, key.defaultValue)

    override fun get(key: LongNonPreferenceKey): Long = sp.getLong(key.key, key.defaultValue)

    override fun getIfExists(key: LongNonPreferenceKey): Long? =
        if (sp.contains(key.key)) sp.getLong(key.key, key.defaultValue) else null

    override fun put(key: LongNonPreferenceKey, value: Long) {
        sp.putLong(key.key, value)
        longFlows[key.key]?.value = value
    }

    override fun observe(key: LongNonPreferenceKey): StateFlow<Long> =
        longFlows.getOrPut(key.key) { MutableStateFlow(get(key)) }

    override fun get(key: LongPreferenceKey): Long = sp.getLong(key.key, key.defaultValue)

    override fun inc(key: LongNonPreferenceKey) {
        sp.incLong(key.key)
        longFlows[key.key]?.let { it.value = get(key) }
    }

    override fun remove(key: NonPreferenceKey) {
        sp.remove(key.key)
    }

    override fun get(key: LongComposedNonPreferenceKey, vararg arguments: Any): Long =
        sp.getLong(key.composeKey(*arguments), key.defaultValue)

    override fun getIfExists(key: LongComposedNonPreferenceKey, vararg arguments: Any): Long? =
        if (sp.contains(key.composeKey(*arguments))) sp.getLong(key.composeKey(*arguments), key.defaultValue) else null

    override fun put(key: LongComposedNonPreferenceKey, vararg arguments: Any, value: Long) {
        sp.putLong(key.composeKey(*arguments), value)
    }

    override fun observe(key: LongComposedNonPreferenceKey, vararg arguments: Any): StateFlow<Long> =
        longFlows.getOrPut(key.composeKey(*arguments)) { MutableStateFlow(get(key, *arguments)) }

    override fun remove(key: ComposedKey, vararg arguments: Any) {
        sp.remove(key.composeKey(*arguments))
    }

    override fun isUnitDependent(key: String): Boolean =
        prefsList
            .flatMap { it.enumConstants!!.asIterable() }
            .filterIsInstance<UnitDoublePreferenceKey>()
            .any { it.key == key }

    override fun get(key: String): NonPreferenceKey? =
        prefsList
            .flatMap { it.enumConstants!!.asIterable() }
            .find { it.key == key }

    override fun getIfExists(key: String): NonPreferenceKey? =
        prefsList
            .flatMap { it.enumConstants!!.asIterable() }
            .find { it.key == key }

    override fun get(key: BooleanComposedNonPreferenceKey, vararg arguments: Any): Boolean =
        sp.getBoolean(key.composeKey(*arguments), key.defaultValue)

    override fun get(key: BooleanComposedNonPreferenceKey, vararg arguments: Any, defaultValue: Boolean): Boolean =
        sp.getBoolean(key.composeKey(*arguments), defaultValue)

    override fun getIfExists(key: BooleanComposedNonPreferenceKey, vararg arguments: Any): Boolean? =
        if (sp.contains(key.composeKey(*arguments))) sp.getBoolean(key.composeKey(*arguments), key.defaultValue) else null

    override fun put(key: BooleanComposedNonPreferenceKey, vararg arguments: Any, value: Boolean) {
        sp.putBoolean(key.composeKey(*arguments), value)
    }

    override fun observe(key: BooleanComposedNonPreferenceKey, vararg arguments: Any): StateFlow<Boolean> =
        booleanFlows.getOrPut(key.composeKey(*arguments)) { MutableStateFlow(get(key, *arguments)) }

    override fun get(key: StringComposedNonPreferenceKey, vararg arguments: Any): String =
        sp.getString(key.composeKey(*arguments), key.defaultValue)

    override fun getIfExists(key: StringComposedNonPreferenceKey, vararg arguments: Any): String? =
        if (sp.contains(key.composeKey(*arguments))) sp.getString(key.composeKey(*arguments), key.defaultValue) else null

    override fun put(key: StringComposedNonPreferenceKey, vararg arguments: Any, value: String) {
        sp.putString(key.composeKey(*arguments), value)
    }

    override fun observe(key: StringComposedNonPreferenceKey, vararg arguments: Any): StateFlow<String> =
        stringFlows.getOrPut(key.composeKey(*arguments)) { MutableStateFlow(get(key, *arguments)) }

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
                if (it.key == key) return true
                if (it is ComposedKey && key.startsWith(it.key)) return true
            }
        return false
    }

    override fun getAllPreferenceKeys(): List<PreferenceKey> =
        prefsList
            .filter { PreferenceKey::class.java.isAssignableFrom(it) }
            .flatMap { it.enumConstants!!.asIterable() }
            .filterIsInstance<PreferenceKey>()
}