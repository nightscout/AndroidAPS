package app.aaps.plugins.constraints.dstHelper

import app.aaps.core.interfaces.aps.Loop
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class DstHelperPluginTest : TestBaseWithProfile() {

    @Mock lateinit var loop: Loop

    private lateinit var plugin: DstHelperPlugin

    @BeforeEach
    fun mock() {
        plugin = DstHelperPlugin(aapsLogger, rh, preferences, activePlugin, notificationManager, loop, profileFunction)
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

    /** [utc] as a calendar in [zone]. Rome changed back from summer time on 2018-10-28 at 01:00 UTC. */
    private fun calendarAt(zone: String, utc: String): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { timeInMillis = Instant.parse(utc).toEpochMilli() }

    @Test
    fun `autumn change in 11 hours warns`() {
        val cal = calendarAt("Europe/Rome", "2018-10-27T14:00:00Z")
        assertThat(plugin.willBeDST(cal)).isTrue()
        assertThat(plugin.wasDST(cal)).isFalse()
    }

    @Test
    fun `autumn change 2 hours ago suspends`() {
        val cal = calendarAt("Europe/Rome", "2018-10-28T03:00:00Z")
        assertThat(plugin.wasDST(cal)).isTrue()
        assertThat(plugin.willBeDST(cal)).isFalse()
    }

    @Test
    fun `autumn change 4 hours ago is over`() {
        val cal = calendarAt("Europe/Rome", "2018-10-28T05:00:00Z")
        assertThat(plugin.wasDST(cal)).isFalse()
        assertThat(plugin.willBeDST(cal)).isFalse()
    }

    @Test
    fun `a zone without summer time never changes`() {
        val cal = calendarAt("Asia/Kolkata", "2018-03-25T01:00:00Z")
        assertThat(plugin.wasDST(cal)).isFalse()
        assertThat(plugin.willBeDST(cal)).isFalse()
    }
}
