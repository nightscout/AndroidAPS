package app.aaps.plugins.aps.loop

import app.aaps.core.data.model.DS
import app.aaps.core.data.model.RM
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.EnforcedState
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpStatusProvider
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class LoopPluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var virtualPumpPlugin: PumpWithConcentration
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Mock lateinit var pumpStatusProvider: PumpStatusProvider
    @Mock lateinit var loopNotifier: LoopNotifier

    private lateinit var loopPlugin: LoopPlugin
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @BeforeEach fun prepare() {
        whenever(config.APS).thenReturn(true)
        loopPlugin = buildLoopPlugin()
        whenever(activePlugin.activePump).thenReturn(virtualPumpPlugin)
    }

    /**
     * The ONLY place this test constructs a [LoopPlugin].
     *
     * A second copy of this argument list broke the build once already: `uiInteraction` was dropped from the
     * constructor, the copy in `prepare` was updated and the one in a test body was not. A test that needs
     * its own instance - one built with a different `config` stubbing, say - calls this instead of pasting
     * the list again.
     */
    private fun buildLoopPlugin() = LoopPlugin(
        aapsLogger, rxBus, preferences, config,
        constraintChecker, baseText, profileFunction, commandQueue, activePlugin, processedTbrEbData, receiverStatusStore, fabricPrivacy, dateUtil, uel,
        // The shared test base still hands out a javax Provider, which other tests rely on;
        // LoopPlugin takes Metro's now, so it is adapted here rather than flipping the base.
        persistenceLayer, notificationManager, { pumpEnactResultProvider() },
        processedDeviceStatusData, pumpStatusProvider, decimalFormatter, ch, loopNotifier, testScope
    )

    /**
     * Leave no live coroutine behind.
     *
     * [testScope] is a real scope on [Dispatchers.Unconfined], so a job left pending here does not die
     * with the test - it waits out its `delay` and then runs against a half-stubbed plugin. The throw
     * lands in kotlinx-coroutines-test's process-wide collector and is reported as
     * `UncaughtExceptionsBeforeTest` against whichever unrelated `runTest` happens to start next, which
     * is what it did to `allowedNextModes returns emptyList if profile is invalid`.
     */
    @AfterEach fun cancelPendingWork() {
        loopPlugin.smbFallbackJob?.cancel()
    }

    @Test
    fun testPluginInterface() {
        whenever(rh.gs(TextRef.AndroidRes(app.aaps.core.ui.R.string.loop))).thenReturn("Loop")
        whenever(rh.gs(TextRef.AndroidRes(app.aaps.plugins.aps.R.string.loop_shortname))).thenReturn("LOOP")
//        whenever(preferences.get(StringKey.LoopApsMode)).thenReturn(ApsMode.CLOSED.name)
        val pumpDescription = PumpDescription()
        whenever(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)
        assertThat(loopPlugin.getType()).isEqualTo(PluginType.LOOP)
        assertThat(loopPlugin.name).isEqualTo("Loop")
        assertThat(loopPlugin.nameShort).isEqualTo("LOOP")
        assertThat(loopPlugin.showInList()).isTrue()

        // Plugin is enabled by default
        assertThat(loopPlugin.isEnabled()).isTrue()

        // A build with an APS of its own may run the loop
        assertThat(loopPlugin.enforcedState()).isEqualTo(EnforcedState.Enabled)
    }

    /**
     * A client must never run the loop, whatever the stored flag says.
     *
     * `ConfigBuilder_Enabled_LOOP_*` is exportable and is not a synced key, so importing a master's
     * settings writes it on a client too. The forced-off enforcement is what stops it: `PluginBase.isEnabled`
     * is answered from the enforcement before the stored state is consulted, so it beats the flag. See #5145.
     *
     * This asserts the enforcement itself rather than driving the state machine: `setPluginEnabled` starts
     * the plugin on a real scope, and a collector left running here would outlive the test - see
     * [cancelPendingWork].
     */
    @Test
    fun `a client may not run the loop`() {
        whenever(config.APS).thenReturn(false)
        val clientLoopPlugin = buildLoopPlugin()

        assertThat(clientLoopPlugin.enforcedState()).isEqualTo(EnforcedState.Disabled)
        // Enforced DISABLED on a client, so isEnabled is false whatever the stored flag says
        assertThat(clientLoopPlugin.isEnabled()).isFalse()
    }

    @Test
    fun iobShouldBeLimited() = runTest {
        whenever(rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)).thenReturn("Low Glucose Suspend")
        whenever(rh.gs(app.aaps.core.ui.R.string.limiting_iob, HardLimits.MAX_IOB_LGS, rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend))).thenReturn("Limiting IOB to %1\$.1f U because of %2\$s")
        whenever(constraintChecker.isLoopInvocationAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(
            RM(
                timestamp = 0,
                mode = RM.Mode.CLOSED_LOOP_LGS,
                duration = 0
            )
        )
        // Apply all limits
        var d: Constraint<Double> = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        d = loopPlugin.applyMaxIOBConstraints(d)
        assertThat(d.value()).isWithin(0.01).of(HardLimits.MAX_IOB_LGS)
        assertThat(d.getReasons()).isEqualTo("Loop: Limiting IOB to 0.0 U because of Low Glucose Suspend")
        assertThat(d.getMostLimitedReasons()).isEqualTo("Loop: Limiting IOB to 0.0 U because of Low Glucose Suspend")
    }

    @Test
    fun `minutesToEndOfSuspend returns 0 when loop is not suspended`() = runTest {
        // Arrange
        val now = 1672531200000L // Jan 1, 2023
        val runningMode = RM(mode = RM.Mode.CLOSED_LOOP, timestamp = now, duration = 0)

        whenever(dateUtil.now()).thenReturn(now)
        whenever(persistenceLayer.getRunningModeActiveAt(now)).thenReturn(runningMode)
        whenever(constraintChecker.isLoopInvocationAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        whenever(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        whenever(constraintChecker.isLgsForced()).thenReturn(ConstraintObject(false, aapsLogger))

        // Act
        val result = loopPlugin.minutesToEndOfSuspend()

        // Assert
        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `minutesToEndOfSuspend returns remaining minutes for a temporary suspension`() = runTest {
        // Arrange
        val startTime = 1672531200000L // Start of suspend
        val durationMins = 30L
        val now = startTime + T.mins(10).msecs() // 10 minutes have passed
        val expectedRemainingMinutes = 20

        val runningMode = RM(
            mode = RM.Mode.SUSPENDED_BY_USER,
            timestamp = startTime,
            duration = T.mins(durationMins).msecs()
        )

        whenever(dateUtil.now()).thenReturn(now)
        whenever(persistenceLayer.getRunningModeActiveAt(now)).thenReturn(runningMode)

        // Act
        val result = loopPlugin.minutesToEndOfSuspend()

        // Assert
        assertThat(result).isEqualTo(expectedRemainingMinutes)
    }

    @Test
    fun `minutesToEndOfSuspend returns Int_MAX_VALUE for an indefinite suspension`() = runTest {
        // Arrange
        val now = 1672531200000L
        // A non-temporary suspend has a duration of 0
        val runningMode = RM(mode = RM.Mode.SUSPENDED_BY_USER, timestamp = now, duration = 0)

        whenever(dateUtil.now()).thenReturn(now)
        whenever(persistenceLayer.getRunningModeActiveAt(now)).thenReturn(runningMode)

        // Act
        val result = loopPlugin.minutesToEndOfSuspend()

        // Assert
        assertThat(result).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `minutesToEndOfSuspend returns 0 when temporary suspension has just ended`() = runTest {
        // Arrange
        val startTime = 1672531200000L
        val durationMins = 30L
        val now = startTime + T.mins(durationMins).msecs() // Exactly at the end time

        val runningMode = RM(
            mode = RM.Mode.SUSPENDED_BY_USER,
            timestamp = startTime,
            duration = T.mins(durationMins).msecs()
        )

        whenever(dateUtil.now()).thenReturn(now)
        whenever(persistenceLayer.getRunningModeActiveAt(now)).thenReturn(runningMode)

        // Act
        val result = loopPlugin.minutesToEndOfSuspend()

        // Assert
        assertThat(result).isEqualTo(0)
    }

    private fun mockCurrentMode(mode: RM.Mode) = runTest {
        val now = 1672531200000L
        val runningMode = RM(mode = mode, timestamp = now, duration = 0)
        whenever(dateUtil.now()).thenReturn(now)
        whenever(persistenceLayer.getRunningModeActiveAt(now)).thenReturn(runningMode)
    }

    @Test
    fun `allowedNextModes returns emptyList if profile is invalid`() = runTest {
        // Arrange
        whenever(profileFunction.isProfileValid(any())).thenReturn(false)
        mockCurrentMode(RM.Mode.OPEN_LOOP) // Any mode

        // Act
        val result = loopPlugin.allowedNextModes()

        // Assert
        assertThat(result).isEmpty()
    }

    @Test
    fun `allowedNextModes for OPEN_LOOP returns correct base list`() = runTest {
        // Arrange
        whenever(profileFunction.isProfileValid(any())).thenReturn(true)
        whenever(constraintChecker.isLoopInvocationAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        whenever(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        mockCurrentMode(RM.Mode.OPEN_LOOP)
        val expectedModes = listOf(
            RM.Mode.DISABLED_LOOP,
            RM.Mode.CLOSED_LOOP,
            RM.Mode.CLOSED_LOOP_LGS,
            RM.Mode.DISCONNECTED_PUMP,
            RM.Mode.SUSPENDED_BY_USER,
            RM.Mode.SUPER_BOLUS
        )

        // Act
        val result = loopPlugin.allowedNextModes()

        // Assert
        assertThat(result).isEqualTo(expectedModes)
    }

    @Test
    fun `allowedNextModes for CLOSED_LOOP returns correct base list`() = runTest {
        // Arrange
        whenever(profileFunction.isProfileValid(any())).thenReturn(true)
        whenever(constraintChecker.isLoopInvocationAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        whenever(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        whenever(constraintChecker.isLgsForced()).thenReturn(ConstraintObject(false, aapsLogger))
        mockCurrentMode(RM.Mode.CLOSED_LOOP)
        val expectedModes = listOf(
            RM.Mode.DISABLED_LOOP,
            RM.Mode.OPEN_LOOP,
            RM.Mode.CLOSED_LOOP_LGS,
            RM.Mode.DISCONNECTED_PUMP,
            RM.Mode.SUSPENDED_BY_USER,
            RM.Mode.SUPER_BOLUS
        )

        // Act
        val result = loopPlugin.allowedNextModes()

        // Assert
        assertThat(result).isEqualTo(expectedModes)
    }

    @Test
    fun `allowedNextModes for SUSPENDED_BY_USER returns correct base list`() = runTest {
        // Arrange
        whenever(profileFunction.isProfileValid(any())).thenReturn(true)
        whenever(constraintChecker.isLoopInvocationAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        whenever(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        mockCurrentMode(RM.Mode.SUSPENDED_BY_USER)
        val expectedModes = listOf(
            RM.Mode.DISCONNECTED_PUMP,
            RM.Mode.RESUME
        )

        // Act
        val result = loopPlugin.allowedNextModes()

        // Assert
        assertThat(result).isEqualTo(expectedModes)
    }

    @Test
    fun `allowedNextModes for DISCONNECTED_PUMP returns correct base list`() = runTest {
        // Arrange
        whenever(profileFunction.isProfileValid(any())).thenReturn(true)
        whenever(constraintChecker.isLoopInvocationAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        whenever(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        mockCurrentMode(RM.Mode.DISCONNECTED_PUMP)
        val expectedModes = listOf(
            RM.Mode.RESUME
        )

        // Act
        val result = loopPlugin.allowedNextModes()

        // Assert
        assertThat(result).isEqualTo(expectedModes)
    }

    @Test
    fun `allowedNextModes removes looping modes when loop invocation is not allowed`() = runTest {
        // Arrange
        whenever(profileFunction.isProfileValid(any())).thenReturn(true)
        whenever(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        mockCurrentMode(RM.Mode.OPEN_LOOP)
        whenever(constraintChecker.isLoopInvocationAllowed()).thenReturn(ConstraintObject(false, aapsLogger))
        whenever(persistenceLayer.insertOrUpdateRunningMode(any(), any(), any(), anyOrNull(), any()))
            .thenReturn(PersistenceLayer.TransactionResult())
        val expectedModes = listOf(
            // OPEN_LOOP, CLOSED_LOOP, and CLOSED_LOOP_LGS should be removed
            RM.Mode.DISABLED_LOOP,
            RM.Mode.DISCONNECTED_PUMP,
            RM.Mode.SUSPENDED_BY_USER,
            RM.Mode.SUPER_BOLUS
        )

        // Act
        val result = loopPlugin.allowedNextModes()

        // Assert
        assertThat(result).isEqualTo(expectedModes)
    }

    @Test
    fun `allowedNextModes removes CLOSED_LOOP when closed loop is not allowed`() = runTest {
        // Arrange
        whenever(profileFunction.isProfileValid(any())).thenReturn(true)
        whenever(constraintChecker.isLoopInvocationAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        mockCurrentMode(RM.Mode.OPEN_LOOP)
        whenever(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(false, aapsLogger))

        val expectedModes = listOf(
            // CLOSED_LOOP should be removed
            RM.Mode.DISABLED_LOOP,
            RM.Mode.CLOSED_LOOP_LGS,
            RM.Mode.DISCONNECTED_PUMP,
            RM.Mode.SUSPENDED_BY_USER,
            RM.Mode.SUPER_BOLUS
        )

        // Act
        val result = loopPlugin.allowedNextModes()

        // Assert
        assertThat(result).isEqualTo(expectedModes)
    }

    // region Tests for runningModePreCheck
    //
    // These drive the reconciliation directly. They used to call runningModeRecord() instead, because a
    // read triggered the pre-check - which is precisely the defect that produced duplicate rows, so the
    // trigger is now explicit. `reading the running mode never writes` at the end of the region guards it.

    private fun setupForPreCheck() = runTest {
        // Default setup: All constraints pass, pump is not suspended.
        whenever(activePlugin.activePump.isSuspended()).thenReturn(false)
        whenever(constraintChecker.isLoopInvocationAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        whenever(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        whenever(constraintChecker.isLgsForced()).thenReturn(ConstraintObject(false, aapsLogger))

        // Mock the database calls
        whenever(persistenceLayer.insertOrUpdateRunningMode(any(), any(), any(), anyOrNull(), any()))
            .thenReturn(PersistenceLayer.TransactionResult())

        // Default the active mode to prevent nulls. The mockCurrentMode helper will override this.
        mockCurrentMode(RM(mode = RM.Mode.DISABLED_LOOP, timestamp = dateUtil.now(), duration = 0))
    }

    // Helper to mock what the DB returns for the *current* active mode
    private fun mockCurrentMode(mode: RM) = runTest {
        whenever(persistenceLayer.getRunningModeActiveAt(any())).thenReturn(mode)
    }

    @Test
    fun `runningModePreCheck forces SUSPENDED_BY_PUMP when pump is suspended`() = runTest {
        // Arrange
        setupForPreCheck()
        // The current mode in the DB is CLOSED_LOOP, but the pump reports it's suspended
        mockCurrentMode(RM(mode = RM.Mode.CLOSED_LOOP, timestamp = dateUtil.now(), duration = 0))
        whenever(activePlugin.activePump.isSuspended()).thenReturn(true)

        // Act
        loopPlugin.runningModePreCheck()

        // Assert
        val modeCaptor = argumentCaptor<RM>()
        verify(persistenceLayer).insertOrUpdateRunningMode(
            modeCaptor.capture(),
            eq(Action.SUSPEND),
            eq(Sources.Loop),
            anyOrNull(),
            anyOrNull()
        )
        // Verify that the plugin tried to insert a new, auto-forced SUSPENDED_BY_PUMP mode
        assertThat(modeCaptor.firstValue.mode).isEqualTo(RM.Mode.SUSPENDED_BY_PUMP)
        assertThat(modeCaptor.firstValue.autoForced).isTrue()
    }

    @Test
    fun `runningModePreCheck reverts from SUSPENDED_BY_PUMP when pump is resumed`() = runTest {
        // Arrange
        setupForPreCheck()
        val suspendedByPumpMode = RM(mode = RM.Mode.SUSPENDED_BY_PUMP, timestamp = dateUtil.now() - T.mins(5).msecs(), duration = 0)
        val previousMode = RM(mode = RM.Mode.CLOSED_LOOP, timestamp = dateUtil.now() - T.mins(10).msecs(), duration = T.mins(5).msecs())
        whenever(activePlugin.activePump.isSuspended()).thenReturn(false)
        whenever(rh.gs(app.aaps.core.ui.R.string.pump_running)).thenReturn("Pump running")

        // 1. First time getRunningModeActiveAt is called, return the suspended mode.
        // 2. Any subsequent time it's called (on the re-run), return the previous, non-suspended mode.
        whenever(persistenceLayer.getRunningModeActiveAt(any()))
            .thenReturn(suspendedByPumpMode)
            .thenReturn(previousMode)

        // Act
        loopPlugin.runningModePreCheck()

        // Assert
        val modeCaptor = argumentCaptor<RM>()
        // We only care that it was called once to end the suspended mode.
        // The re-run should find a consistent state and do nothing.
        verify(persistenceLayer).insertOrUpdateRunningMode(
            modeCaptor.capture(),
            eq(Action.PUMP_RUNNING),
            eq(Sources.Loop),
            anyOrNull(),
            anyOrNull()
        )
        // Verify we are *ending* the SUSPENDED_BY_PUMP mode by setting its duration
        assertThat(modeCaptor.firstValue.mode).isEqualTo(RM.Mode.SUSPENDED_BY_PUMP)
        assertThat(modeCaptor.firstValue.duration).isGreaterThan(0)
    }

    @Test
    fun `runningModePreCheck forces DISABLED_LOOP when loop invocation is denied`() = runTest {
        // Arrange
        setupForPreCheck()
        // The current mode is OPEN_LOOP, but a constraint now forbids looping
        mockCurrentMode(RM(mode = RM.Mode.OPEN_LOOP, timestamp = dateUtil.now(), duration = 0))
        whenever(constraintChecker.isLoopInvocationAllowed()).thenReturn(ConstraintObject(false, aapsLogger))

        // Act
        loopPlugin.runningModePreCheck()

        // Assert
        val modeCaptor = argumentCaptor<RM>()
        verify(persistenceLayer).insertOrUpdateRunningMode(
            modeCaptor.capture(),
            eq(Action.LOOP_DISABLED),
            eq(Sources.Loop),
            anyOrNull(),
            anyOrNull()
        )
        assertThat(modeCaptor.firstValue.mode).isEqualTo(RM.Mode.DISABLED_LOOP)
        assertThat(modeCaptor.firstValue.autoForced).isTrue()
    }

    @Test
    fun `runningModePreCheck forces OPEN_LOOP when closed loop is denied`() = runTest {
        // Arrange
        setupForPreCheck()
        // The current mode is CLOSED_LOOP, but a constraint now forbids it
        mockCurrentMode(RM(mode = RM.Mode.CLOSED_LOOP, timestamp = dateUtil.now(), duration = 0))
        whenever(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(false, aapsLogger))

        // Act
        loopPlugin.runningModePreCheck()

        // Assert
        val modeCaptor = argumentCaptor<RM>()
        verify(persistenceLayer).insertOrUpdateRunningMode(
            modeCaptor.capture(),
            eq(Action.OPEN_LOOP_MODE),
            eq(Sources.Loop),
            anyOrNull(),
            anyOrNull()
        )
        assertThat(modeCaptor.firstValue.mode).isEqualTo(RM.Mode.OPEN_LOOP)
        assertThat(modeCaptor.firstValue.autoForced).isTrue()
    }

    @Test
    fun `runningModePreCheck reverts from forced OPEN_LOOP when constraints pass again`() = runTest {
        // Arrange
        setupForPreCheck()
        // The current mode is an auto-forced OPEN_LOOP
        val forcedOpenLoop = RM(
            mode = RM.Mode.OPEN_LOOP,
            timestamp = dateUtil.now() - T.mins(10).msecs(),
            autoForced = true,
            duration = 0
        )
        mockCurrentMode(forcedOpenLoop)
        // But now, the constraint that caused it is no longer active
        whenever(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        whenever(rh.gs(app.aaps.core.ui.R.string.mode_reverted)).thenReturn("Mode reverted")

        // Act
        loopPlugin.runningModePreCheck()

        // Assert
        val modeCaptor = argumentCaptor<RM>()
        verify(persistenceLayer).insertOrUpdateRunningMode(
            modeCaptor.capture(),
            eq(Action.LOOP_CHANGE),
            eq(Sources.Loop),
            anyOrNull(),
            anyOrNull()
        )
        // Verify that the ended mode is the one we started with, and its duration is now set
        assertThat(modeCaptor.firstValue.mode).isEqualTo(RM.Mode.OPEN_LOOP)
        assertThat(modeCaptor.firstValue.duration).isGreaterThan(0)
    }

    @Test
    fun `runningModePreCheck does nothing if state is consistent`() = runTest {
        // Arrange
        setupForPreCheck()
        // The current mode is consistent with all constraints
        mockCurrentMode(RM(mode = RM.Mode.CLOSED_LOOP, timestamp = dateUtil.now(), duration = 0))
        // All constraints are passing and pump is not suspended (from default setup)

        // Act
        loopPlugin.runningModePreCheck()

        // Assert
        // Verify that no *new* running mode was inserted.
        verify(persistenceLayer, never()).insertOrUpdateRunningMode(any(), any(), any(), anyOrNull(), any())
    }

    /**
     * Reading the running mode must not change it.
     *
     * `runningModeRecord()` runs `runningModePreCheck()` first, so today every one of the ~36 places that
     * read the mode - ViewModels, the Glance widget, wear, `KeepAliveWorker`, SMS, automation - also
     * writes to the RM table. The pre-check is a read-check-write across two separate IO calls with
     * nothing serializing it, so when one pump suspend wakes many readers at once (`EventPumpStatusChanged`
     * is sent centrally by the command queue) they all read the pre-suspend mode before any of them has
     * committed, and each inserts its own row. That is the duplicate `Pump suspended` history in #5001.
     *
     * The fix is to make the read pure and give reconciliation its own trigger, which is also what
     * `_docs/RUNNING_MODE_SPEC.md` proposes under "Known inconsistencies". This test is the guard for that
     * split: it FAILS today on purpose, and it is what stops the write from creeping back onto the read
     * path later.
     *
     * Deliberately arranged so reconciliation really is due (the pump reports suspended while the stored
     * mode is CLOSED_LOOP) - a test where nothing needs doing would pass for the wrong reason. The
     * constraint-driven branches need the same guard; that sibling test belongs with the split, when
     * there is a trigger to move them to.
     */
    @Test
    fun `reading the running mode never writes`() = runTest {
        // Arrange
        setupForPreCheck()
        mockCurrentMode(RM(mode = RM.Mode.CLOSED_LOOP, timestamp = dateUtil.now(), duration = 0))
        whenever(activePlugin.activePump.isSuspended()).thenReturn(true)

        // Act - both public read paths
        loopPlugin.runningModeRecord()
        loopPlugin.runningMode()

        // Assert
        verify(persistenceLayer, never()).insertOrUpdateRunningMode(any(), any(), any(), anyOrNull(), any())
    }

    /**
     * Two reconciliation triggers arriving together must still write one row.
     *
     * `runningModePreCheck` reads the mode, compares it against the pump, then writes - three steps with
     * suspension points between them. On a device the triggers really do arrive together: one pump suspend
     * sends a single `EventPumpStatusChanged` that wakes the collector in `onStart` and the calculation
     * that calls `invoke()`, on different dispatchers. Without `reconcileMutex` both read the pre-suspend
     * mode and both insert.
     *
     * Deterministic, not a stress test. `persistenceLayer` is made to behave like the DB rather than like
     * a fixed stub - the read returns whatever the last write stored - because that is the whole point: the
     * second trigger has to see the first one's row. The first read is held until the test releases it, so
     * the interleaving is fixed rather than hoped for.
     */
    @Test
    fun `two reconciliations arriving together write only one row`() = runTest {
        // Arrange
        setupForPreCheck()
        whenever(activePlugin.activePump.isSuspended()).thenReturn(true)

        val stored = AtomicReference(RM(mode = RM.Mode.CLOSED_LOOP, timestamp = dateUtil.now(), duration = 0))
        val firstReadStarted = CompletableDeferred<Unit>()
        val releaseFirstRead = CompletableDeferred<Unit>()
        val holdFirstRead = AtomicBoolean(true)

        persistenceLayer.stub {
            on { getRunningModeActiveAt(any()) } doSuspendableAnswer {
                // Snapshot BEFORE the hold: a real read returns what the row said when it ran, so holding
                // it must not let this caller pick up a write that landed while it waited. Returning
                // stored.get() after the await makes the test pass with or without the lock.
                val atReadTime = stored.get()
                if (holdFirstRead.compareAndSet(true, false)) {
                    firstReadStarted.complete(Unit)
                    releaseFirstRead.await()
                }
                atReadTime
            }
            on { insertOrUpdateRunningMode(any(), any(), any(), anyOrNull(), any()) } doSuspendableAnswer { invocation ->
                stored.set(invocation.getArgument(0))
                PersistenceLayer.TransactionResult()
            }
        }

        // Act
        val first = launch { loopPlugin.runningModePreCheck() }
        firstReadStarted.await()
        val second = launch { loopPlugin.runningModePreCheck() }
        // Let the second one get as far as it can while the first still holds the critical section.
        yield()
        releaseFirstRead.complete(Unit)
        first.join()
        second.join()

        // Assert
        verify(persistenceLayer, times(1)).insertOrUpdateRunningMode(any(), any(), any(), anyOrNull(), any())
        assertThat(stored.get().mode).isEqualTo(RM.Mode.SUSPENDED_BY_PUMP)
    }

// endregion

// region buildAndStoreDeviceStatus

    /**
     * The device-status payload uploaded to Nightscout had no coverage at all, which is thin for
     * something written every loop cycle. It is also the one place that takes the APS document and adds
     * to it, so it is where an immutable document can quietly lose fields: the old code wrote into the
     * object returned by [app.aaps.core.interfaces.aps.APSResult.json] and relied on that write sticking.
     *
     * The APS result is stubbed rather than run, so these tests are about the envelope - which entries
     * get added, and that the original ones survive - not about what the algorithm produced.
     */
    private fun apsResultReturning(vararg entries: Pair<String, Int>): APSResult =
        mock<APSResult>().also { result ->
            whenever(result.json()).thenReturn(buildJsonObject { entries.forEach { (k, v) -> put(k, v) } })
            whenever(result.duration).thenReturn(30)
            whenever(result.rate).thenReturn(1.5)
            whenever(result.smb).thenReturn(0.4)
        }

    private suspend fun prepareDeviceStatus(request: APSResult, tbrSetByPump: PumpEnactResult? = null) {
        whenever(pumpStatusProvider.generatePumpJsonStatus()).thenReturn(JsonObject(emptyMap()))
        // The profile reads isfMgdlForCarbs through the active APS and errors out when there is none.
        // Stubbed in two steps: a whenever() nested inside another one confuses Mockito.
        val aps = mock<APS>()
        whenever(aps.usingDynamicIsf()).thenReturn(false)
        whenever(activePlugin.activeAPS).thenReturn(aps)
        // ProfileSealed reads activeAPS once, when it is built, so the base fixture's profile was built
        // before the stub above existed. Build a fresh one - outside the thenReturn(), because that
        // constructor call touches a mock and Mockito would read it as another unfinished stubbing.
        val profile = ProfileSealed.EPS(effectiveProfileSwitch, activePlugin)
        whenever(profileFunction.getProfile()).thenReturn(profile)
        loopPlugin.lastRun = Loop.LastRun().apply {
            this.request = request
            this.lastAPSRun = dateUtil.now()
            this.tbrSetByPump = tbrSetByPump
        }
    }

    private suspend fun storedDeviceStatus(): DS {
        loopPlugin.buildAndStoreDeviceStatus("test")
        val captor = argumentCaptor<DS>()
        verify(persistenceLayer).insertDeviceStatus(captor.capture())
        return captor.firstValue
    }

    @Test
    fun `suggested keeps the aps entries and gains timestamp and isf`() = runTest {
        prepareDeviceStatus(apsResultReturning("eventualBG" to 120, "carbsReq" to 0))

        val suggested = JSONObject(storedDeviceStatus().suggested!!)

        // The document the APS produced is still all there…
        assertThat(suggested.getInt("eventualBG")).isEqualTo(120)
        assertThat(suggested.getInt("carbsReq")).isEqualTo(0)
        // …and the two entries this method adds arrived.
        assertThat(suggested.has("timestamp")).isTrue()
        assertThat(suggested.has("isfMgdlForCarbs")).isTrue()
    }

    /** No temp basal was set on the pump, so there is nothing enacted to report. */
    @Test
    fun `enacted is absent when the pump enacted nothing`() = runTest {
        prepareDeviceStatus(apsResultReturning("eventualBG" to 120))

        assertThat(storedDeviceStatus().enacted).isNull()
    }

    @Test
    fun `enacted carries the pump rate and duration, the request and the delivered smb`() = runTest {
        val tbr = pumpEnactResultProvider().enacted(true).isPercent(false).absolute(1.25).duration(45).bolusDelivered(0.0)
        prepareDeviceStatus(apsResultReturning("eventualBG" to 120), tbrSetByPump = tbr)

        val enacted = JSONObject(storedDeviceStatus().enacted!!)

        assertThat(enacted.getInt("eventualBG")).isEqualTo(120)       // the aps document survived the merge
        assertThat(enacted.getDouble("rate")).isEqualTo(1.25)         // …from the pump result, not the request
        assertThat(enacted.getInt("duration")).isEqualTo(45)
        assertThat(enacted.getBoolean("received")).isTrue()
        assertThat(enacted.getDouble("smb")).isEqualTo(0.0)
        // "requested" is a nested object holding what the APS asked for, next to what the pump did.
        val requested = enacted.getJSONObject("requested")
        assertThat(requested.getInt("duration")).isEqualTo(30)
        assertThat(requested.getDouble("rate")).isEqualTo(1.5)
        assertThat(requested.getString("temp")).isEqualTo("absolute")
        assertThat(requested.getDouble("smb")).isEqualTo(0.4)
    }

    /**
     * A pump result that only delivered an SMB carries no rate/duration, and reading them throws.
     *
     * Pinned so the behaviour is known rather than discovered from a crash report. **It is not
     * reachable in production**: `lastRun.tbrSetByPump` is only ever assigned null, a queued result, or
     * the return of `applyTBRRequest`, and only a delivered bolus produces a document without a rate.
     * The exception is a contract guard, so do not "fix" it into returning zeros - that would upload a
     * temp basal the pump never enacted.
     *
     * `NoSuchElementException`, not `JSONException`, since the document became kotlinx.
     */
    @Test
    fun `an smb only pump result has no rate to report and throws`() = runTest {
        val smbOnly = pumpEnactResultProvider().enacted(true).bolusDelivered(0.3)
        prepareDeviceStatus(apsResultReturning("eventualBG" to 120), tbrSetByPump = smbOnly)

        assertThrows<NoSuchElementException> { loopPlugin.buildAndStoreDeviceStatus("test") }
    }

    /** Older than 5 minutes: the run is stale, so neither document is sent. */
    @Test
    fun `a stale run sends no suggested and no enacted`() = runTest {
        prepareDeviceStatus(apsResultReturning("eventualBG" to 120))
        loopPlugin.lastRun?.lastAPSRun = dateUtil.now() - T.mins(6).msecs()

        val stored = storedDeviceStatus()

        assertThat(stored.suggested).isNull()
        assertThat(stored.enacted).isNull()
    }

// endregion

    /**
     * Once the loop starts talking to the pump, cancelling the caller must not stop it - issue #5100.
     *
     * The pump command suspends here, the caller is cancelled while it is still running, and the
     * result still has to be written back afterwards. Without the guard the cancel lands on the
     * command queue's await, the pump is changed and the app never records it. For the accept path
     * the caller is a screen, so this is what used to happen when the user left the screen after
     * pressing the button.
     */
    @Test
    fun `accepting a change still finishes after the caller is cancelled`() = runTest {
        val commandStarted = CompletableDeferred<Unit>()
        val releaseCommand = CompletableDeferred<Unit>()
        val enacted = pumpEnactResultProvider().enacted(true).success(true)

        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        whenever(virtualPumpPlugin.isInitialized()).thenReturn(true)
        whenever(virtualPumpPlugin.isSuspended()).thenReturn(false)
        whenever(virtualPumpPlugin.pumpDescription).thenReturn(PumpDescription().apply { basalStep = 0.05 })
        whenever(virtualPumpPlugin.baseBasalRate).thenReturn(PumpRate(1.0))
        whenever(ch.fromPump(any<PumpRate>())).thenReturn(1.0)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)

        val request = mock<APSResult>()
        whenever(request.isTempBasalRequested).thenReturn(true)
        whenever(request.rate).thenReturn(2.0)
        whenever(request.duration).thenReturn(30)
        whenever(request.usePercent).thenReturn(false)
        loopPlugin.lastRun = Loop.LastRun().apply {
            this.constraintsProcessed = request
            this.lastAPSRun = dateUtil.now()
        }
        // The pump command hangs until the test releases it, so the cancel below is guaranteed to
        // arrive while it is still in flight.
        commandQueue.stub {
            on { tempBasalAbsolute(any(), any(), any(), any(), any()) } doSuspendableAnswer {
                commandStarted.complete(Unit)
                releaseCommand.await()
                enacted
            }
        }

        val accept = launch { loopPlugin.acceptChangeRequest() }
        commandStarted.await()
        accept.cancel()
        releaseCommand.complete(Unit)
        accept.join()

        assertThat(loopPlugin.lastRun?.tbrSetByPump).isEqualTo(enacted)
        assertThat(loopPlugin.lastRun?.lastOpenModeAccept).isNotEqualTo(0L)
    }

    /**
     * Accepting an open-loop suggestion does nothing while the queue is held for a settings import.
     *
     * This path enacts OUTSIDE `invokeMutex` and is reachable from the phone and the watch, so the
     * guard in `invoke` does not cover it. Held, the executor picks nothing up, so enacting would leave
     * the temp basal in the queue to land after the import - against a driver that was just stopped and
     * restarted. Waiting instead deadlocks: `withHold` raises the flag before it waits.
     */
    @Test
    fun `acceptChangeRequest enacts nothing while the queue is held`() = runTest {
        // Everything else is set up so the request WOULD be enacted - a pump that is initialized, not
        // suspended, with a base rate and no running TBR. Without that the early return in
        // applyTBRRequest satisfies the assertions on its own and the test proves nothing, which is
        // what the first version of it did.
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        whenever(virtualPumpPlugin.isInitialized()).thenReturn(true)
        whenever(virtualPumpPlugin.isSuspended()).thenReturn(false)
        whenever(virtualPumpPlugin.pumpDescription).thenReturn(PumpDescription().apply { basalStep = 0.05 })
        whenever(virtualPumpPlugin.baseBasalRate).thenReturn(PumpRate(1.0))
        whenever(ch.fromPump(any<PumpRate>())).thenReturn(1.0)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)
        whenever(commandQueue.isHeld()).thenReturn(true)

        val request = mock<APSResult>()
        whenever(request.isTempBasalRequested).thenReturn(true)
        whenever(request.rate).thenReturn(2.0)
        whenever(request.duration).thenReturn(30)
        whenever(request.usePercent).thenReturn(false)
        loopPlugin.lastRun = Loop.LastRun().apply {
            this.constraintsProcessed = request
            this.lastAPSRun = dateUtil.now()
        }

        loopPlugin.acceptChangeRequest()

        verify(commandQueue, never()).tempBasalAbsolute(any(), any(), any(), any(), any())
        verify(commandQueue, never()).tempBasalPercent(any(), any(), any(), any(), any())
        assertThat(loopPlugin.lastRun?.lastOpenModeAccept).isEqualTo(0L)
    }

    /**
     * The deferred SMB fallback must not outlive the plugin.
     *
     * It re-runs the loop a second later, so a plugin stopped in between - which a settings import
     * does to every plugin - would otherwise have it wake up and queue commands against a pump driver
     * that is being torn down. It ran on the application scope and nothing owned it.
     */
    @Test
    fun `onStop cancels the deferred SMB fallback`() = runTest {
        loopPlugin.scheduleSmbFallback(allowNotification = false)
        val scheduled = loopPlugin.smbFallbackJob
        assertThat(scheduled).isNotNull()
        assertThat(scheduled!!.isActive).isTrue()

        loopPlugin.onStop()

        assertThat(scheduled.isCancelled).isTrue()
    }

    /** Two failures in the same second schedule one re-run, not two stacked on the invoke mutex. */
    @Test
    fun `scheduling the fallback again replaces the pending one`() = runTest {
        loopPlugin.scheduleSmbFallback(allowNotification = false)
        val first = loopPlugin.smbFallbackJob

        loopPlugin.scheduleSmbFallback(allowNotification = false)

        assertThat(first!!.isCancelled).isTrue()
        assertThat(loopPlugin.smbFallbackJob).isNotSameInstanceAs(first)
        assertThat(loopPlugin.smbFallbackJob!!.isActive).isTrue()
    }
}
