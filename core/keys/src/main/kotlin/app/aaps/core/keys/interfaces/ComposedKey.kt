package app.aaps.core.keys.interfaces

import java.util.Locale

interface ComposedKey {

    /**
     * Key is used as prefix for recognizing the preference
     *
     * Final key is composed as key + String.format(Locale.ENGLISH, format, *arguments)
     */
    val key: String

    /**
     * String used to format vararg
     */
    val format: String

    /**
     * Compose final key from arguments
     */
    fun composeKey(vararg arguments: Any): String = String.format(Locale.ENGLISH, key + format, *arguments)
}