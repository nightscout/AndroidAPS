package info.nightscout.androidaps.utils

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.LocaleList
import info.nightscout.androidaps.R
import java.util.*


object LocaleHelper {
    fun currentLanguage(): String =
            SP.getString(R.string.key_language, Locale.getDefault().language)

    private fun currentLocale(): Locale {
        val language = currentLanguage()
        var locale = Locale(language)
        if (language.contains("_")) {
            // language with country like pt_BR defined in arrays.xml
            val lang = language.substring(0, 2)
            val country = language.substring(3, 5)
            locale = Locale(lang, country)
        }
        return locale
    }

    @Suppress("DEPRECATION")
    fun update(context: Context) {
        val locale = currentLocale()
        Locale.setDefault(locale)
        val resources = context.resources
        val configuration = resources.configuration
        context.createConfigurationContext(configuration)
        configuration.setLocale(locale)
        configuration.locale = locale
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }

    fun wrap(ctx: Context): ContextWrapper {
        val res = ctx.resources
        val configuration = res.configuration
        val newLocale = currentLocale()
        val context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(newLocale)
            val localeList = LocaleList(newLocale)
            LocaleList.setDefault(localeList)
            configuration.locales = localeList
            ctx.createConfigurationContext(configuration)
        } else {
            configuration.setLocale(newLocale)
            ctx.createConfigurationContext(configuration)
        }
        return ContextWrapper(context)
    }
}