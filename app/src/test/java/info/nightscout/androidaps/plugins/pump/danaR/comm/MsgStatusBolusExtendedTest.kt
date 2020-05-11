package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgStatusBolusExtended
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgStatusBolusExtendedTest : DanaRTestBase() {

    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var treatmentsPlugin: TreatmentsPlugin

    @Test
    fun runTest() {
        `when`(activePlugin.activeTreatments).thenReturn(treatmentsPlugin)
        val packet = MsgStatusBolusExtended(injector, aapsLogger, danaPump, activePlugin, dateUtil)
        // test message decoding
        val array = ByteArray(100)
        putByteToArray(array, 0, 1)
        packet.handleMessage(array)
        Assert.assertEquals(true, danaPump.isExtendedInProgress)
    }
}