package app.aaps.core.keys.interfaces

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
}