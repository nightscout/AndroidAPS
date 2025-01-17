package app.aaps.core.keys

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

    /**
     * Get [Boolean] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [BooleanPreferenceKey] enum
     * @return value
     */
    fun get(key: BooleanPreferenceKey): Boolean

    /**
     * Get [Boolean] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [BooleanPreferenceKey] enum
     * @return value or null
     */
    fun getIfExists(key: BooleanPreferenceKey): Boolean?

    /**
     * Update [Boolean] value in [android.content.SharedPreferences]
     *
     * @param key [BooleanPreferenceKey] enum
     * @param value value
     */
    fun put(key: BooleanPreferenceKey, value: Boolean)

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
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [String2PreferenceKey] enum
     * @param appendix appendix to compose final key as key + delimiter + appendix
     * @return value
     */
    fun get(key: String2PreferenceKey, appendix: String): String

    /**
     * Get [String] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [String2PreferenceKey] enum
     * @param appendix appendix to compose final key as key + delimiter + appendix
     * @return value or null
     */
    fun getIfExists(key: String2PreferenceKey, appendix: String): String?

    /**
     * Update [String] value in [android.content.SharedPreferences]
     *
     * @param key [String2PreferenceKey] enum
     * @param appendix appendix to compose final key as key + delimiter + appendix
     * @param value value
     */
    fun put(key: String2PreferenceKey, appendix: String, value: String)

    /**
     * Get [Double] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [DoublePreferenceKey] enum
     * @return value
     */
    fun get(key: DoublePreferenceKey): Double

    /**
     * Get [Double] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [DoublePreferenceKey] enum
     * @return value or null
     */
    fun getIfExists(key: DoublePreferenceKey): Double?

    /**
     * Update [Double] value in [android.content.SharedPreferences]
     *
     * @param key [DoublePreferenceKey] enum
     * @param value value
     */
    fun put(key: DoublePreferenceKey, value: Double)

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

    /**
     * Get [Int] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [IntPreferenceKey] enum
     * @return value
     */
    fun get(key: IntPreferenceKey): Int

    /**
     * Get [Int] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [IntPreferenceKey] enum
     * @return value or null
     */
    fun getIfExists(key: IntPreferenceKey): Int?

    /**
     * Update [Int] value in [android.content.SharedPreferences]
     *
     * @param key [IntPreferenceKey] enum
     * @param value value
     */
    fun put(key: IntPreferenceKey, value: Int)

    /**
     * Get [Long] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [LongPreferenceKey] enum
     * @return value
     */
    fun get(key: LongPreferenceKey): Long

    /**
     * Get [Long] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [LongPreferenceKey] enum
     * @return value or null
     */
    fun getIfExists(key: LongPreferenceKey): Long?

    /**
     * Update [Long] value in [android.content.SharedPreferences]
     *
     * @param key [LongPreferenceKey] enum
     * @param value value
     */
    fun put(key: LongPreferenceKey, value: Long)

    /**
     * Remove value from [android.content.SharedPreferences]
     *
     * @param key [PreferenceKey] enum
     */
    fun remove(key: NonPreferenceKey)

    /**
     * Remove value from [android.content.SharedPreferences]
     *
     * @param key [PreferenceKey] enum
     * @param appendix appendix to compose final key as key + delimiter + appendix
     */
    fun remove(key: String2PreferenceKey, appendix: String)

    /**
     * @param key string representation of key
     * @return true if key is unit dependent
     */
    fun isUnitDependent(key: String): Boolean

    /**
     * Find [NonPreferenceKey] definition
     * @param key string representation of key
     * @return [NonPreferenceKey]
     */
    fun get(key: String): NonPreferenceKey

    /**
     * Find [NonPreferenceKey] definition
     * @param key string representation of key
     * @return [NonPreferenceKey] or null
     */
    fun getIfExists(key: String): NonPreferenceKey?

    /**
     * Find all [PreferenceKey] which have `dependency` or `negativeDependency`
     * @param key string representation of key
     * @return list of [PreferenceKey]
     */
    fun getDependingOn(key: String): List<PreferenceKey>

    /**
     * Make new class available to Preference system
     * Called from PluginBase::init
     */
    fun registerPreferences(clazz: Class<out NonPreferenceKey>)
}