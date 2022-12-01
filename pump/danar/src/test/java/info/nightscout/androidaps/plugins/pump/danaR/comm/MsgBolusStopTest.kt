package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.R
import info.nightscout.androidaps.danar.comm.MsgBolusStop
import info.nightscout.rx.events.EventOverviewBolusProgress
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class MsgBolusStopTest : DanaRTestBase() {

    @Test fun runTest() {
        `when`(rh.gs(R.string.overview_bolusprogress_delivered)).thenReturn("Delivered")
        danaPump.bolusingTreatment = EventOverviewBolusProgress.Treatment(0.0, 0, true, 0)
        val packet = MsgBolusStop(injector)

        // test message decoding
        packet.handleMessage(ByteArray(100))
        Assert.assertEquals(true, danaPump.bolusStopped)
    }
}