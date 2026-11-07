package app.aaps.pump.aaruy.compose

import android.content.Context
import android.os.Looper
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.FakeResult
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.comm.aaruyFrameKey
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfoDao
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import app.aaps.pump.aaruy.emulator.AaruyEmulatorBleTransport
import app.aaps.pump.aaruy.emulator.AaruyPumpEmulator
import app.aaps.pump.aaruy.services.AaruyBLE
import app.aaps.pump.aaruy.services.AaruyFillCannulaCommand
import app.aaps.pump.aaruy.services.AaruyMotorResult
import app.aaps.pump.aaruy.services.AaruyPrimeCommand
import app.aaps.pump.aaruy.services.AaruyReplaceSteps
import app.aaps.pump.aaruy.services.AaruyRewindCommand
import app.aaps.pump.aaruy.services.AaruyStartBasalCommand
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
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
    private val wizardPreferences = mock(Preferences::class.java)
    private val insulinManager = mock(InsulinManager::class.java)
    private val persistenceLayer = mock(PersistenceLayer::class.java)
    private val profileFunction = mock(ProfileFunction::class.java)
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
    private lateinit var steps: AaruyReplaceSteps
    private lateinit var wizard: AaruyReplaceWizardViewModel

    /** Runs each step as the queue does. The link stays up: the test opened it, and no queue closes it. */
    private val commandQueue: CommandQueue = mock(CommandQueue::class.java)

    private fun waitFor(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!condition()) {
            val state = if (::wizard.isInitialized) wizard.uiState.value else null
            assertTrue("Timeout waiting for: $what, state $state, pump ${pump.pumpState}", System.currentTimeMillis() < end)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(20)
        }
    }

    /** Moves the clock of the driver, so a long wait can end at once. The emulator keeps the real time. */
    @Volatile private var clockOffset = 0L

    @Before
    fun setUp() {
        `when`(dateUtil.now()).thenAnswer { System.currentTimeMillis() + clockOffset }
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
            pumpSync, tempBasalSyncMock(), mock(NotificationManager::class.java), mock(ResourceHelper::class.java)
        )
        steps = AaruyReplaceSteps(ble, pump, dateUtil)
        runBlocking {
            whenever(commandQueue.customCommand(any())).doSuspendableAnswer { steps.execute(it.arguments[0] as CustomCommand, FakeResult()) }
        }
        ble.connect("test", AaruyEmulatorBleTransport.DEVICE_ADDRESS)
        waitFor("connected") { pump.connectionState == ConnectionState.CONNECTED }
        waitFor("status") { pump.pumpState == AaruyPumpState.BASALRATE_RUNNING }
    }

    @After
    fun tearDown() = transport.shutdown()

    /** Created after the stubs of a test, because the steps are chosen when it starts. */
    private fun startWizard() {
        wizard = AaruyReplaceWizardViewModel(
            logger, commandQueue, pump, pumpSync, dateUtil, mock(ResourceHelper::class.java), mock(RxBus::class.java),
            wizardPreferences, profileFunction, insulinManager, persistenceLayer
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun untilFillCannula() {
        startWizard()
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
        // Every step that moves the motor went through the queue, as every command of every pump.
        runBlocking {
            verify(commandQueue).customCommand(any<AaruyRewindCommand>())
            verify(commandQueue).customCommand(any<AaruyPrimeCommand>())
            verify(commandQueue).customCommand(any<AaruyFillCannulaCommand>())
            verify(commandQueue).customCommand(any<AaruyStartBasalCommand>())
        }
    }

    /** The queue lets a bolus command end first. A bolus the pump runs with no command, as after an app restart, must not be ended. */
    @Test
    fun theRewindStepRefusesARunningBolus() {
        pump.pumpState = AaruyPumpState.LARGEDOSE_RUNNING
        val writes = transport.writes.size
        assertEquals(AaruyMotorResult.NOT_RUN, runBlocking { steps.rewind() })
        assertEquals("nothing was sent", writes, transport.writes.size)
    }

    @Test
    fun skippingTheCannulaStillStartsBasalAndLogsOnlyTheInsulin() {
        untilFillCannula()
        wizard.skipFillingCannula()
        waitFor("done") { wizard.uiState.value.step == AaruyReplaceStep.DONE }
        assertEquals(AaruyPumpState.BASALRATE_RUNNING, pump.pumpState)
        runBlocking { verify(pumpSync, times(1)).insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any()) }
    }

    /** Seen on a real pump: it is stopped already before the fill, so "stopped" alone said nothing. A fill that never ran is not done. */
    @Test
    fun aCannulaFillThePumpNeverRanIsNotDone() {
        untilFillCannula()
        transport.onPumpThread { transport.emulator.ignoreNextControl = AaruyPumpEmulator.CONTROL_PUSH_AIR }
        Thread.sleep(200)
        wizard.fillCannula()
        // Longer than the 3 s after which "stopped" alone counted as done.
        Thread.sleep(3_500)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AaruyReplaceStep.FILL_CANNULA, wizard.uiState.value.step)
        clockOffset += 121_000
        waitFor("the wait for the motor gave up") { !wizard.uiState.value.busy }
        assertEquals(R.string.alarm_discharge_liquid_fail, wizard.uiState.value.error)
        assertFalse(wizard.uiState.value.cannulaFilled)
        assertEquals(AaruyPumpState.STOP_RUNNING, pump.pumpState)
        runBlocking { verify(pumpSync, never()).insertTherapyEventIfNewWithTimestamp(any(), eq(TE.Type.CANNULA_CHANGE), anyOrNull(), anyOrNull(), any(), any()) }
    }

    /** The fill worked and the basal start did not. A second fill would go into the body, so the next press only starts basal. */
    @Test
    fun aFailedBasalStartAfterTheFillTriesOnlyTheBasalAgain() {
        untilFillCannula()
        transport.onPumpThread { transport.emulator.ignoreNextControl = AaruyPumpEmulator.CONTROL_RESTORE }
        Thread.sleep(200)
        wizard.fillCannula()
        waitFor("basal start failed") { wizard.uiState.value.cannulaFilled && !wizard.uiState.value.busy }
        assertEquals(R.string.alarm_basal_run_fail, wizard.uiState.value.error)
        assertEquals(AaruyReplaceStep.FILL_CANNULA, wizard.uiState.value.step)

        pump.forgetSeenStates()
        wizard.fillCannula()
        waitFor("done") { wizard.uiState.value.step == AaruyReplaceStep.DONE }
        assertFalse("no second fill", pump.hasSeen(AaruyPumpState.EXHAUSTING))
        assertEquals(AaruyPumpState.BASALRATE_RUNNING, pump.pumpState)
    }

    /**
     * The link was lost after the fill went out, so its state was never seen. The pump may have filled the cannula, which
     * is in the body by now: Fill must not be offered again, only the basal start. Nothing connects again while a
     * command of the queue runs, so it is not known at once.
     */
    @Test
    fun aFillWhoseLinkWasLostIsTakenAsFilled() {
        untilFillCannula()
        transport.onPumpThread { transport.emulator.ignoreNextControl = AaruyPumpEmulator.CONTROL_PUSH_AIR }
        Thread.sleep(200)
        wizard.fillCannula()
        Thread.sleep(300)
        transport.loseConnection()
        waitFor("link lost") { pump.connectionState != ConnectionState.CONNECTED }
        waitFor("not known") { !wizard.uiState.value.busy }
        assertEquals(R.string.aaruy_fill_unknown, wizard.uiState.value.error)
        assertTrue("no second fill offered", wizard.uiState.value.cannulaFilled)
    }

    /** The screen says at once why there is no rewind. The step itself checks it again when the queue runs it. */
    @Test
    fun noRewindDuringABolus() {
        startWizard()
        pump.pumpState = AaruyPumpState.LARGEDOSE_RUNNING
        wizard.rewind()
        assertEquals(R.string.aaruy_state_bolus, wizard.uiState.value.error)
        assertFalse(wizard.uiState.value.busy)
        assertEquals(AaruyReplaceStep.REWIND, wizard.uiState.value.step)
    }

    /** The view model lives as long as the pump screen. The next change of reservoir starts at the first step. */
    @Test
    fun aResetStartsAgainAtTheFirstStep() {
        untilFillCannula()
        wizard.fillCannula()
        waitFor("done") { wizard.uiState.value.step == AaruyReplaceStep.DONE }
        wizard.reset()
        assertEquals(AaruyReplaceStep.REWIND, wizard.uiState.value.step)
        assertFalse(wizard.uiState.value.cannulaFilled)
        assertFalse(wizard.uiState.value.primed)
    }

    @Test
    fun nextIsNotPossibleBeforeThePumpPrimed() {
        startWizard()
        wizard.rewind()
        waitFor("rewound") { wizard.uiState.value.step == AaruyReplaceStep.ASSEMBLE && !wizard.uiState.value.busy }
        wizard.assembled()
        assertFalse(wizard.uiState.value.primed)
    }

    @Test
    fun insulinAndSiteStepsComeWhenTheyApplyAndTheSiteGoesOnTheNewCannula() {
        whenever(insulinManager.insulins).thenReturn(arrayListOf(ICfg("Fiasp", 55, 6.0, 1.0), ICfg("Lyumjev", 45, 5.0, 1.0)))
        whenever(wizardPreferences.get(BooleanKey.SiteRotationManagePump)).thenReturn(true)
        val cannula = TE(timestamp = 0, type = TE.Type.CANNULA_CHANGE, glucoseUnit = GlucoseUnit.MGDL)
        runBlocking {
            whenever(persistenceLayer.getTherapyEventDataFromTime(any(), any<Boolean>())).thenReturn(emptyList())
            whenever(persistenceLayer.getTherapyEventDataFromToTime(any(), any())).thenReturn(listOf(cannula))
            whenever(persistenceLayer.insertOrUpdateTherapyEvent(any())).thenReturn(PersistenceLayer.TransactionResult())
            whenever(profileFunction.createProfileSwitchWithNewInsulin(any(), any())).thenReturn(true)
        }
        startWizard()
        assertEquals(AaruyReplaceStep.entries, wizard.uiState.value.steps)

        wizard.rewind()
        waitFor("rewound") { wizard.uiState.value.step == AaruyReplaceStep.ASSEMBLE && !wizard.uiState.value.busy }
        wizard.assembled()
        assertEquals(AaruyReplaceStep.SELECT_INSULIN, wizard.uiState.value.step)
        wizard.insulinSelected()
        assertEquals(AaruyReplaceStep.PRIME, wizard.uiState.value.step)
        wizard.prime()
        waitFor("primed") { wizard.uiState.value.primed }
        wizard.dropletsSeen()
        wizard.connectedToBody()
        assertEquals(AaruyReplaceStep.SITE_LOCATION, wizard.uiState.value.step)
        wizard.updateSiteLocation(TE.Location.FRONT_LEFT_LOWER_ABDOMEN)
        wizard.completeSiteLocation()
        assertEquals(AaruyReplaceStep.FILL_CANNULA, wizard.uiState.value.step)

        wizard.fillCannula()
        waitFor("done") { wizard.uiState.value.step == AaruyReplaceStep.DONE }
        val saved = argumentCaptor<TE>()
        runBlocking { verify(persistenceLayer).insertOrUpdateTherapyEvent(saved.capture()) }
        assertEquals(TE.Location.FRONT_LEFT_LOWER_ABDOMEN, saved.firstValue.location)
        // No insulin ran before, so the chosen one is switched to.
        runBlocking { verify(profileFunction).createProfileSwitchWithNewInsulin(any(), any()) }
    }

    @Test
    fun withOneInsulinAndNoSiteRotationTheStepsAreTheOldOnes() {
        startWizard()
        assertFalse(AaruyReplaceStep.SELECT_INSULIN in wizard.uiState.value.steps)
        assertFalse(AaruyReplaceStep.SITE_LOCATION in wizard.uiState.value.steps)
    }
}
