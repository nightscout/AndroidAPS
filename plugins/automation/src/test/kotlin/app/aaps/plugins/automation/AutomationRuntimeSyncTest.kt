package app.aaps.plugins.automation

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.keys.StringNonKey
import app.aaps.plugins.automation.services.LocationServiceHelper
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

/**
 * Client-side behavior for Automation on the generic `SyncSpec(Bidirectional)` channel, **verbatim-load**
 * model (persistence is edit-driven, not load-driven). Verifies:
 *
 *  1. **An edit persists once; a verbatim reload writes nothing.** Persistence is driven only by edits
 *     (markEdited → requestPersist), never by loadFromSP — so a reload (incl. a self-observe-triggered
 *     one) re-parses without storing. No band-aid, no apply→store→observe loop.
 *  2. **Self-observe applies a master push, and applying it does not echo a write back.**
 *
 * Persistence is debounced on the runtime's internal scope, so [AutomationRuntime.start] is given an
 * injectable scope bound to the test scheduler (so `advanceUntilIdle()` drives the 300 ms debounce).
 * The fake below layers an in-memory `AutomationEvents` value over the `@Mock preferences`: `observe()`
 * feeds the self-observe subscription; `put()` records a LOCAL write and updates the observed flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutomationRuntimeSyncTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var loop: Loop
    @Mock lateinit var locationServiceHelper: LocationServiceHelper
    @Mock lateinit var timerUtil: TimerUtil
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var sceneApi: SceneAutomationApi

    private lateinit var automationRuntime: AutomationRuntime

    // Seed with an empty JSON array (not "") so loadFromSP doesn't inject the EMPTY_EVENT default,
    // whose TriggerBg/TriggerDelta need more injected fields than this test wires. Our own events use
    // an empty TriggerConnector, which (de)serializes with just rh/aapsLogger.
    private val autoFlow = MutableStateFlow("[]")
    private var localPutCount = 0
    private val remoteWrites = mutableListOf<String>() // values written via putRemote (master-wins apply)

    init {
        addInjector {
            if (it is Trigger) {
                it.rh = rh
                it.aapsLogger = aapsLogger
            }
        }
    }

    @BeforeEach fun prepare() {
        // In-memory fake for StringNonKey.AutomationEvents (overrides the base's generic observe stub).
        whenever(preferences.observe(StringNonKey.AutomationEvents)).thenReturn(autoFlow)
        whenever(preferences.get(StringNonKey.AutomationEvents)).thenAnswer { autoFlow.value }
        doAnswer { autoFlow.value = it.getArgument(1); localPutCount++; null } // local edit
            .whenever(preferences).put(eq(StringNonKey.AutomationEvents), any<String>())
        doAnswer { autoFlow.value = it.getArgument(1); remoteWrites.add(it.getArgument(1)); null } // master-wins apply
            .whenever(preferences).putRemote(eq(StringNonKey.AutomationEvents), any<String>(), any<Long>())

        whenever(config.APS).thenReturn(false)       // start() returns right after the persistence subs
        whenever(config.AAPSCLIENT).thenReturn(true) // client by default; master-bootstrap test overrides to false

        automationRuntime = newRuntime()
    }

    private fun newRuntime() = AutomationRuntime(
        injector, aapsLogger, rh, preferences, context, fabricPrivacy, loop, rxBus, constraintChecker,
        aapsSchedulers, config, locationServiceHelper, dateUtil, activePlugin, timerUtil, receiverStatusStore,
        uel, profileRepository, sceneApi
    )

    private fun event(title: String) = AutomationEventObject(injector).apply {
        this.title = title
        trigger = TriggerConnector(injector)
    }

    @Test
    fun `an edit persists once and a verbatim reload writes nothing`() = runTest {
        val sutScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        automationRuntime.start(sutScope)
        advanceUntilIdle() // collectors subscribe; drop(1) consumes the load seed

        // A genuine edit persists exactly once (edit-driven requestPersist → debounced storeToSP). The
        // resulting write re-triggers the self-observe → loadFromSP, but a verbatim load never persists,
        // so it does NOT spin into a second write.
        automationRuntime.add(event("t1"))
        advanceUntilIdle()

        assertThat(automationRuntime.events.value.map { it.title }).contains("t1")
        val writes = localPutCount
        assertThat(writes).isEqualTo(1)
        assertThat(autoFlow.value).contains("t1")

        // Reload (the master-apply path) is verbatim — zero new writes.
        automationRuntime.loadFromSP()
        advanceUntilIdle()
        assertThat(localPutCount).isEqualTo(writes)

        sutScope.cancel()
    }

    @Test
    fun `self-observe applies an external master push without echoing back`() = runTest {
        val sutScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        automationRuntime.start(sutScope)
        advanceUntilIdle()

        automationRuntime.add(event("only-on-master"))
        advanceUntilIdle()
        val masterConfig = autoFlow.value                       // canonical config, as the master would hold it
        val masterSize = automationRuntime.events.value.size

        // Client diverges locally to a longer list.
        automationRuntime.add(event("local-extra"))
        advanceUntilIdle()
        assertThat(automationRuntime.events.value.size).isEqualTo(masterSize + 1)
        val writesBeforePush = localPutCount

        // Master pushes its (different) config: a putRemote-style external write feeds observe() WITHOUT
        // counting as a local put.
        autoFlow.value = masterConfig
        advanceUntilIdle()

        // Self-observe applied it (in-memory list reverted to the master's size)…
        assertThat(automationRuntime.events.value.size).isEqualTo(masterSize)
        // …and the apply did NOT echo a local write back (verbatim load never persists).
        assertThat(localPutCount).isEqualTo(writesBeforePush)

        sutScope.cancel()
    }

    @Test
    fun `master adopts an external client push at runtime`() = runTest {
        // Same scenario as the client self-observe test above, but on a MASTER (AAPSCLIENT=false).
        // Regression guard: the self-observe was once gated `if (config.AAPSCLIENT)`, so a client→master
        // push (applied by ClientControlReceiver via putRemote → observe) never reloaded the master's
        // in-memory list — "message received, automations not updated". APS=false here only skips the
        // processing loop; the observe/reload path is flavor-independent.
        whenever(config.AAPSCLIENT).thenReturn(false)
        val master = newRuntime()
        val sutScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        master.start(sutScope)
        advanceUntilIdle()

        master.add(event("base"))
        advanceUntilIdle()
        val canonical = autoFlow.value
        val baseSize = master.events.value.size

        // Master diverges locally (e.g. a stale list before the push arrives).
        master.add(event("local-extra"))
        advanceUntilIdle()
        assertThat(master.events.value.size).isEqualTo(baseSize + 1)
        val writesBeforePush = localPutCount

        // A client pushes its config; the receiver applies it via putRemote, feeding observe() without
        // a local put. The master must re-parse and adopt it.
        autoFlow.value = canonical
        advanceUntilIdle()

        assertThat(master.events.value.size).isEqualTo(baseSize)   // master reloaded and adopted the push
        assertThat(localPutCount).isEqualTo(writesBeforePush)      // verbatim load — no echo write back

        sutScope.cancel()
    }

    @Test
    fun `master bootstrap backfills missing ids and persists once via putRemote`() = runTest {
        // Build a canonical event JSON via the runtime, then strip its id to simulate legacy id-less data.
        val producerScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        automationRuntime.start(producerScope) // client (AAPSCLIENT=true) from setup
        advanceUntilIdle()
        automationRuntime.add(event("legacy"))
        advanceUntilIdle()
        val legacy = JSONArray(autoFlow.value).also { it.getJSONObject(0).remove("id") }.toString()
        producerScope.cancel()

        // Now bootstrap a MASTER (AAPSCLIENT=false) over the id-less pref.
        whenever(config.AAPSCLIENT).thenReturn(false)
        autoFlow.value = legacy
        localPutCount = 0
        remoteWrites.clear()
        val master = newRuntime()
        val masterScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        master.start(masterScope)
        advanceUntilIdle()

        // Master bootstrap assigned an id and persisted the canonical form exactly once, via putRemote
        // (version-neutral master-wins) — NOT a local put that would be a self-edit.
        assertThat(remoteWrites).hasSize(1)
        assertThat(remoteWrites.single()).contains("\"id\"")
        assertThat(localPutCount).isEqualTo(0)

        masterScope.cancel()
    }

    @Test
    fun `master bootstrap does not rewrite an already-canonical pref`() = runTest {
        // Capture the runtime's canonical (id-ful) serialization of one event.
        val producerScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        automationRuntime.start(producerScope)
        advanceUntilIdle()
        automationRuntime.add(event("kept"))
        advanceUntilIdle()
        val canonical = autoFlow.value
        producerScope.cancel()

        // A master booting over an already-canonical pref must NOT re-persist (idempotent → no churn).
        whenever(config.AAPSCLIENT).thenReturn(false)
        autoFlow.value = canonical
        localPutCount = 0
        remoteWrites.clear()
        val master = newRuntime()
        val masterScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        master.start(masterScope)
        advanceUntilIdle()

        assertThat(remoteWrites).isEmpty()
        assertThat(localPutCount).isEqualTo(0)

        masterScope.cancel()
    }
}
