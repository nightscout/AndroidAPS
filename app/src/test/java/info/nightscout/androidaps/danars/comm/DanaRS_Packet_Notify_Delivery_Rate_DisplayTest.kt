package info.nightscout.androidaps.danars.comm

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.danars.DanaRSPlugin
import info.nightscout.androidaps.db.Treatment
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConstraintChecker::class, RxBusWrapper::class, DetailedBolusInfoStorage::class)
class DanaRS_Packet_Notify_Delivery_Rate_DisplayTest : DanaRSTestBase() {

    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var context: Context
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage

    private lateinit var danaRSPlugin: DanaRSPlugin

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_Notify_Delivery_Rate_Display) {
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
        `when`(resourceHelper.gs(ArgumentMatchers.anyInt(), anyObject())).thenReturn("SomeString")
        // val packet = DanaRS_Packet_Notify_Delivery_Rate_Display(1.0, Treatment(treatmentInjector))
        val packet = DanaRS_Packet_Notify_Delivery_Rate_Display(packetInjector)
        // test params
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
// 0% delivered
        packet.handleMessage(createArray(17, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        // 100 % delivered
        packet.handleMessage(createArray(17, 1.toByte()))
        Assert.assertEquals(false, packet.failed)
        Assert.assertEquals("NOTIFY__DELIVERY_RATE_DISPLAY", packet.friendlyName)
    }

    @Before
    fun mock() {
        danaRSPlugin = DanaRSPlugin(HasAndroidInjector { AndroidInjector { Unit } }, aapsLogger, aapsSchedulers, rxBus, context, resourceHelper, constraintChecker, profileFunction, activePluginProvider, sp, commandQueue, danaPump, detailedBolusInfoStorage, fabricPrivacy, dateUtil)
        danaPump.bolusingTreatment = Treatment(packetInjector)
    }
}