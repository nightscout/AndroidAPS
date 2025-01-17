package app.aaps.core.keys

interface IntPreferenceKey : PreferenceKey {

    /**
     * Default value if not changed from preferences
     */
    val defaultValue: Int

    /**
     *  Minimal allowed value
     */
    val min: Int

    /**
     *  Maximal allowed value
     */
    val max: Int

    /**
     *  Value with calculation of default value
     */
    val calculatedDefaultValue: Boolean

    /**
     * Visible in engineering mode only, otherwise `defaultValue`
     */
    val engineeringModeOnly: Boolean
}