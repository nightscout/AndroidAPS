package app.aaps.core.keys

interface BooleanPreferenceKey : PreferenceKey {

    /**
     * Default value if not changed from preferences
     */
    val defaultValue: Boolean
}