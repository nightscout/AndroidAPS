package app.aaps.plugins.aps.loop

import android.app.NotificationManager
import android.content.Context
import androidx.preference.PreferenceManager
import app.aaps.core.data.model.RM
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.PumpStatusProvider
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.pump.virtual.VirtualPumpPlugin
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LoopPluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var runningConfiguration: RunningConfiguration
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Mock lateinit var pumpStatusProvider: PumpStatusProvider

    private lateinit var loopPlugin: LoopPlugin

    @BeforeEach fun prepare() {
        whenever(config.APS).thenReturn(true)
        preferenceManager = PreferenceManager(context)
        loopPlugin = LoopPlugin(
            aapsLogger, aapsSchedulers, rxBus, preferences, config,
            constraintChecker, rh, profileFunction, context, commandQueue, activePlugin, virtualPumpPlugin, iobCobCalculator, processedTbrEbData, receiverStatusStore, fabricPrivacy, dateUtil, uel,
            persistenceLayer, runningConfiguration, uiInteraction, pumpEnactResultProvider, processedDeviceStatusData, pumpStatusProvider
        )
        whenever(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        whenever(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(notificationManager)
    }

    @Test
    fun testPluginInterface() {
        whenever(rh.gs(app.aaps.core.ui.R.string.loop)).thenReturn("Loop")
        whenever(rh.gs(app.aaps.plugins.aps.R.string.loop_shortname)).thenReturn("LOOP")
//        whenever(preferences.get(StringKey.LoopApsMode)).thenReturn(ApsMode.CLOSED.name)
        val pumpDescription = PumpDescription()
        whenever(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)
        assertThat(loopPlugin.pluginDescription.fragmentClass).isEqualTo(LoopFragment::class.java.name)
        assertThat(loopPlugin.getType()).isEqualTo(PluginType.LOOP)
        assertThat(loopPlugin.name).isEqualTo("Loop")
        assertThat(loopPlugin.nameShort).isEqualTo("LOOP")
        assertThat(loopPlugin.hasFragment()).isTrue()
        assertThat(loopPlugin.showInList(PluginType.LOOP)).isTrue()
        assertThat(loopPlugin.preferencesId.toLong()).isEqualTo(PluginDescription.PREFERENCE_SCREEN)

        // Plugin is enabled by default
        assertThat(loopPlugin.isEnabled()).isTrue()

        // No temp basal capable pump should disable plugin
        virtualPumpPlugin.pumpDescription.isTempBasalCapable = false
        assertThat(loopPlugin.specialEnableCondition()).isFalse()
        virtualPumpPlugin.pumpDescription.isTempBasalCapable = true

        // Fragment is hidden by default
        assertThat(loopPlugin.isFragmentVisible()).isFalse()
        loopPlugin.setFragmentVisible(PluginType.LOOP, true)
        assertThat(loopPlugin.isFragmentVisible()).isTrue()
    }

    @Test
    fun iobShouldBeLimited() {
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
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        loopPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }

    @Test
    fun `minutesToEndOfSuspend returns 0 when loop is not suspended`() {
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
    fun `minutesToEndOfSuspend returns remaining minutes for a temporary suspension`() {
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
    fun `minutesToEndOfSuspend returns Int_MAX_VALUE for an indefinite suspension`() {
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
    fun `minutesToEndOfSuspend returns 0 when temporary suspension has just ended`() {
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

    private fun mockCurrentMode(mode: RM.Mode) {
        val now = 1672531200000L
        val runningMode = RM(mode = mode, timestamp = now, duration = 0)
        whenever(dateUtil.now()).thenReturn(now)
        whenever(persistenceLayer.getRunningModeActiveAt(now)).thenReturn(runningMode)
    }

    @Test
    fun `allowedNextModes returns emptyList if profile is invalid`() {
        // Arrange
        whenever(profileFunction.isProfileValid(any())).thenReturn(false)
        mockCurrentMode(RM.Mode.OPEN_LOOP) // Any mode

        // Act
        val result = loopPlugin.allowedNextModes()

        // Assert
        assertThat(result).isEmpty()
    }

    @Test
    fun `allowedNextModes for OPEN_LOOP returns correct base list`() {
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
    fun `allowedNextModes for CLOSED_LOOP returns correct base list`() {
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
    fun `allowedNextModes for SUSPENDED_BY_USER returns correct base list`() {
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
    fun `allowedNextModes for DISCONNECTED_PUMP returns correct base list`() {
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
    fun `allowedNextModes removes looping modes when loop invocation is not allowed`() {
        // Arrange
        whenever(profileFunction.isProfileValid(any())).thenReturn(true)
        whenever(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        mockCurrentMode(RM.Mode.OPEN_LOOP)
        whenever(constraintChecker.isLoopInvocationAllowed()).thenReturn(ConstraintObject(false, aapsLogger))
        whenever(persistenceLayer.insertOrUpdateRunningMode(any(), any(), any(), anyOrNull(), any()))
            .thenReturn(Single.just(PersistenceLayer.TransactionResult()))
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
    fun `allowedNextModes removes CLOSED_LOOP when closed loop is not allowed`() {
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

    // region Tests for runningModePreCheck (via public runningModeRecord property)

    private fun setupForPreCheck() {
        // Default setup: All constraints pass, pump is not suspended.
        whenever(activePlugin.activePump.isSuspended()).thenReturn(false)
        whenever(constraintChecker.isLoopInvocationAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        whenever(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(true, aapsLogger))
        whenever(constraintChecker.isLgsForced()).thenReturn(ConstraintObject(false, aapsLogger))

        // Mock the database calls
        whenever(persistenceLayer.insertOrUpdateRunningMode(any(), any(), any(), anyOrNull(), any()))
            .thenReturn(Single.just(PersistenceLayer.TransactionResult()))

        // Default the active mode to prevent nulls. The mockCurrentMode helper will override this.
        mockCurrentMode(RM(mode = RM.Mode.DISABLED_LOOP, timestamp = dateUtil.now(), duration = 0))
    }

    // Helper to mock what the DB returns for the *current* active mode
    private fun mockCurrentMode(mode: RM) {
        whenever(persistenceLayer.getRunningModeActiveAt(any())).thenReturn(mode)
    }

    @Test
    fun `runningModeRecord forces SUSPENDED_BY_PUMP when pump is suspended`() {
        // Arrange
        setupForPreCheck()
        // The current mode in the DB is CLOSED_LOOP, but the pump reports it's suspended
        mockCurrentMode(RM(mode = RM.Mode.CLOSED_LOOP, timestamp = dateUtil.now(), duration = 0))
        whenever(activePlugin.activePump.isSuspended()).thenReturn(true)

        // Act
        loopPlugin.runningModeRecord // Accessing the property triggers the pre-check

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
    fun `runningModeRecord reverts from SUSPENDED_BY_PUMP when pump is resumed`() {
        // Arrange
        setupForPreCheck()
        val suspendedByPumpMode = RM(mode = RM.Mode.SUSPENDED_BY_PUMP, timestamp = dateUtil.now() - T.mins(5).msecs(), duration = 0)
        val previousMode = RM(mode = RM.Mode.CLOSED_LOOP, timestamp = dateUtil.now() - T.mins(10).msecs(), duration = T.mins(5).msecs())
        whenever(activePlugin.activePump.isSuspended()).thenReturn(false)
        whenever(rh.gs(app.aaps.core.ui.R.string.pump_running)).thenReturn("Pump running")

        // 1. First time getRunningModeActiveAt is called, return the suspended mode.
        // 2. Any subsequent time it's called (in the recursive call), return the previous, non-suspended mode.
        whenever(persistenceLayer.getRunningModeActiveAt(any()))
            .thenReturn(suspendedByPumpMode)
            .thenReturn(previousMode)

        // Act
        loopPlugin.runningModeRecord // Accessing the property triggers the pre-check

        // Assert
        val modeCaptor = argumentCaptor<RM>()
        // We only care that it was called once to end the suspended mode.
        // The recursive call should find a consistent state and do nothing.
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
    fun `runningModeRecord forces DISABLED_LOOP when loop invocation is denied`() {
        // Arrange
        setupForPreCheck()
        // The current mode is OPEN_LOOP, but a constraint now forbids looping
        mockCurrentMode(RM(mode = RM.Mode.OPEN_LOOP, timestamp = dateUtil.now(), duration = 0))
        whenever(constraintChecker.isLoopInvocationAllowed()).thenReturn(ConstraintObject(false, aapsLogger))

        // Act
        loopPlugin.runningModeRecord

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
    fun `runningModeRecord forces OPEN_LOOP when closed loop is denied`() {
        // Arrange
        setupForPreCheck()
        // The current mode is CLOSED_LOOP, but a constraint now forbids it
        mockCurrentMode(RM(mode = RM.Mode.CLOSED_LOOP, timestamp = dateUtil.now(), duration = 0))
        whenever(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(false, aapsLogger))

        // Act
        loopPlugin.runningModeRecord

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
    fun `runningModeRecord reverts from forced OPEN_LOOP when constraints pass again`() {
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
        loopPlugin.runningModeRecord

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
    fun `runningModeRecord does nothing if state is consistent`() {
        // Arrange
        setupForPreCheck()
        // The current mode is consistent with all constraints
        mockCurrentMode(RM(mode = RM.Mode.CLOSED_LOOP, timestamp = dateUtil.now(), duration = 0))
        // All constraints are passing and pump is not suspended (from default setup)

        // Act
        loopPlugin.runningModeRecord

        // Assert
        // Verify that no *new* running mode was inserted.
        verify(persistenceLayer, never()).insertOrUpdateRunningMode(any(), any(), any(), anyOrNull(), any())
    }

// endregion

}
