package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.DanaRSTestBase
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import org.junit.Assert
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyDouble
import org.mockito.Mockito.anyInt

class DanaRSPacketNotifyDeliveryCompleteTest : DanaRSTestBase() {

    @Mock lateinit var activePlugin: ActivePlugin

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

        danaPump.bolusingTreatment = EventOverviewBolusProgress.Treatment(0.0, 0, true)
        val packet = DanaRSPacketNotifyDeliveryComplete(packetInjector)
        // test params
        Assert.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        packet.handleMessage(createArray(17, 0.toByte()))
        Assert.assertEquals(true, danaPump.bolusDone)
        Assert.assertEquals("NOTIFY__DELIVERY_COMPLETE", packet.friendlyName)
    }
}