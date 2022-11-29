package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.R
import info.nightscout.androidaps.danar.comm.MsgBolusProgress
import info.nightscout.rx.events.EventOverviewBolusProgress
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`

class MsgBolusProgressTest : DanaRTestBase() {

    @Test fun runTest() {
        `when`(rh.gs(ArgumentMatchers.eq(R.string.bolus_delivering), ArgumentMatchers.anyDouble())).thenReturn("Delivering %1\$.2fU")
        danaPump.bolusingTreatment = EventOverviewBolusProgress.Treatment(0.0, 0, true, 0)
        danaPump.bolusAmountToBeDelivered = 3.0
        val packet = MsgBolusProgress(injector)

        // test message decoding
        val array = ByteArray(100)
        putIntToArray(array, 0, 2 * 100)
        packet.handleMessage(array)
        Assert.assertEquals(1.0, danaPump.bolusingTreatment?.insulin!!, 0.0)
    }
}