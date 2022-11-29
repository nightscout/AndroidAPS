package info.nightscout.core.ui.locale

import android.content.Context
import android.content.ContextWrapper
import android.os.LocaleList
import androidx.preference.PreferenceManager
import info.nightscout.core.ui.R
import java.util.Locale

object LocaleHelper {
    private fun selectedLanguage(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.key_language), "default")
            ?: "default"
    // injection not possible because of use in attachBaseContext
    //SP.getString(R.string.key_language, Locale.getDefault().language)

    private fun currentLocale(context: Context): Locale {
        val language = selectedLanguage(context)
        if (language == "default") return Locale.getDefault()

        var locale = Locale(language)
        if (language.contains("_")) {
            // language with country like pt_BR defined in arrays.xml
            val lang = language.substring(0, 2)
            val country = language.substring(3, 5)
            locale = Locale(lang, country)
        }
        return locale
    }

    fun update(context: Context) {
        // no action for system default language
        if (selectedLanguage(context) == "default") return

        val locale = currentLocale(context)
        Locale.setDefault(locale)
        val resources = context.resources
        val configuration = resources.configuration
        context.createConfigurationContext(configuration)
        configuration.setLocale(locale)
    }

    fun wrap(ctx: Context): Context {
        // no action for system default language
        if (selectedLanguage(ctx) == "default") return ctx

        val res = ctx.resources
        val configuration = res.configuration
        val newLocale = currentLocale(ctx)
        configuration.setLocale(newLocale)
        val localeList = LocaleList(newLocale)
        LocaleList.setDefault(localeList)
        configuration.setLocales(localeList)
        val context = ctx.createConfigurationContext(configuration)
        return ContextWrapper(context)
    }
}