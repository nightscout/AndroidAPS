package app.aaps.core.keys.interfaces

interface UnitDoublePreferenceKey : PreferenceKey {

    /**
     * Default value if not changed from preferences
     */
    val defaultValue: Double

    /**
     *  Minimal allowed value
     */
    val minMgdl: Int

    /**
     *  Maximal allowed value
     */
    val maxMgdl: Int
}