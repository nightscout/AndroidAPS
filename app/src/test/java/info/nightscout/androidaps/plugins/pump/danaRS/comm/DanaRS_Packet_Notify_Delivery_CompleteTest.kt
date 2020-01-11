package info.nightscout.androidaps.plugins.pump.danaRS.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.AAPSMocker
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.treatments.Treatment
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.SP
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(MainApp::class, SP::class, L::class)
class DanaRS_Packet_Notify_Delivery_CompleteTest {

    @Mock lateinit var defaultValueHelper: DefaultValueHelper
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var configBuilderPlugin: ConfigBuilderPlugin

    private var treatmentInjector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is Treatment) {
                it.defaultValueHelper = defaultValueHelper
                it.resourceHelper = resourceHelper
                it.profileFunction = profileFunction
                it.configBuilderPlugin = configBuilderPlugin
            }
        }
    }

    @Test fun runTest() {
        AAPSMocker.mockMainApp()
        AAPSMocker.mockApplicationContext()
        AAPSMocker.mockSP()
        AAPSMocker.mockL()
        AAPSMocker.mockStrings()
        val packet = DanaRS_Packet_Notify_Delivery_Complete(0.5, Treatment(treatmentInjector))
        // test params
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        packet.handleMessage(createArray(17, 0.toByte()))
        Assert.assertEquals(true, DanaRS_Packet_Notify_Delivery_Complete.done)
        Assert.assertEquals("NOTIFY__DELIVERY_COMPLETE", packet.friendlyName)
    }

    fun createArray(length: Int, fillWith: Byte): ByteArray {
        val ret = ByteArray(length)
        for (i in 0 until length) {
            ret[i] = fillWith
        }
        return ret
    }

    fun createArray(length: Int, fillWith: Double): DoubleArray {
        val ret = DoubleArray(length)
        for (i in 0 until length) {
            ret[i] = fillWith
        }
        return ret
    }
}