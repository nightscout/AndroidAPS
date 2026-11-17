package app.aaps.pump.danar.comm

import app.aaps.core.data.time.T
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgStatusBolusExtendedTest : DanaRTestBase() {

    @Test
    fun runTest() {
        val packet = MsgStatusBolusExtended(injector)
        // test message decoding
        val array = ByteArray(100)
        putByteToArray(array, 0, 1)
        putByteToArray(array, 1, 1)
        packet.handleMessage(array)
        Assertions.assertEquals(T.mins(30).msecs(), danaPump.extendedBolusDuration)
    }

    /** Another extended bolus that starts within 3 s of the one before is read, with its amount and duration. */
    @Test
    fun anotherExtendedBolusThatStartsWithin3SecondsIsRead() {
        MsgStatusBolusExtended(injector).handleMessage(extendedBolus(halfHours = 1, hundredths = 100, seconds = 2))
        Assertions.assertEquals(1.0, danaPump.extendedBolusAmount, 0.001)

        MsgStatusBolusExtended(injector).handleMessage(extendedBolus(halfHours = 2, hundredths = 200, seconds = 0))

        Assertions.assertEquals(2.0, danaPump.extendedBolusAmount, 0.001)
        Assertions.assertEquals(T.mins(60).msecs(), danaPump.extendedBolusDuration)
    }

    /** A 6 byte header, then: running, half hours, amount in hundredths (high byte first), seconds it runs (3 bytes). */
    private fun extendedBolus(halfHours: Int, hundredths: Int, seconds: Int) =
        byteArrayOf(0, 0, 0, 0, 0, 0, 1, halfHours.toByte(), (hundredths shr 8).toByte(), hundredths.toByte(), seconds.toByte(), 0, 0)
}