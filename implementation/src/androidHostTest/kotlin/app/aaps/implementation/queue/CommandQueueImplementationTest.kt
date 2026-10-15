package app.aaps.implementation.queue

import android.content.Context
import android.os.PowerManager
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.data.Block
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
import app.aaps.core.interfaces.profile.Profile.ProfileValue
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.CoreUiStrings
import app.aaps.implementation.profile.ProfileSwitchSilentGate
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.verification.VerificationMode
import java.util.Calendar
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
        rh: TextResolver,
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

    /** Real English for the generated strings, falling through to the mock for the ids that remain. */
    private val text by lazy { generatedTextResolver(rh) }

    private lateinit var commandQueue: CommandQueueImplementation

    /** [appScope] is where the queue launches its own work - pass a test scope to control its time. */
    private fun newQueue(appScope: CoroutineScope): CommandQueueImplementation =
        CommandQueueMocked(
            aapsLogger,
            rxBus,
            text,
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
            appScope,
            bolusProgressData
        )

    @BeforeEach
    fun prepare() {
        runTest {
            whenever(persistenceLayer.observeChanges(anyOrNull<KClass<*>>())).thenReturn(emptyFlow())
            commandQueue = newQueue(testScope)
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
            whenever(constraintChecker.applyPrimingConstraints(anyOrNull())).thenReturn(PumpInsulin(0.0))
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
            whenever(rh.gs(app.aaps.core.ui.R.string.goingtodeliver)).thenReturn("Going to deliver %1\$.2f U")
        }
    }

    @Test
    fun commandIsPickedUp() = runTest {
        lateinit var executor: CommandExecutor
        commandQueue = CommandQueueImplementation(
            aapsLogger, rxBus, text, constraintChecker, profileFunction, activePlugin, config, dateUtil,
            fabricPrivacy, notificationManager, persistenceLayer, decimalFormatter, { pumpEnactResultProvider() },
            pumpSync, preferences, profileSwitchSilentGate, localAlertUtilsProvider, smsCommunicatorProvider,
            { executor }, testScope, bolusProgressData
        )
        // Real executor sharing this queue: notifyAboutNewCommand() signals it and it drains on its own thread.
        executor = CommandExecutor(
            aapsLogger, fabricPrivacy, commandQueue, rxBus, activePlugin, text, preferences, config, bolusProgressData, TestCommandExecutionPlatform()
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

    /**
     * A pump that is neither connected nor connecting is asked to connect, and then the command runs. A Medtronic
     * after a failed tune-up reports this now: it reported connecting, so the queue only waited until it timed out
     * and never asked it to connect, and nothing tuned again (#3253).
     */
    @Test
    fun `a pump that is not connected and not connecting is asked to connect`() = runTest {
        testPumpPlugin.connected = false
        lateinit var executor: CommandExecutor
        commandQueue = CommandQueueImplementation(
            aapsLogger, rxBus, text, constraintChecker, profileFunction, activePlugin, config, dateUtil,
            fabricPrivacy, notificationManager, persistenceLayer, decimalFormatter, { pumpEnactResultProvider() },
            pumpSync, preferences, profileSwitchSilentGate, localAlertUtilsProvider, smsCommunicatorProvider,
            { executor }, testScope, bolusProgressData
        )
        executor = CommandExecutor(
            aapsLogger, fabricPrivacy, commandQueue, rxBus, activePlugin, text, preferences, config, bolusProgressData, TestCommandExecutionPlatform()
        )

        var result: PumpEnactResult? = null
        CoroutineScope(Dispatchers.IO).launch { result = commandQueue.bolus(DetailedBolusInfo()) }
        var waitedMs = 0
        while (result == null && waitedMs < 8000) {
            Thread.sleep(100); waitedMs += 100
        }

        // TestPumpPlugin.connect() sets connected. Without the call the queue waits for the connection until it times out.
        assertThat(testPumpPlugin.connected).isTrue()
        assertThat(result?.success).isTrue()
    }

    @Test
    fun bolusSucceedsButCarbsFailToStore_postsCarbsLostNotification() = runTest {
        // Regression: bolus delivered, but persisting the accompanying carbs threw. This used to be
        // swallowed log-only, leaving COB/IOB silently wrong. The failure must now surface a notification
        // so the user knows to re-enter the carbs. See CommandQueueImplementation.bolus() post-bolus catch.
        lateinit var executor: CommandExecutor
        commandQueue = CommandQueueImplementation(
            aapsLogger, rxBus, text, constraintChecker, profileFunction, activePlugin, config, dateUtil,
            fabricPrivacy, notificationManager, persistenceLayer, decimalFormatter, { pumpEnactResultProvider() },
            pumpSync, preferences, profileSwitchSilentGate, localAlertUtilsProvider, smsCommunicatorProvider,
            { executor }, testScope, bolusProgressData
        )
        executor = CommandExecutor(
            aapsLogger, fabricPrivacy, commandQueue, rxBus, activePlugin, text, preferences, config, bolusProgressData, TestCommandExecutionPlatform()
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

    // region onProfileChanged - a PS that is already effective, and the basal in the pump

    /** The requested PS has id 140; [effectiveFromPs] is the PS id the active EPS was made from. */
    private suspend fun givenActiveEpsFrom(effectiveFromPs: Long?) {
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch.copy(id = 140))
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(effectiveProfileSwitch.copy(id = 311, originalPsId = effectiveFromPs))
    }

    /** Lets the queued profile command finish as a pump that wrote it. */
    private fun completeQueuedProfileSet(enacted: Boolean = true) {
        commandQueue.pickup()
        val command = commandQueue.performing()
        assertThat(command?.commandType).isEqualTo(Command.CommandType.BASAL_PROFILE)
        command?.completion?.complete(enactResult(isSuccess = true, isEnacted = enacted))
    }

    /** NSClient updating the PS with its nsId, or KeepAlive with the pump in order: nothing to do. */
    @Test
    fun `a PS that is already effective and in the pump is not set again`() = runTest {
        givenActiveEpsFrom(140)
        testPumpPlugin.isProfileSet = true

        rxBus.send(EventProfileChangeRequested())

        assertThat(commandQueue.size()).isEqualTo(0)
        verify(persistenceLayer, never()).insertOrUpdateEffectiveProfileSwitch(any())
    }

    /**
     * KeepAliveWorker found another basal in the pump (a new or replaced pump, or the basal changed on the
     * pump). The PS is already effective, which used to stop it here, so the pump kept its own basal. It must
     * be set in the pump again - without a second EPS for the same PS, which was the reason for the skip.
     */
    @Test
    fun `a PS that is already effective is set again when the pump has another basal, without a new EPS`() = runTest {
        givenActiveEpsFrom(140)
        testPumpPlugin.isProfileSet = false

        rxBus.send(EventProfileChangeRequested())
        assertThat(commandQueue.size()).isEqualTo(1)
        completeQueuedProfileSet()

        verify(persistenceLayer, never()).insertOrUpdateEffectiveProfileSwitch(any())
    }

    /** A new PS is set in the pump and gets its EPS. */
    @Test
    fun `a new PS is set in the pump and gets an EPS`() = runTest {
        givenActiveEpsFrom(139)
        testPumpPlugin.isProfileSet = false

        rxBus.send(EventProfileChangeRequested())
        assertThat(commandQueue.size()).isEqualTo(1)
        completeQueuedProfileSet()

        verify(persistenceLayer, times(1)).insertOrUpdateEffectiveProfileSwitch(any())
    }

    /**
     * The switches changed while the pump was busy: on a Medtronic a scene ended 40 s into the write and cut this
     * switch. Its EPS put the scene's percentage in force again until the next pump write. The event for the change
     * waits behind this one and sets the switch then in force, so this one gets no EPS and no notice.
     */
    @Test
    fun `a PS that is no longer requested when the pump has set it gets no EPS`() = runTest {
        givenActiveEpsFrom(139)
        testPumpPlugin.isProfileSet = false

        rxBus.send(EventProfileChangeRequested())
        assertThat(commandQueue.size()).isEqualTo(1)
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch.copy(id = 141))
        completeQueuedProfileSet()

        verify(persistenceLayer, never()).insertOrUpdateEffectiveProfileSwitch(any())
        assertThat(roundingNotices()).isEmpty()
        verifyNoPlainOkPosted()
    }

    // endregion

    // region #2454 - the EPS holds the basal the pump really gives

    private val hour = 1.hours.inWholeMilliseconds

    private suspend fun savedEps(): EPS = argumentCaptor<EPS>().run {
        verify(persistenceLayer).insertOrUpdateEffectiveProfileSwitch(capture())
        firstValue
    }

    /** "Basal profile in pump updated" with the rounding text: the String post, not the plain TextRef one. */
    private fun roundingNotices(): List<String> = argumentCaptor<String>().run {
        verify(notificationManager, atLeast(0)).post(
            eq(NotificationId.PROFILE_SET_OK), capture(), any<NotificationLevel>(), any<Int>(), anyOrNull(), any<List<NotificationAction>>(), anyOrNull()
        )
        allValues
    }

    /** A new PS (id 140) set in a pump that reports [delivered]. */
    private suspend fun setNewPsInPumpThatGives(delivered: List<ProfileValue>?, event: EventProfileChangeRequested = EventProfileChangeRequested(), enacted: Boolean = true) {
        givenActiveEpsFrom(139)
        testPumpPlugin.isProfileSet = false
        testPumpPlugin.delivered = delivered
        rxBus.send(event)
        completeQueuedProfileSet(enacted)
    }

    /** One notice for one profile switch: the rounding is said in "Basal profile in pump updated", not beside it. */
    @Test
    fun `a pump that rounds the basal gets an EPS with the rates it really gives`() = runTest {
        setNewPsInPumpThatGives(listOf(ProfileValue(0, 0.95), ProfileValue(6 * 3600, 1.0)))

        assertThat(savedEps().basalBlocks).containsExactly(Block(6 * hour, 0.95), Block(18 * hour, 1.0)).inOrder()
        // The profile asks 1 U/h all day: 24 U. The pump gives 6 * 0.95 + 18 * 1.0 = 23.7 U.
        assertThat(roundingNotices()).containsExactly(
            "Basal profile in pump updated. The pump rounds the basal to its own step: it gives ${"%.2f".format(23.7)} U per day instead of ${"%.2f".format(24.0)} U."
        )
        verifyNoPlainOkPosted()
    }

    @Test
    fun `a pump that gives the profile exactly gets the profile basal and the plain notice`() = runTest {
        setNewPsInPumpThatGives(null)

        assertThat(savedEps().basalBlocks).isEqualTo(profileSwitch.basalBlocks)
        assertThat(roundingNotices()).isEmpty()
        verifyOkPosted()
    }

    @Test
    fun `rounding that does not change the insulin per day gives the plain notice`() = runTest {
        setNewPsInPumpThatGives(listOf(ProfileValue(0, 0.95), ProfileValue(12 * 3600, 1.05)))

        assertThat(savedEps().basalBlocks).containsExactly(Block(12 * hour, 0.95), Block(12 * hour, 1.05)).inOrder()
        assertThat(roundingNotices()).isEmpty()
        verifyOkPosted()
    }

    /** The pump had this basal already (enacted == false): nothing was updated, so nothing is said. */
    @Test
    fun `a set the pump did not need posts no notice but gets an EPS`() = runTest {
        setNewPsInPumpThatGives(listOf(ProfileValue(0, 0.95)), enacted = false)

        assertThat(savedEps().basalBlocks).containsExactly(Block(24 * hour, 0.95))
        assertThat(roundingNotices()).isEmpty()
        verifyNoPlainOkPosted()
    }

    /** The running profile is still the old one (U100) when the new one (U200) has just been set. */
    @Test
    fun `the rates are converted with the concentration of the profile that was set`() = runTest {
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch.copy(id = 140, iCfg = someICfg.copy(concentration = 2.0)))
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(effectiveProfileSwitch.copy(id = 311, originalPsId = 139))
        testPumpPlugin.isProfileSet = false
        testPumpPlugin.delivered = listOf(ProfileValue(0, 0.5))

        rxBus.send(EventProfileChangeRequested())
        completeQueuedProfileSet()

        assertThat(savedEps().basalBlocks).containsExactly(Block(24 * hour, 1.0))
        assertThat(roundingNotices()).isEmpty()
    }

    @Test
    fun `a report that does not start at midnight is not used`() = runTest {
        setNewPsInPumpThatGives(listOf(ProfileValue(3600, 0.9)))

        assertThat(savedEps().basalBlocks).isEqualTo(profileSwitch.basalBlocks)
        assertThat(roundingNotices()).isEmpty()
    }

    /** The running profile holds hour values only. */
    @Test
    fun `a report with a half hour is not used`() = runTest {
        setNewPsInPumpThatGives(listOf(ProfileValue(0, 0.9), ProfileValue(1800, 1.0)))

        assertThat(savedEps().basalBlocks).isEqualTo(profileSwitch.basalBlocks)
    }

    /** One value per hour, as EOPatch reports it, is merged like the profile itself. */
    @Test
    fun `hours with the same rate are merged into one block`() = runTest {
        setNewPsInPumpThatGives((0 until 24).map { hour -> ProfileValue(hour * 3600, if (hour < 6) 0.95 else 1.0) })

        assertThat(savedEps().basalBlocks).containsExactly(Block(6 * hour, 0.95), Block(18 * hour, 1.0)).inOrder()
    }

    private fun failedUpdateNotices(): List<String> = argumentCaptor<String>().run {
        verify(notificationManager, atLeast(0)).post(
            eq(NotificationId.FAILED_UPDATE_PROFILE), capture(), any<NotificationLevel>(), any<Int>(), anyOrNull(), any<List<NotificationAction>>(), anyOrNull()
        )
        allValues
    }

    /**
     * Seen on the DanaR emulator: a read one byte off gave 130.56 U/h for 0.51. A report that far from the profile is
     * not a rounding. The EPS must not take it, and the user must check the pump.
     */
    @Test
    fun `a report far from the profile is not used and the user is told to check the pump`() = runTest {
        setNewPsInPumpThatGives(listOf(ProfileValue(0, 0.0), ProfileValue(3600, 130.56)))

        assertThat(savedEps().basalBlocks).isEqualTo(profileSwitch.basalBlocks)
        assertThat(failedUpdateNotices()).containsExactly("The pump reports a basal far from the profile. AAPS keeps the profile basal. Check the basal rates in the pump.")
        // No "updated" beside the alarm.
        assertThat(roundingNotices()).isEmpty()
        verifyNoPlainOkPosted()
    }

    /** One hour far off is enough: the rest of the day does not make up for it. */
    @Test
    fun `one hour far from the profile is enough to not use the report`() = runTest {
        setNewPsInPumpThatGives((0 until 24).map { hour -> ProfileValue(hour * 3600, if (hour == 13) 1.5 else 1.0) })

        assertThat(savedEps().basalBlocks).isEqualTo(profileSwitch.basalBlocks)
    }

    /** A pump with a coarse step rounds by more than 0.1 U/h: up to its own step is a rounding. */
    @Test
    fun `a pump with a coarse step may round by its step`() = runTest {
        testPumpPlugin.pumpDescription.basalStep = 0.1
        setNewPsInPumpThatGives(listOf(ProfileValue(0, 0.85)))

        assertThat(savedEps().basalBlocks).containsExactly(Block(24 * hour, 0.85))
    }

    @Test
    fun `a report with times out of order is not used`() = runTest {
        setNewPsInPumpThatGives(listOf(ProfileValue(0, 0.9), ProfileValue(7200, 1.0), ProfileValue(3600, 1.1)))

        assertThat(savedEps().basalBlocks).isEqualTo(profileSwitch.basalBlocks)
    }

    /**
     * Issue #4959: a Scene reverting its own ProfileSwitch enacts a real write but must stay silent - no "Basal profile
     * in pump updated" card, with or without rounding. It still gets its EPS and clears a stale failure.
     */
    @Test
    fun `a set by a scene posts no notice`() = runTest {
        setNewPsInPumpThatGives(listOf(ProfileValue(0, 0.95)), EventProfileChangeRequested(silent = true))

        assertThat(savedEps().basalBlocks).containsExactly(Block(24 * hour, 0.95))
        assertThat(roundingNotices()).isEmpty()
        verifyNoPlainOkPosted()
        verify(notificationManager).dismiss(NotificationId.FAILED_UPDATE_PROFILE)
    }

    /** A new pump with another step: the PS is the same, but the EPS must hold what this pump gives. */
    @Test
    fun `an effective PS set again gets a new EPS when this pump gives other rates`() = runTest {
        givenActiveEpsFrom(140)
        testPumpPlugin.isProfileSet = false
        testPumpPlugin.delivered = listOf(ProfileValue(0, 0.95))

        rxBus.send(EventProfileChangeRequested())
        completeQueuedProfileSet()

        assertThat(savedEps().basalBlocks).containsExactly(Block(24 * hour, 0.95))
    }

    /** Before, the rounded EPS never matched the PS by value, so a profile that was set looked unset for good. */
    @Test
    fun `a PS is set when the pump has it and its EPS holds the rounded basal`() = runTest {
        // Built before whenever(): the constructor reads activePlugin, a mock.
        val running = ProfileSealed.EPS(effectiveProfileSwitch.copy(originalPsId = 140, basalBlocks = listOf(Block(24 * hour, 0.95))), activePlugin)
        whenever(profileFunction.getProfile()).thenReturn(running)
        testPumpPlugin.isProfileSet = true

        assertThat(commandQueue.isThisProfileSet(ProfileSealed.PS(profileSwitch.copy(id = 140), activePlugin))).isTrue()
        assertThat(commandQueue.isThisProfileSet(ProfileSealed.PS(profileSwitch.copy(id = 141), activePlugin))).isFalse()
    }

    // endregion

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

    private fun verifyNoPlainOkPosted() =
        verify(notificationManager, never()).post(
            eq(NotificationId.PROFILE_SET_OK), any<TextRef>(),
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
    fun postProfileWriteResult_updated_clearsFailureAndLeavesTheConfirmationToNotifyProfileSet() {
        // profile updated: success=true, enacted=true → stale failure cleared. The confirmation waits for the basal
        // the pump gives, so it is posted later by notifyProfileSet, as one notice.
        val persisted = commandQueue.postProfileWriteResult(enactResult(isSuccess = true, isEnacted = true))

        assertThat(persisted).isTrue()
        verify(notificationManager).dismiss(NotificationId.FAILED_UPDATE_PROFILE)
        verifyNothingPosted()
        verifyNoPlainOkPosted()
    }

    @Test
    fun postProfileWriteResult_alreadySet_isSilentButPersists() {
        // already set (basal unchanged): success=true, enacted=false → no confirmation, but still persist EPS.
        val persisted = commandQueue.postProfileWriteResult(enactResult(isSuccess = true, isEnacted = false))

        assertThat(persisted).isTrue()
        verify(notificationManager).dismiss(NotificationId.FAILED_UPDATE_PROFILE)
        verifyNothingPosted()
    }

    @Test
    fun postProfileWriteResult_notInitialized_isSilentButPersists() {
        // not initialized maps to success=true, enacted=false at the driver — same handling as already-set:
        // no alarm, no confirmation, but the EPS is created so the loop has a profile.
        val persisted = commandQueue.postProfileWriteResult(enactResult(isSuccess = true, isEnacted = false))

        assertThat(persisted).isTrue()
        verifyNothingPosted()
    }

    @Test
    fun postProfileWriteResult_error_ringsFailureAlarmAndDoesNotPersist() {
        // any error: success=false → persistent FAILED_UPDATE_PROFILE card rung with boluserror; driver comment surfaces.
        val persisted = commandQueue.postProfileWriteResult(enactResult(isSuccess = false, isEnacted = false, commentText = "pump rejected"))

        assertThat(persisted).isFalse()
        verifyFailurePosted("pump rejected")
        verify(notificationManager, never()).dismiss(any<NotificationId>())
    }

    @Test
    fun postProfileWriteResult_timeout_ringsFailureAlarmWithFallbackText() {
        // timeout: result == null (the command was never completed) → treated as failure, generic fallback text.
        whenever(rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile)).thenReturn("Failed to update basal profile")

        val persisted = commandQueue.postProfileWriteResult(null)

        assertThat(persisted).isFalse()
        verifyFailurePosted("Failed to update basal profile")
        verify(notificationManager, never()).dismiss(any<NotificationId>())
    }
    // endregion

    /** A prime is in pump units, so it gets the priming constraint, not the bolus one that takes insulin units. */
    @Test
    fun `a prime is constrained in pump units`() = runTest {
        whenever(constraintChecker.applyPrimingConstraints(anyOrNull())).thenReturn(PumpInsulin(2.5))
        val prime = DetailedBolusInfo().apply { insulin = 3.0; bolusType = BS.Type.PRIMING }

        backgroundScope.launch { commandQueue.bolus(prime) }
        yield()

        assertThat(prime.insulin).isEqualTo(2.5)
        verify(constraintChecker, never()).applyBolusConstraints(any())
    }

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
        // A manual bolus drops a queued SMB, so the two are never queued together: one at a time
        backgroundScope.launch { commandQueue.bolus(smb) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)
        commandQueue.cancelAllBoluses(null)
        assertThat(commandQueue.size()).isEqualTo(0)

        backgroundScope.launch { commandQueue.bolus(DetailedBolusInfo()) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)
        commandQueue.cancelAllBoluses(null)
        assertThat(commandQueue.size()).isEqualTo(0)
    }

    /** #4546: the SMB did not count the manual bolus, and the manual bolus did not count the SMB. */
    @Test
    fun `a manual bolus drops a queued SMB`() = runTest {
        var smbResult: PumpEnactResult? = null
        val smb = DetailedBolusInfo().apply {
            bolusType = BS.Type.SMB
            lastKnownBolusTime = System.currentTimeMillis()
        }
        backgroundScope.launch { smbResult = commandQueue.bolus(smb) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)

        backgroundScope.launch { commandQueue.bolus(DetailedBolusInfo().apply { insulin = 1.0 }) }
        yield() // the manual bolus drops the SMB
        yield() // the SMB caller resumes with its result

        assertThat(commandQueue.size()).isEqualTo(1)
        commandQueue.pickup()
        assertThat(commandQueue.performing()?.commandType).isEqualTo(Command.CommandType.BOLUS)
        assertThat(smbResult).isNotNull()
        assertThat(smbResult!!.success).isFalse()
        // Not a delivery failure: the loop runs again, and no bolus error alarm
        assertThat(smbResult!!.cancelled).isTrue()
        // The progress belongs to the manual bolus now
        assertThat(bolusProgressData.state.value?.isSMB).isFalse()
    }

    /**
     * #4546: an SMB that is being delivered cannot be dropped. Queued behind it, the manual bolus would share
     * the progress state with the SMB, so it is refused and the SMB is left alone.
     */
    @Test
    fun `a manual bolus is refused while an SMB is being delivered`() = runTest {
        var smbResult: PumpEnactResult? = null
        val smb = DetailedBolusInfo().apply {
            bolusType = BS.Type.SMB
            lastKnownBolusTime = System.currentTimeMillis()
        }
        backgroundScope.launch { smbResult = commandQueue.bolus(smb) }
        yield()
        commandQueue.pickup()

        val manual = commandQueue.bolus(DetailedBolusInfo().apply { insulin = 1.0 })

        assertThat(manual.success).isFalse()
        // No insulin was given: the user is told, but it is not a delivery error alarm
        assertThat(manual.cancelled).isTrue()
        assertThat(commandQueue.size()).isEqualTo(0)
        assertThat(commandQueue.performing()?.commandType).isEqualTo(Command.CommandType.SMB_BOLUS)
        assertThat(smbResult).isNull()
        assertThat(bolusProgressData.state.value?.isSMB).isTrue()
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
    // calls the gate and returns its decision to the caller.

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

    // ---- dropping the queue ---------------------------------------------------------------

    /**
     * What a dropped command tells the caller.
     *
     * The drain used to answer `success = true` for the import, and `LoopPlugin.invoke` branches on
     * `enacted || success`: it carried on behind a temp basal that never reached the pump and then
     * delivered an SMB against it. `CommandQueueImplementation.bolus` also persists carbs on a
     * successful result, for insulin that was never given.
     */
    @Test
    fun `a dropped command reports failure when the drain says so`() = runTest {
        var result: PumpEnactResult? = null
        backgroundScope.launch { result = commandQueue.bolus(DetailedBolusInfo()) }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)

        commandQueue.cancelAll(TextRef.Literal("import"), success = false)
        yield()

        assertThat(commandQueue.size()).isEqualTo(0)
        assertThat(result).isNotNull()
        assertThat(result!!.success).isFalse()
        assertThat(result!!.enacted).isFalse()
    }

    /**
     * What the `success` flag actually costs. `CommandExecutor` drops the queue when the pump is not
     * configured - selected but never paired - and it used to do that with `success = true`. [bolus]
     * persists the accompanying carbs on a successful result, so the carbs were written for insulin
     * that never left the pump, and the loop then counted them.
     */
    @Test
    fun `carbs are not persisted when the bolus was dropped`() = runTest {
        backgroundScope.launch { commandQueue.bolus(DetailedBolusInfo().apply { insulin = 1.0; carbs = 20.0 }) }
        yield()

        commandQueue.cancelAll(TextRef.Literal("pump not configured"), success = false)
        yield()

        verify(persistenceLayer, never()).insertOrUpdateCarbs(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    /**
     * Dropping is not failing. The alarm in `WizardBolusExecutorImpl` fires on `!success`, so
     * without this flag the import would start raising BOLUS_DELIVERY_FAILED where nothing failed -
     * the same reason a bolus the user stopped does not alarm.
     */
    @Test
    fun `a dropped command is marked cancelled, whatever it reports`() = runTest {
        var failed: PumpEnactResult? = null
        backgroundScope.launch { failed = commandQueue.bolus(DetailedBolusInfo()) }
        yield()
        commandQueue.cancelAll(TextRef.Literal("import"), success = false)
        yield()

        var noOp: PumpEnactResult? = null
        backgroundScope.launch { noOp = commandQueue.bolus(DetailedBolusInfo()) }
        yield()
        // A drain that still reports success. No production caller passes true any more - the last one,
        // CommandExecutor's "pump not configured", was flipped to false - but the flag is part of the
        // API, and the point here is that `cancelled` is set either way.
        commandQueue.cancelAll(TextRef.Literal("drained, reported as success"), success = true)
        yield()

        assertThat(failed!!.cancelled).isTrue()
        assertThat(noOp!!.cancelled).isTrue()
        assertThat(noOp!!.success).isTrue()
    }

    /**
     * A connection timeout is a real delivery failure, not a drop on purpose, so it must NOT be
     * marked cancelled - otherwise the BOLUS_DELIVERY_FAILED alarm stops firing when the pump
     * cannot be reached, which is exactly when it is needed.
     */
    @Test
    fun `a connection timeout is a failure, not a cancellation`() = runTest {
        var result: PumpEnactResult? = null
        backgroundScope.launch { result = commandQueue.bolus(DetailedBolusInfo()) }
        yield()

        commandQueue.clear()
        yield()

        assertThat(result!!.success).isFalse()
        assertThat(result!!.cancelled).isFalse()
    }

    // region stuck command alarm (#5209)

    private fun verifyDriverAlarm(mode: VerificationMode) =
        verify(notificationManager, mode).post(eq(NotificationId.PUMP_DRIVER_NOT_RESPONDING), any<String>(), any(), any<Int>(), anyOrNull(), any(), anyOrNull())

    @Test
    fun `a command still running after 30 minutes raises the driver alarm and finishing it dismisses the alarm`() = runTest {
        val queue = newQueue(backgroundScope)
        backgroundScope.launch { queue.readStatus("test") }
        runCurrent()
        queue.pickup()
        val command = checkNotNull(queue.performing())

        advanceTimeBy(29.minutes)
        runCurrent()
        verifyDriverAlarm(never())

        advanceTimeBy(2.minutes)
        runCurrent()
        verifyDriverAlarm(times(1))
        verify(notificationManager, never()).dismiss(NotificationId.PUMP_DRIVER_NOT_RESPONDING)

        command.completion.complete(enactResult(isSuccess = true, isEnacted = false))
        runCurrent()
        verify(notificationManager).dismiss(NotificationId.PUMP_DRIVER_NOT_RESPONDING)
    }

    @Test
    fun `a command that finishes in time raises no driver alarm`() = runTest {
        val queue = newQueue(backgroundScope)
        backgroundScope.launch { queue.readStatus("test") }
        runCurrent()
        queue.pickup()
        val command = checkNotNull(queue.performing())

        advanceTimeBy(10.minutes)
        command.completion.complete(enactResult(isSuccess = true, isEnacted = false))
        advanceTimeBy(60.minutes)
        runCurrent()

        verifyDriverAlarm(never())
        verify(notificationManager, never()).dismiss(NotificationId.PUMP_DRIVER_NOT_RESPONDING)
    }

    /** A drain clears `performing` while the command is still running. The alarm must still come. */
    @Test
    fun `a drain while a command is stuck does not hide the driver alarm`() = runTest {
        val queue = newQueue(backgroundScope)
        backgroundScope.launch { queue.readStatus("test") }
        runCurrent()
        queue.pickup()

        queue.cancelAll(CoreUiStrings.connectiontimedout, success = false)
        assertThat(queue.performing()).isNull()
        advanceTimeBy(31.minutes)
        runCurrent()

        verifyDriverAlarm(times(1))
    }

    // endregion

    /**
     * #5193: cancelAllBoluses went through removeAll, which reports success because it is meant for a
     * newer command replacing an older one. bolus() then saved the carbs of a bolus the pump never got.
     */
    @Test
    fun `carbs are not persisted when a queued bolus is cancelled by cancelAllBoluses`() = runTest {
        var result: PumpEnactResult? = null
        backgroundScope.launch {
            result = commandQueue.bolus(DetailedBolusInfo().apply { insulin = 1.0; carbs = 20.0 })
        }
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)

        commandQueue.cancelAllBoluses(null)
        yield()

        assertThat(commandQueue.size()).isEqualTo(0)
        assertThat(result).isNotNull()
        assertThat(result!!.success).isFalse()
        // A stop by the user is not a delivery failure, so it must not ring the bolus error alarm.
        assertThat(result!!.cancelled).isTrue()
        verify(persistenceLayer, never()).insertOrUpdateCarbs(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    /** #5193, the replace path: a queued bolus replaced by a newer one was not delivered either. */
    @Test
    fun `carbs are not persisted when a queued bolus is replaced by a newer one`() = runTest {
        var replaced: PumpEnactResult? = null
        backgroundScope.launch {
            replaced = commandQueue.bolus(DetailedBolusInfo().apply { insulin = 1.0; carbs = 20.0 })
        }
        yield()
        backgroundScope.launch { commandQueue.bolus(DetailedBolusInfo().apply { insulin = 2.0 }) }
        yield() // the second bolus replaces the first
        yield() // the first caller resumes with its result

        assertThat(commandQueue.size()).isEqualTo(1)
        assertThat(replaced).isNotNull()
        assertThat(replaced!!.success).isFalse()
        assertThat(replaced!!.cancelled).isTrue()
        verify(persistenceLayer, never()).insertOrUpdateCarbs(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    /**
     * The drain used to complete the caller directly, so `CommandBolus.cancel` never ran and the
     * bolus progress it owns was left running with nothing to finish it.
     */
    @Test
    fun `dropping a bolus clears the progress it owns`() = runTest {
        backgroundScope.launch { commandQueue.bolus(DetailedBolusInfo()) }
        yield()
        assertThat(bolusProgressData.state.value).isNotNull()

        commandQueue.cancelAll(TextRef.Literal("import"), success = false)
        yield()

        assertThat(bolusProgressData.state.value).isNull()
    }
}
