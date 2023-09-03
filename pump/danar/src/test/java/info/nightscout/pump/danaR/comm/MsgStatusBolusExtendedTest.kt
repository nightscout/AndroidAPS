package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgStatusBolusExtended
import info.nightscout.shared.utils.T
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
}