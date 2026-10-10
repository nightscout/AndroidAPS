package app.aaps.core.keys.interfaces

/**
 * Preference key where key is a format string see [String::format]
 *
 * Final key is composed as key + format, with %d and %s replaced by the arguments
 */
interface BooleanComposedNonPreferenceKey : NonPreferenceKey, ComposedKey {

    /**
     * Key is used as prefix for recognizing the preference
     *
     * Final key is composed as key + format, with %d and %s replaced by the arguments
     */
    override val key: String

    /**
     * String used to format vararg
     */
    override val format: String

    /**
     * Default value
     */
    val defaultValue: Boolean
}