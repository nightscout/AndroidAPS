package app.aaps.implementation.locale

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The NTP server rule had no test before it moved. It is one line, but getting it wrong is invisible
 * outside China: everyone else keeps working and the affected users silently cannot verify time, which
 * is what gates starting and verifying an objective.
 */
class LocaleDependentSettingTest {

    @Test
    fun mainlandChinaGetsTheAliyunServer() {
        assertEquals("ntp1.aliyun.com", ntpServerFor(language = "zh", country = "CN"))
    }

    @Test
    fun theCountryMatchIgnoresCase() {
        // Android returns an upper case code, iOS is not guaranteed to.
        assertEquals("ntp1.aliyun.com", ntpServerFor(language = "zh", country = "cn"))
    }

    @Test
    fun chineseOutsideMainlandGetsTheDefaultServer() {
        // Taiwan, Hong Kong and Singapore all speak zh but are not behind the same restriction.
        assertEquals("time.google.com", ntpServerFor(language = "zh", country = "TW"))
        assertEquals("time.google.com", ntpServerFor(language = "zh", country = "HK"))
    }

    @Test
    fun anotherLanguageInChinaGetsTheDefaultServer() {
        // The original condition is language AND country, not either.
        assertEquals("time.google.com", ntpServerFor(language = "en", country = "CN"))
    }

    @Test
    fun aLocaleWithoutACountryGetsTheDefaultServer() {
        // iOS may report a language with no country at all.
        assertEquals("time.google.com", ntpServerFor(language = "zh", country = ""))
    }

    @Test
    fun theUsualCaseGetsTheDefaultServer() {
        assertEquals("time.google.com", ntpServerFor(language = "en", country = "GB"))
    }
}
