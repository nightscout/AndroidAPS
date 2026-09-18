package app.aaps.plugins.automation

import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.alerts.ReminderScheduler
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.location.LocationServiceController
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventBTChange
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.keys.StringNonKey
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.actions.ActionFactory
import app.aaps.plugins.automation.elements.ComparatorConnect
import app.aaps.plugins.automation.triggers.TriggerBTDevice
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerDeps
import app.aaps.plugins.automation.triggers.TriggerFactory
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * When `AutomationRuntime` runs its rules, and that two runs never overlap. Issue #5137.
 *
 * A fixed-time rule is due for only 5 minutes. The runtime's own timer is a `delay()` on a clock that
 * stops in deep sleep, so with the screen off it can miss that window entirely. Until 3.1.0 automation
 * also ran on every finished BG calculation; that subscription was dropped when the calculation moved
 * into a workflow, and nothing replaced it. These tests pin it back in.
 *
 * The same release also dropped `@Synchronized` from `processActions()` (to fix an ANR). A rule's
 * `lastRun` is set only after its actions finished, so two overlapping runs executed a due rule twice.
 * Serializing the runs brought one more thing to get right: a BT event that arrives during a run must
 * survive until the run queued behind it.
 *
 * A run gets past its first checks only with a loop that is also a [PluginBase] - the runtime casts it
 * to check whether it is enabled - so the loop here is a `PluginBase` mock that also implements [Loop].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutomationRuntimeProcessingTest : TestBaseWithProfile() {

    @Mock lateinit var actionFactory: ActionFactory
    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var locationServiceController: LocationServiceController
    @Mock lateinit var reminderScheduler: ReminderScheduler
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var sceneApi: SceneAutomationApi

    private val loopPlugin: PluginBase = mock(extraInterfaces = arrayOf(Loop::class))
    private val loop: Loop get() = loopPlugin as Loop

    private val triggerDeps: TriggerDeps by lazy {
        TriggerDeps(
            aapsLogger, rxBus, rh, profileFunction, profileUtil, preferences, mock(), mock(),
            activePlugin, iobCobCalculator, smbGlucoseStatusProvider, dateUtil
        )
    }
    private val triggerFactory: TriggerFactory by lazy { TriggerFactory(triggerDeps, { runtime }, sceneApi, receiverStatusStore) }
    private val eventFactory by lazy { AutomationEventFactory(aapsLogger, dateUtil, actionFactory, triggerFactory, triggerDeps) }
    private lateinit var runtime: AutomationRuntime

    @BeforeEach fun prepare() {
        whenever(config.APS).thenReturn(true)
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(config.appInitialized).thenReturn(true)
        loop.stub { onBlocking { runningMode() } doReturn RM.Mode.CLOSED_LOOP }
        whenever(loopPlugin.isEnabled()).thenReturn(true)
        val automationAllowed = mock<Constraint<Boolean>>()
        whenever(automationAllowed.value()).thenReturn(true)
        whenever(constraintChecker.isAutomationEnabled()).thenReturn(automationAllowed)
        // Empty stored list, so start() does not add the default rule, whose triggers need more wiring.
        whenever(preferences.get(StringNonKey.AutomationEvents)).thenReturn("[]")
        whenever(preferences.observe(StringNonKey.AutomationEvents)).thenReturn(MutableStateFlow("[]"))
        whenever(receiverStatusStore.chargingStatusFlow).thenReturn(MutableStateFlow(null))
        whenever(receiverStatusStore.networkStatusFlow).thenReturn(MutableStateFlow(null))

        runtime = AutomationRuntime(
            mock<LocationPermissions>(), eventFactory, aapsLogger, rh, preferences, loop, rxBus, constraintChecker,
            config, locationServiceController, dateUtil, activePlugin, reminderScheduler, actionFactory, triggerFactory, triggerDeps, receiverStatusStore,
            uel, profileRepository, sceneApi, mock()
        )
    }

    /**
     * Runs [block] with the runtime started on the test scheduler, and always cancels it afterwards.
     *
     * The cancel must happen even when an assertion fails. start() launches the never-ending timer
     * loop, and runTest drains the shared scheduler when it finishes - with the loop still alive that
     * drain moves virtual time forward for ever, and a failing test hangs instead of failing.
     */
    private fun TestScope.withStartedRuntime(block: TestScope.() -> Unit) {
        val sutScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        try {
            runtime.start(sutScope)
            // runCurrent, not advanceUntilIdle: the timer loop never ends, and virtual time must not
            // move on - the point is that a run does not come from the timer.
            runCurrent()
            block()
        } finally {
            sutScope.cancel()
        }
    }

    /** An action whose runs are counted, and which stays inside until [release] completes, if given. */
    private fun countingAction(name: String, release: CompletableDeferred<Unit>? = null, onRun: () -> Unit): Action {
        val result = mock<PumpEnactResult>()
        whenever(result.comment).thenReturn("")
        val action = mock<Action>()
        whenever(action.isValid()).thenReturn(true)
        whenever(action.shortDescription()).thenReturn(name)
        action.stub {
            onBlocking { doAction() } doSuspendableAnswer {
                onRun()
                release?.await() // stays inside the action, so lastRun is not set yet
                result
            }
        }
        return action
    }

    private fun addRule(title: String, trigger: TriggerConnector, action: Action) {
        runtime.add(eventFactory.newEvent().apply {
            this.title = title
            isEnabled = true
            this.trigger = trigger
            actions.add(action)
        })
    }

    @Test
    fun `a finished BG calculation starts a rule run`() = runTest {
        withStartedRuntime {
            clearInvocations(constraintChecker)

            rxBus.send(EventAutosensCalculationFinished(triggeredByNewBG = true))
            runCurrent()

            // The checks at the start of a run were made, so a run happened - not from the timer.
            verify(constraintChecker).isAutomationEnabled()
        }
    }

    @Test
    fun `nothing runs before a BG calculation or the timer`() = runTest {
        withStartedRuntime {
            verify(constraintChecker, never()).isAutomationEnabled()
        }
    }

    @Test
    fun `overlapping runs execute a due rule once`() = runTest {
        val release = CompletableDeferred<Unit>()
        var executions = 0
        addRule("due now", TriggerConnector(triggerDeps), countingAction("slow action", release) { executions++ })

        try {
            // Two triggers at nearly the same moment - the timer and a new BG, say.
            launch { runtime.processActions() }
            launch { runtime.processActions() }
            runCurrent() // the first run is inside the action; the second must be waiting, not executing
            assertThat(executions).isEqualTo(1)
        } finally {
            release.complete(Unit) // also on failure, so no run is left suspended in the action
        }
        advanceUntilIdle() // no start() here, so there is no timer loop to run forever

        assertThat(executions).isEqualTo(1)
    }

    @Test
    fun `a BT connect that arrives during a run still fires its rule`() = runTest {
        withStartedRuntime {
            var carRuleFired = 0
            val release = CompletableDeferred<Unit>()
            val carConnects = TriggerBTDevice(triggerDeps, runtime).apply {
                btDevice.value = "car"
                comparator.value = ComparatorConnect.Compare.ON_CONNECT
            }
            // The BT rule is checked first, then a slow rule keeps the run busy.
            addRule("car", TriggerConnector(triggerDeps).also { it.list.add(carConnects) }, countingAction("car action") { carRuleFired++ })
            addRule("slow", TriggerConnector(triggerDeps), countingAction("slow action", release) {})

            try {
                launch { runtime.processActions() }
                runCurrent() // run A checked the car rule (nothing yet) and is now inside the slow action

                // The car connects now. The collector stores the event and starts run B, which waits for A.
                rxBus.send(EventBTChange(EventBTChange.Change.CONNECT, "car"))
                runCurrent()
            } finally {
                release.complete(Unit)
            }
            runCurrent() // A finishes, then B runs

            // A must not throw away an event it never saw; B has to find it.
            assertThat(carRuleFired).isEqualTo(1)
        }
    }
}
