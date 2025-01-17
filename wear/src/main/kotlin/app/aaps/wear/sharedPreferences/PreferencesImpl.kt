package app.aaps.wear.sharedPreferences

import app.aaps.core.interfaces.sharedPreferences.SP
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
            IntKey::class.java,
            DoubleKey::class.java,
            UnitDoubleKey::class.java,
            StringKey::class.java,
            IntentKey::class.java,
        )

    override fun get(key: BooleanPreferenceKey): Boolean = sp.getBoolean(key.key, key.defaultValue)

    override fun getIfExists(key: BooleanPreferenceKey): Boolean? =
        if (sp.contains(key.key)) sp.getBoolean(key.key, key.defaultValue) else null

    override fun put(key: BooleanPreferenceKey, value: Boolean) {
        sp.putBoolean(key.key, value)
    }

    override fun get(key: StringNonPreferenceKey): String = sp.getString(key.key, key.defaultValue)

    override fun get(key: StringPreferenceKey): String = sp.getString(key.key, key.defaultValue)

    override fun getIfExists(key: StringNonPreferenceKey): String? =
        if (sp.contains(key.key)) sp.getString(key.key, key.defaultValue) else null

    override fun put(key: StringNonPreferenceKey, value: String) {
        sp.putString(key.key, value)
    }

    override fun get(key: String2PreferenceKey, appendix: String): String = sp.getString(key.key + key.delimiter + appendix, key.defaultValue)

    override fun getIfExists(key: String2PreferenceKey, appendix: String): String? =
        if (sp.contains(key.key + key.delimiter + appendix)) sp.getString(key.key + key.delimiter + appendix, key.defaultValue) else null

    override fun put(key: String2PreferenceKey, appendix: String, value: String) {
        sp.putString(key.key + key.delimiter + appendix, value)
    }

    override fun get(key: DoublePreferenceKey): Double = sp.getDouble(key.key, key.defaultValue)

    override fun getIfExists(key: DoublePreferenceKey): Double? =
        if (sp.contains(key.key)) sp.getDouble(key.key, key.defaultValue) else null

    override fun put(key: DoublePreferenceKey, value: Double) {
        sp.putDouble(key.key, value)
    }

    override fun get(key: UnitDoublePreferenceKey): Double =
        error("Not implemented")
    //profileUtil.valueInCurrentUnitsDetect(sp.getDouble(key.key, key.defaultValue))

    override fun getIfExists(key: UnitDoublePreferenceKey): Double? =
        if (sp.contains(key.key)) sp.getDouble(key.key, key.defaultValue) else null

    override fun put(key: UnitDoublePreferenceKey, value: Double) {
        sp.putDouble(key.key, value)
    }

    override fun get(key: IntPreferenceKey): Int = sp.getInt(key.key, key.defaultValue)

    override fun getIfExists(key: IntPreferenceKey): Int? =
        if (sp.contains(key.key)) sp.getInt(key.key, key.defaultValue) else null

    override fun put(key: IntPreferenceKey, value: Int) {
        sp.putInt(key.key, value)
    }

    override fun get(key: LongPreferenceKey): Long = sp.getLong(key.key, key.defaultValue)

    override fun getIfExists(key: LongPreferenceKey): Long? =
        if (sp.contains(key.key)) sp.getLong(key.key, key.defaultValue) else null

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
        prefsList
            .flatMap { it.enumConstants!!.asIterable() }
            .filterIsInstance<UnitDoublePreferenceKey>()
            .any { it.key == key }

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
}