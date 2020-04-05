package info.nightscout.androidaps.plugins.pump.danaRS.comm

import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(NSUpload::class)
class DanaRS_Packet_Notify_AlarmTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRS_Packet_Notify_Alarm(aapsLogger, resourceHelper)
        // test params
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
// handlemessage testing fails on non-eror byte because of NSUpload not properly mocked
        packet.handleMessage(createArray(17, 0x01.toByte()))
        Assert.assertEquals(false, packet.failed)
        // no error
        packet.handleMessage(createArray(17, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("NOTIFY__ALARM", packet.friendlyName)
    }

    @Before
    fun mock() {
        PowerMockito.mockStatic(NSUpload::class.java)
    }
}