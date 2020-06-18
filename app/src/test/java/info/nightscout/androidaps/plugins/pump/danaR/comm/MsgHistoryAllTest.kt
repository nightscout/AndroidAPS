package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.danar.comm.MsgHistoryAll
import info.nightscout.androidaps.db.DatabaseHelper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgHistoryAllTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistoryAll(injector)

        // test message decoding
        val array = createArray(100, 2)

        putByteToArray(array, 0, 1)
        packet.handleMessage(array)
        Assert.assertEquals(false, packet.failed)
        // passing an bigger number
        putByteToArray(array, 0, 17)
        packet.handleMessage(array)
        Assert.assertEquals(true, packet.failed)
    }
}