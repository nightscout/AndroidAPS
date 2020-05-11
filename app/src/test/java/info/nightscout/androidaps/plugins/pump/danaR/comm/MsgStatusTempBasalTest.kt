package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgStatusTempBasal
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConfigBuilderPlugin::class)
class MsgStatusTempBasalTest : DanaRTestBase() {

    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var treatmentsPlugin: TreatmentsPlugin

    @Test fun runTest() {
        `when`(activePlugin.activeTreatments).thenReturn(treatmentsPlugin)
        val packet = MsgStatusTempBasal(aapsLogger, danaPump, activePlugin, injector)
        // test message decoding
        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
        Assert.assertEquals(true, danaPump.isTempBasalInProgress)
        // passing an bigger number
        packet.handleMessage(createArray(34, 2.toByte()))
        Assert.assertEquals(false, danaPump.isTempBasalInProgress)
    }
}