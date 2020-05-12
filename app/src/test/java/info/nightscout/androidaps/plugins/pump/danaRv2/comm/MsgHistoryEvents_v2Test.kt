package info.nightscout.androidaps.plugins.pump.danaRv2.comm

import info.nightscout.androidaps.danaRv2.comm.MsgHistoryEvents_v2
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.plugins.pump.danaR.comm.DanaRTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(DetailedBolusInfoStorage::class)
class MsgHistoryEvents_v2Test : DanaRTestBase() {

    @Test @Throws(Exception::class) fun runTest() {
        val packet = MsgHistoryEvents_v2(injector, 0)

        // test message decoding
        val array = createArray(100, 2)

        putByteToArray(array, 0, 0xFF.toByte())
        packet.handleMessage(array)
        Assert.assertEquals(true, danaRv2Plugin.eventsLoadingDone)
        // passing an bigger number
        putByteToArray(array, 0, 0x01.toByte())
        packet.handleMessage(array)
        Assert.assertEquals(false, danaRv2Plugin.eventsLoadingDone)
    }
}