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
     * @param key [BooleanKeys] enum
     * @return value
     */
    fun get(key: BooleanKeys): Boolean

    /**
     * Get [Boolean] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [BooleanKeys] enum
     * @return value or null
     */
    fun getIfExists(key: BooleanKeys): Boolean?

    /**
     * Update [Boolean] value in [android.content.SharedPreferences]
     *
     * @param key [BooleanKeys] enum
     * @param value value
     */
    fun put(key: BooleanKeys, value: Boolean)

    /**
     * Get [String] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [StringKeys] enum
     * @return value
     */
    fun get(key: StringKeys): String

    /**
     * Get [String] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [StringKeys] enum
     * @return value or null
     */
    fun getIfExists(key: StringKeys): String?

    /**
     * Update [String] value in [android.content.SharedPreferences]
     *
     * @param key [StringKeys] enum
     * @param value value
     */
    fun put(key: StringKeys, value: String)

    /**
     * Get [Double] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [DoubleKeys] enum
     * @return value
     */
    fun get(key: DoubleKeys): Double

    /**
     * Get [Double] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [DoubleKeys] enum
     * @return value or null
     */
    fun getIfExists(key: DoubleKeys): Double?

    /**
     * Update [Double] value in [android.content.SharedPreferences]
     *
     * @param key [DoubleKeys] enum
     * @param value value
     */
    fun put(key: DoubleKeys, value: Double)

    /**
     * Get [Int] value from [android.content.SharedPreferences]
     * In SimpleMode return default value
     * In FullMode return value from [android.content.SharedPreferences]
     *
     * @param key [IntKeys] enum
     * @return value
     */
    fun get(key: IntKeys): Int

    /**
     * Get [Int] value from [android.content.SharedPreferences] or null if doesn't exist
     *
     * @param key [IntKeys] enum
     * @return value or null
     */
    fun getIfExists(key: IntKeys): Int?

    /**
     * Update [Int] value in [android.content.SharedPreferences]
     *
     * @param key [IntKeys] enum
     * @param value value
     */
    fun put(key: IntKeys, value: Int)

    /**
     * Remove value from [android.content.SharedPreferences]
     *
     * @param key [Keys] enum
     */
    fun remove(key: Keys)
}