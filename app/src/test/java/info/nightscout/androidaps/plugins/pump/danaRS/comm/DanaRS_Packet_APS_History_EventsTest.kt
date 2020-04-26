package info.nightscout.androidaps.plugins.pump.danaRS.comm

import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin
import info.nightscout.androidaps.utils.DateUtil
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*

@RunWith(PowerMockRunner::class)
@PrepareForTest(RxBusWrapper::class, DetailedBolusInfoStorage::class)
class DanaRS_Packet_APS_History_EventsTest : DanaRSTestBase() {

    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var danaRSPlugin: DanaRSPlugin
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage

    @Test fun runTest() {
        val now = DateUtil.now()

        val testPacket = DanaRS_Packet_APS_History_Events(aapsLogger, rxBus, resourceHelper, activePlugin, danaRSPlugin, detailedBolusInfoStorage, injector, now)
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
        // test message decoding
        testPacket.handleMessage(createArray(50, 0.toByte()))
        Assert.assertEquals(false, testPacket.failed)
        Assert.assertEquals("APS_HISTORY_EVENTS", testPacket.friendlyName)
    }

    fun getCalender(from: Long): ByteArray {
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