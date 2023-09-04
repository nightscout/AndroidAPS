package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import info.nightscout.rx.events.EventOverviewBolusProgress
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyDouble
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.`when`

class DanaRSPacketNotifyDeliveryCompleteTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketNotifyDeliveryComplete) {
                it.aapsLogger = aapsLogger
                it.rxBus = rxBus
                it.rh = rh
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        `when`(rh.gs(anyInt(), anyDouble())).thenReturn("SomeString")

        danaPump.bolusingTreatment = EventOverviewBolusProgress.Treatment(0.0, 0, true, 0)
        val packet = DanaRSPacketNotifyDeliveryComplete(packetInjector)
        // test params
        Assertions.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        packet.handleMessage(createArray(17, 0.toByte()))
        Assertions.assertEquals(true, danaPump.bolusDone)
        Assertions.assertEquals("NOTIFY__DELIVERY_COMPLETE", packet.friendlyName)
    }
}