package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class DanaRsPacketBolusSetStepBolusStopTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketBolusSetStepBolusStop) {
                it.aapsLogger = aapsLogger
                it.rxBus = rxBus
                it.rh = rh
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        `when`(rh.gs(Mockito.anyInt())).thenReturn("SomeString")

        danaPump.bolusingTreatment = EventOverviewBolusProgress.Treatment(0.0, 0, true, 0)
        val testPacket = DanaRSPacketBolusSetStepBolusStop(packetInjector)
        // test message decoding
        testPacket.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assertions.assertEquals(false, testPacket.failed)
        testPacket.handleMessage(byteArrayOf(1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte()))
        Assertions.assertEquals(true, testPacket.failed)
        Assertions.assertEquals("BOLUS__SET_STEP_BOLUS_STOP", testPacket.friendlyName)
    }
}