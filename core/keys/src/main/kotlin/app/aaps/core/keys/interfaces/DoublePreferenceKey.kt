package app.aaps.core.keys.interfaces

interface DoublePreferenceKey : PreferenceKey, DoubleNonPreferenceKey {

    /**
     * Default value if not changed from preferences
     */
    override val defaultValue: Double

    /**
     *  Minimal allowed value
     */
    val min: Double

    /**
     *  Maximal allowed value
     */
    val max: Double

    /**
     *  Value with calculation in simple mode
     */
    val calculatedBySM: Boolean
}