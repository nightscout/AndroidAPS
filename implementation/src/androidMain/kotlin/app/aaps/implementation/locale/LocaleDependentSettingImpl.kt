package app.aaps.implementation.locale

import android.content.res.Resources
import app.aaps.core.interfaces.local.LocaleDependentSetting
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class LocaleDependentSettingImpl @Inject constructor() : LocaleDependentSetting {

    private val language get() = Resources.getSystem().configuration.locales[0]
    override val ntpServer: String
        get() {
            val lang = language.language
            val country = language.country
            return if (lang == "zh" && country.equals("CN", ignoreCase = true)) "ntp1.aliyun.com" else "time.google.com"
        }
}