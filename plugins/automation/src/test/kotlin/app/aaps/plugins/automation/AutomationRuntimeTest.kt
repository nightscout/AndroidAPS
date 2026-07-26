package app.aaps.plugins.automation

import android.Manifest
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.AutomationEvent
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.services.LocationServiceHelper
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerLocation
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class AutomationRuntimeTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var loop: Loop
    @Mock lateinit var locationServiceHelper: LocationServiceHelper
    @Mock lateinit var timerUtil: TimerUtil
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var sceneApi: SceneAutomationApi
    private lateinit var automationRuntime: AutomationRuntime

    init {
        // Triggers read injected fields (e.g. rh) in their constructors.
        addInjector {
            if (it is Trigger) {
                it.rh = rh
                it.aapsLogger = aapsLogger
            }
        }
    }

    @BeforeEach fun prepare() {
        automationRuntime = AutomationRuntime(
            injector, aapsLogger, rh, preferences, context, fabricPrivacy, loop, rxBus, constraintChecker,
            aapsSchedulers, config, locationServiceHelper, dateUtil, activePlugin, timerUtil, receiverStatusStore,
            uel, profileRepository, sceneApi
        )
    }

    private fun addLocationEvent() {
        // usesLocationTrigger() only does `is TriggerLocation` type checks, so the trigger's
        // injected fields don't need to be populated here.
        val event = AutomationEventObject(injector).apply {
            isEnabled = true
            trigger = TriggerConnector(injector).also { it.list.add(TriggerLocation(injector)) }
        }
        automationRuntime.add(event)
    }

    @Test
    fun `requiredPermissions are empty without an enabled location trigger`() {
        whenever(config.APS).thenReturn(true)
        assertThat(automationRuntime.requiredPermissions()).isEmpty()
    }

    @Test
    fun `requiredPermissions include location permissions on master when a location trigger is enabled`() {
        whenever(config.APS).thenReturn(true)
        addLocationEvent()
        val allPermissions = automationRuntime.requiredPermissions().flatMap { it.permissions }
        assertThat(allPermissions).contains(Manifest.permission.ACCESS_FINE_LOCATION)
        assertThat(allPermissions).contains(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertThat(allPermissions).contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    @Test
    fun `requiredPermissions stay empty on a client even with a location trigger`() {
        whenever(config.APS).thenReturn(false)
        addLocationEvent()
        assertThat(automationRuntime.requiredPermissions()).isEmpty()
    }

    @Test
    fun `executionEnabled mirrors config APS - master`() {
        whenever(config.APS).thenReturn(true)
        assertThat(automationRuntime.executionEnabled).isTrue()
    }

    @Test
    fun `processEvent does not execute on a client`() = runTest {
        whenever(config.APS).thenReturn(false)
        val action = mock<Action>()
        // Default (empty) trigger -> canRun()/preconditionCanRun() would both be true on master, so
        // the only thing stopping execution here is the master-only guard at the top of processEvent.
        val event = AutomationEventObject(injector).apply { actions.add(action) }
        automationRuntime.processEvent(event)
        verifyNoInteractions(action) // guard returned before the actions loop
    }

    @Test
    fun `processActions does not run on a client`() = runTest {
        whenever(config.appInitialized).thenReturn(true)
        whenever(config.APS).thenReturn(false)
        automationRuntime.processActions()
        // Master-only guard returns before the loop-state / constraint checks are even consulted.
        verify(loop, never()).runningMode()
    }

    /**
     * Regression guard for the IdentityList wrapper in `notifyChanged()`. The underlying list of
     * mutable AutomationEventObject references doesn't change identity on in-place field mutations
     * (`event.isEnabled = …`), so the default `MutableStateFlow.value =` setter — which uses
     * structural equality — would silently swallow the emission. IdentityList's identity-only
     * equals forces every notifyChanged() to publish. If a future refactor drops the wrapper or
     * overrides AutomationEventObject.equals structurally, this test fails.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `notifyChanged always emits even when list contents are unchanged`() = runTest(UnconfinedTestDispatcher()) {
        val captured = mutableListOf<List<AutomationEvent>>()
        val job = automationRuntime.events.onEach { captured += it }.launchIn(this)

        // Three explicit notifyChanged() with no mutations between them — each one must reach the
        // collector. With Unconfined, emissions are observed synchronously on collection.
        automationRuntime.notifyChanged()
        automationRuntime.notifyChanged()
        automationRuntime.notifyChanged()

        job.cancel()
        // 1 seed + 3 explicit emits = 4 captured snapshots, all distinct references.
        assertThat(captured).hasSize(4)
        assertThat(captured.distinctBy { System.identityHashCode(it) }).hasSize(4)
    }

    // NOTE: the "EventWearUpdateTiles fires from runtime scope on notifyChanged" behavior is NOT
    // unit-tested here. AutomationRuntime.start() has heavy side effects (loadFromSP, the 1-min
    // processActions loop, preferences flow observation, location-service start) that would need
    // significant test scaffolding — not worth the fragility. The behavior is observable on a real
    // device: NS-sync an automation edit while on the Overview screen, watch the wear tile refresh
    // ~300ms later without opening the Automation screen first.
}
