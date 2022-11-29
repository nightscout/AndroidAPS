package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.dana.comm.RecordTypes
import info.nightscout.pump.dana.database.DanaHistoryRecordDao
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.mockito.Mock
import java.util.GregorianCalendar

class DanaRSPacketHistoryAlarmTest : DanaRSTestBase() {

    @Mock lateinit var danaHistoryRecordDao: DanaHistoryRecordDao

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
            if (it is DanaRSPacketHistoryAlarm) {
                it.rxBus = rxBus
                it.danaHistoryRecordDao = danaHistoryRecordDao
            }
        }
    }

    @Test
    fun runTest() {
        val packet = DanaRSPacketHistoryAlarm(packetInjector, 0)

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
        Assert.assertEquals(RecordTypes.RECORD_TYPE_ALARM, packet.danaRHistoryRecord.code)
        val date = GregorianCalendar().also {
            it.clear()
            it.set(2019, 1, 4, 20, 11, 35)
        }
        Assert.assertEquals(date.timeInMillis, packet.danaRHistoryRecord.timestamp)
        Assert.assertEquals("Occlusion", packet.danaRHistoryRecord.alarm)
        Assert.assertEquals(3.56, packet.danaRHistoryRecord.value, 0.01)
        Assert.assertEquals("REVIEW__ALARM", packet.friendlyName)
    }
}