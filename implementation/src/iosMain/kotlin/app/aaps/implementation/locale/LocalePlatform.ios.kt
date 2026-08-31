package app.aaps.implementation.locale

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

/**
 * `currentLocale` is the device's locale, which is the iOS counterpart of `Resources.getSystem()` on
 * Android - not the app's preferred language.
 *
 * Both codes are optional on iOS (a locale can be language-only), so an absent one becomes empty and
 * [ntpServerFor] falls through to its default, exactly as an empty Android country code does.
 */
internal actual object LocalePlatform {

    actual val language: String get() = NSLocale.currentLocale.languageCode
    actual val country: String get() = NSLocale.currentLocale.countryCode ?: ""
}
