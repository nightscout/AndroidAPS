package app.aaps.pump.danar.comm

import app.aaps.core.data.pump.defs.PumpType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verifyBlocking

class MsgErrorTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgError(injector)

        // test message decoding
        val array = ByteArray(100)

        putByteToArray(array, 0, 1)
        packet.handleMessage(array)
        Assertions.assertEquals(true, packet.failed)
        // bigger than 8 - no error
        putByteToArray(array, 0, 10)
        packet.handleMessage(array)
        Assertions.assertEquals(false, packet.failed)
    }

    // The DanaR driver on a pump with v2 firmware: the firmware says DanaRv2, the driver records DanaR.
    // An alarm with the firmware type was dropped as data from another pump.
    @Test fun alarmIsRecordedWithTheTypeOfTheDriver() {
        danaPump.hwModel = 0x03
        danaPump.protocol = 0x02
        val array = ByteArray(100)
        putByteToArray(array, 0, 8) // battery discharged

        MsgError(injector).handleMessage(array)

        verifyBlocking(pumpSync) { insertAnnouncement(any(), anyOrNull(), eq(testPumpPlugin.model()), any()) }
        Assertions.assertNotEquals(PumpType.DANA_RV2, testPumpPlugin.model())
    }
}
