package app.aaps.core.keys

/**
 * Preference key with dynamic appendix
 */
interface String2PreferenceKey : PreferenceKey {

    /**
     * Default value if not changed from preferences
     */
    val defaultValue: String

    /**
     * Delimiter
     * Final key is composed as key + delimiter + dynamic_part
     */
    val delimiter: String
}