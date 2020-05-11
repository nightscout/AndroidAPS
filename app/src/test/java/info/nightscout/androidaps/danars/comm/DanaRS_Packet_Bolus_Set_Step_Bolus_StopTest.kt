package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.db.Treatment
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.danars.DanaRSPlugin
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
class DanaRS_Packet_Bolus_Set_Step_Bolus_StopTest : DanaRSTestBase() {

    @Mock lateinit var activePlugin: ActivePluginProvider

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_Bolus_Set_Step_Bolus_Stop) {
                it.aapsLogger = aapsLogger
                it.rxBus = rxBus
                it.resourceHelper = resourceHelper
                it.danaPump = danaPump
            }
            if (it is Treatment) {
                it.defaultValueHelper = defaultValueHelper
                it.resourceHelper = resourceHelper
                it.profileFunction = profileFunction
                it.activePlugin = activePlugin
            }
        }
    }

    @Test fun runTest() {
        `when`(resourceHelper.gs(Mockito.anyInt())).thenReturn("SomeString")

        danaPump.bolusingTreatment = Treatment(packetInjector)
        val testPacket = DanaRS_Packet_Bolus_Set_Step_Bolus_Stop(packetInjector)
        // test message decoding
        testPacket.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assert.assertEquals(false, testPacket.failed)
        testPacket.handleMessage(byteArrayOf(1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte()))
        Assert.assertEquals(true, testPacket.failed)
        Assert.assertEquals("BOLUS__SET_STEP_BOLUS_STOP", testPacket.friendlyName)
    }
}