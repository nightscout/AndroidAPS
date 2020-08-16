package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgError
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(NSUpload::class)
class MsgErrorTest : DanaRTestBase() {

    @Test fun runTest() {
        PowerMockito.mockStatic(NSUpload::class.java)
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