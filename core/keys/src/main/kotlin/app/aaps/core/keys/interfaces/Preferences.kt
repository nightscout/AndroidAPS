package app.aaps.core.keys.interfaces

interface Preferences {

    /**
     * Are we in currently in SimpleMode ?
     */
    val simpleMode: Boolean

    /**
     * Are we in currently in APS build ?
     */
    val apsMode: Boolean

    /**
     * Are we in currently in NSClient build ?
     */
    val nsclientMode: Boolean

    /**
     * Are we in currently in PumpControl build ?
     */
    val pumpControlMode: Boolean

    /* BOOLEAN */

    /**
     * Get [String] value from [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.BooleanNonPreferenceKey] enum
     * @return value
     */
    fun get(key: BooleanNonPreferenceKey): Boolean

    /**
     * Get [Boolean] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [app.aaps.core.keys.interfaces.BooleanNonPreferenceKey] enum
     * @return value or null
     */
    fun getIfExists(key: BooleanNonPreferenceKey): Boolean?

    /**
     * Update [String] value in [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.BooleanNonPreferenceKey] enum
     * @param value value
     */
    fun put(key: BooleanNonPreferenceKey, value: Boolean)

    /**
     * Get [Boolean] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.BooleanPreferenceKey] enum
     * @return value
     */
    fun get(key: BooleanPreferenceKey): Boolean

    /**
     * Get [String] value from [android.content.SharedPreferences]
     * *
     * @param key [app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey] enum
     * @param arguments arguments to compose final key using String::format
     * @return value
     */
    fun get(key: BooleanComposedNonPreferenceKey, vararg arguments: Any): Boolean

    /**
     * Get [String] value from [android.content.SharedPreferences]
     * *
     * @param key [app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey] enum
     * @param arguments arguments to compose final key using String::format
     * @param defaultValue alternative default value
     * @return value
     */
    fun get(key: BooleanComposedNonPreferenceKey, vararg arguments: Any, defaultValue: Boolean): Boolean

    /**
     * Get [String] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey] enum
     * @param arguments arguments to compose final key using String::format
     * @return value or null
     */
    fun getIfExists(key: BooleanComposedNonPreferenceKey, vararg arguments: Any): Boolean?

    /**
     * Update [String] value in [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey] enum
     * @param arguments arguments to compose final key using String::format
     * @param value value
     */
    fun put(key: BooleanComposedNonPreferenceKey, vararg arguments: Any, value: Boolean)

    /**
     * Remove value from [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.PreferenceKey] enum
     * @param arguments arguments to compose final key using String::format
     */
    fun remove(key: ComposedKey, vararg arguments: Any)

    /* STRING */

    /**
     * Get [String] value from [android.content.SharedPreferences]
     *
     * @param key [StringNonPreferenceKey] enum
     * @return value
     */
    fun get(key: StringNonPreferenceKey): String

    /**
     * Get [String] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [StringNonPreferenceKey] enum
     * @return value or null
     */
    fun getIfExists(key: StringNonPreferenceKey): String?

    /**
     * Update [String] value in [android.content.SharedPreferences]
     *
     * @param key [StringNonPreferenceKey] enum
     * @param value value
     */
    fun put(key: StringNonPreferenceKey, value: String)

    /**
     * Get [String] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [StringPreferenceKey] enum
     * @return value
     */
    fun get(key: StringPreferenceKey): String

    /**
     * Get [String] value from [android.content.SharedPreferences]
     * *
     * @param key [app.aaps.core.keys.interfaces.StringComposedNonPreferenceKey] enum
     * @param arguments arguments to compose final key using String::format
     * @return value
     */
    fun get(key: StringComposedNonPreferenceKey, vararg arguments: Any): String

    /**
     * Get [String] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [app.aaps.core.keys.interfaces.StringComposedNonPreferenceKey] enum
     * @param arguments arguments to compose final key using String::format
     * @return value or null
     */
    fun getIfExists(key: StringComposedNonPreferenceKey, vararg arguments: Any): String?

