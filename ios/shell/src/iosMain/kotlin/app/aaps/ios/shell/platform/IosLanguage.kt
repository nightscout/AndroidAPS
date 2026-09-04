package app.aaps.ios.shell.platform

import app.aaps.core.interfaces.resources.TextRefValueRegistry
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * Which language the app answers in on iOS.
 *
 * Android never needs this: the locale lives in `Resources`, so `rh.gs(...)` reads it without being
 * told and the setting is applied by recreating the activity. There is no `Resources` here, so the
 * same job is done by pointing [TextRefValueRegistry] at a locale - which is why no call site had to
 * change and `gs(ref)` still takes only a `TextRef`.
 *
 * Before this, only English text was generated at all, so the app was English whatever the phone or
 * the setting said.
 */
object IosLanguage {

    /** What [StringKey.GeneralLanguage] holds when the user has not chosen a language. */
    private const val FOLLOW_DEVICE = "default"

    /**
     * Points the registry at the language to use: the user's choice, or the phone's.
     *
     * Call before anything asks for text, and again whenever the setting changes.
     */
    fun apply(preferences: Preferences) {
        val chosen = preferences.get(StringKey.GeneralLanguage)
        TextRefValueRegistry.locale = if (chosen == FOLLOW_DEVICE) deviceLanguage() else chosen
    }

    /**
     * The phone's language as a BCP 47 tag, or null when iOS offers none.
     *
     * `preferredLanguages` rather than `currentLocale`: it is the ordered list the user actually set
     * in Settings, and its first entry is what every other app treats as their language. It already
     * uses tags like `cs-CZ`, which is the shape the generated translations are keyed by - and a bare
     * language resolves too, so `de` finds `de-DE`.
     */
    fun deviceLanguage(): String? = NSLocale.preferredLanguages.firstOrNull() as? String
}
