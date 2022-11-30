package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgError
import org.junit.Assert
import org.junit.jupiter.api.Test

class MsgErrorTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgError(injector)

        // test message decoding
        val array = ByteArray(100)

        putByteToArray(array, 0, 1)
        packet.handleMessage(array)
        Assert.assertEquals(true, packet.failed)
        // bigger than 8 - no error
        putByteToArray(array, 0, 10)
        packet.handleMessage(array)
        Assert.assertEquals(false, packet.failed)
    }
}