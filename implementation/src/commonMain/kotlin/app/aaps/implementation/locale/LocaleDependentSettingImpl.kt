package app.aaps.implementation.locale

import app.aaps.core.interfaces.local.LocaleDependentSetting
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class LocaleDependentSettingImpl @Inject constructor() : LocaleDependentSetting {

    override val ntpServer: String get() = ntpServerFor(LocalePlatform.language, LocalePlatform.country)
}

/**
 * Which time server to ask.
 *
 * `time.google.com` is not reachable from mainland China, so a device there is sent to Alibaba's
 * public NTP server instead. Everywhere else keeps Google's.
 *
 * The country match ignores case because the platforms do not agree on it: Android hands back an
 * upper case ISO 3166 code, iOS is documented to but is not guaranteed to.
 *
 * Kept as a plain function so the rule can be tested without a device locale - see
 * `LocaleDependentSettingTest`.
 */
internal fun ntpServerFor(language: String, country: String): String =
    if (language == "zh" && country.equals("CN", ignoreCase = true)) "ntp1.aliyun.com" else "time.google.com"
