package app.aaps.core.keys.interfaces

interface BooleanPreferenceKey : PreferenceKey, BooleanNonPreferenceKey {

    /**
     * Default value if not changed from preferences
     */
    override val defaultValue: Boolean

    /**
     * Default value is calculated instead of `defaultValue`
     */
    val calculatedDefaultValue: Boolean

    /**
     * Visible in engineering mode only, otherwise `defaultValue`
     */
    val engineeringModeOnly: Boolean
}