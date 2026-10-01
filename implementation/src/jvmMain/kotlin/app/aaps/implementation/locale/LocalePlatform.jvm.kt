package app.aaps.implementation.locale

import java.util.Locale

/**
 * The JVM default locale, which follows the machine's regional settings - the desktop counterpart of
 * `Resources.getSystem()` on Android, not the app's preferred language.
 *
 * A JVM locale can be language-only, so an absent country becomes empty and [ntpServerFor] falls
 * through to its default, exactly as an empty Android country code does.
 */
internal actual object LocalePlatform {

    actual val language: String get() = Locale.getDefault().language
    actual val country: String get() = Locale.getDefault().country
}
