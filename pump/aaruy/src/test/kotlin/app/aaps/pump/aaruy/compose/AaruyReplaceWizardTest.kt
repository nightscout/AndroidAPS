package app.aaps.pump.aaruy.compose

import android.content.Context
import android.os.Looper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.comm.aaruyFrameKey
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfoDao
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import app.aaps.pump.aaruy.emulator.AaruyEmulatorBleTransport
import app.aaps.pump.aaruy.emulator.AaruyPumpEmulator
import app.aaps.pump.aaruy.services.AaruyBLE
import app.aaps.pump.aaruy.services.AaruyTempBasalSync
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** The wizard on the driver and the emulator, which follows what a real pump did in this flow. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaruyReplaceWizardTest {

    private fun tempBasalSyncMock() = mock(AaruyTempBasalSync::class.java)

    private val logger = mock(AAPSLogger::class.java)
    private val dateUtil = mock(DateUtil::class.java)
    private val pumpSync = mock(PumpSync::class.java)
    private val pump = AaruyPump(
        logger,
        mock(ResourceHelper::class.java),
        mock(Preferences::class.java),
        dateUtil,
        pumpSync,
        mock(TemporaryBasalStorage::class.java),
        mock(BolusProgressData::class.java)
    )
    private val transport = AaruyEmulatorBleTransport(AaruyPumpEmulator(keyProvider = { aaruyFrameKey() }))
    private lateinit var ble: AaruyBLE
    private lateinit var wizard: AaruyReplaceWizardViewModel

    private fun waitFor(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!condition()) {
            assertTrue("Timeout waiting for: $what, state ${wizard.uiState.value}, pump ${pump.pumpState}", System.currentTimeMillis() < end)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(20)
        }
    }

    @Before
    fun setUp() {
        `when`(dateUtil.now()).thenAnswer { System.currentTimeMillis() }
        runBlocking { whenever(pumpSync.insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn(true) }
        ble = AaruyBLE(
            transport,
            logger,
            RuntimeEnvironment.getApplication() as Context,
            pump,
            dateUtil,
            mock(RxBus::class.java),
            mock(Preferences::class.java),
            mock(HistoryUnitInfoDao::class.java),
            mock(HistoryDailyUnitInfoDao::class.java),
            mock(DetailedBolusInfoStorage::class.java),
            pumpSync, tempBasalSyncMock()
        )
        wizard = AaruyReplaceWizardViewModel(logger, ble, pump, pumpSync, dateUtil)
        ble.connect("test", AaruyEmulatorBleTransport.DEVICE_ADDRESS)
        waitFor("connected") { pump.connectionState == ConnectionState.CONNECTED }
        waitFor("status") { pump.pumpState == AaruyPumpState.BASALRATE_RUNNING }
    }

    @After
    fun tearDown() = transport.shutdown()

    private fun untilFillCannula() {
        wizard.rewind()
        waitFor("rewound") { wizard.uiState.value.step == AaruyReplaceStep.ASSEMBLE && !wizard.uiState.value.busy }
        assertEquals(AaruyPumpState.STOP_RUNNING, pump.pumpState)
        wizard.assembled()
        wizard.prime()
        waitFor("primed") { wizard.uiState.value.primed }
        wizard.askDroplets()
        wizard.dropletsSeen()
        wizard.connectedToBody()
        assertEquals(AaruyReplaceStep.FILL_CANNULA, wizard.uiState.value.step)
    }

    @Test
    fun theWholeFlowEndsWithBasalRunning() {
        untilFillCannula()
        wizard.fillCannula()
        waitFor("done") { wizard.uiState.value.step == AaruyReplaceStep.DONE }
        assertEquals(AaruyPumpState.BASALRATE_RUNNING, pump.pumpState)
        runBlocking { verify(pumpSync, times(2)).insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any()) }
    }

    @Test
    fun skippingTheCannulaStillStartsBasalAndLogsOnlyTheInsulin() {
        untilFillCannula()
        wizard.skipFillingCannula()
        waitFor("done") { wizard.uiState.value.step == AaruyReplaceStep.DONE }
        assertEquals(AaruyPumpState.BASALRATE_RUNNING, pump.pumpState)
        runBlocking { verify(pumpSync, times(1)).insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any()) }
    }

    @Test
    fun nextIsNotPossibleBeforeThePumpPrimed() {
        wizard.rewind()
        waitFor("rewound") { wizard.uiState.value.step == AaruyReplaceStep.ASSEMBLE && !wizard.uiState.value.busy }
        wizard.assembled()
        assertFalse(wizard.uiState.value.primed)
    }
}
