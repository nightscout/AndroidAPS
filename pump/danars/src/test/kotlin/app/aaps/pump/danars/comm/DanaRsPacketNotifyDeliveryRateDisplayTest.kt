package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

class DanaRsPacketNotifyDeliveryRateDisplayTest : DanaRSTestBase() {

    @Mock lateinit var danaHistoryDatabase: DanaHistoryDatabase
    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var blePreCheck: BlePreCheck

    private lateinit var danaRSPlugin: DanaRSPlugin

    @Test
    fun runTest() {
        whenever(rh.gs(ArgumentMatchers.anyInt(), anyOrNull())).thenReturn("SomeString")
        danaPump.bolusingDetailedBolusInfo = DetailedBolusInfo().apply { insulin = 1.0 }
        bolusProgressData.start(1.0, isSMB = false)
        val packet = DanaRSPacketNotifyDeliveryRateDisplay(aapsLogger, ch, bolusProgressData, danaPump)
        // test params
        Assertions.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        // 0% delivered
        packet.handleMessage(createArray(17, 0.toByte()))
        Assertions.assertEquals(true, packet.failed)
        // 100 % delivered
        packet.handleMessage(createArray(17, 1.toByte()))
        Assertions.assertEquals(false, packet.failed)
        Assertions.assertEquals("NOTIFY__DELIVERY_RATE_DISPLAY", packet.friendlyName)
    }

    @BeforeEach
    fun mock() {
        danaRSPlugin =
            DanaRSPlugin(
                aapsLogger, rh, preferences, commandQueue, aapsSchedulers, rxBus, context, constraintChecker, danaPump, pumpSync,
                detailedBolusInfoStorage, temporaryBasalStorage, fabricPrivacy, dateUtil, notificationManager, danaHistoryDatabase, decimalFormatter, pumpEnactResultProvider, blePreCheck, bolusProgressData
            )
        danaPump.bolusingDetailedBolusInfo = DetailedBolusInfo()
    }
}