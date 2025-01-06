package app.aaps.core.keys

interface StringPreferenceKey : PreferenceKey {

    /**
     * Default value if not changed from preferences
     */
    val defaultValue: String
    val isPassword: Boolean
    val isPin: Boolean
}