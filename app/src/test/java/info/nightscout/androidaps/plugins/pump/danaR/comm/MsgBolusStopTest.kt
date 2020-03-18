package info.nightscout.androidaps.plugins.pump.danaR.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.treatments.Treatment
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgBolusStopTest : DanaRTestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper
    val rxBus = RxBusWrapper()

    @Test fun runTest() {
        `when`(resourceHelper.gs(R.string.overview_bolusprogress_delivered)).thenReturn("Delivered")
        danaRPump.bolusingTreatment = Treatment(HasAndroidInjector { AndroidInjector {  } })
        val packet = MsgBolusStop(aapsLogger, rxBus, resourceHelper, danaRPump)

        // test message decoding
        packet.handleMessage(ByteArray(100))
        Assert.assertEquals(true, danaRPump.bolusStopped)
    }
}