package info.nightscout.plugins.constraints.dstHelper

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class DstHelperPluginTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var sp: SP
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var loop: Loop

    private lateinit var plugin: DstHelperPlugin

    private val injector = HasAndroidInjector { AndroidInjector { } }

    @BeforeEach
    fun mock() {
        plugin = DstHelperPlugin(injector, aapsLogger, rh, sp, activePlugin, loop)
    }

    @Test
    fun runTest() {
        val tz = TimeZone.getTimeZone("Europe/Rome")
        TimeZone.setDefault(tz)
        var cal = Calendar.getInstance(tz, Locale.ITALIAN)
        val df: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ITALIAN)
        var dateBeforeDST = df.parse("2018-03-25 01:55")
        cal.time = dateBeforeDST!!
        Assertions.assertEquals(false, plugin.wasDST(cal))
        Assertions.assertEquals(true, plugin.willBeDST(cal))
        TimeZone.setDefault(tz)
        cal = Calendar.getInstance(tz, Locale.ITALIAN)
        dateBeforeDST = df.parse("2018-03-25 03:05")
        cal.time = dateBeforeDST!!
        Assertions.assertEquals(true, plugin.wasDST(cal))
        Assertions.assertEquals(false, plugin.willBeDST(cal))
        TimeZone.setDefault(tz)
        cal = Calendar.getInstance(tz, Locale.ITALIAN)
        dateBeforeDST = df.parse("2018-03-25 02:05") //Cannot happen!!!
        cal.time = dateBeforeDST!!
        Assertions.assertEquals(true, plugin.wasDST(cal))
        Assertions.assertEquals(false, plugin.willBeDST(cal))
        TimeZone.setDefault(tz)
        cal = Calendar.getInstance(tz, Locale.ITALIAN)
        dateBeforeDST = df.parse("2018-03-25 05:55") //Cannot happen!!!
        cal.time = dateBeforeDST!!
        Assertions.assertEquals(true, plugin.wasDST(cal))
        Assertions.assertEquals(false, plugin.willBeDST(cal))
        TimeZone.setDefault(tz)
        cal = Calendar.getInstance(tz, Locale.ITALIAN)
        dateBeforeDST = df.parse("2018-03-25 06:05") //Cannot happen!!!
        cal.time = dateBeforeDST!!
        Assertions.assertEquals(false, plugin.wasDST(cal))
        Assertions.assertEquals(false, plugin.willBeDST(cal))
    }
}