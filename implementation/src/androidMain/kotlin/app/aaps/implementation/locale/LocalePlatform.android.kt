package app.aaps.implementation.locale

import android.content.res.Resources

/**
 * Deliberately `Resources.getSystem()`, not `Locale.getDefault()`.
 *
 * `getSystem()` is the **device's** configuration. `Locale.getDefault()` follows the language AAPS
 * itself is running in, which `LocaleHelper` overrides from a preference - so a phone set to Chinese
 * with AAPS displayed in English would answer differently. The two agree for most users and disagree
 * for exactly the ones this setting exists for, so the original reading is kept.
 */
internal actual object LocalePlatform {

    private val locale get() = Resources.getSystem().configuration.locales[0]

    actual val language: String get() = locale.language
    actual val country: String get() = locale.country
}
