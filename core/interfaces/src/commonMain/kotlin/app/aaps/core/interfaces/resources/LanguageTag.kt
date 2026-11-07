package app.aaps.core.interfaces.resources

/**
 * Turns the stored language preference into a BCP 47 tag.
 *
 * The values behind `StringKey.GeneralLanguage` are not tags and never were. Two of them need
 * fixing up, and both were fixed up only on Android:
 *
 *  - **`dk`** is a country code, not a language code. Danish is `da` in ISO 639, and every Danish
 *    translation lives under `da`. The stored value is left alone because it syncs between master
 *    and client; it is corrected here, where the tag is built.
 *  - **`pt_BR`, `zh_TW`, `zh_CN`** use an underscore, which is the Java `Locale.toString()` form,
 *    not a tag. BCP 47 wants a hyphen.
 *
 * Android's `LocaleHelper` has always applied both rules while building a `java.util.Locale`. The
 * iOS and desktop shells passed the stored value straight to [TextRefValueRegistry.locale], which
 * matches nothing for those four - so choosing Danish, Brazilian Portuguese or either Chinese gave a
 * fully English app on those platforms, indistinguishable from "not translated yet", with nothing
 * logged. This is the one place the rules live now; `LocaleHelper` reads them from here too.
 *
 * Anything already a plain language code passes through unchanged, so adding a language to the list
 * needs no change here unless it repeats one of the two mistakes above.
 */
object LanguageTag {

    /** What the preference holds when the user has not chosen a language. */
    const val FOLLOW_DEVICE = "default"

    /**
     * The BCP 47 tag for [stored], or null when it means "follow the device".
     *
     * Null rather than a device lookup, because what "the device language" is differs per platform
     * and only the caller knows how to ask.
     */
    fun of(stored: String): String? =
        when {
            stored == FOLLOW_DEVICE -> null
            stored.contains('_')    -> stored.replace('_', '-')
            else                    -> isoLanguage(stored)
        }

    /** The ISO 639 code for a stored value that is not one. */
    fun isoLanguage(stored: String): String = if (stored == DANISH_STORED) DANISH_ISO else stored

    private const val DANISH_STORED = "dk"
    private const val DANISH_ISO = "da"
}
