package info.nightscout.androidaps.plugins.constraints.dstHelper

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class DstHelperPluginTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var sp: SP
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var loop: Loop

    private lateinit var plugin: DstHelperPlugin

    val injector = HasAndroidInjector { AndroidInjector { } }

    @Before
    fun mock() {
        plugin = DstHelperPlugin(injector, aapsLogger, RxBus(aapsSchedulers, aapsLogger), rh,
                                 sp, activePlugin, loop)
    }

    @Test
    fun runTest() {
        val tz = TimeZone.getTimeZone("Europe/Rome")
        TimeZone.setDefault(tz)
        var cal = Calendar.getInstance(tz, Locale.ITALIAN)
        val df: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ITALIAN)
        var dateBeforeDST = df.parse("2018-03-25 01:55")
        cal.time = dateBeforeDST!!
        Assert.assertEquals(false, plugin.wasDST(cal))
        Assert.assertEquals(true, plugin.willBeDST(cal))
        TimeZone.setDefault(tz)
        cal = Calendar.getInstance(tz, Locale.ITALIAN)
        dateBeforeDST = df.parse("2018-03-25 03:05")
        cal.time = dateBeforeDST!!
        Assert.assertEquals(true, plugin.wasDST(cal))
        Assert.assertEquals(false, plugin.willBeDST(cal))
        TimeZone.setDefault(tz)
        cal = Calendar.getInstance(tz, Locale.ITALIAN)
        dateBeforeDST = df.parse("2018-03-25 02:05") //Cannot happen!!!
        cal.time = dateBeforeDST!!
        Assert.assertEquals(true, plugin.wasDST(cal))
        Assert.assertEquals(false, plugin.willBeDST(cal))
        TimeZone.setDefault(tz)
        cal = Calendar.getInstance(tz, Locale.ITALIAN)
        dateBeforeDST = df.parse("2018-03-25 05:55") //Cannot happen!!!
        cal.time = dateBeforeDST!!
        Assert.assertEquals(true, plugin.wasDST(cal))
        Assert.assertEquals(false, plugin.willBeDST(cal))
        TimeZone.setDefault(tz)
        cal = Calendar.getInstance(tz, Locale.ITALIAN)
        dateBeforeDST = df.parse("2018-03-25 06:05") //Cannot happen!!!
        cal.time = dateBeforeDST!!
        Assert.assertEquals(false, plugin.wasDST(cal))
        Assert.assertEquals(false, plugin.willBeDST(cal))
    }
}