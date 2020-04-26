package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(NSUpload::class)
class MsgErrorTest : DanaRTestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper
    val rxBus = RxBusWrapper()

    @Test fun runTest() {
        PowerMockito.mockStatic(NSUpload::class.java)
        val packet = MsgError(aapsLogger, rxBus, resourceHelper, danaRPump)

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