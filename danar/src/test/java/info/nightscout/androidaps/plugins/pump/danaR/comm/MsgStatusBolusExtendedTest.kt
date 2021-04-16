package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgStatusBolusExtended
import info.nightscout.androidaps.utils.T
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgStatusBolusExtendedTest : DanaRTestBase() {

    @Test
    fun runTest() {
        val packet = MsgStatusBolusExtended(injector)
        // test message decoding
        val array = ByteArray(100)
        putByteToArray(array, 0, 1)
        putByteToArray(array, 1, 1)
        packet.handleMessage(array)
        Assert.assertEquals(T.mins(30).msecs(), danaPump.extendedBolusDuration)
    }
}