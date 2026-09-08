package app.aaps.implementation.queue

import android.content.Context
import android.os.PowerManager
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.CoreUiStrings
import app.aaps.implementation.profile.ProfileSwitchSilentGate
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import java.util.Calendar
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CommandQueueImplementationTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var powerManager: PowerManager
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var localAlertUtils: LocalAlertUtils
    private val localAlertUtilsProvider: () -> LocalAlertUtils by lazy { { localAlertUtils } }
    @Mock lateinit var smsCommunicator: SmsCommunicator
    private val smsCommunicatorProvider: () -> SmsCommunicator by lazy { { smsCommunicator } }
    @Mock lateinit var commandExecutor: CommandExecutor
    private val commandExecutorProvider: () -> CommandExecutor by lazy { { commandExecutor } }

    private val testScope = CoroutineScope(Dispatchers.Unconfined)
    private val bolusProgressData by lazy { BolusProgressData(ch, testScope) }
    private val profileSwitchSilentGate = ProfileSwitchSilentGate()

    class CommandQueueMocked(
        aapsLogger: AAPSLogger,
        rxBus: RxBus,
        rh: ResourceHelper,
        constraintChecker: ConstraintsChecker,
        profileFunction: ProfileFunction,
        activePlugin: ActivePlugin,
        config: Config,
        dateUtil: DateUtil,
        fabricPrivacy: FabricPrivacy,
        notificationManager: NotificationManager,
        persistenceLayer: PersistenceLayer,
        decimalFormatter: DecimalFormatter,
        pumpEnactResultProvider: () -> PumpEnactResult,
        pumpSync: PumpSync,
        preferences: Preferences,
        profileSwitchSilentGate: ProfileSwitchSilentGate,
        localAlertUtils: () -> LocalAlertUtils,
        smsCommunicator: () -> SmsCommunicator,
        commandExecutor: () -> CommandExecutor,
        appScope: CoroutineScope,
        bolusProgressData: BolusProgressData
    ) : CommandQueueImplementation(
        aapsLogger, rxBus, rh, constraintChecker, profileFunction,
        activePlugin, config, dateUtil, fabricPrivacy,
        notificationManager, persistenceLayer, decimalFormatter, pumpEnactResultProvider, pumpSync, preferences, profileSwitchSilentGate, localAlertUtils, smsCommunicator, commandExecutor, appScope, bolusProgressData
    ) {

        override fun notifyAboutNewCommand(): Boolean = true

    }

    private lateinit var commandQueue: CommandQueueImplementation

    @BeforeEach
    fun prepare() {
        runTest {
            whenever(persistenceLayer.observeChanges(anyOrNull<KClass<*>>())).thenReturn(emptyFlow())
            commandQueue = CommandQueueMocked(
                aapsLogger,
                rxBus,
                rh,
                constraintChecker,
                profileFunction,
                activePlugin,
                config,
                dateUtil,
                fabricPrivacy,
                notificationManager,
                persistenceLayer,
                decimalFormatter,
                { pumpEnactResultProvider() },
                pumpSync,
                preferences,
                profileSwitchSilentGate,
                localAlertUtilsProvider,
                smsCommunicatorProvider,
                commandExecutorProvider,
                testScope,
                bolusProgressData
            )
            testPumpPlugin.pumpDescription.basalMinimumRate = 0.1
            testPumpPlugin.connected = true

            whenever(context.getSystemService(Context.POWER_SERVICE)).thenReturn(powerManager)
            whenever(activePlugin.activePump).thenReturn(testPumpPlugin)
            whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(effectiveProfileSwitch)
            whenever(persistenceLayer.getNewestBolus()).thenReturn(
                BS(
                    timestamp = Calendar.getInstance().also { it.set(2000, 0, 1) }.timeInMillis,
                    type = BS.Type.NORMAL,
                    amount = 0.0,
                    iCfg = someICfg
                )
            )
            whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)

            val bolusConstraint = ConstraintObject(0.0, aapsLogger)
            whenever(constraintChecker.applyBolusConstraints(anyOrNull())).thenReturn(bolusConstraint)
            whenever(constraintChecker.applyExtendedBolusConstraints(anyOrNull())).thenReturn(bolusConstraint)
            val carbsConstraint = ConstraintObject(0, aapsLogger)
            whenever(constraintChecker.applyCarbsConstraints(anyOrNull())).thenReturn(carbsConstraint)
            val rateConstraint = ConstraintObject(0.0, aapsLogger)
            whenever(constraintChecker.applyBasalConstraints(anyOrNull(), anyOrNull())).thenReturn(rateConstraint)
            val percentageConstraint = ConstraintObject(0, aapsLogger)
            whenever(constraintChecker.applyBasalPercentConstraints(anyOrNull(), anyOrNull())).thenReturn(percentageConstraint)
            whenever(rh.gs(app.aaps.core.ui.R.string.connectiontimedout)).thenReturn("Connection timed out")
            whenever(rh.gs(app.aaps.implementation.R.string.executing_right_now)).thenReturn("Executing right now")
            whenever(rh.gs(app.aaps.core.ui.R.string.command_replaced)).thenReturn("Replaced by newer command")
            whenever(rh.gs(InterfacesStrings.format_insulin_units)).thenReturn("%1\$.2f U")
            whenever(rh.gs(app.aaps.core.ui.R.string.goingtodeliver)).thenReturn("Going to deliver %1\$.2f U")
        }
    }

    @Test
    fun commandIsPickedUp() = runTest {
        lateinit var executor: CommandExecutor
        commandQueue = CommandQueueImplementation(
            aapsLogger, rxBus, rh, constraintChecker, profileFunction, activePlugin, config, dateUtil,
            fabricPrivacy, notificationManager, persistenceLayer, decimalFormatter, { pumpEnactResultProvider() },
            pumpSync, preferences, profileSwitchSilentGate, localAlertUtilsProvider, smsCommunicatorProvider,
            { executor }, testScope, bolusProgressData
        )
        // Real executor sharing this queue: notifyAboutNewCommand() signals it and it drains on its own thread.
        executor = CommandExecutor(
            aapsLogger, fabricPrivacy, commandQueue, rxBus, activePlugin, rh, preferences, config, bolusProgressData, TestCommandExecutionPlatform()
        )

        // start with empty queue
        assertThat(commandQueue.size()).isEqualTo(0)

        // Run bolus() on a real dispatcher, same as the carbs test below: its internal deferred.await()
        // is completed by the executor off the test scheduler, so only real threads can resume it.
        var result: PumpEnactResult? = null
        CoroutineScope(Dispatchers.IO).launch { result = commandQueue.bolus(DetailedBolusInfo()) }

        // Wait for the command to finish instead of sleeping a fixed time.
        //
        // The queue size is deliberately not asserted while the command is in flight. CommandExecutor
        // takes the command with pickup(), which removes it from the queue before running it, so the
        // size goes 0 -> 1 -> 0 on the executor's own thread and "1" can be gone before the next line
        // reads it. Asserting it made this test fail in CI with `expected 1 but was 0`, and a fixed
        // sleep afterwards could just as easily be too short on a loaded machine.
        var waitedMs = 0
        while (result == null && waitedMs < 8000) {
            Thread.sleep(100); waitedMs += 100
        }

        // A successful result can only come back through the executor: every early return in bolus()
        // reports failure, so this proves the command was queued, picked up and run.
        assertThat(result?.success).isTrue()
        // And the queue is empty again afterwards - checked once the work is known to be over.
        assertThat(commandQueue.size()).isEqualTo(0)
    }

    @Test
    fun bolusSucceedsButCarbsFailToStore_postsCarbsLostNotification() = runTest {
        // Regression: bolus delivered, but persisting the accompanying carbs threw. This used to be
        // swallowed log-only, leaving COB/IOB silently wrong. The failure must now surface a notification
        // so the user knows to re-enter the carbs. See CommandQueueImplementation.bolus() post-bolus catch.
        lateinit var executor: CommandExecutor
        commandQueue = CommandQueueImplementation(
            aapsLogger, rxBus, rh, constraintChecker, profileFunction, activePlugin, config, dateUtil,
            fabricPrivacy, notificationManager, persistenceLayer, decimalFormatter, { pumpEnactResultProvider() },
            pumpSync, preferences, profileSwitchSilentGate, localAlertUtilsProvider, smsCommunicatorProvider,
            { executor }, testScope, bolusProgressData
        )
        executor = CommandExecutor(
            aapsLogger, fabricPrivacy, commandQueue, rxBus, activePlugin, rh, preferences, config, bolusProgressData, TestCommandExecutionPlatform()
        )
        whenever(rh.gs(app.aaps.core.ui.R.string.carbs_not_saved_after_bolus)).thenReturn("Carbs could not be saved")
        // The pump delivers successfully (TestPumpPlugin), but storing carbs blows up.
        whenever(persistenceLayer.insertOrUpdateCarbs(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenThrow(RuntimeException("db unavailable"))

        // Run the whole bolus() on a real dispatcher: its internal deferred.await() must resume on real
        // threads (the queue worker completes it off the test scheduler), which Thread.sleep can advance.
        // insulin != 0 → NOT the carbs-only path; carbs != 0 → carbs persisted only after the bolus succeeds.
        var result: PumpEnactResult? = null
        CoroutineScope(Dispatchers.IO).launch { result = commandQueue.bolus(DetailedBolusInfo().apply { insulin = 1.0; carbs = 20.0 }) }
        var waitedMs = 0
        while (result == null && waitedMs < 8000) {
            Thread.sleep(100); waitedMs += 100
        }
        assertThat(result?.success).isTrue()

        // The user is alerted (URGENT) that the carbs were lost — not silently dropped.
        verify(notificationManager).post(
            eq(NotificationId.CARBS_STORE_FAILED), eq(CoreUiStrings.carbs_not_saved_after_bolus),
            any<NotificationLevel>(), any<Int>(), any<Long>(), any<Long>(), anyOrNull(), any<List<NotificationAction>>(), anyOrNull()
        )
    }

    @Test
    fun profileChangeCollectorSurvivesException() = runTest {
        // Regression: a throwable during one profile change must not permanently wedge the
        // profile-change collector. Before hardening, the first failure cancelled the flow and every
        // later ProfileSwitch was silently dropped (no pump push, no EffectiveProfileSwitch created,
        // isProfileChangePending() stuck true). See onProfileChanged() try/catch + retryWhen backstop.

        // First emission blows up inside onProfileChanged(); the second must still be processed.
        whenever(profileFunction.getRequestedProfile())
            .thenThrow(RuntimeException("induced failure"))
            .thenReturn(profileSwitch)

        rxBus.send(EventProfileChangeRequested())
        rxBus.send(EventProfileChangeRequested())

        // Collector reacted to BOTH events. With the old (unguarded) collector the first throw would
        // have cancelled the flow and the second event would never reach getRequestedProfile() (== 1).
        verify(profileFunction, times(2)).getRequestedProfile()
    }

    // region postProfileWriteResult — the central, driver-agnostic profile-set notification contract.
    // Drivers now only return (success, enacted, comment); every notification decision lives here.

    private fun enactResult(isSuccess: Boolean, isEnacted: Boolean, commentText: String = ""): PumpEnactResult {
        val result = mock<PumpEnactResult>()
        whenever(result.success).thenReturn(isSuccess)
        whenever(result.enacted).thenReturn(isEnacted)
        whenever(result.comment).thenReturn(commentText)
        return result
    }

    // Both helpers match the String post() overload (id, text, level, validMinutes, sound, actions, validityCheck).
    // PROFILE_SET_OK now passes the TextRef and lets the notification resolve it, so this matches the
    // TextRef overload (id, textRef, level, validMinutes, date, validTo, sound, actions, validityCheck).
    private fun verifyOkPosted() =
        verify(notificationManager).post(
            eq(NotificationId.PROFILE_SET_OK), eq(CoreUiStrings.profile_set_ok),
            any<NotificationLevel>(), any<Int>(), any<Long>(), any<Long>(),
            anyOrNull(), any<List<NotificationAction>>(), anyOrNull()
        )

    private fun verifyFailurePosted(text: String) =
        verify(notificationManager).post(
            eq(NotificationId.FAILED_UPDATE_PROFILE), eq(text), any<NotificationLevel>(), any<Int>(),
            eq(AlarmSound.BOLUS_ERROR), any<List<NotificationAction>>(), anyOrNull()
        )

    private fun verifyNothingPosted() =
        verify(notificationManager, never()).post(any<NotificationId>(), any<String>(), any<NotificationLevel>(), any<Int>(), anyOrNull(), any<List<NotificationAction>>(), anyOrNull())

    @Test
    fun postProfileWriteResult_updated_postsOkAndClearsFailure() {
        // profile updated: success=true, enacted=true, not silent → confirmation shown, stale failure cleared.
        whenever(rh.gs(CoreUiStrings.profile_set_ok)).thenReturn("Basal profile in pump updated")

        val persisted = commandQueue.postProfileWriteResult(enactResult(isSuccess = true, isEnacted = true), silent = false)

        assertThat(persisted).isTrue()
        verify(notificationManager).dismiss(NotificationId.FAILED_UPDATE_PROFILE)
        verifyOkPosted()
    }

    @Test
    fun postProfileWriteResult_alreadySet_isSilentButPersists() {
        // already set (basal unchanged): success=true, enacted=false → no confirmation, but still persist EPS.
        val persisted = commandQueue.postProfileWriteResult(enactResult(isSuccess = true, isEnacted = false), silent = false)

        assertThat(persisted).isTrue()
        verify(notificationManager).dismiss(NotificationId.FAILED_UPDATE_PROFILE)
        verifyNothingPosted()
    }

    @Test
    fun postProfileWriteResult_notInitialized_isSilentButPersists() {
        // not initialized maps to success=true, enacted=false at the driver — same handling as already-set:
        // no alarm, no confirmation, but the EPS is created so the loop has a profile.
        val persisted = commandQueue.postProfileWriteResult(enactResult(isSuccess = true, isEnacted = false), silent = false)

        assertThat(persisted).isTrue()
        verifyNothingPosted()
    }

    @Test
    fun postProfileWriteResult_error_ringsFailureAlarmAndDoesNotPersist() {
        // any error: success=false → persistent FAILED_UPDATE_PROFILE card rung with boluserror; driver comment surfaces.
        val persisted = commandQueue.postProfileWriteResult(enactResult(isSuccess = false, isEnacted = false, commentText = "pump rejected"), silent = false)

        assertThat(persisted).isFalse()
        verifyFailurePosted("pump rejected")
        verify(notificationManager, never()).dismiss(any<NotificationId>())
    }

    @Test
    fun postProfileWriteResult_timeout_ringsFailureAlarmWithFallbackText() {
        // timeout: result == null (deferred pump callback never arrived) → treated as failure, generic fallback text.
        whenever(rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile)).thenReturn("Failed to update basal profile")

        val persisted = commandQueue.postProfileWriteResult(null, silent = false)

        assertThat(persisted).isFalse()
        verifyFailurePosted("Failed to update basal profile")
        verify(notificationManager, never()).dismiss(any<NotificationId>())
    }

    @Test
    fun postProfileWriteResult_silentEnacted_suppressesConfirmation() {
        // issue #4959: a Scene reverting its own ProfileSwitch enacts a real write but must stay silent —
        // no "Basal profile in pump updated" card. Still persists and clears any stale failure.
        val persisted = commandQueue.postProfileWriteResult(enactResult(isSuccess = true, isEnacted = true), silent = true)

        assertThat(persisted).isTrue()
        verify(notificationManager).dismiss(NotificationId.FAILED_UPDATE_PROFILE)
        verifyNothingPosted()
    }
    // endregion

    @Test
    fun doTests() = runTest {

        // start with empty queue
        assertThat(commandQueue.size()).isEqualTo(0)

        // add bolus command
        backgroundScope.launch { commandQueue.bolus(DetailedBolusInfo()) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)

        // add READSTATUS
        backgroundScope.launch { commandQueue.readStatus("anyString") }
        yield()
        assertThat(commandQueue.size()).isEqualTo(2)

        // adding another bolus should remove the first one (size still == 2)
        backgroundScope.launch { commandQueue.bolus(DetailedBolusInfo()) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(2)

        // clear the queue should reset size
        commandQueue.clear()
        assertThat(commandQueue.size()).isEqualTo(0)

        // add tempbasal
        backgroundScope.launch { commandQueue.tempBasalAbsolute(0.0, 30, true, validProfile, PumpSync.TemporaryBasalType.NORMAL) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)

        // add tempbasal percent. it should replace previous TEMPBASAL
        backgroundScope.launch { commandQueue.tempBasalPercent(0, 30, true, validProfile, PumpSync.TemporaryBasalType.NORMAL) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)

        // cancel tempbasal it should replace previous TEMPBASAL
        backgroundScope.launch { commandQueue.cancelTempBasal(enforceNew = false, autoForced = false) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)

        // add extended bolus
        backgroundScope.launch { commandQueue.extendedBolus(1.0, 30) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(2)

        // add extended should remove previous extended setting
        backgroundScope.launch { commandQueue.extendedBolus(1.0, 30) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(2)

        // cancel extended bolus should replace previous extended
        backgroundScope.launch { commandQueue.cancelExtended() }
        yield()
        assertThat(commandQueue.size()).isEqualTo(2)

        // add setProfile
        // TODO: this crash the test
        //        commandQueue.setProfile(validProfile, null)
        //        assertThat(commandQueue.size()).isEqualTo(3)

        // add loadHistory
        backgroundScope.launch { commandQueue.loadHistory(0.toByte()) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(3)

        // add loadEvents
        backgroundScope.launch { commandQueue.loadEvents() }
        yield()
        assertThat(commandQueue.size()).isEqualTo(4)

        // add clearAlarms
        backgroundScope.launch { commandQueue.clearAlarms() }
        yield()
        assertThat(commandQueue.size()).isEqualTo(5)

        // add deactivate
        backgroundScope.launch { commandQueue.deactivate() }
        yield()
        assertThat(commandQueue.size()).isEqualTo(6)

        commandQueue.clear()
        backgroundScope.launch { commandQueue.tempBasalAbsolute(0.0, 30, true, validProfile, PumpSync.TemporaryBasalType.NORMAL) }
        yield()
        commandQueue.pickup()
        assertThat(commandQueue.size()).isEqualTo(0)
        assertThat(commandQueue.performing).isNotNull()
        assertThat(commandQueue.performing?.commandType).isEqualTo(Command.CommandType.TEMPBASAL)
        commandQueue.resetPerforming()
        assertThat(commandQueue.performing).isNull()
    }

    @Test
    fun callingCancelAllBolusesClearsQueue() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)
        val smb = DetailedBolusInfo()
        smb.lastKnownBolusTime = System.currentTimeMillis()
        smb.bolusType = BS.Type.SMB
        backgroundScope.launch { commandQueue.bolus(smb) }
        yield()
        backgroundScope.launch { commandQueue.bolus(DetailedBolusInfo()) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(2)

        // when
        commandQueue.cancelAllBoluses(null)

        // then
        assertThat(commandQueue.size()).isEqualTo(0)
    }

    @Test
    fun smbIsRejectedIfABolusIsQueued() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        backgroundScope.launch { commandQueue.bolus(DetailedBolusInfo()) }
        yield()
        val smb = DetailedBolusInfo()
        smb.bolusType = BS.Type.SMB
        backgroundScope.launch { commandQueue.bolus(smb) }
        yield()

        // then
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun smbIsRejectedIfLastKnownBolusIsOutdated() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        val bolus = DetailedBolusInfo()
        bolus.bolusType = BS.Type.SMB
        bolus.lastKnownBolusTime = 0
        backgroundScope.launch { commandQueue.bolus(bolus) }
        yield()

        // then
        assertThat(commandQueue.size()).isEqualTo(0)
    }

    @Test
    fun isCustomCommandRunning() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        backgroundScope.launch { commandQueue.customCommand(CustomCommand1()) }
        yield()
        backgroundScope.launch { commandQueue.customCommand(CustomCommand2()) }
        yield()
        commandQueue.pickup()

        // then
        assertThat(commandQueue.isCustomCommandInQueue(CustomCommand1::class)).isTrue()
        assertThat(commandQueue.isCustomCommandInQueue(CustomCommand2::class)).isTrue()
        assertThat(commandQueue.isCustomCommandInQueue(CustomCommand3::class)).isFalse()

        assertThat(commandQueue.isCustomCommandRunning(CustomCommand1::class)).isTrue()
        assertThat(commandQueue.isCustomCommandRunning(CustomCommand2::class)).isFalse()
        assertThat(commandQueue.isCustomCommandRunning(CustomCommand3::class)).isFalse()

        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isSetUserOptionsCommandInQueue() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        backgroundScope.launch { commandQueue.setUserOptions() }
        yield()

        // then
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
        assertThat(commandQueue.size()).isEqualTo(1)
        // next replaces the previously queued command (size stays at 1)
        backgroundScope.launch { commandQueue.setUserOptions() }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isLoadEventsCommandInQueue() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        backgroundScope.launch { commandQueue.loadEvents() }
        yield()

        // then
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
        assertThat(commandQueue.size()).isEqualTo(1)
        // next replaces the previously queued command (size stays at 1)
        backgroundScope.launch { commandQueue.loadEvents() }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isClearAlarmsCommandInQueue() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        backgroundScope.launch { commandQueue.clearAlarms() }
        yield()

        // then
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
        assertThat(commandQueue.size()).isEqualTo(1)
        // next replaces the previously queued command (size stays at 1)
        backgroundScope.launch { commandQueue.clearAlarms() }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isDeactivateCommandInQueue() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        backgroundScope.launch { commandQueue.deactivate() }
        yield()

        // then
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
        assertThat(commandQueue.size()).isEqualTo(1)
        // next replaces the previously queued command (size stays at 1)
        backgroundScope.launch { commandQueue.deactivate() }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isUpdateTimeCommandInQueue() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        backgroundScope.launch { commandQueue.updateTime() }
        yield() // let coroutine enqueue the command and suspend on the result Deferred

        // then
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
        assertThat(commandQueue.size()).isEqualTo(1)
        // next should be ignored
        backgroundScope.launch { commandQueue.updateTime() }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isLoadTDDsCommandInQueue() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        backgroundScope.launch { commandQueue.loadTDDs() }
        yield()

        // then
        assertThat(commandQueue.size()).isEqualTo(1)
        // next replaces the previously queued command (size stays at 1)
        backgroundScope.launch { commandQueue.loadTDDs() }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isLoadHistoryCommandInQueue() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        backgroundScope.launch { commandQueue.loadHistory(0) }
        yield()

        // then
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
        assertThat(commandQueue.size()).isEqualTo(1)
        // next replaces the previously queued command (size stays at 1)
        backgroundScope.launch { commandQueue.loadHistory(0) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isProfileSetCommandInQueue() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when the same profile is already set — early return, nothing queued
        testPumpPlugin.isProfileSet = true
        val sameProfileResult = commandQueue.setProfile(effectiveProfile, false)
        assertThat(sameProfileResult.success).isTrue()
        assertThat(sameProfileResult.enacted).isFalse()
        assertThat(commandQueue.size()).isEqualTo(0)

        // different profile -> queued (awaits deferred, so run in backgroundScope)
        testPumpPlugin.isProfileSet = false
        backgroundScope.launch { commandQueue.setProfile(effectiveProfile, false) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)

        // next replaces the previously queued command (size stays at 1)
        backgroundScope.launch { commandQueue.setProfile(effectiveProfile, false) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)
        testPumpPlugin.isProfileSet = true
    }

    @Test
    fun isStopCommandInQueue() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        backgroundScope.launch { commandQueue.stopPump() }
        yield()

        // then
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isStarCommandInQueue() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        backgroundScope.launch { commandQueue.startPump() }
        yield()

        // then
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isSetTbrNotificationCommandInQueue() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        backgroundScope.launch { commandQueue.setTBROverNotification(true) }
        yield()

        // then
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun differentCustomCommandsAllowed() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        backgroundScope.launch { commandQueue.customCommand(CustomCommand1()) }
        yield()
        backgroundScope.launch { commandQueue.customCommand(CustomCommand2()) }
        yield()

        // then
        assertThat(commandQueue.size()).isEqualTo(2)
    }

    @Test
    fun sameCustomCommandNotAllowed() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        backgroundScope.launch { commandQueue.customCommand(CustomCommand1()) }
        yield()
        backgroundScope.launch { commandQueue.customCommand(CustomCommand1()) }
        yield()

        // then
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun readStatusTwiceIsNotAllowed() = runTest {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        backgroundScope.launch { commandQueue.readStatus("1") }
        yield()
        backgroundScope.launch { commandQueue.readStatus("2") }
        yield()

        // then
        assertThat(commandQueue.size()).isEqualTo(1)
        assertThat(commandQueue.statusInQueue()).isTrue()
    }

    private class CustomCommand1 : CustomCommand {

        override val statusDescription: String
            get() = "CUSTOM COMMAND 1"
    }

    private class CustomCommand2 : CustomCommand {

        override val statusDescription: String
            get() = "CUSTOM COMMAND 2"
    }

    private class CustomCommand3 : CustomCommand {

        override val statusDescription: String
            get() = "CUSTOM COMMAND 3"
    }

    // --- Running-mode gate tests ---
    //
    // These verify the queue rejects commands when the active running mode forbids them.
    // The gate itself is exhaustively tested in PumpCommandGateTest; here we only verify the queue
    // calls the gate and propagates its decision to the callback.

    @Test
    fun `tempBasalAbsolute non-zero is rejected during DISCONNECTED_PUMP`() = runTest {
        stubActiveMode(app.aaps.core.data.model.RM.Mode.DISCONNECTED_PUMP)
        val result = commandQueue.tempBasalAbsolute(1.5, 30, true, validProfile, PumpSync.TemporaryBasalType.NORMAL)
        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `tempBasalAbsolute rate zero passes during DISCONNECTED_PUMP`() = runTest {
        // The reconciler must be able to enact zero-TBR while DISCONNECTED_PUMP is active.
        stubActiveMode(app.aaps.core.data.model.RM.Mode.DISCONNECTED_PUMP)
        backgroundScope.launch { commandQueue.tempBasalAbsolute(0.0, 30, true, validProfile, PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND) }
        yield()
        assertThat(commandQueue.size()).isGreaterThan(0)
    }

    @Test
    fun `bolus is rejected during DISCONNECTED_PUMP`() = runTest {
        stubActiveMode(app.aaps.core.data.model.RM.Mode.DISCONNECTED_PUMP)
        val info = DetailedBolusInfo().also { it.insulin = 1.0 }
        val result = commandQueue.bolus(info)
        assertThat(result.success).isFalse()
    }

    @Test
    fun `extendedBolus is rejected during DISCONNECTED_PUMP`() = runTest {
        stubActiveMode(app.aaps.core.data.model.RM.Mode.DISCONNECTED_PUMP)
        val result = commandQueue.extendedBolus(2.0, 30)
        assertThat(result.success).isFalse()
    }

    @Test
    fun `cancelTempBasal is allowed during DISCONNECTED_PUMP`() = runTest {
        // Cancel is always allowed — it is the primitive used by RESUME and startup drift.
        stubActiveMode(app.aaps.core.data.model.RM.Mode.DISCONNECTED_PUMP)
        backgroundScope.launch { commandQueue.cancelTempBasal(enforceNew = true, autoForced = false) }
        yield()
        assertThat(commandQueue.size()).isGreaterThan(0)
    }

    @Test
    fun `tempBasalAbsolute non-zero is allowed during SUSPENDED_BY_USER`() = runTest {
        // SUSPENDED_BY_USER is the temporary counterpart of DISABLED_LOOP — manual delivery stays
        // available; the gate does not block TBR / bolus / EB.
        stubActiveMode(app.aaps.core.data.model.RM.Mode.SUSPENDED_BY_USER)
        backgroundScope.launch { commandQueue.tempBasalAbsolute(1.5, 30, true, validProfile, PumpSync.TemporaryBasalType.NORMAL) }
        yield()
        assertThat(commandQueue.size()).isGreaterThan(0)
    }

    @Test
    fun `bolus is allowed during SUSPENDED_BY_USER`() = runTest {
        stubActiveMode(app.aaps.core.data.model.RM.Mode.SUSPENDED_BY_USER)
        val info = DetailedBolusInfo().also { it.insulin = 1.0 }
        backgroundScope.launch { commandQueue.bolus(info) }
        yield()
        assertThat(commandQueue.size()).isGreaterThan(0)
    }

    @Test
    fun `working mode allows all commands`() = runTest {
        stubActiveMode(app.aaps.core.data.model.RM.Mode.CLOSED_LOOP)
        backgroundScope.launch { commandQueue.tempBasalAbsolute(1.5, 30, true, validProfile, PumpSync.TemporaryBasalType.NORMAL) }
        yield()
        // cancelTempBasal replaces pending TEMPBASAL commands, so size stays at 1
        backgroundScope.launch { commandQueue.cancelTempBasal(enforceNew = true, autoForced = false) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    /**
     * The queue status carries real styling now.
     *
     * It used to be assembled as an HTML string and handed to `Html.fromHtml`, but the only consumer
     * flattened that with `toString()` - which keeps the line breaks and silently drops the bold,
     * because a String cannot carry spans. So the emphasis on the command actually executing never
     * reached the screen. These two tests pin both halves of the replacement: one line per command,
     * and the running one actually bold.
     */
    /**
     * The two command captions the status list is built from.
     *
     * `read_status` takes an argument, and the shared mock formats it from the NO-ARG `gs(id)`, so
     * this has to hand back the template rather than the finished string.
     */
    private fun stubStatusCaptions() {
        whenever(rh.gs(CoreUiStrings.read_status)).thenReturn("READSTATUS %1\$s")
        whenever(rh.gs(CoreUiStrings.load_events)).thenReturn("LOAD EVENTS")
    }

    @Test
    fun `queued commands are listed one per line`() = runTest {
        stubStatusCaptions()
        backgroundScope.launch { commandQueue.readStatus("test") }
        yield()

        val status = commandQueue.statusAsAnnotated()

        assertThat(status.text).isNotEmpty()
        // Only one command queued and nothing running, so there is no separator yet.
        assertThat(status.text).doesNotContain("\n")
        assertThat(status.spanStyles).isEmpty()
    }

    @Test
    fun `the executing command is bold, the queued ones are not`() = runTest {
        stubStatusCaptions()
        backgroundScope.launch { commandQueue.readStatus("test") }
        yield()
        // pickup() moves the head of the queue into `performing`, which is what the bold marks.
        commandQueue.pickup()
        backgroundScope.launch { commandQueue.loadEvents() }
        yield()

        val status = commandQueue.statusAsAnnotated()

        assertThat(status.text).contains("\n")
        val bold = status.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertThat(bold).hasSize(1)
        // Exactly the first line - the running command - and nothing after it.
        assertThat(bold.single().start).isEqualTo(0)
        assertThat(bold.single().end).isEqualTo(status.text.indexOf('\n'))
    }

    // ---- withHold ----
    //
    // Holding exists for applying imported settings, which stops and starts pump drivers. Sampling
    // size()/performing() and then applying is not enough on its own: the loop or an automation can
    // enqueue between the check and the teardown, and that command starts against a driver that is
    // being pulled out from under it.

    @Test
    fun `withHold runs the block and reports success when nothing is in flight`() = runTest {
        var ran = false

        val held = commandQueue.withHold("test", 1.seconds) { ran = true }

        assertThat(held).isTrue()
        assertThat(ran).isTrue()
    }

    /** What the executor loop reads. It must be up for the whole block and down again afterwards. */
    @Test
    fun `the queue reports itself held only while the block runs`() = runTest {
        assertThat(commandQueue.isHeld()).isFalse()

        commandQueue.withHold("test", 1.seconds) {
            assertThat(commandQueue.isHeld()).isTrue()
        }

        assertThat(commandQueue.isHeld()).isFalse()
    }

    /**
     * The flag goes up before the wait, not after it. That ordering is the point: from the moment the
     * hold is asked for, nothing new is picked up, so waiting for `performing` to clear actually
     * reaches an idle pump instead of racing the next command onto it.
     */
    @Test
    fun `the hold is up while it is still waiting for the command in flight`() = runTest {
        commandQueue.performing = mock<Command>()
        var ran = false

        val holder = backgroundScope.launch { commandQueue.withHold("test", 5.seconds) { ran = true } }
        yield()

        assertThat(commandQueue.isHeld()).isTrue()
        assertThat(ran).isFalse()   // still waiting - nothing applied yet

        commandQueue.performing = null
        holder.join()

        assertThat(ran).isTrue()
    }

    /** A command that never finishes must not leave the caller waiting for ever, or the queue held. */
    @Test
    fun `a command that never finishes refuses the hold`() = runTest {
        commandQueue.performing = mock<Command>()
        var ran = false

        val held = commandQueue.withHold("test", 200.milliseconds) { ran = true }

        assertThat(held).isFalse()
        assertThat(ran).isFalse()   // nothing is applied when the hold is refused
        assertThat(commandQueue.isHeld()).isFalse()
    }

    /**
     * The one failure that would matter most: a hold that leaks. The queue would stop picking up
     * anything for the rest of the session - no bolus, no temporary basal - so the release is in a
     * `finally` and this pins it.
     */
    @Test
    fun `the hold is released when the block throws`() = runTest {
        val thrown = runCatching {
            commandQueue.withHold("test", 1.seconds) { error("boom") }
        }

        assertThat(thrown.isFailure).isTrue()
        assertThat(commandQueue.isHeld()).isFalse()
    }

    /** Two holders do not overlap: the second waits rather than ending the first one's hold early. */
    @Test
    fun `holders do not overlap`() = runTest {
        val order = mutableListOf<String>()

        val first = backgroundScope.launch {
            commandQueue.withHold("first", 5.seconds) {
                order += "first in"
                delay(100)
                order += "first out"
            }
        }
        yield()
        val second = backgroundScope.launch {
            commandQueue.withHold("second", 5.seconds) { order += "second in" }
        }
        first.join()
        second.join()

        assertThat(order).containsExactly("first in", "first out", "second in").inOrder()
    }

    private suspend fun stubActiveMode(mode: app.aaps.core.data.model.RM.Mode) {
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(
            app.aaps.core.data.model.RM(timestamp = 0, mode = mode, duration = 0L)
        )
        // Resource strings used by the gate's rejection comment.
        whenever(rh.gs(app.aaps.core.interfaces.R.string.pump_disconnected)).thenReturn("pump disconnected")
        whenever(rh.gs(app.aaps.core.interfaces.R.string.loopsuspended)).thenReturn("loop suspended")
        whenever(rh.gs(app.aaps.core.interfaces.R.string.pumpsuspended)).thenReturn("pump suspended")
    }
}
