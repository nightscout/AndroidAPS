package info.nightscout.pump.danaR.comm

import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import info.nightscout.androidaps.danar.comm.MsgBolusStop
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class MsgBolusStopTest : DanaRTestBase() {

    @Test fun runTest() {
        `when`(rh.gs(info.nightscout.pump.dana.R.string.overview_bolusprogress_delivered)).thenReturn("Delivered")
        danaPump.bolusingTreatment = EventOverviewBolusProgress.Treatment(0.0, 0, true, 0)
        val packet = MsgBolusStop(injector)

        // test message decoding
        packet.handleMessage(ByteArray(100))
        Assertions.assertEquals(true, danaPump.bolusStopped)
    }
}