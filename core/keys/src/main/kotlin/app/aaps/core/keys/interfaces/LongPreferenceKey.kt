package app.aaps.core.keys.interfaces

interface LongPreferenceKey : PreferenceKey, LongNonPreferenceKey {

    /**
     * Default value if not changed from preferences
     */
    override val defaultValue: Long

    /**
     *  Minimal allowed value
     */
    val min: Long

    /**
     *  Maximal allowed value
     */
    val max: Long

    /**
     *  Value with calculation of default value
     */
    val calculatedDefaultValue: Boolean

    /**
     * Visible in engineering mode only, otherwise `defaultValue`
     */
    val engineeringModeOnly: Boolean
}