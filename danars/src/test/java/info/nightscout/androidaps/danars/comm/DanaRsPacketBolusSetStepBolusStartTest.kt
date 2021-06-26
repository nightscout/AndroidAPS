package info.nightscout.androidaps.danars.comm

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.DanaRSPlugin
import info.nightscout.androidaps.danars.DanaRSTestBase
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.TemporaryBasalStorage
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConstraintChecker::class, DetailedBolusInfoStorage::class, TemporaryBasalStorage::class)
class DanaRsPacketBolusSetStepBolusStartTest : DanaRSTestBase() {

    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var context: Context
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var pumpSync: PumpSync

    private lateinit var danaRSPlugin: DanaRSPlugin

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketBolusSetStepBolusStart) {
                it.aapsLogger = aapsLogger
                it.danaPump = danaPump
                it.constraintChecker = constraintChecker
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketBolusSetStepBolusStart(packetInjector)
        // test params
        val testParams = packet.getRequestParams()
        Assert.assertEquals(0.toByte(), testParams[0])
        Assert.assertEquals(0.toByte(), testParams[2])
        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(byteArrayOf(1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("BOLUS__SET_STEP_BOLUS_START", packet.friendlyName)
    }

    @Before
    fun mock() {
        danaRSPlugin = DanaRSPlugin({ AndroidInjector { } }, aapsLogger, aapsSchedulers, rxBus, context, resourceHelper, constraintChecker, profileFunction, sp, commandQueue, danaPump, pumpSync, detailedBolusInfoStorage, temporaryBasalStorage, fabricPrivacy, dateUtil)
        Mockito.`when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))
    }
}