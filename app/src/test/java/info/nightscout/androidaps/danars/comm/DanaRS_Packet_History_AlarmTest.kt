package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_History_AlarmTest : DanaRSTestBase() {

    @Mock lateinit var databaseHelper: DatabaseHelperInterface
    @Mock lateinit var nsUpload: NSUpload

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
            if (it is DanaRS_Packet_History_Alarm) {
                it.rxBus = rxBus
                it.databaseHelper = databaseHelper
            }
        }
    }

    @Test
    fun runTest() {
        val packet = DanaRS_Packet_History_Alarm(packetInjector, 0)

        val array = createArray(12, 0.toByte()) // 10 + 2
        putByteToArray(array, 0, 0x0A) // record code alarm
        putByteToArray(array, 1, 19) // year 2019
        putByteToArray(array, 2, 2) // month february
        putByteToArray(array, 3, 4) // day 4
        putByteToArray(array, 4, 20) // hour 20
        putByteToArray(array, 5, 11) // min 11
        putByteToArray(array, 6, 35) // second 35
        putByteToArray(array, 7, 79) // occlusion
        putByteToArray(array, 8, 1) // value
        putByteToArray(array, 9, 100) // value

        packet.handleMessage(array)
        Assert.assertEquals(info.nightscout.androidaps.dana.comm.RecordTypes.RECORD_TYPE_ALARM, packet.danaRHistoryRecord.recordCode)
        Assert.assertEquals(Date(119, 1, 4, 20, 11, 35).time, packet.danaRHistoryRecord.recordDate)
        Assert.assertEquals("Occlusion", packet.danaRHistoryRecord.recordAlarm)
        Assert.assertEquals(3.56, packet.danaRHistoryRecord.recordValue, 0.01)
        Assert.assertEquals("REVIEW__ALARM", packet.friendlyName)
    }
}