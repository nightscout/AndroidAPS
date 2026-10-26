package app.aaps.core.ui.locale

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import app.aaps.core.interfaces.resources.LanguageTag
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import java.util.Locale

object LocaleHelper {

    /**
     * The settings store the picked language is read from.
     *
     * Set by the app at the start of `Application.onCreate`. Android runs that before it creates any
     * activity, so it is set before the first `attachBaseContext`, which is where injection is not
     * possible yet. Until then, and in a test that does not set it, the phone language is used.
     *
     * The store, not its file: the store decides where the settings are kept, and a direct read of a
     * file the store no longer writes would keep returning an old language.
     */
    @Volatile var settings: KeyValueStore? = null

    private fun selectedLanguage(): String {
        val store = settings ?: return "default"
        return if (store.getBoolean(BooleanKey.GeneralSimpleMode.key, BooleanKey.GeneralSimpleMode.defaultValue)) "default"
        else store.getString(StringKey.GeneralLanguage.key, StringKey.GeneralLanguage.defaultValue)
    }

    // The language list has offered "dk" for Danish from the start, but "dk" is a country code,
    // not a language code. The ISO 639 code is "da", and every Danish translation lives in a
    // values-da-rDK folder. A stored "dk" matched no folder, so picking Danish showed the whole
    // app in English. The stored value is left as it is, because it is a preference that syncs
    // between master and client; it is corrected here, where the Locale is built.
    //
    // Read from LanguageTag rather than repeated here. This rule and the underscore one existed only
    // on Android, so the same four languages that work here were English on iOS and desktop; keeping
    // one copy is what stops that happening again.
    private fun isoLanguage(language: String): String = LanguageTag.isoLanguage(language)

    /**
     * True when the user did not pick a language, so resources must follow the phone.
     *
     * Ask this instead of comparing a locale with [Locale.getDefault]. [update] and [wrap] both move
     * the default locale to the selected language, so after either of them has run such a comparison
     * says "already right" for a context that is still in the phone language.
     */
    fun isSystemDefault(): Boolean = selectedLanguage() == "default"

    fun currentLocale(): Locale {
        val language = selectedLanguage()
        if (language == "default") return Locale.getDefault()

        return if (language.contains("_")) {
            // language with country like pt_BR defined in arrays.xml
            val lang = language.substring(0, 2)
            val country = language.substring(3, 5)
            Locale.Builder().setLanguage(lang).setRegion(country).build()
        } else {
            Locale.Builder().setLanguage(isoLanguage(language)).build()
        }
    }

    fun update() {
        // no action for system default language
        if (isSystemDefault()) return

        val locale = currentLocale()
        Locale.setDefault(locale)
    }

    fun wrap(ctx: Context): Context {
        // no action for system default language
        if (isSystemDefault()) return ctx

        val configuration = Configuration(ctx.resources.configuration)
        val newLocale = currentLocale()
        configuration.setLocale(newLocale)
        val localeList = LocaleList(newLocale)
        LocaleList.setDefault(localeList)
        configuration.setLocales(localeList)
        val context = ctx.createConfigurationContext(configuration)
        return ContextWrapper(context)
    }
}