    /**
     * Update [String] value in [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.StringComposedNonPreferenceKey] enum
     * @param arguments arguments to compose final key using String::format
     * @param value value
     */
    fun put(key: StringComposedNonPreferenceKey, vararg arguments: Any, value: String)

    /* DOUBLE */

    /**
     * Get [Double] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.DoubleNonPreferenceKey] enum
     * @return value
     */
    fun get(key: DoubleNonPreferenceKey): Double

    /**
     * Get [Double] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.DoublePreferenceKey] enum
     * @return value
     */
    fun get(key: DoublePreferenceKey): Double

    /**
     * Get [Double] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [app.aaps.core.keys.interfaces.DoublePreferenceKey] enum
     * @return value or null
     */
    fun getIfExists(key: DoublePreferenceKey): Double?

    /**
     * Update [Double] value in [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.DoubleNonPreferenceKey] enum
     * @param value value
     */
    fun put(key: DoubleNonPreferenceKey, value: Double)

    /**
     * Get [String] value from [android.content.SharedPreferences]
     * *
     * @param key [app.aaps.core.keys.interfaces.DoubleComposedNonPreferenceKey] enum
     * @param arguments arguments to compose final key using String::format
     * @return value
     */
    fun get(key: DoubleComposedNonPreferenceKey, vararg arguments: Any): Double

    /**
     * Get [String] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [app.aaps.core.keys.interfaces.DoubleComposedNonPreferenceKey] enum
     * @param arguments arguments to compose final key using String::format
     * @return value or null
     */
    fun getIfExists(key: DoubleComposedNonPreferenceKey, vararg arguments: Any): Double?

    /**
     * Update [String] value in [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.DoubleComposedNonPreferenceKey] enum
     * @param arguments arguments to compose final key using String::format
     * @param value value
     */
    fun put(key: DoubleComposedNonPreferenceKey, vararg arguments: Any, value: Double)

    /* UNIT DOUBLE */

    /**
     * Get [Double] value from [android.content.SharedPreferences] converted to current units
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [UnitDoublePreferenceKey] enum
     * @return value
     */
    fun get(key: UnitDoublePreferenceKey): Double

    /**
     * Get [Double] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [UnitDoublePreferenceKey] enum
     * @return value or null
     */
    fun getIfExists(key: UnitDoublePreferenceKey): Double?

    /**
     * Update [Double] value in [android.content.SharedPreferences]
     *
     * @param key [UnitDoublePreferenceKey] enum
     * @param value value
     */
    fun put(key: UnitDoublePreferenceKey, value: Double)

    /* INT */

    /**
     * Get [Int] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.IntNonPreferenceKey] enum
     * @return value
     */
    fun get(key: IntNonPreferenceKey): Int

    /**
     * Get [Int] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [app.aaps.core.keys.interfaces.IntNonPreferenceKey] enum
     * @return value or null
     */
    fun getIfExists(key: IntNonPreferenceKey): Int?

    /**
     * Update [Int] value in [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.IntComposedNonPreferenceKey] enum
     * @param arguments arguments to compose final key using String::format
     * @param value value
     */
    fun put(key: IntComposedNonPreferenceKey, vararg arguments: Any, value: Int)

    /**
     * Update [Int] value in [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.IntNonPreferenceKey] enum
     * @param value value
     */
    fun put(key: IntNonPreferenceKey, value: Int)

    /**
     * Increment [Int] value in [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.IntNonPreferenceKey] enum
     * @param value value
     */
    fun inc(key: IntNonPreferenceKey)

    /**
     * Get [Int] value from [android.content.SharedPreferences]
     * *
     * @param key [app.aaps.core.keys.interfaces.IntComposedNonPreferenceKey] enum
     * @param arguments arguments to compose final key using String::format
     * @return value
     */
    fun get(key: IntComposedNonPreferenceKey, vararg arguments: Any): Int

