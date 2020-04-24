package info.nightscout.androidaps.plugins.pump.danaRS.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin
import info.nightscout.androidaps.plugins.treatments.Treatment
import info.nightscout.androidaps.utils.DefaultValueHelper
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
@PrepareForTest(RxBusWrapper::class)
class DanaRS_Packet_Notify_Delivery_CompleteTest : DanaRSTestBase() {

    @Mock lateinit var danaRSPlugin: DanaRSPlugin
    @Mock lateinit var activePlugin: ActivePluginProvider

    private var treatmentInjector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
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

        danaRSPlugin.bolusingTreatment = Treatment(treatmentInjector)
        val packet = DanaRS_Packet_Notify_Delivery_Complete(aapsLogger, rxBus, resourceHelper, danaRSPlugin)
        // test params
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        packet.handleMessage(createArray(17, 0.toByte()))
        Assert.assertEquals(true, danaRSPlugin.bolusDone)
        Assert.assertEquals("NOTIFY__DELIVERY_COMPLETE", packet.friendlyName)
    }
}