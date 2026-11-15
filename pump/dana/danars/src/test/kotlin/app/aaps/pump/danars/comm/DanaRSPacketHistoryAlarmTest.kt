package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock
import java.util.GregorianCalendar

class DanaRSPacketHistoryAlarmTest : DanaRSTestBase() {

    @Mock lateinit var danaHistoryRecordDao: DanaHistoryRecordDao
    @Mock lateinit var pumpSync: PumpSync

    @Test
    fun runTest() {
        val packet = DanaRSPacketHistoryAlarm(aapsLogger, dateUtil, rxBus, danaHistoryRecordDao, pumpSync, danaPump).with(0)

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
        Assertions.assertEquals(RecordTypes.RECORD_TYPE_ALARM, packet.danaRHistoryRecord.code)
        val date = GregorianCalendar().also {
            it.clear()
            it.set(2019, 1, 4, 20, 11, 35)
        }
        Assertions.assertEquals(date.timeInMillis, packet.danaRHistoryRecord.timestamp)
        Assertions.assertEquals("Occlusion", packet.danaRHistoryRecord.alarm)
        Assertions.assertEquals(3.56, packet.danaRHistoryRecord.value, 0.01)
        Assertions.assertEquals("REVIEW__ALARM", packet.friendlyName)
    }
}