package app.aaps.plugins.configuration.configBuilder

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.AppExit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers the PR2 ConfigBuilder ↔ ActivePlugin-key bridge write path: a plugin switch mirrors the new
 * selection (by [PluginBase.pluginId]) into the synthetic synced key, and the write is suppressed when
 * the key already holds that value (the no-op guard that prevents spurious publishes / echo). The async
 * observer-adoption direction is left to integration/device coverage.
 */
internal class ConfigBuilderImplTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var pumpSync: PumpSync
    @Mock private lateinit var appExit: AppExit
    @Mock private lateinit var config: Config
    @Mock private lateinit var sens2: PluginBase
    @Mock private lateinit var sens2Desc: PluginDescription

    private lateinit var sut: ConfigBuilderImpl
    private var sensEnabled = false   // stateful so setPluginEnabled is reflected by isEnabled

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(config.AAPSCLIENT).thenReturn(true)   // skip the UEL block in performPluginSwitch

        whenever(sens2.getType()).thenReturn(PluginType.SENSITIVITY)
        whenever(sens2.pluginId).thenReturn("Sens2")
        whenever(sens2.pluginDescription).thenReturn(sens2Desc)
        whenever(sens2Desc.alwaysEnabled).thenReturn(false)
        whenever(sens2.isEnabled(PluginType.SENSITIVITY)).thenAnswer { sensEnabled }
        whenever(sens2.isEnabled()).thenAnswer { sensEnabled }
        // Returns the start/stop job now, so a caller can wait for the plugin to really start or stop.
        // Null here: this stub changes the flag and nothing else has to be waited for.
        whenever(sens2.setPluginEnabled(eq(PluginType.SENSITIVITY), any())).thenAnswer { sensEnabled = it.getArgument(1); null }

        whenever(activePlugin.getPluginsList()).thenReturn(arrayListOf<PluginBase>(sens2))
        whenever(activePlugin.getSpecificPluginsListByInterface(any())).thenReturn(arrayListOf<PluginBase>(sens2))
        whenever(activePlugin.getSpecificPluginsList(any())).thenReturn(arrayListOf<PluginBase>(sens2))
        whenever(preferences.get(any<StringNonPreferenceKey>())).thenReturn("")

        sut = ConfigBuilderImpl(aapsLogger, rh, preferences, rxBus, activePlugin, uel, pumpSync, appExit, config)
    }

    /** A local plugin switch mirrors the new selection (by pluginId) into the synced ActivePlugin key. */
    @Test
    fun performPluginSwitchMirrorsSelectionIntoKey() {
        sut.performPluginSwitch(sens2, true, PluginType.SENSITIVITY)
        verify(preferences).put(StringNonKey.ActivePluginSensitivity, "Sens2")
    }

    /** No-op guard: when the key already holds the current selection, the mirror write is skipped. */
    @Test
    fun syncSkipsNoOpWriteWhenUnchanged() {
        whenever(preferences.get(StringNonKey.ActivePluginSensitivity)).thenReturn("Sens2")  // already current
        sut.performPluginSwitch(sens2, true, PluginType.SENSITIVITY)
        verify(preferences, never()).put(eq(StringNonKey.ActivePluginSensitivity), any<String>())
    }

    /**
     * SSOT guard: PluginType.singleSelect / selectionSyncs are the single source of truth, and the
     * activePluginKey wiring must agree with them for EVERY type — a single-select type has exactly one
     * ActivePlugin key, and a selection-syncing type's key carries a SyncSpec. Fails the build if either
     * drifts (e.g. a new synced category whose key wasn't flagged, or a flag flipped without the key).
     */
    @Test
    fun activePluginKeyWiringMatchesPluginTypeFlags() {
        PluginType.entries.forEach { type ->
            assertEquals(type.singleSelect, sut.activePluginKey(type) != null, "singleSelect mismatch for $type")
            assertEquals(type.selectionSyncs, sut.activePluginKey(type)?.sync != null, "selectionSyncs mismatch for $type")
        }
    }

    // ---- applyConfiguration ----

    /** One observer per synced key, however many times the configuration has been applied. */
    private val observedKeys get() = sut.syncedSelectionTypes.count { sut.activePluginKey(it) != null }

    private fun stubKeyObservation(flow: MutableStateFlow<String>) {
        whenever(preferences.observe(any<StringNonPreferenceKey>())).thenReturn(flow)
    }

    /**
     * Applying the configuration on a live app must not leave a second set of observers behind.
     *
     * `initialize` used to launch a collector per synced key with nothing cancelling the previous
     * ones, which was harmless while only startup called it. It is not harmless now that applying
     * imported settings calls it too: every later selection change - including one pushed by the
     * master - would run `performPluginSwitch` once more for each import done in that session, and
     * nothing would say so.
     */
    @Test
    fun `applying the configuration twice leaves one observer per key`() = runBlocking {
        val keyFlow = MutableStateFlow("")
        stubKeyObservation(keyFlow)

        sut.applyConfiguration()
        sut.applyConfiguration()

        withTimeout(5.seconds) { keyFlow.subscriptionCount.first { it == observedKeys } }
        // ...and it stays there. Without the cancel, the second set subscribes on top of the first and
        // the count settles at twice this instead.
        delay(200)
        assertThat(keyFlow.subscriptionCount.value).isEqualTo(observedKeys)
    }

    /**
     * The difference between this and `initialize`: it returns only once the plugins it started and
     * stopped have finished doing so.
     *
     * `setPluginEnabled` sets the flag now but only *schedules* `onStart` / `onStop`. Returning before
     * those have run would give away the whole point of applying with the pump held - the caller would
     * let commands flow again while a pump driver was still being torn down.
     */
    @Test
    fun `applying the configuration waits for the plugins to start`() = runBlocking {
        stubKeyObservation(MutableStateFlow(""))
        whenever(preferences.getIfExists(eq(BooleanComposedKey.ConfigBuilderEnabled), any())).thenReturn(true)
        val stillStarting = Job()
        whenever(sens2.setPluginEnabled(eq(PluginType.SENSITIVITY), any())).thenReturn(stillStarting)

        val applying = launch { sut.applyConfiguration() }
        delay(200)
        assertThat(applying.isCompleted).isFalse()   // the plugin has not finished starting

        stillStarting.complete()
        withTimeout(5.seconds) { applying.join() }
    }

    /**
     * Applying on a live app must announce the change, or nothing on screen follows it.
     *
     * Device-found: importing a file that selected a different pump switched the plugin correctly -
     * the log showed "Starting: Virtual Pump" / "Stopping: DanaR" - but every screen went on drawing
     * the old pump, because `loadPref` flips plugin state directly and skips everything
     * `performPluginSwitch` emits. `initialize` never needed these; nothing is on screen at startup.
     */
    @Test
    fun `applying the configuration announces the change`() = runBlocking {
        stubKeyObservation(MutableStateFlow(""))

        sut.applyConfiguration()

        verify(rxBus).send(any<EventConfigBuilderChange>())
    }

    /** The Configuration screen refreshes off this one rather than the raw key flow. */
    @Test
    fun `applying the configuration signals an active-selection change`() = runBlocking {
        stubKeyObservation(MutableStateFlow(""))
        // The flow has no replay, so the collector has to be subscribed before the emit or it misses it.
        val seen = CompletableDeferred<Unit>()
        val collector = launch { sut.activeSelectionChanges.collect { seen.complete(Unit) } }
        delay(200)

        sut.applyConfiguration()

        withTimeout(5.seconds) { seen.await() }
        collector.cancel()
    }

    /** Startup keeps the old behaviour: it schedules the plugin starts and does not wait for them. */
    @Test
    fun `initialize does not wait for the plugins to start`() {
        stubKeyObservation(MutableStateFlow(""))
        whenever(preferences.getIfExists(eq(BooleanComposedKey.ConfigBuilderEnabled), any())).thenReturn(true)
        whenever(sens2.setPluginEnabled(eq(PluginType.SENSITIVITY), any())).thenReturn(Job())   // never completes

        sut.initialize()   // returns, rather than hanging on a plugin that is still starting
    }
}
