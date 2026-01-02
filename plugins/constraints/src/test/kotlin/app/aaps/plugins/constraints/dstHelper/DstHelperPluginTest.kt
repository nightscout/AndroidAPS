package app.aaps.plugins.constraints.dstHelper

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class DstHelperPluginTest : TestBaseWithProfile() {

    @Mock lateinit var loop: Loop
    @Mock lateinit var uiInteraction: UiInteraction

    private lateinit var plugin: DstHelperPlugin

    @BeforeEach
    fun mock() {
        plugin = DstHelperPlugin(aapsLogger, rh, preferences, activePlugin, uiInteraction, loop, profileFunction)
    }

    @Test
    fun runTest() {
        val tz = TimeZone.getTimeZone("Europe/Rome")
        TimeZone.setDefault(tz)
        var cal = Calendar.getInstance(tz, Locale.ITALIAN)
        val df: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ITALIAN)
        var dateBeforeDST = df.parse("2018-03-25 01:55")
        cal.time = dateBeforeDST!!
        assertThat(plugin.wasDST(cal)).isFalse()
        assertThat(plugin.willBeDST(cal)).isTrue()
        TimeZone.setDefault(tz)
        cal = Calendar.getInstance(tz, Locale.ITALIAN)
        dateBeforeDST = df.parse("2018-03-25 03:05")
        cal.time = dateBeforeDST!!
        assertThat(plugin.wasDST(cal)).isTrue()
        assertThat(plugin.willBeDST(cal)).isFalse()
        TimeZone.setDefault(tz)
        cal = Calendar.getInstance(tz, Locale.ITALIAN)
        dateBeforeDST = df.parse("2018-03-25 02:05") //Cannot happen!!!
        cal.time = dateBeforeDST!!
        assertThat(plugin.wasDST(cal)).isTrue()
        assertThat(plugin.willBeDST(cal)).isFalse()
        TimeZone.setDefault(tz)
        cal = Calendar.getInstance(tz, Locale.ITALIAN)
        dateBeforeDST = df.parse("2018-03-25 05:55") //Cannot happen!!!
        cal.time = dateBeforeDST!!
        assertThat(plugin.wasDST(cal)).isTrue()
        assertThat(plugin.willBeDST(cal)).isFalse()
        TimeZone.setDefault(tz)
        cal = Calendar.getInstance(tz, Locale.ITALIAN)
        dateBeforeDST = df.parse("2018-03-25 06:05") //Cannot happen!!!
        cal.time = dateBeforeDST!!
        assertThat(plugin.wasDST(cal)).isFalse()
        assertThat(plugin.willBeDST(cal)).isFalse()
    }
}
