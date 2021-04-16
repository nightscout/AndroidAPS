package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.R
import info.nightscout.androidaps.danar.comm.MsgBolusStop
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgBolusStopTest : DanaRTestBase() {

    @Test fun runTest() {
        `when`(resourceHelper.gs(R.string.overview_bolusprogress_delivered)).thenReturn("Delivered")
        danaPump.bolusingTreatment = EventOverviewBolusProgress.Treatment(0.0, 0, true)
        val packet = MsgBolusStop(injector)

        // test message decoding
        packet.handleMessage(ByteArray(100))
        Assert.assertEquals(true, danaPump.bolusStopped)
    }
}