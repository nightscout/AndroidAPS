package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgStatusTempBasal
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConfigBuilderPlugin::class)
class MsgStatusTempBasalTest : DanaRTestBase() {

    @Test fun runTest() {
        `when`(activePluginProvider.activeTreatments).thenReturn(treatmentsInterface)
        val packet = MsgStatusTempBasal(injector)
        // test message decoding
        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
        Assert.assertEquals(true, danaPump.isTempBasalInProgress)
        // passing an bigger number
        packet.handleMessage(createArray(34, 2.toByte()))
        Assert.assertEquals(false, danaPump.isTempBasalInProgress)
    }
}