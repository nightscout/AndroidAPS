package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.DanaRSPlugin
import info.nightscout.androidaps.danars.DanaRSTestBase
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(RxBusWrapper::class, DanaRSPlugin::class)
class DanaRsPacketBolusSetStepBolusStopTest : DanaRSTestBase() {

    @Mock lateinit var activePlugin: ActivePlugin

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketBolusSetStepBolusStop) {
                it.aapsLogger = aapsLogger
                it.rxBus = rxBus
                it.resourceHelper = resourceHelper
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        `when`(resourceHelper.gs(Mockito.anyInt())).thenReturn("SomeString")

        danaPump.bolusingTreatment = EventOverviewBolusProgress.Treatment(0.0, 0, true)
        val testPacket = DanaRSPacketBolusSetStepBolusStop(packetInjector)
        // test message decoding
        testPacket.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assert.assertEquals(false, testPacket.failed)
        testPacket.handleMessage(byteArrayOf(1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte()))
        Assert.assertEquals(true, testPacket.failed)
        Assert.assertEquals("BOLUS__SET_STEP_BOLUS_STOP", testPacket.friendlyName)
    }
}