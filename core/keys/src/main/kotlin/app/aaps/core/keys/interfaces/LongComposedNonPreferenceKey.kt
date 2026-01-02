package app.aaps.core.keys.interfaces

import java.util.Locale

/**
 * Preference key where key is a format string see [String::format]
 *
 * Final key is composed as key + String.format(Locale.ENGLISH, format, *arguments)
 */
interface LongComposedNonPreferenceKey : NonPreferenceKey, ComposedKey {

    /**
     * Key is used as prefix for recognizing the preference
     *
     * Final key is composed as key + String.format(Locale.ENGLISH, format, *arguments)
     */
    override val key: String

    /**
     * String used to format vararg
     */
    override val format: String

    /**
     * Default value
     */
    val defaultValue: Long
}