    /**
     * Get [Int] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.IntPreferenceKey] enum
     * @return value
     */
    fun get(key: IntPreferenceKey): Int

    /* LONG */

    /**
     * Get [Long] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.LongNonPreferenceKey] enum
     * @return value
     */
    fun get(key: LongNonPreferenceKey): Long

    /**
     * Get [Long] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [app.aaps.core.keys.interfaces.LongNonPreferenceKey] enum
     * @return value or null
     */
    fun getIfExists(key: LongNonPreferenceKey): Long?

    /**
     * Update [Long] value in [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.LongNonPreferenceKey] enum
     * @param value value
     */
    fun put(key: LongNonPreferenceKey, value: Long)

    /**
     * Get [Long] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.LongPreferenceKey] enum
     * @return value
     */
    fun get(key: LongPreferenceKey): Long

    /**
     * Increment [Long] value in [android.content.SharedPreferences]
     *
     * @param key [LongNonPreferenceKey] enum
     * @param value value
     */
    fun inc(key: LongNonPreferenceKey)

    /**
     * Get [Long] value from [android.content.SharedPreferences]
     * *
     * @param key [app.aaps.core.keys.interfaces.LongComposedNonPreferenceKey] enum
     * @param arguments arguments to compose final key using String::format
     * @return value
     */
    fun get(key: LongComposedNonPreferenceKey, vararg arguments: Any): Long

    /**
     * Get [Long] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [app.aaps.core.keys.interfaces.LongComposedNonPreferenceKey] enum
     * @param arguments arguments to compose final key using String::format
     * @return value or null
     */
    fun getIfExists(key: LongComposedNonPreferenceKey, vararg arguments: Any): Long?

    /**
     * Update [Long] value in [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.LongComposedNonPreferenceKey] enum
     * @param arguments arguments to compose final key using String::format
     * @param value value
     */
    fun put(key: LongComposedNonPreferenceKey, vararg arguments: Any, value: Long)

    /* GENERAL */

    /**
     * Remove value from [android.content.SharedPreferences]
     *
     * @param key [app.aaps.core.keys.interfaces.PreferenceKey] enum
     */
    fun remove(key: NonPreferenceKey)

    /**
     * @param key string representation of key
     * @return true if key is unit dependent
     */
    fun isUnitDependent(key: String): Boolean

    /**
     * Find [app.aaps.core.keys.interfaces.NonPreferenceKey] definition
     * @param key string representation of key
     * @return [app.aaps.core.keys.interfaces.NonPreferenceKey] or null for [ComposedKey]
     */
    fun get(key: String): NonPreferenceKey?

    /**
     * Find [app.aaps.core.keys.interfaces.NonPreferenceKey] definition
     * @param key string representation of key
     * @return [app.aaps.core.keys.interfaces.NonPreferenceKey] or null
     */
    fun getIfExists(key: String): NonPreferenceKey?

    /**
     * Find all [app.aaps.core.keys.interfaces.PreferenceKey] which have `dependency` or `negativeDependency`
     * @param key string representation of key
     * @return list of [app.aaps.core.keys.interfaces.PreferenceKey]
     */
    fun getDependingOn(key: String): List<PreferenceKey>

    /**
     * Make new class available to Preference system
     * Called from PluginBase::init
     */
    fun registerPreferences(clazz: Class<out NonPreferenceKey>)

    /**
     * List all stored preferences formatters
     * Note: only single formatting "%s" parameter is supported
     */
    fun allMatchingStrings(key: ComposedKey): List<String>

    /**
     * List all stored preferences formatters
     * Note: only single formatting "%d" parameter is supported
     */
    fun allMatchingInts(key: ComposedKey): List<Int>

    /**
     * Check if the key string looks like a valid registered key and is set exportable
     * ie it does match exactly
     * or has a valid registered prefix
     * @return true if exportable key
     */
    fun isExportableKey(key: String): Boolean
}