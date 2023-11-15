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
     * @param key [BooleanKey] enum
     * @return value
     */
    fun get(key: BooleanKey): Boolean

    /**
     * Get [Boolean] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [BooleanKey] enum
     * @return value or null
     */
    fun getIfExists(key: BooleanKey): Boolean?

    /**
     * Update [Boolean] value in [android.content.SharedPreferences]
     *
     * @param key [BooleanKey] enum
     * @param value value
     */
    fun put(key: BooleanKey, value: Boolean)

    /**
     * Get [String] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [StringKey] enum
     * @return value
     */
    fun get(key: StringKey): String

    /**
     * Get [String] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [StringKey] enum
     * @return value or null
     */
    fun getIfExists(key: StringKey): String?

    /**
     * Update [String] value in [android.content.SharedPreferences]
     *
     * @param key [StringKey] enum
     * @param value value
     */
    fun put(key: StringKey, value: String)

    /**
     * Get [Double] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [DoubleKey] enum
     * @return value
     */
    fun get(key: DoubleKey): Double

    /**
     * Get [Double] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [DoubleKey] enum
     * @return value or null
     */
    fun getIfExists(key: DoubleKey): Double?

    /**
     * Update [Double] value in [android.content.SharedPreferences]
     *
     * @param key [DoubleKey] enum
     * @param value value
     */
    fun put(key: DoubleKey, value: Double)

    /**
     * Get [Double] value from [android.content.SharedPreferences] converted to current units
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [UnitDoubleKey] enum
     * @return value
     */
    fun get(key: UnitDoubleKey): Double

    /**
     * Get [Double] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [UnitDoubleKey] enum
     * @return value or null
     */
    fun getIfExists(key: UnitDoubleKey): Double?

    /**
     * Update [Double] value in [android.content.SharedPreferences]
     *
     * @param key [UnitDoubleKey] enum
     * @param value value
     */
    fun put(key: UnitDoubleKey, value: Double)

    /**
     * Get [Int] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [IntKey] enum
     * @return value
     */
    fun get(key: IntKey): Int

    /**
     * Get [Int] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [IntKey] enum
     * @return value or null
     */
    fun getIfExists(key: IntKey): Int?

    /**
     * Update [Int] value in [android.content.SharedPreferences]
     *
     * @param key [IntKey] enum
     * @param value value
     */
    fun put(key: IntKey, value: Int)

    /**
     * Remove value from [android.content.SharedPreferences]
     *
     * @param key [PreferenceKey] enum
     */
    fun remove(key: PreferenceKey)

    /**
     * @param key string representation of key
     * @return true if key is unit dependent
     */
    fun isUnitDependent(key: String): Boolean

    /**
     * Find [PreferenceKey] definition
     * @param key string representation of key
     * @return [PreferenceKey]
     */
    fun get(key: String): PreferenceKey
}