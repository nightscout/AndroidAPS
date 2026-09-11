package app.aaps.core.ui.locale

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import app.aaps.core.interfaces.resources.LanguageTag
import java.util.Locale

object LocaleHelper {

    private fun defaultSharedPreferences(context: Context) =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    private fun selectedLanguage(context: Context): String =
        // do not use the key name "language" from :core:keys here, to avoid a module dependency
        if (defaultSharedPreferences(context).getBoolean("simple_mode", true)) "default"
        else defaultSharedPreferences(context).getString("language", "default")
            ?: "default"
    // injection not possible because of use in attachBaseContext
    //preferences.get(R.string.key_language, Locale.getDefault().language)

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

    fun currentLocale(context: Context): Locale {
        val language = selectedLanguage(context)
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

    fun update(context: Context) {
        // no action for system default language
        if (selectedLanguage(context) == "default") return

        val locale = currentLocale(context)
        Locale.setDefault(locale)
    }

    fun wrap(ctx: Context): Context {
        // no action for system default language
        if (selectedLanguage(ctx) == "default") return ctx

        val configuration = Configuration(ctx.resources.configuration)
        val newLocale = currentLocale(ctx)
        configuration.setLocale(newLocale)
        val localeList = LocaleList(newLocale)
        LocaleList.setDefault(localeList)
        configuration.setLocales(localeList)
        val context = ctx.createConfigurationContext(configuration)
        return ContextWrapper(context)
    }
}
