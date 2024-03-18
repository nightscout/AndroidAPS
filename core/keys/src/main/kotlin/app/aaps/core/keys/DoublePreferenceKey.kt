package app.aaps.core.keys

interface DoublePreferenceKey : PreferenceKey {

    /**
     * Default value if not changed from preferences
     */
    val defaultValue: Double

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