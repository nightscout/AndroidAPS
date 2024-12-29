package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito

class DanaRsPacketBolusSetStepBolusStartTest : DanaRSTestBase() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var danaHistoryDatabase: DanaHistoryDatabase

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
        Assertions.assertEquals(0.toByte(), testParams[0])
        Assertions.assertEquals(0.toByte(), testParams[2])
        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        packet.handleMessage(byteArrayOf(1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("BOLUS__SET_STEP_BOLUS_START", packet.friendlyName)
    }

    @BeforeEach
    fun mock() {
        danaRSPlugin =
            DanaRSPlugin(
                aapsLogger,
                aapsSchedulers,
                rxBus,
                context,
                rh,
                constraintChecker,
                profileFunction,
                commandQueue,
                danaPump,
                pumpSync,
                preferences,
                detailedBolusInfoStorage,
                temporaryBasalStorage,
                fabricPrivacy,
                dateUtil,
                uiInteraction,
                danaHistoryDatabase,
                decimalFormatter,
                instantiator
            )
        Mockito.`when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(ConstraintObject(0.0, aapsLogger))
    }
}