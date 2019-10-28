package info.nightscout.androidaps.utils

import android.content.Context
import info.nightscout.androidaps.R
import java.util.*

object LocaleHelper {
    fun update(context: Context) =
            updateResources(context, currentLanguage())

    fun currentLanguage(): String =
            SP.getString(R.string.key_language, Locale.getDefault().language)

    fun currentLocale(): Locale =
            Locale(SP.getString(R.string.key_language, Locale.getDefault().language))

    @Suppress("DEPRECATION")
    private fun updateResources(context: Context, language: String) {
        var locale = Locale(language)
        if (language.contains("_")) {
            // language with country like pt_BR defined in arrays.xml
            val lang = language.substring(0, 2)
            val country = language.substring(3, 5)
            locale = Locale(lang, country)
        }

        Locale.setDefault(locale)
        val resources = context.resources
        resources.configuration.setLocale(locale)
        resources.updateConfiguration(resources.configuration, resources.displayMetrics)
    }
}