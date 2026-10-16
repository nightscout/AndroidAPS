package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
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
        lastBolus.timeInMillis = danaPump.lastBolusTime ?: 0L
        Assertions.assertEquals(13, lastBolus.get(Calendar.HOUR_OF_DAY))
        Assertions.assertEquals(20, lastBolus.get(Calendar.MINUTE))
        Assertions.assertEquals(12.5, danaPump.lastBolusAmount ?: 0.0, 0.01)
        Assertions.assertEquals(25.0, danaPump.maxBolus, 0.01)
        Assertions.assertEquals(1.0, danaPump.bolusStep, 0.01)
        Assertions.assertEquals("BOLUS__GET_STEP_BOLUS_INFORMATION", packet.friendlyName)
    }

    // The pump sends only the time of day of the last bolus

    private fun lastBolusAt(hours: Int, minutes: Int, now: DateTime): Long? {
        whenever(dateUtil.now()).thenReturn(now.millis)
        val array = createArray(13, 0.toByte())
        putByteToArray(array, 4, hours.toByte())
        putByteToArray(array, 5, minutes.toByte())
        putIntToArray(array, 6, 25) // 0.25 U
        DanaRSPacketBolusGetStepBolusInformation(aapsLogger, dateUtil, danaPump).handleMessage(array)
        return danaPump.lastBolusTime
    }

    @Test
    fun `a bolus from yesterday evening is not shown later today`() {
        val now = DateTime(2026, 10, 13, 10, 0)

        Assertions.assertEquals(DateTime(2026, 10, 12, 22, 35).millis, lastBolusAt(22, 35, now))
    }

    @Test
    fun `a bolus from earlier today stays today`() {
        val now = DateTime(2026, 10, 13, 10, 0)

        Assertions.assertEquals(DateTime(2026, 10, 13, 9, 58).millis, lastBolusAt(9, 58, now))
    }

    @Test
    fun `a pump clock a few minutes ahead still means today`() {
        val now = DateTime(2026, 10, 13, 10, 0)

        Assertions.assertEquals(DateTime(2026, 10, 13, 10, 3).millis, lastBolusAt(10, 3, now))
    }

    @Test
    fun `a pump in UTC reads the time in UTC`() {
        danaPump.hwModel = 7 // Dana-i and newer use UTC
        val now = DateTime(2026, 10, 13, 10, 0, DateTimeZone.UTC)

        Assertions.assertEquals(DateTime(2026, 10, 12, 22, 35, DateTimeZone.UTC).millis, lastBolusAt(22, 35, now))
    }
}