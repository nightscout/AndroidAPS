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
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyDouble
import org.mockito.Mockito.anyInt
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(RxBusWrapper::class, DanaRSPlugin::class)
class DanaRS_Packet_Notify_Delivery_CompleteTest : DanaRSTestBase() {

    @Mock lateinit var danaRSPlugin: DanaRSPlugin
    @Mock lateinit var activePlugin: ActivePluginProvider

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_Notify_Delivery_Complete) {
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
        `when`(resourceHelper.gs(anyInt(), anyDouble())).thenReturn("SomeString")

        danaPump.bolusingTreatment = Treatment(packetInjector)
        val packet = DanaRS_Packet_Notify_Delivery_Complete(packetInjector)
        // test params
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        packet.handleMessage(createArray(17, 0.toByte()))
        Assert.assertEquals(true, danaPump.bolusDone)
        Assert.assertEquals("NOTIFY__DELIVERY_COMPLETE", packet.friendlyName)
    }
}