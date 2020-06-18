package info.nightscout.androidaps.danars.comm

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.danars.DanaRSPlugin
import info.nightscout.androidaps.utils.DateUtil
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*

@RunWith(PowerMockRunner::class)
@PrepareForTest(RxBusWrapper::class, DetailedBolusInfoStorage::class, DanaRSPlugin::class)
class DanaRS_Packet_APS_History_EventsTest : DanaRSTestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
            if (it is DanaRS_Packet_APS_History_Events) {
                it.rxBus = rxBus
                it.resourceHelper = resourceHelper
                it.activePlugin = activePlugin
                it.danaPump = danaPump
                it.detailedBolusInfoStorage = detailedBolusInfoStorage
                it.sp = sp
            }
        }
    }

    @Test fun runTest() {
        val now = DateUtil.now()

        val testPacket = DanaRS_Packet_APS_History_Events(packetInjector, now)
        // test getRequestedParams
        val returnedValues = testPacket.requestParams
        val expectedValues = getCalender(now)
        //year
        Assert.assertEquals(expectedValues[0], returnedValues[0])
        //month
        Assert.assertEquals(expectedValues[1], returnedValues[1])
        //day of month
        Assert.assertEquals(expectedValues[2], returnedValues[2])
        // hour
        Assert.assertEquals(expectedValues[3], returnedValues[3])
        // minute
        Assert.assertEquals(expectedValues[4], returnedValues[4])
        // second
        Assert.assertEquals(expectedValues[5], returnedValues[5])
        Assert.assertEquals("APS_HISTORY_EVENTS", testPacket.friendlyName)
    }

    private fun getCalender(from: Long): ByteArray {
        val cal = GregorianCalendar()
        if (from != 0L) cal.timeInMillis = from else cal[2000, 0, 1, 0, 0] = 0
        val ret = ByteArray(6)
        ret[0] = (cal[Calendar.YEAR] - 1900 - 100 and 0xff).toByte()
        ret[1] = (cal[Calendar.MONTH] + 1 and 0xff).toByte()
        ret[2] = (cal[Calendar.DAY_OF_MONTH] and 0xff).toByte()
        ret[3] = (cal[Calendar.HOUR_OF_DAY] and 0xff).toByte()
        ret[4] = (cal[Calendar.MINUTE] and 0xff).toByte()
        ret[5] = (cal[Calendar.SECOND] and 0xff).toByte()
        return ret
    }
}