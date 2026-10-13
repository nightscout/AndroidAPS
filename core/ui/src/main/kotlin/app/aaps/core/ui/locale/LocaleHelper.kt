package app.aaps.core.ui.locale

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import androidx.preference.PreferenceManager
import java.util.Locale

object LocaleHelper {

    private fun selectedLanguage(context: Context): String =
        // do not use app.aaps.core.keys.R.strings.kay_language = "language" to avoid module dependency
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("simple_mode", true)) "default"
        else PreferenceManager.getDefaultSharedPreferences(context).getString("language", "default")
            ?: "default"
    // injection not possible because of use in attachBaseContext
    //preferences.get(R.string.key_language, Locale.getDefault().language)

    fun currentLocale(context: Context): Locale {
        val language = selectedLanguage(context)
        if (language == "default") return Locale.getDefault()

        return if (language.contains("_")) {
            // language with country like pt_BR defined in arrays.xml
            val lang = language.substring(0, 2)
            val country = language.substring(3, 5)
            Locale.Builder().setLanguage(lang).setRegion(country).build()
        } else {
            Locale.Builder().setLanguage(language).build()
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
