package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.Calendar

class DanaRsPacketBolusGetStepBolusInformationTest : DanaRSTestBase() {

    @Test
    fun runTest() {
        val packet = DanaRSPacketBolusGetStepBolusInformation(aapsLogger, dateUtil, danaPump)

        val array = createArray(13, 0.toByte()) // 11 + 2
        putByteToArray(array, 0, 2) // error 2
        putByteToArray(array, 1, 3) // bolus type 3
        putIntToArray(array, 2, 600) // initial bolus amount 6
        putByteToArray(array, 4, 13) // 13h
        putByteToArray(array, 5, 20) // 20min
        putIntToArray(array, 6, 1250) // last bolus amount 12.5
        putIntToArray(array, 8, 2500) // max bolus 25
        putByteToArray(array, 10, 100) // bolus step 1

        packet.handleMessage(array)
        Assertions.assertTrue(packet.failed)
        Assertions.assertEquals(6.0, danaPump.initialBolusAmount, 0.01)
        val lastBolus = Calendar.getInstance()
        lastBolus.timeInMillis = danaPump.lastBolusTime
        Assertions.assertEquals(13, lastBolus.get(Calendar.HOUR_OF_DAY))
        Assertions.assertEquals(20, lastBolus.get(Calendar.MINUTE))
        Assertions.assertEquals(12.5, danaPump.lastBolusAmount, 0.01)
        Assertions.assertEquals(25.0, danaPump.maxBolus, 0.01)
        Assertions.assertEquals(1.0, danaPump.bolusStep, 0.01)
        Assertions.assertEquals("BOLUS__GET_STEP_BOLUS_INFORMATION", packet.friendlyName)
    }
}