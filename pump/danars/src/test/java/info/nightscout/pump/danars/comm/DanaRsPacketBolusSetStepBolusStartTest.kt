package info.nightscout.pump.danars.comm

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.pump.DetailedBolusInfoStorage
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.TemporaryBasalStorage
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.pump.dana.database.DanaHistoryDatabase
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito

class DanaRsPacketBolusSetStepBolusStartTest : DanaRSTestBase() {

    @Mock lateinit var constraintChecker: Constraints
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var context: Context
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var danaHistoryDatabase: DanaHistoryDatabase

    private lateinit var danaRSPlugin: info.nightscout.pump.danars.DanaRSPlugin

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
        danaRSPlugin =
            info.nightscout.pump.danars.DanaRSPlugin(
                { AndroidInjector { } },
                aapsLogger,
                aapsSchedulers,
                rxBus,
                context,
                rh,
                constraintChecker,
                profileFunction,
                sp,
                commandQueue,
                danaPump,
                pumpSync,
                detailedBolusInfoStorage,
                temporaryBasalStorage,
                fabricPrivacy,
                dateUtil,
                activityNames,
                danaHistoryDatabase
            )
        Mockito.`when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))
    }
}