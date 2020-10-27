package info.nightscout.androidaps.data

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.db.ProfileSwitch
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*

@RunWith(PowerMockRunner::class)
@PrepareForTest(VirtualPumpPlugin::class)
class ProfileIntervalsTest : TestBaseWithProfile() {

    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin

    private val startDate = DateUtil.now()
    var list = ProfileIntervals<ProfileSwitch>()

    @Before
    fun mock() {
        `when`(activePluginProvider.activePump).thenReturn(virtualPumpPlugin)
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(PumpDescription())
    }
    @Test
    fun doTests() {
        // create one 10h interval and test value in and out
        list.add(ProfileSwitch(profileInjector).date(startDate).duration(T.hours(10).mins().toInt()).profileName("1").profile(validProfile))
        // for older date first record should be returned only if has zero duration
        Assert.assertEquals(null, list.getValueToTime(startDate - T.secs(1).msecs()))
        Assert.assertEquals("1", (list.getValueToTime(startDate) as ProfileSwitch?)!!.profileName)
        Assert.assertEquals(null, list.getValueToTime(startDate + T.hours(10).msecs() + 1))
        list.reset()
        list.add(ProfileSwitch(profileInjector).date(startDate).profileName("1").profile(validProfile))
        Assert.assertEquals("1", (list.getValueToTime(startDate - T.secs(1).msecs()) as ProfileSwitch?)!!.profileName)
        Assert.assertEquals("1", (list.getValueToTime(startDate) as ProfileSwitch?)!!.profileName)
        Assert.assertEquals("1", (list.getValueToTime(startDate + T.hours(10).msecs() + 1) as ProfileSwitch?)!!.profileName)

        // switch to different profile after 5h
        list.add(ProfileSwitch(profileInjector).date(startDate + T.hours(5).msecs()).duration(0).profileName("2").profile(validProfile))
        Assert.assertEquals("1", (list.getValueToTime(startDate - T.secs(1).msecs()) as ProfileSwitch?)!!.profileName)
        Assert.assertEquals("1", (list.getValueToTime(startDate + T.hours(5).msecs() - 1) as ProfileSwitch?)!!.profileName)
        Assert.assertEquals("2", (list.getValueToTime(startDate + T.hours(5).msecs() + 1) as ProfileSwitch?)!!.profileName)

        // insert 1h interval inside
        list.add(ProfileSwitch(profileInjector).date(startDate + T.hours(6).msecs()).duration(T.hours(1).mins().toInt()).profileName("3").profile(validProfile))
        Assert.assertEquals("2", (list.getValueToTime(startDate + T.hours(6).msecs() - 1) as ProfileSwitch?)!!.profileName)
        Assert.assertEquals("3", (list.getValueToTime(startDate + T.hours(6).msecs() + 1) as ProfileSwitch?)!!.profileName)
        Assert.assertEquals("2", (list.getValueToTime(startDate + T.hours(7).msecs() + 1) as ProfileSwitch?)!!.profileName)
    }

    @Test
    fun testCopyConstructor() {
        list.reset()
        list.add(ProfileSwitch(profileInjector).date(startDate).duration(T.hours(10).mins().toInt()).profileName("4").profile(validProfile))
        val list2 = ProfileIntervals(list)
        Assert.assertEquals(1, list2.list.size.toLong())
    }

    @Test fun invalidProfilesShouldNotBeReturned() {
        list.reset()
        list.add(ProfileSwitch(profileInjector).date(startDate + T.hours(1).msecs()).profileName("6"))
        Assert.assertEquals(null, list[0])
    }

    @Test fun testReversingArrays() {
        val someList: MutableList<ProfileSwitch> = ArrayList()
        someList.add(ProfileSwitch(profileInjector).date(startDate).duration(T.hours(3).mins().toInt()).profileName("5").profile(validProfile))
        someList.add(ProfileSwitch(profileInjector).date(startDate + T.hours(1).msecs()).duration(T.hours(1).mins().toInt()).profileName("6").profile(validProfile))
        list.reset()
        list.add(someList)
        Assert.assertEquals(startDate, list[0].date)
        Assert.assertEquals(startDate + T.hours(1).msecs(), list.getReversed(0).date)
        Assert.assertEquals(startDate + T.hours(1).msecs(), list.reversedList[0].date)
    }
}